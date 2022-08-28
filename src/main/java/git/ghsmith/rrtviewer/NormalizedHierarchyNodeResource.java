package git.ghsmith.rrtviewer;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

@Path("NormalizedHierarchyNode")
public class NormalizedHierarchyNodeResource {

    public static class JsTree {
        public String id;
        public String text;
        public Boolean children;
        public String nodeType;
        public String dispId;
        public Boolean hasEap;
    }

    public static final String[] attributes = {
        "COMMON_NAME",
        "PROCEDURE_NAME",
        "PROCEDURE_ID",
        "PROCEDURE_CATEGORY",
        "LRR_ID",
        "LRR_NAME",
        "BASE_NAME",
        "LRR_EXTERNAL_NAME",
        "LRR_ISSUE",
        "TEST_ISSUE",
        "TEST_INACTIVE",
        "TEST_ID",
        "TEST_NAME",
        "SHOW_EAP_IN_RR",
    };
    
    private static boolean cached = false;
    private static NormalizedHierarchyNode rootNhn;
    private static final Map<String, NormalizedHierarchyNode> nhnMapById = new HashMap<String, NormalizedHierarchyNode>();
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    public NormalizedHierarchyNode getJson(@PathParam("id") String id, @Context HttpServletResponse response) {
        response.setHeader("Expires", "0");
        loadCache();
        return nhnMapById.get(id);
    }
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<NormalizedHierarchyNode> getJsonByParentId(@QueryParam("parentId") String parentId, @Context HttpServletResponse response) {
        response.setHeader("Expires", "0");
        loadCache();
        return nhnMapById.get(parentId).children;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/jsTree")
    public List<JsTree> getJsonByParentIdJsTree(@QueryParam("parentId") String parentId, @Context HttpServletResponse response) {
        List jsTreeList = new ArrayList();
        for(NormalizedHierarchyNode nhn : getJsonByParentId(parentId, response)) {
            JsTree jsTree = new JsTree();
            jsTree.id = nhn.id;
            jsTree.text = String.format("[%s] %s", nhn.nodeType, nhn.disp);
            jsTree.children = !nhn.children.isEmpty();
            jsTree.nodeType = nhn.nodeType;
            jsTree.dispId = nhn.dispId;
            jsTree.hasEap = nhn.hasEap;
            jsTreeList.add(jsTree);
        }
        return jsTreeList;
    }
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/searchResult")
    public Map<String, Integer[]> getJsonSearchResult(@QueryParam("searchString") String searchString, @Context HttpServletResponse response) {
        response.setHeader("Expires", "0");
        loadCache();
        Map<String, Integer[]> searchResultMap = new HashMap();
        for(String searchStringParsed : (searchString.split(("\\|")))) {
            search(rootNhn, searchStringParsed.trim(), searchResultMap);
        }
        return searchResultMap;
    }

    private void loadCache() {
        synchronized(nhnMapById) {
            if(!cached) {
                try {
                    rootNhn = new NormalizedHierarchyNode();
                    rootNhn.id = "ROOT";
                    Reader in = new FileReader("c:/stuff/epic_rrt_normalized.csv");
                    CSVParser parser = CSVParser.parse(in, CSVFormat.DEFAULT.withFirstRecordAsHeader());
                    for (CSVRecord record : parser) {
                        NormalizedHierarchyNode nhn = new NormalizedHierarchyNode();
                        nhn.id = record.get("ID");
                        nhn.parentId = record.get("PARENT_ID");
                        nhn.nodeType = record.get("NODE_TYPE");
                        nhn.seq = Long.valueOf(record.get("COLLATING_SEQ"));
                        nhn.disp = record.get("NODE_NAME");
                        nhn.dispId = nhn.id.replaceAll("(-.*$|RRT$|CN$)", "");
                        for(String attribute : attributes) {
                            nhn.attributeMap.put(attribute, record.get(attribute));
                        }
                        nhnMapById.put(nhn.id, nhn);
                    }
                    for(NormalizedHierarchyNode nhn : nhnMapById.values()) {
                        if(nhn.parentId == null || nhn.parentId.length() == 0) {
                            nhn.parent = rootNhn;
                        }
                        else {
                            nhn.parent = nhnMapById.get(nhn.parentId);
                        }
                        nhn.parent.children.add(nhn);
                    }
                    nhnMapById.put(rootNhn.id, rootNhn);
                    for(NormalizedHierarchyNode nhn : nhnMapById.values()) {
                        Collections.sort(nhn.children);
                        nhn.hasEap = searchForEap(nhn);
                    }
                }
                catch(FileNotFoundException ex) {
                    Logger.getLogger(NormalizedHierarchyNodeResource.class.getName()).log(Level.SEVERE, null, ex);
                    throw new RuntimeException(ex);
                } catch (IOException ex) {
                    Logger.getLogger(NormalizedHierarchyNodeResource.class.getName()).log(Level.SEVERE, null, ex);
                    throw new RuntimeException(ex);
                }
                cached = true;
            }   
        }
    }

    private void search(NormalizedHierarchyNode nhn, String searchString, Map<String, Integer[]> searchResultMap) {
        if(nhn.containsIgnoreCase(searchString)) {
            NormalizedHierarchyNode nhnWalker = nhn;
            while(nhnWalker != null) {
                if(searchResultMap.get(nhnWalker.getId()) == null) {
                    //searchResultMap.put(nhnWalker.getId(), new Integer[] {0, 0, 0, 0, 0});
                    searchResultMap.put(nhnWalker.getId(), new Integer[] {0});
                }
                //searchResultMap.get(nhnWalker.getId())[searchResultArrayKey.get(nhn.getNodeType())]++;
                searchResultMap.get(nhnWalker.getId())[0]++;
                nhnWalker = nhnWalker.parent;
            }
        }
        for(NormalizedHierarchyNode childNhn : nhn.children) {
            search(childNhn, searchString, searchResultMap);
        }
    }    

    private boolean searchForEap(NormalizedHierarchyNode nhn) {
        if(nhn.nodeType != null && nhn.nodeType.equals("EAP")) {
            return true;
        }
        for(NormalizedHierarchyNode childNhn : nhn.children) {
            if(searchForEap(childNhn)) {
                return true;
            }
        }
        return false;
    }    
    
}
