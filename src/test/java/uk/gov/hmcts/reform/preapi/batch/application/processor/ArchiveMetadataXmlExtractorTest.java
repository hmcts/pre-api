package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureVodafoneStorageService;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@SpringBootTest(classes = ArchiveMetadataXmlExtractor.class)
class ArchiveMetadataXmlExtractorTest {

    @MockitoBean
    private AzureVodafoneStorageService azureVodafoneStorageService;

    @MockitoBean
    private MigrationRecordService migrationRecordService;

    @MockitoBean
    private LoggingService loggingService;

    @Autowired
    private ArchiveMetadataXmlExtractor extractor;

    @Test
    void shouldLogWarningIfNoBlobsFound() {
        when(azureVodafoneStorageService.fetchBlobNamesWithPrefix(anyString(), anyString()))
            .thenReturn(List.of());

        extractor.extractAndReportArchiveMetadata("empty-container", "prefix/", "some/dir", "out");

        verify(loggingService).logWarning("No XML blobs found in container: %sempty-container");
    }

    @Test
    void shouldHandleInvalidXmlBlobGracefully() throws Exception {
        when(azureVodafoneStorageService.fetchBlobNamesWithPrefix(anyString(), anyString()))
            .thenReturn(List.of("broken.xml"));

        when(azureVodafoneStorageService.fetchSingleXmlBlob(anyString(), anyString()))
            .thenThrow(new RuntimeException("Corrupt XML"));

        extractor.extractAndReportArchiveMetadata("container", "prefix", "dir", "report");

        verify(loggingService).logError(startsWith("Failed to process blob:"), eq("broken.xml"), any(Exception.class));
    }



    @Test
    void shouldExtractMetadataAndInsertRecord() throws Exception {
        String validXml = """
            <ArchiveFiles>
                <ArchiveID>abc123</ArchiveID>
                <DisplayName>Test-230101-CASE1-WITNESS-ORIG</DisplayName>
                <CreatTime>1731681144000</CreatTime>
                <Duration>100</Duration>
                <MP4FileGrp>
                    <MP4File>
                        <Name>0x1e_test_file.mp4</Name>
                        <Size>2048</Size>
                    </MP4File>
                </MP4FileGrp>
            </ArchiveFiles>
            """;

        AzureVodafoneStorageService azureVodafoneStorageService = mock(AzureVodafoneStorageService.class);
        MigrationRecordService migrationRecordService = mock(MigrationRecordService.class);
        LoggingService loggingService = mock(LoggingService.class);

        when(azureVodafoneStorageService.fetchBlobNamesWithPrefix(anyString(), anyString()))
            .thenReturn(List.of("blob1.xml"));

        InputStreamResource xmlResource = new InputStreamResource(
            new ByteArrayInputStream(validXml.getBytes())
        );

        when(azureVodafoneStorageService.fetchSingleXmlBlob(anyString(), anyString()))
            .thenReturn(xmlResource);

        when(migrationRecordService.insertPendingFromXml(any(), any(), any(), any(), any(), any()))
            .thenReturn(true);

        ArchiveMetadataXmlExtractor extractor = new ArchiveMetadataXmlExtractor(
            azureVodafoneStorageService,
            migrationRecordService,
            loggingService
        );

        extractor.extractAndReportArchiveMetadata("test-container", "prefix", "out", "report");

        verify(migrationRecordService).insertPendingFromXml(
            eq("abc123"),
            eq("Test-230101-CASE1-WITNESS-ORIG"),
            eq("1731681144000"),
            eq("100"),
            eq("abc123/0x1e_test_file.mp4"),
            eq("2.00")
        );

        verify(loggingService).logInfo(
            eq("Successfully generated %s.csv with %d entries"),
            eq("report"),
            eq(1)
        );
    }



    @Test
    void sortsOrigBeforeCopy() throws Exception {
        String twoArchives = """
            <root>
              <ArchiveFiles>
                <ArchiveID>A1</ArchiveID>
                <DisplayName>Case-ORIG</DisplayName>
                <CreatTime>1731681144000</CreatTime>
                <Duration>100</Duration>
                <MP4FileGrp><MP4File><Name>a1.mp4</Name><Size>1024</Size></MP4File></MP4FileGrp>
              </ArchiveFiles>
              <ArchiveFiles>
                <ArchiveID>A2</ArchiveID>
                <DisplayName>Case-COPY</DisplayName>
                <CreatTime>1731681145000</CreatTime>
                <Duration>100</Duration>
                <MP4FileGrp><MP4File><Name>a2.mp4</Name><Size>1024</Size></MP4File></MP4FileGrp>
              </ArchiveFiles>
            </root>
            """;

        stubSingleBlob("c", "p/x.xml", twoArchives);

        extractor.extractAndReportArchiveMetadata("c", "p", "out", "report");

        InOrder inOrder = inOrder(migrationRecordService);
        inOrder.verify(migrationRecordService).insertPendingFromXml(
            eq("A1"), eq("Case-ORIG"), eq("1731681144000"), eq("100"),
            eq("p/A1/a1.mp4"), eq("1.00"));
        inOrder.verify(migrationRecordService).insertPendingFromXml(
            eq("A2"), eq("Case-COPY"), eq("1731681145000"), eq("100"),
            eq("p/A2/a2.mp4"), eq("1.00"));
    }

    // ------------------------------------
    // CreatTime fallback to "updatetime"
    // ------------------------------------

    @Test
    void creatTimeFallbacksWhenZero() throws Exception {
        String xml = """
            <ArchiveFiles>
              <ArchiveID>X1</ArchiveID>
              <DisplayName>Name-ORIG</DisplayName>
              <CreatTime>0</CreatTime>
              <updatetime>999</updatetime>
              <Duration>5</Duration>
              <MP4FileGrp><MP4File><Name>x1.mp4</Name><Size>2048</Size></MP4File></MP4FileGrp>
            </ArchiveFiles>
            """;
        stubSingleBlob("c","pref/x.xml", xml);

        extractor.extractAndReportArchiveMetadata("c", "pref", "out", "rep");

        verify(migrationRecordService).insertPendingFromXml(
            eq("X1"), eq("Name-ORIG"), eq("999"), eq("5"),
            eq("pref/X1/x1.mp4"), eq("2.00"));
    }

    @Test
    void creatTimeFallbacksWhen3600000() throws Exception {
        String xml = """
            <ArchiveFiles>
              <ArchiveID>X2</ArchiveID>
              <DisplayName>Name-ORIG</DisplayName>
              <CreatTime>3600000</CreatTime>
              <updatetime>123456</updatetime>
              <Duration>5</Duration>
              <MP4FileGrp><MP4File><Name>x2.mp4</Name><Size>2048</Size></MP4File></MP4FileGrp>
            </ArchiveFiles>
            """;
        stubSingleBlob("c","pref/x.xml", xml);

        extractor.extractAndReportArchiveMetadata("c", "pref", "out", "rep");

        verify(migrationRecordService).insertPendingFromXml(
            eq("X2"), eq("Name-ORIG"), eq("123456"), eq("5"),
            eq("pref/X2/x2.mp4"), eq("2.00"));
    }

    // ------------------------------------
    // MP4 selection rules (two files)
    // ------------------------------------

    @Test
    void choosesWatermarkedWhenOnlyOneWatermarked() throws Exception {
        String xml = """
            <ArchiveFiles>
              <ArchiveID>W1</ArchiveID>
              <DisplayName>Pick-Watermark-ORIG</DisplayName>
              <CreatTime>1</CreatTime><Duration>10</Duration>
              <MP4FileGrp>
                <MP4File><Name>a.mp4</Name><Size>1000</Size><watermark>false</watermark></MP4File>
                <MP4File><Name>b.mp4</Name><Size>900</Size><watermark>true</watermark></MP4File>
              </MP4FileGrp>
            </ArchiveFiles>
            """;
        stubSingleBlob("c","pref/x.xml", xml);

        extractor.extractAndReportArchiveMetadata("c", "pref", "out", "rep");

        verify(migrationRecordService).insertPendingFromXml(
            eq("W1"), eq("Pick-Watermark-ORIG"), eq("1"), eq("10"),
            eq("pref/W1/b.mp4"), eq("0.88"));
    }

    @Test
    void choosesUGCWhenOnlyOneHasUGCAndNoWatermarkEdge() throws Exception {
        String xml = """
            <ArchiveFiles>
              <ArchiveID>U1</ArchiveID>
              <DisplayName>Pick-UGC-ORIG</DisplayName>
              <CreatTime>2</CreatTime><Duration>10</Duration>
              <MP4FileGrp>
                <MP4File><Name>UGC_big.mp4</Name><Size>100</Size><watermark>false</watermark></MP4File>
                <MP4File><Name>plain.mp4</Name><Size>9999</Size><watermark>false</watermark></MP4File>
              </MP4FileGrp>
            </ArchiveFiles>
            """;
        stubSingleBlob("c","pref/x.xml", xml);

        extractor.extractAndReportArchiveMetadata("c", "pref", "out", "rep");

        verify(migrationRecordService).insertPendingFromXml(
            eq("U1"), eq("Pick-UGC-ORIG"), eq("2"), eq("10"),
            eq("pref/U1/UGC_big.mp4"), eq("0.10"));
    }

    @Test
    void choosesLargerSizeWhenWatermarkAndUGCAreEqual() throws Exception {
        String xml = """
            <ArchiveFiles>
              <ArchiveID>S1</ArchiveID>
              <DisplayName>Pick-Size-ORIG</DisplayName>
              <CreatTime>3</CreatTime><Duration>10</Duration>
              <MP4FileGrp>
                <MP4File><Name>a.mp4</Name><Size>2048</Size></MP4File>
                <MP4File><Name>b.mp4</Name><Size>1024</Size></MP4File>
              </MP4FileGrp>
            </ArchiveFiles>
            """;
        stubSingleBlob("c","pref/x.xml", xml);

        extractor.extractAndReportArchiveMetadata("c", "pref", "out", "rep");

        verify(migrationRecordService).insertPendingFromXml(
            eq("S1"), eq("Pick-Size-ORIG"), eq("3"), eq("10"),
            eq("pref/S1/a.mp4"), eq("2.00"));
    }

    @Test
    void choosesLongerDurationWhenSizesEqual() throws Exception {
        String xml = """
            <ArchiveFiles>
              <ArchiveID>D1</ArchiveID>
              <DisplayName>Pick-Duration-ORIG</DisplayName>
              <CreatTime>4</CreatTime><Duration>10</Duration>
              <MP4FileGrp>
                <MP4File><Name>a.mp4</Name><Size>1000</Size><Duration>50</Duration></MP4File>
                <MP4File><Name>b.mp4</Name><Size>1000</Size><Duration>60</Duration></MP4File>
              </MP4FileGrp>
            </ArchiveFiles>
            """;
        stubSingleBlob("c","pref/x.xml", xml);

        extractor.extractAndReportArchiveMetadata("c", "pref", "out", "rep");

        verify(migrationRecordService).insertPendingFromXml(
            eq("D1"), eq("Pick-Duration-ORIG"), eq("4"), eq("10"),
            eq("pref/D1/b.mp4"), eq("0.98"));
    }

    @Test
    void choosesLongerNameWhenDurationsTieOrInvalid() throws Exception {
        String xml = """
            <ArchiveFiles>
              <ArchiveID>N1</ArchiveID>
              <DisplayName>Pick-NameLen-ORIG</DisplayName>
              <CreatTime>5</CreatTime><Duration>10</Duration>
              <MP4FileGrp>
                <MP4File><Name>a.mp4</Name><Size>1000</Size><Duration>0</Duration></MP4File>
                <MP4File><Name>longer_name.mp4</Name><Size>1000</Size><Duration>0</Duration></MP4File>
              </MP4FileGrp>
            </ArchiveFiles>
            """;
        stubSingleBlob("c","pref/x.xml", xml);

        extractor.extractAndReportArchiveMetadata("c", "pref", "out", "rep");

        verify(migrationRecordService).insertPendingFromXml(
            eq("N1"), eq("Pick-NameLen-ORIG"), eq("5"), eq("10"),
            eq("pref/N1/longer_name.mp4"), eq("0.98"));
    }

    @Test
    void tieBreakerFallsBackToSecondWhenEverythingEqual() throws Exception {
        String xml = """
            <ArchiveFiles>
              <ArchiveID>T1</ArchiveID>
              <DisplayName>Tie-ORIG</DisplayName>
              <CreatTime>6</CreatTime><Duration>10</Duration>
              <MP4FileGrp>
                <MP4File><Name>sameA.mp4</Name><Size>1000</Size><Duration>42</Duration></MP4File>
                <MP4File><Name>sameB.mp4</Name><Size>1000</Size><Duration>42</Duration></MP4File>
              </MP4FileGrp>
            </ArchiveFiles>
            """;
        stubSingleBlob("c","pref/x.xml", xml);

        extractor.extractAndReportArchiveMetadata("c", "pref", "out", "rep");

        verify(migrationRecordService).insertPendingFromXml(
            eq("T1"), eq("Tie-ORIG"), eq("6"), eq("10"),
            eq("pref/T1/sameB.mp4"), eq("0.98"));
    }


    @Test
    @DisplayName("Should warn when no archive metadata found to generate report")
    void shouldWarnWhenNoArchiveMetadataFound() throws Exception {
        String xml = """
            <ArchiveFiles>
            <ArchiveID>A1</ArchiveID>
            <DisplayName>Case-ORIG</DisplayName>
            <CreatTime>1</CreatTime><Duration>10</Duration>
            <MP4FileGrp>
                <MP4File><Name>not_video.txt</Name><Size>100</Size></MP4File> <!-- filtered out -->
            </MP4FileGrp>
            </ArchiveFiles>
            """;
        when(azureVodafoneStorageService.fetchBlobNamesWithPrefix(eq("c"), anyString()))
            .thenReturn(List.of("pref/x.xml"));
        when(azureVodafoneStorageService.fetchSingleXmlBlob(eq("c"), eq("pref/x.xml")))
            .thenReturn(new InputStreamResource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));

        extractor.extractAndReportArchiveMetadata("c", "pref", "out", "rep");

        verify(loggingService).logWarning("No archive metadata found to generate report");
    }

    @Test
    @DisplayName("Should log warning and default size when file size is invalid")
    void shouldDefaultFileSizeOnInvalidNumber() throws Exception {
        String xml = """
            <ArchiveFiles>
            <ArchiveID>A1</ArchiveID>
            <DisplayName>Case-ORIG</DisplayName>
            <CreatTime>1</CreatTime><Duration>10</Duration>
            <MP4FileGrp>
                <MP4File><Name>a.mp4</Name><Size>oops</Size></MP4File>
            </MP4FileGrp>
            </ArchiveFiles>
            """;
        stubSingleBlob("c","pref/x.xml", xml);

        extractor.extractAndReportArchiveMetadata("c", "pref", "out", "rep");

        verify(loggingService).logWarning("Invalid file size: oops");
        verify(migrationRecordService).insertPendingFromXml(
            eq("A1"), eq("Case-ORIG"), eq("1"), eq("10"),
            eq("pref/A1/a.mp4"), eq("0.00")
        );
    }


    @Test
    @DisplayName("Should log warning when MP4 fields are missing")
    void shouldWarnWhenMp4FieldsMissing() throws Exception {
        String xml = """
            <ArchiveFiles>
            <ArchiveID>A1</ArchiveID>
            <DisplayName>Case-ORIG</DisplayName>
            <CreatTime>1</CreatTime><Duration>10</Duration>
            <MP4FileGrp>
                <MP4File><Name></Name><Size>100</Size></MP4File>
            </MP4FileGrp>
            </ArchiveFiles>
            """;
        stubSingleBlob("c","pref/x.xml", xml);

        extractor.extractAndReportArchiveMetadata("c", "pref", "out", "rep");

        verify(loggingService).logWarning(
            startsWith("MP4 file missing required fields: Name=%s, Size=%s")
        );
    }

    @Test
    @DisplayName("Should pick best MP4 when more than two files exist (largest size)")
    void shouldPickBestWhenMoreThanTwo() throws Exception {
        String xml = """
            <ArchiveFiles>
            <ArchiveID>A1</ArchiveID>
            <DisplayName>Case-ORIG</DisplayName>
            <CreatTime>1</CreatTime><Duration>10</Duration>
            <MP4FileGrp>
                <MP4File><Name>first.mp4</Name><Size>100</Size></MP4File>
                <MP4File><Name>second.mp4</Name><Size>200</Size></MP4File>
                <MP4File><Name>third.mp4</Name><Size>300</Size></MP4File>
            </MP4FileGrp>
            </ArchiveFiles>
            """;
        stubSingleBlob("c","pref/x.xml", xml);

        extractor.extractAndReportArchiveMetadata("c", "pref", "out", "rep");

        verify(migrationRecordService).insertPendingFromXml(
            eq("A1"), eq("Case-ORIG"), eq("1"), eq("10"),
            eq("pref/A1/third.mp4"), eq("0.29")
        );
    }

    @Test
    @DisplayName("Should log error and continue when insert throws")
    void shouldLogAndContinueWhenInsertThrows() throws Exception {
        String xml = """
            <ArchiveFiles>
            <ArchiveID>A1</ArchiveID>
            <DisplayName>Case-ORIG</DisplayName>
            <CreatTime>1</CreatTime><Duration>10</Duration>
            <MP4FileGrp><MP4File><Name>a.mp4</Name><Size>1024</Size></MP4File></MP4FileGrp>
            </ArchiveFiles>
            """;
        stubSingleBlob("c","pref/x.xml", xml);

        when(migrationRecordService.insertPendingFromXml(any(), any(), any(), any(), any(), any()))
            .thenThrow(new RuntimeException("DB down"));

        extractor.extractAndReportArchiveMetadata("c", "pref", "out", "rep");

        verify(loggingService).logError(startsWith("Failed to insert row into migration records:"), anyString());
    }

    @Test
    @DisplayName("Should warn when DisplayName or CreatTime missing")
    void shouldWarnWhenDisplayNameOrCreateTimeMissing() throws Exception {
        String xml = """
            <ArchiveFiles>
            <ArchiveID>A1</ArchiveID>
            <DisplayName></DisplayName>
            <CreatTime></CreatTime>
            <Duration>1</Duration>
            <MP4FileGrp><MP4File><Name>a.mp4</Name><Size>100</Size></MP4File></MP4FileGrp>
            </ArchiveFiles>
            """;
        stubSingleBlob("c","pref/x.xml", xml);

        extractor.extractAndReportArchiveMetadata("c", "pref", "out", "rep");

        verify(loggingService).logWarning("Missing DisplayName in ArchiveFiles element.");
        verify(loggingService).logWarning("Missing CreatTime for archive: ");
    }

    // ---------------------------
    // Helpers
    // ---------------------------

    private InputStreamResource xml(String content) {
        return new InputStreamResource(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    private void stubSingleBlob(String container, String blobName, String xml) throws Exception {
        when(azureVodafoneStorageService.fetchBlobNamesWithPrefix(eq(container), anyString()))
            .thenReturn(List.of(blobName));
        when(azureVodafoneStorageService.fetchSingleXmlBlob(eq(container), eq(blobName)))
            .thenReturn(xml(xml));
    }
}