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
    
    public Map<String, String> attributeMap = new LinkedHashMap<>();

    NormalizedHierarchyNode parent;
    List<NormalizedHierarchyNode> children = new ArrayList<>();

    @Override
    public int compareTo(NormalizedHierarchyNode o) {
        return(seq.compareTo(o.seq));
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
        while(currentNode.parent != null) {
            currentNode = currentNode.parent;
            if(currentNode.nodeType != null && currentNode.id.startsWith("GHS")) {
                pathList.add(currentNode.disp);
            }
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
    
}