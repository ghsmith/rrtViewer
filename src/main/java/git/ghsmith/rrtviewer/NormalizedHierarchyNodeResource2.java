package git.ghsmith.rrtviewer;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

@Path("NormalizedHierarchyNode2")
public class NormalizedHierarchyNodeResource2 {

    public static final String directory = "/home/ec2-user/";
    //public static final String directory = "c:/stuff/";
    
    public static class JsTree {
        public String id;
        public String text;
        public Boolean children;
        public String nodeType;
        public String dispId;
        public Boolean hasEap;
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
    
    private static final Map<String, Grouper> grouperMap = new LinkedHashMap<>();
    private static final Map<String, Lrr> lrrMap = new LinkedHashMap<>();
    private static final Map<String, Lrr> procedureMap = new LinkedHashMap<>();
    private static final Map<String, Lrr> chartingProcedureMap = new LinkedHashMap<>();
    private static boolean cached = false;
    private static NormalizedHierarchyNode rootNhn;
    private static final Map<String, NormalizedHierarchyNode> nhnMapById = new HashMap<>();
    
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
    public Map<String, Integer[]> getJsonSearchResult(@QueryParam("searchString") String searchString, @QueryParam("avoidInactiveEaps") boolean avoidInactiveEaps, @Context HttpServletResponse response) {
        response.setHeader("Expires", "0");
        loadCache();
        Map<String, Integer[]> searchResultMap = new HashMap();
        for(String searchStringParsed : (searchString.split(("\\|")))) {
            search(rootNhn, searchStringParsed.trim(), searchResultMap, avoidInactiveEaps);
        }
        return searchResultMap;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/searchWithChildrenResult")
    public Map<String, Integer[]> getJsonSearchWithChildrenResult(@QueryParam("searchString") String searchString, @QueryParam("avoidInactiveEaps") boolean avoidInactiveEaps, @Context HttpServletResponse response) {
        response.setHeader("Expires", "0");
        loadCache();
        Map<String, Integer[]> searchResultMap = new HashMap();
        for(String searchStringParsed : (searchString.split(("\\|")))) {
            searchWithChildren(rootNhn, searchStringParsed.trim(), searchResultMap, avoidInactiveEaps);
        }
        return searchResultMap;
    }

    private void loadCache() {
        synchronized(nhnMapById) {
            if(!cached) {
                try {
                    rootNhn = new NormalizedHierarchyNode();
                    rootNhn.id = "ROOT";
                    {
                        Reader in = new FileReader(directory + "grouper.csv");
                        CSVParser parser = CSVParser.parse(in, CSVFormat.DEFAULT.withFirstRecordAsHeader());
                        for (CSVRecord record : parser) {
                            Grouper grouper = Grouper.load(record);
                            grouperMap.put(grouper.id, grouper);
                        }
                        in.close();
                    }
                    {
                        Reader in = new FileReader(directory + "lrr.csv");
                        CSVParser parser = CSVParser.parse(in, CSVFormat.DEFAULT.withFirstRecordAsHeader());
                        int seq = 0;
                        for (CSVRecord record : parser) {
                            if(
                                record.get("LRR ID") != null && record.get("LRR ID").length() > 0
                            ) {
                                Lrr lrr = Lrr.load(record, seq++);
                                lrrMap.put(lrr.lrrId, lrr);
                            }
                        }
                        in.close();
                    }
                    {
                        Reader in = new FileReader(directory + "lrr.csv");
                        CSVParser parser = CSVParser.parse(in, CSVFormat.DEFAULT.withFirstRecordAsHeader());
                        int seq = 0;
                        for (CSVRecord record : parser) {
                            if(
                                record.get("LRR ID") != null && record.get("LRR ID").length() > 0
                                && record.get("Procedure ID") != null && record.get("Procedure ID").length() > 0
                            ) {
                                Lrr lrr = Lrr.load(record, seq++);
                                procedureMap.put(lrr.procedureId + "-" + lrr.lrrId, lrr);
                            }
                        }
                        in.close();
                    }
                    {
                        Reader in = new FileReader(directory + "lrr.csv");
                        CSVParser parser = CSVParser.parse(in, CSVFormat.DEFAULT.withFirstRecordAsHeader());
                        int seq = 0;
                        for (CSVRecord record : parser) {
                            if(
                                (record.get("LRR ID") == null || record.get("LRR ID").length() == 0)
                                && record.get("Procedure ID") != null && record.get("Procedure ID").length() > 0
                                && "Yes".equals(record.get("Show EAP in RR?"))
                            ) {
                                Lrr lrr = Lrr.load(record, seq++);
                                chartingProcedureMap.put(lrr.procedureId + "-CHARTING", lrr);
                            }
                        }
                        in.close();
                    }
                }
                catch (IOException ex) {
                    Logger.getLogger(NormalizedHierarchyNodeResource2.class.getName()).log(Level.SEVERE, null, ex);
                    throw new RuntimeException(ex);
                }
                // load groupers
Logger.getLogger(NormalizedHierarchyNodeResource2.class.getName()).log(Level.INFO, "loading groupers...");
                rootNhn = new NormalizedHierarchyNode();
                rootNhn.id = "ROOT";
                for(String id : grouperMap.keySet()) {
                    Grouper cGrouper = grouperMap.get(id);
                    if(cGrouper.parentId == null || cGrouper.parentId.length() == 0) {
                        NormalizedHierarchyNode nhn = NormalizedHierarchyNode.loadFromGrouper(cGrouper);
                        nhnMapById.put(nhn.id, nhn);
                        rootNhn.children.add(nhn);
                        nhn.parent = rootNhn;
                        new Object() {
                            void loadChildren(NormalizedHierarchyNode nhn) {
                                for(String id : grouperMap.keySet()) {
                                    Grouper cGrouper = grouperMap.get(id);
                                    if(cGrouper.parentId != null && cGrouper.parentId.length() > 0 && cGrouper.parentId.equals(nhn.id)) {
                                        NormalizedHierarchyNode childNhn = NormalizedHierarchyNode.loadFromGrouper(cGrouper);
                                        nhnMapById.put(childNhn.id, childNhn);
                                        nhn.children.add(childNhn);
                                        childNhn.parent = nhn;
                                        loadChildren(childNhn);
                                    }
                                }
                            }
                        }.loadChildren(nhn);
                    }
                }
                // load external names and common names
Logger.getLogger(NormalizedHierarchyNodeResource2.class.getName()).log(Level.INFO, "loading external names and common names...");
                Map<String, Lrr> commonNameMap = new HashMap<>();
                {
                    for(String id : lrrMap.keySet()) {
                        Lrr cLrr = lrrMap.get(id);
                        if(cLrr.commonName == null || cLrr.commonName.length() == 0) {
                            continue;
                        }
                        Lrr lrr = commonNameMap.get(cLrr.commonName);
                        if(lrr == null || (Long.valueOf(cLrr.lrrId) < Long.valueOf(lrr.lrrId))) {
                            commonNameMap.put(cLrr.commonName, cLrr);
                        }
                    }
                    for(String commonName : commonNameMap.keySet()) {
                        Lrr cLrr = commonNameMap.get(commonName);
                        for(String id : grouperMap.keySet()) {
                            Grouper cGrouper = grouperMap.get(id);
                            if(cGrouper.pathPrecalculated.equals(cLrr.resultReviewTree)) {
                                NormalizedHierarchyNode nhnRrt = NormalizedHierarchyNode.loadRrtFromLrr(cLrr, cGrouper.id, cLrr);
                                nhnMapById.put(nhnRrt.id, nhnRrt);
                                nhnMapById.get(nhnRrt.parentId).children.add(nhnRrt);
                                nhnRrt.parent = nhnMapById.get(nhnRrt.parentId);
                                NormalizedHierarchyNode nhnCn = NormalizedHierarchyNode.loadCnFromLrr(cLrr, nhnRrt.id, cLrr);
                                nhnMapById.put(nhnCn.id, nhnCn);
                                nhnMapById.get(nhnCn.parentId).children.add(nhnCn);
                                nhnCn.parent = nhnMapById.get(nhnCn.parentId);
                                {
                                    for(NormalizedHierarchyNode nhn : nhnMapById.values()) {
                                        if("RRT".equals(nhn.nodeType) && nhn != nhnRrt && nhn.disp.equals(nhnRrt.disp)) {
                                            nhn.duplicatedElsewhere = true;
                                            nhnRrt.duplicatedElsewhere = true;
                                        }
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
                // load LRRs
Logger.getLogger(NormalizedHierarchyNodeResource2.class.getName()).log(Level.INFO, "loading LRRs...");
                for(String lrrId : lrrMap.keySet()) {
                    Lrr cLrr = lrrMap.get(lrrId);
                    if(cLrr.commonName == null || cLrr.commonName.length() == 0) {
                        continue;
                    }
                    NormalizedHierarchyNode nhnCn = nhnMapById.get(commonNameMap.get(cLrr.commonName).lrrId + "CN");
                    if(nhnCn == null) {
                        continue;
                    }
                    NormalizedHierarchyNode nhn = NormalizedHierarchyNode.loadLrrFromLrr(cLrr, nhnCn.id, cLrr);
                    nhnMapById.put(nhn.id, nhn);
                    nhnMapById.get(nhn.parentId).children.add(nhn);
                    nhn.parent = nhnMapById.get(nhn.parentId);
                }
                // load EAPs
Logger.getLogger(NormalizedHierarchyNodeResource2.class.getName()).log(Level.INFO, "loading EAPs...");
                for(String procedureId : procedureMap.keySet()) {
                    Lrr cLrr = procedureMap.get(procedureId);
                    NormalizedHierarchyNode nhnLrr = nhnMapById.get(cLrr.lrrId);
                    if(nhnLrr == null) {
                        System.out.println("could not map procedure " + cLrr.procedureName);
                        continue;
                    }
                    NormalizedHierarchyNode nhn = NormalizedHierarchyNode.loadEapFromLrr(cLrr, nhnLrr.id, cLrr);
                    nhnMapById.put(nhn.id, nhn);
                    nhnMapById.get(nhn.parentId).children.add(nhn);
                    nhn.parent = nhnMapById.get(nhn.parentId);
                }
                // load charting EAPs
Logger.getLogger(NormalizedHierarchyNodeResource2.class.getName()).log(Level.INFO, "loading charting EAPs...");
                for(String idProc : chartingProcedureMap.keySet()) {
                    Lrr cLrr = chartingProcedureMap.get(idProc);
                    for(String idGrouper : grouperMap.keySet()) {
                        Grouper cGrouper = grouperMap.get(idGrouper);
                        if(cGrouper.pathPrecalculated.equals(cLrr.resultReviewTree)) {
                            NormalizedHierarchyNode nhnRrt = NormalizedHierarchyNode.loadChartingProcedureRrtFromLrr(cLrr, cGrouper.id, cLrr);
                            nhnMapById.put(nhnRrt.id, nhnRrt);
                            nhnMapById.get(nhnRrt.parentId).children.add(nhnRrt);
                            nhnRrt.parent = nhnMapById.get(nhnRrt.parentId);
                            NormalizedHierarchyNode nhnEap = NormalizedHierarchyNode.loadChartingProcedureEapFromLrr(cLrr, nhnRrt.id, cLrr);
                            nhnMapById.put(nhnEap.id, nhnEap);
                            nhnMapById.get(nhnEap.parentId).children.add(nhnEap);
                            nhnEap.parent = nhnMapById.get(nhnEap.parentId);
                            break;
                        }
                    }
                }
                nhnMapById.put(rootNhn.id, rootNhn);
Logger.getLogger(NormalizedHierarchyNodeResource2.class.getName()).log(Level.INFO, "sorting...");
                for(NormalizedHierarchyNode nhn : nhnMapById.values()) {
                    if(nhn.duplicatedElsewhere) {
                        nhn.disp = nhn.disp + " {ALSO DUPLICATED ELSEWHERE}";
                    }
                    Collections.sort(nhn.children);
                    nhn.hasEap = searchForEap(nhn);

if(
nhn.nodeType != null && nhn.nodeType.equals("EAP") && nhn.sourceRecord != null
&& ((nhn.sourceRecord.testIssue != null && nhn.sourceRecord.testIssue.length() > 0) || (nhn.sourceRecord.testInactive != null && nhn.sourceRecord.testInactive.length() > 0))
) {
  nhn.disp = nhn.disp + " {INACTIVE}";
}

                }
Logger.getLogger(NormalizedHierarchyNodeResource2.class.getName()).log(Level.INFO, "done.");
                cached = true;
            }   
        }
    }

    private void search(NormalizedHierarchyNode nhn, String searchString, Map<String, Integer[]> searchResultMap, boolean avoidInactiveEaps) {
        if(
(
    (
        !(                
        nhn.nodeType != null && nhn.nodeType.equals("EAP") && nhn.sourceRecord != null
        && ((nhn.sourceRecord.testIssue != null && nhn.sourceRecord.testIssue.length() > 0) || (nhn.sourceRecord.testInactive != null && nhn.sourceRecord.testInactive.length() > 0))
        )
        && avoidInactiveEaps
    )
||
    !avoidInactiveEaps
)             
                && nhn.containsIgnoreCase(searchString)) {
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
            search(childNhn, searchString, searchResultMap, avoidInactiveEaps);
        }
    }    

    private void searchWithChildren(NormalizedHierarchyNode nhn, String searchString, Map<String, Integer[]> searchResultMap, boolean avoidInactiveEaps) {
        if(
(
    (
        !(                
        nhn.nodeType != null && nhn.nodeType.equals("EAP") && nhn.sourceRecord != null
        && ((nhn.sourceRecord.testIssue != null && nhn.sourceRecord.testIssue.length() > 0) || (nhn.sourceRecord.testInactive != null && nhn.sourceRecord.testInactive.length() > 0))
        )
        && avoidInactiveEaps
    )
||
    !avoidInactiveEaps
)             
                && nhn.containsIgnoreCase(searchString)) {
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
            new Object() {
                public void includeChildren(NormalizedHierarchyNode nhn, Map<String, Integer[]> searchResultMap) {
                    for(NormalizedHierarchyNode nhnChild : nhn.children) {
                        searchResultMap.put(nhnChild.id, new Integer[] {0});
                        includeChildren(nhnChild, searchResultMap);
                    }
                }
            }.includeChildren(nhn, searchResultMap);
        }
        else {
            for(NormalizedHierarchyNode childNhn : nhn.children) {
                searchWithChildren(childNhn, searchString, searchResultMap, avoidInactiveEaps);
            }
        }
    }    

    private boolean searchForEap(NormalizedHierarchyNode nhn) {
//        if(nhn.nodeType != null && nhn.nodeType.equals("EAP")) {
if(nhn.nodeType != null && nhn.nodeType.equals("EAP") && nhn.sourceRecord != null
&& ((nhn.sourceRecord.testIssue == null || nhn.sourceRecord.testIssue.length() == 0) && (nhn.sourceRecord.testInactive == null || nhn.sourceRecord.testInactive.length() == 0))) {
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
