package git.ghsmith.rrtviewer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

@Path("NormalizedHierarchyNodeEdit")
public class NormalizedHierarchyNodeResourceEdit {

    public final static SimpleDateFormat sdfDay = new SimpleDateFormat("yyyyMMdd");        
    public final static SimpleDateFormat sdfTimestamp = new SimpleDateFormat("yyyyMMddHHmmss");        

    public final static String directory = "c:/stuff/";
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsTree {
        public String id;
        public String text;
        public Boolean children;
        public String nodeType;
        public String dispId;
        public Boolean hasEap;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JsTreeMove {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Node {
            public String id;
        }
        public Node node;
        @JsonProperty("old_parent")
        public String oldParent;
        @JsonProperty("old_position")
        public int oldPosition;
        public String parent;
        public int position;
    }

    public static final String[] attributes = {
        "RESULT_REVIEW_TREE",
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
    
    private static final Map<String, NormalizedHierarchyNode> rootNhnByFile = new HashMap<>();
    private static final Map<String, Map<String, NormalizedHierarchyNode>> nhnMapByIdByFile = new HashMap<>();
    private static final Map<String, List<Change>> changeLogByFile = new HashMap<>();
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{csvFileName}/{id}")
    public NormalizedHierarchyNode getJson(@PathParam("csvFileName") String csvFileName, @PathParam("id") String id, @Context HttpServletResponse response) {
        response.setHeader("Expires", "0");
        loadCache(csvFileName);
        Map<String, NormalizedHierarchyNode> nhnMapById = nhnMapByIdByFile.get(csvFileName);
        return nhnMapById.get(id);
    }
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{csvFileName}/timestamp")
    public String getTimestamp(@PathParam("csvFileName") String csvFileName, @QueryParam("parentId") String parentId, @Context HttpServletResponse response) {
        response.setHeader("Expires", "0");
        loadCache(csvFileName);
        File file = new File(directory + csvFileName + ".csv");
        return (new Date(file.lastModified())).toString();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{csvFileName}/")
    public List<NormalizedHierarchyNode> getJsonByParentId(@PathParam("csvFileName") String csvFileName, @QueryParam("parentId") String parentId, @Context HttpServletResponse response) {
        response.setHeader("Expires", "0");
        loadCache(csvFileName);
        Map<String, NormalizedHierarchyNode> nhnMapById = nhnMapByIdByFile.get(csvFileName);
        return nhnMapById.get(parentId).children;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{csvFileName}/jsTree")
    public List<JsTree> getJsonByParentIdJsTree(@PathParam("csvFileName") String csvFileName, @QueryParam("parentId") String parentId, @Context HttpServletResponse response) {
        List jsTreeList = new ArrayList();
        for(NormalizedHierarchyNode nhn : getJsonByParentId(csvFileName, parentId, response)) {
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
    
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{csvFileName}/jsTree/move")
    public Change putJsonJsTreeMove(@PathParam("csvFileName") String csvFileName, @Context HttpServletResponse response, JsTreeMove jsTreeMove) {
        response.setHeader("Expires", "0");
        loadCache(csvFileName);
        Map<String, NormalizedHierarchyNode> nhnMapById = nhnMapByIdByFile.get(csvFileName);
        NormalizedHierarchyNode rootNhn = rootNhnByFile.get(csvFileName);
        NormalizedHierarchyNode node = nhnMapById.get(jsTreeMove.node.id);
        NormalizedHierarchyNode oldParent = jsTreeMove.oldParent.equals("#") ? rootNhn : nhnMapById.get(jsTreeMove.oldParent);
        NormalizedHierarchyNode newParent = jsTreeMove.parent.equals("#") ? rootNhn : nhnMapById.get(jsTreeMove.parent);
        String oldChartsAfterString = "position 0 (first in grouper)";
        if(jsTreeMove.oldPosition > 0) {
            NormalizedHierarchyNode oldChartsAfter = oldParent.children.get(jsTreeMove.oldPosition - 1);
            oldChartsAfterString = String.format("position %d (just after '%s' [%s])", jsTreeMove.oldPosition, oldChartsAfter.disp, oldChartsAfter.attributeMap.get("LRR_ID") != null && oldChartsAfter.attributeMap.get("LRR_ID").length() > 0 ? oldChartsAfter.attributeMap.get("LRR_ID") : oldChartsAfter.dispId);
        }
        oldParent.children.remove(jsTreeMove.oldPosition);
        newParent.children.add(jsTreeMove.position, node);
        node.parentId = newParent.id.equals("ROOT") ? null : newParent.id;
        node.parent = newParent;
        for(int x = 0; x < oldParent.children.size(); x++) {
            oldParent.children.get(x).seq = new Long(x);
        }
        for(int x = 0; x < newParent.children.size(); x++) {
            newParent.children.get(x).seq = new Long(x);
        }
        Change change = new Change();
        change.timestamp = new Date();
        change.timestampFormatted = change.timestamp.toString();
        change.id = node.attributeMap.get("LRR_ID") != null && node.attributeMap.get("LRR_ID").length() > 0 ? node.attributeMap.get("LRR_ID") : node.dispId;
        change.lrrName = node.attributeMap.get("LRR_NAME");
        change.chartingName = node.disp;
        String newChartsAfterString = "position 0 (first in grouper)";
        if(jsTreeMove.position > 0) {
            NormalizedHierarchyNode newChartsAfter = newParent.children.get(jsTreeMove.position - 1);
            newChartsAfterString = String.format("position %d (just after '%s' [%s])", jsTreeMove.position, newChartsAfter.disp, newChartsAfter.attributeMap.get("LRR_ID") != null && newChartsAfter.attributeMap.get("LRR_ID").length() > 0 ? newChartsAfter.attributeMap.get("LRR_ID") : newChartsAfter.dispId);
        }
        if(oldParent == newParent) {
            change.type = "re-order within a grouper";
            change.description = String.format("within grouper '%s' [%s], moved from %s to %s", oldParent.disp, oldParent.dispId, oldChartsAfterString, newChartsAfterString);
        }
        else {
            change.type = "re-order across groupers";
            change.description = String.format("moved from grouper '%s' [%s] %s to grouper '%s' [%s] %s", oldParent.disp, oldParent.dispId, oldChartsAfterString, newParent.disp, newParent.dispId, newChartsAfterString);
        }
        changeLogByFile.get(csvFileName).add(change);
        return change;
    }
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{csvFileName}/searchResult")
    public Map<String, Integer[]> getJsonSearchResult(@PathParam("csvFileName") String csvFileName, @QueryParam("searchString") String searchString, @Context HttpServletResponse response) {
        response.setHeader("Expires", "0");
        loadCache(csvFileName);
        NormalizedHierarchyNode rootNhn = rootNhnByFile.get(csvFileName);
        Map<String, Integer[]> searchResultMap = new HashMap();
        for(String searchStringParsed : (searchString.split(("\\|")))) {
            search(rootNhn, searchStringParsed.trim(), searchResultMap);
        }
        return searchResultMap;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{csvFileName}/rollback")
    public void getJsonRollback(@PathParam("csvFileName") String csvFileName, @Context HttpServletResponse response) {
        response.setHeader("Expires", "0");
        synchronized(nhnMapByIdByFile) {
            nhnMapByIdByFile.remove(csvFileName);
            rootNhnByFile.remove(csvFileName);
            changeLogByFile.remove(csvFileName);
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{csvFileName}/commit")
    public String getJsonCommit(@PathParam("csvFileName") String csvFileName, @Context HttpServletResponse response) {
        Date now = new Date();
        response.setHeader("Expires", "0");
        loadCache(csvFileName);
        Map<String, NormalizedHierarchyNode> nhnMapById = nhnMapByIdByFile.get(csvFileName);
        NormalizedHierarchyNode rootNhn = rootNhnByFile.get(csvFileName);
        try {
            Files.move(
                Paths.get(String.format(directory + csvFileName + ".csv.")),
                Paths.get(String.format(directory + csvFileName + ".csv." + sdfTimestamp.format(now)))
            );       
            FileOutputStream fos = new FileOutputStream(directory + csvFileName + ".csv");
            PrintStream fps = new PrintStream(fos);
            fps.println(nhnMapById.get("GHS0").toStringCsvHeader());
            printTree(fps, rootNhn);
            fps.close();
        }    
        catch(IOException e) {
            throw new RuntimeException(e);
        }
        try {
            Files.copy(
                Paths.get(String.format(directory + csvFileName + ".log.")),
                Paths.get(String.format(directory + csvFileName + ".log." + sdfTimestamp.format(now)))
            );       
            FileOutputStream fos = new FileOutputStream(directory + csvFileName + ".log", true);
            PrintStream fps = new PrintStream(fos);
            fps.println(Change.toStringCsvHeader());
            for(Change change : changeLogByFile.get(csvFileName)) {
                fps.println(change.toStringCsv());
            }
            fps.close();
        }    
        catch(IOException e) {
            throw new RuntimeException(e);
        }
        int commitSize = changeLogByFile.get(csvFileName).size();
        synchronized(nhnMapByIdByFile) {
            nhnMapByIdByFile.remove(csvFileName);
            rootNhnByFile.remove(csvFileName);
            changeLogByFile.remove(csvFileName);
        }
        return String.format("%d changes committed.", commitSize);
    }
    
    private void loadCache(String csvFileName) {
        synchronized(nhnMapByIdByFile) {
            Map<String, NormalizedHierarchyNode> nhnMapById = nhnMapByIdByFile.get(csvFileName);
            if(nhnMapById == null) {
                if(!csvFileName.matches("^[a-z0-9_]*$")) {
                    throw new RuntimeException("file name rejected");
                }
                nhnMapById = new HashMap<>();
                NormalizedHierarchyNode rootNhn = new NormalizedHierarchyNode();
                try {
                    rootNhn = new NormalizedHierarchyNode();
                    rootNhn.id = "ROOT";
                    Reader in = new FileReader(directory + csvFileName + ".csv");
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
                    normalizeCollatingSequenceNumbers(rootNhn);
                    in.close();
                }
                catch(FileNotFoundException ex) {
                    Logger.getLogger(NormalizedHierarchyNodeResourceEdit.class.getName()).log(Level.SEVERE, null, ex);
                    throw new RuntimeException(ex);
                } catch (IOException ex) {
                    Logger.getLogger(NormalizedHierarchyNodeResourceEdit.class.getName()).log(Level.SEVERE, null, ex);
                    throw new RuntimeException(ex);
                }
                nhnMapByIdByFile.put(csvFileName, nhnMapById);
                rootNhnByFile.put(csvFileName, rootNhn);
                changeLogByFile.put(csvFileName, new ArrayList<Change>());
            }   
        }
    }

    private void search(NormalizedHierarchyNode nhn, String searchString, Map<String, Integer[]> searchResultMap) {
        if(nhn.containsIgnoreCase(searchString)) {
            NormalizedHierarchyNode nhnWalker = nhn;
            while(nhnWalker != null) {
                if(searchResultMap.get(nhnWalker.id) == null) {
                    //searchResultMap.put(nhnWalker.id, new Integer[] {0, 0, 0, 0, 0});
                    searchResultMap.put(nhnWalker.id, new Integer[] {0});
                }
                //searchResultMap.get(nhnWalker.id)[searchResultArrayKey.get(nhn.getNodeType())]++;
                searchResultMap.get(nhnWalker.id)[0]++;
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
    
    private void normalizeCollatingSequenceNumbers(NormalizedHierarchyNode nhn) {
        long seq = 0;
        for(NormalizedHierarchyNode childNhn : nhn.children) {
            childNhn.seq = seq++;
            normalizeCollatingSequenceNumbers(childNhn);
        }
    }

    private void printTree(PrintStream ps, NormalizedHierarchyNode nhn) {
        if(nhn.parent != null) {
            ps.println(nhn.toStringCsv());
        }
        for(NormalizedHierarchyNode childNhn : nhn.children) {
            printTree(ps, childNhn);
        }
    }
    
}
