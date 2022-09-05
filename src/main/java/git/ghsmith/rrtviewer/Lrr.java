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
public class Lrr {

    public int collatingSeq;
    public String resultReviewTree;
    public String commonName;
    public String procedureName;
    public String procedureId;
    public String procedureCategory;
    public String lrrId;
    public String lrrName;
    public String baseName;
    public String lrrExternalName;
    public String lrrIssue;
    public String testIssue;
    public String testInactive;
    public String testId;
    public String testName;
    public String showEapInRr;
            
    public static Lrr load(CSVRecord record, int collatingSeq) {
        Lrr lrr = new Lrr();
        lrr.collatingSeq = collatingSeq;
        lrr.resultReviewTree = record.get("Result Review tree");
        lrr.commonName = record.get("Common Name");
        lrr.procedureName = record.get("Procedure Name");
        lrr.procedureId = record.get("Procedure ID");
        lrr.procedureCategory = record.get("Procedure Category");
        lrr.lrrId = record.get("LRR ID");
        lrr.lrrName = record.get("LRR Name");
        lrr.baseName = record.get("Base Name");
        lrr.lrrExternalName = record.get("LRR External Name");
        lrr.lrrIssue = record.get("LRR Issue");
        lrr.testIssue = record.get("Test Issue");
        lrr.testInactive = record.get("Test Inactive?");
        lrr.testId = record.get("Test ID");
        lrr.testName = record.get("Test Name");
        lrr.showEapInRr = record.get("Show EAP in RR?");
        return lrr;
    }

    public static String toStringCsvHeader() {
        return String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"",
            "Result Review tree",
            "Common Name",
            "Procedure Name",
            "Procedure ID",
            "Procedure Category",
            "LRR ID",
            "LRR Name",
            "Base Name",
            "LRR External Name",
            "LRR Issue",
            "Test Issue",
            "Test Inactive?",
            "Test ID",
            "Test Name",
            "Show EAP in RR?"
        );
    }

    public String toStringCsv() {
        return String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"",
            resultReviewTree,
            commonName,
            procedureName,
            procedureId,
            procedureCategory,
            lrrId,
            lrrName,
            baseName,
            lrrExternalName,
            lrrIssue,
            testIssue,
            testInactive,
            testId,
            testName,
            showEapInRr
        );
    }
    
    @Override
    public String toString() {
        return "Lrr{" + "collatingSeq=" + collatingSeq + ", resultReviewTree=" + resultReviewTree + ", commonName=" + commonName + ", procedureName=" + procedureName + ", procedureId=" + procedureId + ", procedureCategory=" + procedureCategory + ", lrrId=" + lrrId + ", lrrName=" + lrrName + ", baseName=" + baseName + ", lrrExternalName=" + lrrExternalName + ", lrrIssue=" + lrrIssue + ", testIssue=" + testIssue + ", testInactive=" + testInactive + ", testId=" + testId + ", testName=" + testName + ", showEapInRr=" + showEapInRr + '}';
    }
    
}
