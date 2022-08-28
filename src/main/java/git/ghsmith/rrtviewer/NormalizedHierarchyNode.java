package git.ghsmith.rrtviewer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NormalizedHierarchyNode implements Comparable<NormalizedHierarchyNode> {

    String id;
    String parentId;
    String nodeType;
    Long seq;
    String disp;
    
    Map<String, String> attributeMap = new LinkedHashMap<>();

    NormalizedHierarchyNode parent;
    List<NormalizedHierarchyNode> children = new ArrayList<>();

    @Override
    public int compareTo(NormalizedHierarchyNode o) {
        return(seq.compareTo(o.seq));
    }
    
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }

    public String getDisp() {
        return disp;
    }

    public void setDisp(String disp) {
        this.disp = disp;
    }

    public Map<String, String> getAttributeMap() {
        return attributeMap;
    }

    public void setAttributeMap(Map<String, String> attributeMap) {
        this.attributeMap = attributeMap;
    }
    
    
}