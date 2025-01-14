package uk.gov.hmcts.reform.preapi.batch.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.xml.StaxEventItemReader;
import org.springframework.core.io.ClassPathResource;
import uk.gov.hmcts.reform.preapi.entities.batch.XMLArchiveFileData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class XMLReaderTest {

    private XMLReader xmlReader;

    @BeforeEach
    void setUp() {
        xmlReader = new XMLReader();
    }

    @DisplayName("Create XML Reader - Read XML Successfully")
    @Test
    void createReader_successfullyReadsXml() throws Exception {
        ClassPathResource resource = new ClassPathResource("batch/sample-archive.xml");
        
        StaxEventItemReader<XMLArchiveFileData> reader = xmlReader.createReader(resource, XMLArchiveFileData.class);

        ExecutionContext executionContext = new ExecutionContext();
        reader.open(executionContext);

        XMLArchiveFileData archiveFiles = reader.read();

        assertTrue(resource.exists(), "The resource file should exist.");
        assertNotNull(archiveFiles, "The reader should return a valid ArchiveFile object.");
        assertEquals("Sample Archive", archiveFiles.getDisplayName(), 
                    "The display name should match the XML content.");
        assertNotNull(archiveFiles.getMp4FileGrp(), "The MP4 file group should not be null.");
        assertEquals(120, archiveFiles.getDuration(),
                    "The duration should match the XML content.");

        reader.close();
    }


    @DisplayName("Create XML Reader - Fail to Read Non-Existent File")
    @Test
    void createReader_fileNotFound() {
        ClassPathResource resource = new ClassPathResource("batch/non-existent-file.xml");

        assertFalse(resource.exists(), "The resource file should not exist.");
        Exception exception = assertThrows(ItemStreamException.class, () -> {
            StaxEventItemReader<XMLArchiveFileData> reader = xmlReader.createReader(resource, XMLArchiveFileData.class);
            ExecutionContext executionContext = new ExecutionContext();
            reader.open(executionContext);
        });

        String expectedMessage = "Failed to initialize the reader";
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains(expectedMessage), 
            "The exception message should contain the expected message.");
    }

}
