package git.ghsmith.rrtviewer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NormalizedHierarchyNode implements Comparable<NormalizedHierarchyNode> {

    public String id;
    public String parentId;
    public String nodeType;
    public Long seq;
    public String disp;
    public String dispId;
    public boolean hasEap;
    public boolean duplicatedElsewhere = false;

    public Lrr sourceRecord;
    
    public Map<String, String> attributeMap = new LinkedHashMap<>();

    NormalizedHierarchyNode parent;
    List<NormalizedHierarchyNode> children = new ArrayList<>();

    @Override
    public int compareTo(NormalizedHierarchyNode o) {
        return(seq.compareTo(o.seq));
    }
    
    public static NormalizedHierarchyNode loadFromGrouper(Grouper grouper) {
        NormalizedHierarchyNode nhn = new NormalizedHierarchyNode();
        nhn.id = grouper.id;
        nhn.parentId = grouper.parentId;
        nhn.nodeType = "RRT";
        nhn.seq = Long.valueOf(grouper.collatingSeq);
        nhn.disp = grouper.nodeName;
        nhn.dispId = grouper.id;
        return nhn;
    }

    public static NormalizedHierarchyNode loadRrtFromLrr(Lrr lrr, String parentId, Lrr sourceRecord) {
        NormalizedHierarchyNode nhn = new NormalizedHierarchyNode();
        nhn.id = lrr.lrrId + "RRT";
        nhn.parentId = parentId;
        nhn.nodeType = "RRT";
        nhn.seq = Long.valueOf(lrr.collatingSeq);
        nhn.disp = lrr.lrrExternalName != null && lrr.lrrExternalName.length() > 0 ? lrr.lrrExternalName : lrr.commonName;
        nhn.dispId = lrr.lrrId;
        nhn.sourceRecord = sourceRecord;
        return nhn;
    }
    
    public static NormalizedHierarchyNode loadCnFromLrr(Lrr lrr, String parentId, Lrr sourceRecord) {
        NormalizedHierarchyNode nhn = new NormalizedHierarchyNode();
        nhn.id = lrr.lrrId + "CN";
        nhn.parentId = parentId;
        nhn.nodeType = "CN";
        nhn.seq = Long.valueOf(lrr.collatingSeq);
        nhn.disp = lrr.commonName;
        nhn.dispId = lrr.lrrId;
        nhn.sourceRecord = sourceRecord;
        return nhn;
    }

    public static NormalizedHierarchyNode loadLrrFromLrr(Lrr lrr, String parentId, Lrr sourceRecord) {
        NormalizedHierarchyNode nhn = new NormalizedHierarchyNode();
        nhn.id = lrr.lrrId;
        nhn.parentId = parentId;
        nhn.nodeType = "LRR";
        nhn.seq = Long.valueOf(lrr.collatingSeq);
        nhn.disp = lrr.lrrName;
        nhn.dispId = lrr.lrrId;
        nhn.sourceRecord = sourceRecord;
        return nhn;
    }
    
    public static NormalizedHierarchyNode loadEapFromLrr(Lrr lrr, String parentId, Lrr sourceRecord) {
        NormalizedHierarchyNode nhn = new NormalizedHierarchyNode();
        nhn.id = lrr.procedureId + "-" + lrr.lrrId;
        nhn.parentId = parentId;
        nhn.nodeType = "EAP";
        nhn.seq = Long.valueOf(lrr.collatingSeq);
        nhn.disp = lrr.procedureName;
        nhn.dispId = lrr.procedureId;
        nhn.sourceRecord = sourceRecord;
        return nhn;
    }

    public static NormalizedHierarchyNode loadChartingProcedureRrtFromLrr(Lrr lrr, String parentId, Lrr sourceRecord) {
        NormalizedHierarchyNode nhn = new NormalizedHierarchyNode();
        nhn.id = lrr.procedureId + "-CHARTINGRRT";
        nhn.parentId = parentId;
        nhn.nodeType = "RRT";
        nhn.seq = Long.valueOf(lrr.collatingSeq);
        nhn.disp = lrr.procedureName;
        nhn.dispId = lrr.procedureId;
        nhn.sourceRecord = sourceRecord;
        return nhn;
    }

    public static NormalizedHierarchyNode loadChartingProcedureEapFromLrr(Lrr lrr, String parentId, Lrr sourceRecord) {
        NormalizedHierarchyNode nhn = new NormalizedHierarchyNode();
        nhn.id = lrr.procedureId + "-CHARTING";
        nhn.parentId = parentId;
        nhn.nodeType = "EAP";
        nhn.seq = Long.valueOf(lrr.collatingSeq);
        nhn.disp = lrr.procedureName;
        nhn.dispId = lrr.procedureId;
        nhn.sourceRecord = sourceRecord;
        return nhn;
    }

    public boolean containsIgnoreCase(String searchString){
        String searchStringUC = searchString.toUpperCase();
        if(searchStringUC.startsWith("=")) {
            searchStringUC = searchStringUC.substring(1);
            if(
                (disp != null && disp.toUpperCase().equals(searchStringUC))
                || (dispId != null && dispId.toUpperCase().equals(searchStringUC))
            ){
                return true;
            }
            for(String attribute : attributeMap.values()) {
                if(attribute != null && attribute.toUpperCase().equals(searchStringUC)) {
                    return true;
                }
            }
        }
        else {
            if(
                (disp != null && disp.toUpperCase().contains(searchStringUC))
                || (dispId != null && dispId.toUpperCase().contains(searchStringUC))
            ){
                return true;
            }
            for(String attribute : attributeMap.values()) {
                if(attribute != null && attribute.toUpperCase().contains(searchStringUC)) {
                    return true;
                }
            }
        }
        return false;
    }

    public String getPathPrecalculated() {
        List<String> pathList = new ArrayList<>();
        NormalizedHierarchyNode currentNode = this;
        while(currentNode != null) {
            if(currentNode.nodeType != null && currentNode.id.startsWith("GHS")) {
                pathList.add(currentNode.disp);
            }
            currentNode = currentNode.parent;
        }
        StringBuffer s = new StringBuffer();
        for(int x = pathList.size() - 1; x >= 0; x--) {
            if(s.length() > 0) {
                s.append(" > ");
            }
            s.append(pathList.get(x));
        }
        return s.toString();
    }
    
    String toStringCsv() {
        StringBuffer s = new StringBuffer();
        s.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"",
            id != null ? id : "",
            parentId != null ? parentId : "",
            nodeType != null ? nodeType : "",
            seq != null ? seq : 0,
            disp != null ? disp : "",
            getPathPrecalculated()
        ));
        for(String name : attributeMap.keySet()) {
            s.append(String.format(",\"%s\"", attributeMap.get(name)));
        }
        return s.toString();
    }

    String toStringCsvHeader() {
        StringBuffer s = new StringBuffer();
        s.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"",
            "ID",
            "PARENT_ID",
            "NODE_TYPE",
            "COLLATING_SEQ",
            "NODE_NAME",
            "PATH_PRECALCULATED"
        ));
        for(String name : attributeMap.keySet()) {
            s.append(String.format(",\"%s\"", name));
        }
        return s.toString();
    }

    @Override
    public String toString() {
        return "NormalizedHierarchyNode{" + "id=" + id + ", parentId=" + parentId + ", nodeType=" + nodeType + ", seq=" + seq + ", disp=" + disp + ", dispId=" + dispId + ", hasEap=" + hasEap + ", duplicatedElsewhere=" + duplicatedElsewhere + '}';
    }
    
}