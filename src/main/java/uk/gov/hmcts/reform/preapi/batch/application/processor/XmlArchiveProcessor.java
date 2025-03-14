package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import uk.gov.hmcts.reform.preapi.batch.application.services.AzureBlobService;
import uk.gov.hmcts.reform.preapi.batch.application.services.ReportingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Service for processing XML files and writing data to CSV.
 * It fetches XML files stored in Azure Blob Storage, parses them, and writes the extracted data into a CSV file.
 */
@Service
public class XmlArchiveProcessor {
    private final AzureBlobService azureBlobService;
    private final ReportingService reportingService;
    private final LoggingService loggingService;

    public XmlArchiveProcessor(
        AzureBlobService azureBlobService, 
        ReportingService reportingService,
        LoggingService loggingService
    ) {
        this.azureBlobService = azureBlobService;
        this.reportingService = reportingService;
        this.loggingService = loggingService;
    }

    /**
     * Process XML files from a specified Azure Blob container and write extracted data to CSV.
     * @param containerName The Azure Blob container name.
     * @param outputDir The directory where the CSV files will be written.
     */
    public void processArchiveMetadata(String containerName, String outputDir) {
        try {
            loggingService.logInfo("Starting metadata extraction for container: %s", containerName);

            List<String> blobNames = azureBlobService.fetchBlobNames(
                containerName, 
                Constants.Environment.SOURCE_ENVIRONMENT
            );

            if (blobNames.isEmpty()) {
                loggingService.logWarning("No XML blobs found in container: %s. Skipping processing.", 
                    containerName);
                return;
            }
            
            loggingService.logDebug("Found %d blobs in container: %s", blobNames.size(), containerName);
            List<List<String>> allArchiveMetadata = parseXmlBlobs(containerName, blobNames);

            if (!allArchiveMetadata.isEmpty()) {
                generateArchiveMetadataReport(allArchiveMetadata, outputDir);
                loggingService.logDebug("Generated Archive_List.csv with %d entries in %s directory", 
                    allArchiveMetadata.size(), outputDir);
            } 

        } catch (Exception e) {
            loggingService.logError("Unexpected error processing XML: %s", e.getMessage(), e);
        }
    }

    /**
     * Extracts metadata from XML blobs.
     * 
     * @param containerName Azure Blob container name
     * @param blobNames List of blob names to process
     * @return List of metadata rows
     */
    private List<List<String>> parseXmlBlobs(String containerName, List<String> blobNames) {
        List<List<String>> allArchiveMetadata = new ArrayList<>();
        for (String blobName : blobNames) {
            try (InputStream xmlStream = azureBlobService.fetchSingleXmlBlob(
                    containerName, Constants.Environment.SOURCE_ENVIRONMENT, blobName).getInputStream()) {

                List<List<String>> blobMetadata = parseXmlDocument(xmlStream);
                if (!blobMetadata.isEmpty()) {
                    allArchiveMetadata.addAll(blobMetadata);
                    loggingService.logDebug("Processed blob: %s | Extracted metadata count: %d",  
                        blobName, blobMetadata.size());
                } 

            } catch (Exception e) {
                loggingService.logError("Failed to process blob: %s | Error: %s", blobName, e);
            }
        }
        return allArchiveMetadata;
    }

    /**
     * Generates a CSV report of archive metadata.
     * 
     * @param archiveMetadata Extracted metadata
     * @param outputDirectory Directory for report
     */
    private void generateArchiveMetadataReport(List<List<String>> archiveMetadata, 
        String outputDirectory) throws IOException {

        List<String> headers = List.of(
            "archive_name", 
            "create_time", 
            "duration", 
            "file_name", 
            "file_size"
        );

        reportingService.writeToCsv(
            headers, 
            archiveMetadata, 
            "Archive_List", 
            outputDirectory, 
            false
        );
    }

    /**
     * Parses XML content from an InputStream to extract data as rows.
     * @param inputStream The InputStream containing the XML data.
     * @return A list of lists, each list being a row of data.
     * @throws Exception If there is an error during XML parsing.
     */
    private List<List<String>> parseXmlDocument(InputStream inputStream) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(inputStream);
        doc.getDocumentElement().normalize();

        return extractRecordingMetadata(doc);
    }

    /**
     * Extracts detailed metadata for archive files.
     * 
     * @param document Parsed XML document
     * @return List of metadata rows
     */
    private List<List<String>> extractRecordingMetadata(Document document) {
        List<List<String>> metadataRows = new ArrayList<>();
        NodeList archiveFileNodes = document.getElementsByTagName("ArchiveFiles");

        for (int i = 0; i < archiveFileNodes.getLength(); i++) {
            Node archiveNode = archiveFileNodes.item(i);
            if (archiveNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element archiveElement = (Element) archiveNode;
            String displayName = extractTextContent(archiveElement, "DisplayName");
            String createTime = extractTextContent(archiveElement, "CreatTime");
            String duration = extractTextContent(archiveElement, "Duration");

            if (displayName.isEmpty() || createTime.isEmpty()) {
                loggingService.logWarning("Incomplete metadata for archive. Missing fields: %s %s", 
                    displayName.isEmpty() ? "[DisplayName]" : "", 
                    createTime.isEmpty() ? "[CreateTime]" : "");
            }
           
            metadataRows.addAll(extractMp4Metadata(archiveElement, displayName, createTime, duration));
        }
        return metadataRows;
    }

    /**
     * Processes MP4 files within an archive element.
     * 
     * @param archiveElement Archive XML element
     * @param displayName Archive display name
     * @param createTime Creation time
     * @param duration Recording duration
     * @return List of metadata rows for MP4 files
     */
    private List<List<String>> extractMp4Metadata(
        Element archiveElement, 
        String displayName, 
        String createTime, 
        String duration
    ) {
        List<List<String>> fileRows = new ArrayList<>();
        NodeList mp4FileGroups = archiveElement.getElementsByTagName("MP4FileGrp");

        for (int j = 0; j < mp4FileGroups.getLength(); j++) {
            Element mp4Group = (Element) mp4FileGroups.item(j);
            NodeList mp4Files = mp4Group.getElementsByTagName("MP4File");

            for (int k = 0; k < mp4Files.getLength(); k++) {
                Element fileElement = (Element) mp4Files.item(k);
                
                String fileName = extractTextContent(fileElement, "Name");
                String fileSizeKb = extractTextContent(fileElement, "Size");

                if (fileName.isEmpty() || fileSizeKb.isEmpty()) {
                    loggingService.logWarning("MP4 file missing fields: %s %s", 
                        fileName.isEmpty() ? "[Name]" : "", 
                        fileSizeKb.isEmpty() ? "[Size]" : "");
                    continue;
                }
                if (isValidMp4File(fileName)) {
                    String formattedFileSize = convertKbToMb(fileSizeKb);
                    fileRows.add(List.of(displayName, createTime, duration, fileName, formattedFileSize));
                }
            }
        }
        return fileRows;
    }

    /**
     * Checks if the file is a valid MP4 file.
     * 
     * @param fileName File name to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidMp4File(String fileName) {
        return fileName.endsWith(".mp4") && fileName.startsWith("0x1e");
    }

    /**
     * Extracts text content from a specific tag within an element.
     * 
     * @param element Parent XML element
     * @param tagName Tag to extract
     * @return Extracted text content or empty string
     */
    private String extractTextContent(Element element, String tagName) {
        return Optional.ofNullable(element.getElementsByTagName(tagName).item(0))
            .map(Node::getTextContent)
            .orElse("");
    }

    /**
     * Formats file size from KB to MB with two decimal places.
     * 
     * @param fileSizeKb File size in kilobytes
     * @return Formatted file size in MB
     */
    private String convertKbToMb(String fileSizeKb) {
        try {
            double sizeInKb = Double.parseDouble(fileSizeKb);
            double sizeInMb = sizeInKb / 1024.0;
            DecimalFormat fileSizeFormat = new DecimalFormat("0.00");
            return fileSizeFormat.format(sizeInMb) + " MB";
        } catch (NumberFormatException e) {
            loggingService.logWarning("Invalid file size format: %s", fileSizeKb);
            return "0.00 MB";
        }
    }
}
