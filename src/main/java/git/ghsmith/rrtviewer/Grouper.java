/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package git.ghsmith.rrtviewer;

import org.apache.commons.csv.CSVRecord;

/**
 *
 * @author geoff
 */
public class Grouper {

    public int collatingSeq;

    public String id;
    public String parentId;
    public String nodeName;
    public String pathPrecalculated;

    public static Grouper load(CSVRecord record) {
        Grouper grouper = new Grouper();
        grouper.collatingSeq = Integer.valueOf(record.get("COLLATING_SEQ"));
        //grouper.id = record.get("ID"); Apache CSV BUG?
        grouper.id = record.get(0);
        grouper.parentId = record.get("PARENT_ID");
        grouper.nodeName = record.get("NODE_NAME");
        grouper.pathPrecalculated = record.get("PATH_PRECALCULATED");
        return grouper;
    }
    
}
