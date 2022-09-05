package git.ghsmith.rrtviewer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import static git.ghsmith.rrtviewer.NormalizedHierarchyNodeResourceEdit.directory;
import java.io.File;
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
import java.util.LinkedHashMap;
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

@Path("NormalizedHierarchyNodeEdit2")
public class NormalizedHierarchyNodeResourceEdit2 {

    public final static SimpleDateFormat sdfDay = new SimpleDateFormat("yyyyMMdd");        
    public final static SimpleDateFormat sdfTimestamp = new SimpleDateFormat("yyyyMMddHHmmss");        

    public static final String directory = "c:/stuff/edit/";
    
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
    
    private static Map<String, Grouper> grouperMap = new LinkedHashMap<>();
    private static Map<String, Lrr> lrrMap = new LinkedHashMap<>();
    private static Map<String, Lrr> procedureMap = new LinkedHashMap<>();
    private static Map<String, Lrr> chartingProcedureMap = new LinkedHashMap<>();
    private static boolean cached = false;
    private static NormalizedHierarchyNode rootNhn;
    private static Map<String, NormalizedHierarchyNode> nhnMapById = new HashMap<>();
    private static List<Change> changeLog = new ArrayList<>();
    
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
    @Path("/timestamp")
    public String getTimestamp(@Context HttpServletResponse response) {
        response.setHeader("Expires", "0");
        loadCache();
        File file = new File(directory + "lrr.csv");
        return (new Date(file.lastModified())).toString();
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
    
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/jsTree/move")
    public Change putJsonJsTreeMove(@Context HttpServletResponse response, NormalizedHierarchyNodeResourceEdit.JsTreeMove jsTreeMove) {
        response.setHeader("Expires", "0");
        loadCache();
        NormalizedHierarchyNode node = nhnMapById.get(jsTreeMove.node.id);
        NormalizedHierarchyNode oldParent = jsTreeMove.oldParent.equals("#") ? rootNhn : nhnMapById.get(jsTreeMove.oldParent);
        NormalizedHierarchyNode newParent = jsTreeMove.parent.equals("#") ? rootNhn : nhnMapById.get(jsTreeMove.parent);
        String oldChartsAfterString = "position 0 (first in grouper)";
        if(jsTreeMove.oldPosition > 0) {
            NormalizedHierarchyNode oldChartsAfter = oldParent.children.get(jsTreeMove.oldPosition - 1);
            oldChartsAfterString = String.format("position %d (just after '%s' [%s])", jsTreeMove.oldPosition, oldChartsAfter.disp, oldChartsAfter.sourceRecord != null && oldChartsAfter.sourceRecord.lrrId != null && oldChartsAfter.sourceRecord.lrrId.length() > 0 ? oldChartsAfter.sourceRecord.lrrId : oldChartsAfter.dispId);
        }
        oldParent.children.remove(jsTreeMove.oldPosition);
        newParent.children.add(jsTreeMove.position, node);
        node.parentId = newParent.id.equals("ROOT") ? null : newParent.id;
        node.parent = newParent;
        node.sourceRecord.resultReviewTree = newParent.getPathPrecalculated();
        new Object() {
            void updateSubordinateLrrPaths(NormalizedHierarchyNode nhn, String path) {
                if(nhn.nodeType.equals("LRR")) {
                    for(NormalizedHierarchyNode nhnSeeker : nhnMapById.values()) {
                        if(nhnSeeker.sourceRecord != null && nhnSeeker.sourceRecord.lrrId != null && nhnSeeker.sourceRecord.lrrId.equals(nhn.id)) {
                            nhnSeeker.sourceRecord.resultReviewTree = path;
                        }
                    }
                }
                if(nhn.nodeType.equals("EAP") && "Yes".equals(nhn.sourceRecord.showEapInRr)) {
                    for(NormalizedHierarchyNode nhnSeeker : nhnMapById.values()) {
                        if(nhnSeeker.sourceRecord != null && nhnSeeker.sourceRecord.procedureId != null && nhnSeeker.sourceRecord.procedureId.equals(nhn.id.replace("-CHARTING", ""))) {
                            nhnSeeker.sourceRecord.resultReviewTree = path;
                        }
                    }
                }
            }
        }.updateSubordinateLrrPaths(node, newParent.getPathPrecalculated());
        for(int x = 0; x < oldParent.children.size(); x++) {
            oldParent.children.get(x).seq = new Long(x);
        }
        for(int x = 0; x < newParent.children.size(); x++) {
            newParent.children.get(x).seq = new Long(x);
        }
        Change change = new Change();
        change.timestamp = new Date();
        change.timestampFormatted = change.timestamp.toString();
        change.id = node.sourceRecord.lrrId != null && node.sourceRecord.lrrId.length() > 0 ? node.sourceRecord.lrrId : node.dispId;
        change.lrrName = node.sourceRecord.lrrName;
        change.chartingName = node.disp;
        String newChartsAfterString = "position 0 (first in grouper)";
        if(jsTreeMove.position > 0) {
            NormalizedHierarchyNode newChartsAfter = newParent.children.get(jsTreeMove.position - 1);
            newChartsAfterString = String.format("position %d (just after '%s' [%s])", jsTreeMove.position, newChartsAfter.disp, newChartsAfter.sourceRecord.lrrId != null && newChartsAfter.sourceRecord.lrrId.length() > 0 ? newChartsAfter.sourceRecord.lrrId : newChartsAfter.dispId);
        }
        if(oldParent == newParent) {
            change.type = "re-order within a grouper";
            change.description = String.format("within grouper '%s' [%s], moved from %s to %s", oldParent.disp, oldParent.dispId, oldChartsAfterString, newChartsAfterString);
        }
        else {
            change.type = "re-order across groupers";
            change.description = String.format("moved from grouper '%s' [%s] %s to grouper '%s' [%s] %s", oldParent.disp, oldParent.dispId, oldChartsAfterString, newParent.disp, newParent.dispId, newChartsAfterString);
        }
        changeLog.add(change);
        return change;
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

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/searchWithChildrenResult")
    public Map<String, Integer[]> getJsonSearchWithChildrenResult(@QueryParam("searchString") String searchString, @Context HttpServletResponse response) {
        response.setHeader("Expires", "0");
        loadCache();
        Map<String, Integer[]> searchResultMap = new HashMap();
        for(String searchStringParsed : (searchString.split(("\\|")))) {
            searchWithChildren(rootNhn, searchStringParsed.trim(), searchResultMap);
        }
        return searchResultMap;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/rollback")
    public void getJsonRollback(@Context HttpServletResponse response) {
        response.setHeader("Expires", "0");
        grouperMap = new LinkedHashMap<>();
        lrrMap = new LinkedHashMap<>();
        procedureMap = new LinkedHashMap<>();
        chartingProcedureMap = new LinkedHashMap<>();
        rootNhn = null;
        nhnMapById = new HashMap<>();
        changeLog = new ArrayList<>();        
        cached = false;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/commit")
    public String getJsonCommit(@Context HttpServletResponse response) {
        Date now = new Date();
        response.setHeader("Expires", "0");
        loadCache();
        try {
            Files.move(
                Paths.get(String.format(directory + "lrr.csv")),
                Paths.get(String.format(directory + "lrr.csv." + sdfTimestamp.format(now)))
            );       
            FileOutputStream fos = new FileOutputStream(directory + "lrr.csv");
            PrintStream fps = new PrintStream(fos);
            fps.println(Lrr.toStringCsvHeader());
            printTree(fps, rootNhn);
            fps.close();
        }    
        catch(IOException e) {
            throw new RuntimeException(e);
        }
        try {
            Files.copy(
                Paths.get(String.format(directory + "lrr.log.")),
                Paths.get(String.format(directory + "llr.log." + sdfTimestamp.format(now)))
            );       
            FileOutputStream fos = new FileOutputStream(directory + "lrr.log", true);
            PrintStream fps = new PrintStream(fos);
            fps.println(Change.toStringCsvHeader());
            for(Change change : changeLog) {
                fps.println(change.toStringCsv());
            }
            fps.close();
        }    
        catch(IOException e) {
            throw new RuntimeException(e);
        }
        int commitSize = changeLog.size();
        grouperMap = new LinkedHashMap<>();
        lrrMap = new LinkedHashMap<>();
        procedureMap = new LinkedHashMap<>();
        chartingProcedureMap = new LinkedHashMap<>();
        rootNhn = null;
        nhnMapById = new HashMap<>();
        changeLog = new ArrayList<>();        
        cached = false;
        return String.format("%d changes committed.", commitSize);
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
                                Lrr lrr = Lrr.load(record, seq);
                                lrrMap.put(lrr.lrrId, lrr);
                            }
                            seq++;
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
                                Lrr lrr = Lrr.load(record, seq);
                                procedureMap.put(lrr.procedureId + "-" + lrr.lrrId, lrr);
                            }
                            seq++;
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
                                Lrr lrr = Lrr.load(record, seq);
                                chartingProcedureMap.put(lrr.procedureId + "-CHARTING", lrr);
                            }
                            seq++;
                        }
                        in.close();
                    }
                }
                catch (IOException ex) {
                    Logger.getLogger(NormalizedHierarchyNodeResourceEdit2.class.getName()).log(Level.SEVERE, null, ex);
                    throw new RuntimeException(ex);
                }
                // load groupers
Logger.getLogger(NormalizedHierarchyNodeResourceEdit2.class.getName()).log(Level.INFO, "loading groupers...");
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
Logger.getLogger(NormalizedHierarchyNodeResourceEdit2.class.getName()).log(Level.INFO, "loading external names and common names...");
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
Logger.getLogger(NormalizedHierarchyNodeResourceEdit2.class.getName()).log(Level.INFO, "loading LRRs...");
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
Logger.getLogger(NormalizedHierarchyNodeResourceEdit2.class.getName()).log(Level.INFO, "loading EAPs...");
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
Logger.getLogger(NormalizedHierarchyNodeResourceEdit2.class.getName()).log(Level.INFO, "loading charting EAPs...");
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
Logger.getLogger(NormalizedHierarchyNodeResourceEdit2.class.getName()).log(Level.INFO, "sorting...");
                for(NormalizedHierarchyNode nhn : nhnMapById.values()) {
                    if(nhn.duplicatedElsewhere) {
                        nhn.disp = nhn.disp + " {ALSO DUPLICATED ELSEWHERE}";
                    }
                    Collections.sort(nhn.children);
                    nhn.hasEap = searchForEap(nhn);
                }
                normalizeCollatingSequenceNumbers(rootNhn);
Logger.getLogger(NormalizedHierarchyNodeResourceEdit2.class.getName()).log(Level.INFO, "done.");
                cached = true;
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

    private void searchWithChildren(NormalizedHierarchyNode nhn, String searchString, Map<String, Integer[]> searchResultMap) {
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
                searchWithChildren(childNhn, searchString, searchResultMap);
            }
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
        if(
            nhn.parent != null
            && 
            (
                nhn.nodeType.equals("LRR")
                || (nhn.nodeType.equals("EAP") && nhn.sourceRecord != null && nhn.sourceRecord.showEapInRr.equals("Yes"))    
            )
        ) {
            ps.println(nhn.sourceRecord.toStringCsv());
        }
        for(NormalizedHierarchyNode childNhn : nhn.children) {
            printTree(ps, childNhn);
        }
    }
    
}
