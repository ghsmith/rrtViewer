package git.ghsmith.rrtviewer;

import java.util.Date;

public class Change {

    public Date timestamp;
    public String timestampFormatted;
    public String id;
    public String lrrName;
    public String chartingName;
    public String type;
    public String description;
   
    String toStringCsv() {
        StringBuffer s = new StringBuffer();
        s.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"",
            timestamp,
            timestampFormatted,
            id,
            lrrName,
            chartingName,
            type,
            description
        ));
        return s.toString();
    }

    static String toStringCsvHeader() {
        StringBuffer s = new StringBuffer();
        s.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"",
            "TIMESTAMP",
            "TIMESTAMP_FORMATTED",
            "ID",
            "LRR_NAME",
            "CHARTING_NAME",
            "TYPE",
            "DESCRIPTION"
        ));
        return s.toString();
    }

}