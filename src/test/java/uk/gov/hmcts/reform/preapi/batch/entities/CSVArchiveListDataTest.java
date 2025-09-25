package uk.gov.hmcts.reform.preapi.batch.entities;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CSVArchiveListDataTest {

    @Test
    void getArchiveNameNoExt() {
        CSVArchiveListData data = new CSVArchiveListData("","testfile.csv", "", 0, "", "");
        assertEquals("testfile", data.getArchiveNameNoExt());

        data.setArchiveName("anotherfile");
        assertEquals("anotherfile", data.getArchiveNameNoExt());
    }

    @Test
    void getCreateTimeAsLocalDateTime() {
        CSVArchiveListData data = new CSVArchiveListData("", "", "2021-08-01 12:00:00", 0, "", "");
        LocalDateTime expectedDateTime = LocalDateTime.of(2021, 8, 1, 12, 0);
        assertEquals(expectedDateTime, data.getCreateTimeAsLocalDateTime());

        data.setCreateTime("01/08/2021 12:00");
        assertEquals(expectedDateTime, data.getCreateTimeAsLocalDateTime());
    }

    @Test
    void testToString() {
        CSVArchiveListData data = new CSVArchiveListData("Edbc1efa5cc1e4104afc5185a241c8f4f", 
            "testfile.csv", "1627849200000", 30, "file.csv", "100KB");
        String expectedString =
            """
                CSVArchiveListData{archiveId='Edbc1efa5cc1e4104afc5185a241c8f4f', \
                archiveName='testfile.csv', \
                sanitizedName='', \
                createTime='1627849200000', \
                duration=30, \
                fileName='file.csv', \
                fileSize='100KB'}""";
        assertEquals(expectedString, data.toString());
    }

    @Test
    void setSanitizedArchiveName() {
        CSVArchiveListData data = new CSVArchiveListData("", "QC_testfile.csv", "", 0, "", "");
        data.setArchiveName("QC_testfile.csv");
        assertEquals("QC_testfile.csv", data.getSanitizedArchiveName());

        data.setArchiveName("CP_Case_testfile.csv");
        assertEquals("Case_testfile.csv", data.getSanitizedArchiveName());
    }

    @Test
    void getParsedCreateTimeHandlesEpochMillis() {
        long millis = 1627849200000L; 
        CSVArchiveListData data = new CSVArchiveListData();
        data.setCreateTime(String.valueOf(millis));

        LocalDateTime expected = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(millis),
            java.time.ZoneId.systemDefault()
        );

        assertEquals(expected, data.getParsedCreateTime());
    }

    @Test
    void getParsedCreateTimeReturnsNullForZeroTimestamp() {
        CSVArchiveListData data = new CSVArchiveListData();
        data.setCreateTime("0");

        assertEquals(null, data.getParsedCreateTime());
    }

    @Test
    void getParsedCreateTimeReturnsNullForJunkString() {
        CSVArchiveListData data = new CSVArchiveListData();
        data.setCreateTime("nonsense");

        assertEquals(null, data.getParsedCreateTime());
    }
}

