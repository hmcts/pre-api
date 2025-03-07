package uk.gov.hmcts.reform.preapi.batch.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import uk.gov.hmcts.reform.preapi.batch.services.AzureBlobService;
import uk.gov.hmcts.reform.preapi.batch.services.ReportingService;

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
public class ArchiveMetadataXmlExtractor {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveMetadataXmlExtractor.class);

    private static final String ENV_DEV = "dev";
    private static final DecimalFormat FILE_SIZE_FORMAT = new DecimalFormat("0.00");

    private final AzureBlobService azureBlobService;
    private final ReportingService reportingService;

    public ArchiveMetadataXmlExtractor(
        AzureBlobService azureBlobService, 
        ReportingService reportingService
    ) {
        this.azureBlobService = azureBlobService;
        this.reportingService = reportingService;
    }

    /**
     * Process XML files from a specified Azure Blob container and write extracted data to CSV.
     * @param containerName The Azure Blob container name.
     * @param outputDir The directory where the CSV files will be written.
     */
    public void extractAndReportArchiveMetadata(String containerName, String outputDir) {
        try {
            List<String> blobNames = azureBlobService.fetchBlobNames(containerName, ENV_DEV);
            List<List<String>> allArchiveMetadata = extractMetadataFromBlobs(containerName, blobNames);

            if (!allArchiveMetadata.isEmpty()) {
                generateArchiveMetadataReport(allArchiveMetadata, outputDir);
            }

        } catch (Exception e) {
            logger.error("Error processing XML and writing to CSV: {}", e.getMessage());
        }
    }

    /**
     * Extracts metadata from XML blobs.
     * 
     * @param containerName Azure Blob container name
     * @param blobNames List of blob names to process
     * @return List of metadata rows
     */
    private List<List<String>> extractMetadataFromBlobs(String containerName, List<String> blobNames) {
        List<List<String>> allArchiveMetadata = new ArrayList<>();

        for (String blobName : blobNames) {
            try (InputStream xmlStream = azureBlobService.fetchSingleXmlBlob(containerName, ENV_DEV, blobName).getInputStream()) {
                List<List<String>> blobMetadata = parseArchiveMetadataFromXml(xmlStream);
                allArchiveMetadata.addAll(blobMetadata);
            } catch (Exception e) {
                logger.warn("Failed to process blob: {}", blobName, e);
            }
        }

        return allArchiveMetadata;
    }

    /**
     * Generates a CSV report of archive metadata.
     * 
     * @param archiveMetadata Extracted metadata
     * @param outputDirectory Directory for report
     * @throws IOException 
     */
    private void generateArchiveMetadataReport(List<List<String>> archiveMetadata, String outputDirectory) throws IOException {
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
    private List<List<String>> parseArchiveMetadataFromXml(InputStream inputStream) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(inputStream);
        doc.getDocumentElement().normalize();

        return extractArchiveFileDetails(doc);
    }

    /**
     * Extracts detailed metadata for archive files.
     * 
     * @param document Parsed XML document
     * @return List of metadata rows
     */
    private List<List<String>> extractArchiveFileDetails(Document document) {
        List<List<String>> metadataRows = new ArrayList<>();
        NodeList archiveFileNodes = document.getElementsByTagName("ArchiveFiles");

        for (int i = 0; i < archiveFileNodes.getLength(); i++) {
            Node archiveNode = archiveFileNodes.item(i);
            if (archiveNode.getNodeType() != Node.ELEMENT_NODE) continue;

            Element archiveElement = (Element) archiveNode;
            
            String displayName = extractTextContent(archiveElement, "DisplayName");
            String createTime = extractTextContent(archiveElement, "CreatTime");
            String duration = extractTextContent(archiveElement, "Duration");

            metadataRows.addAll(processMP4Files(archiveElement, displayName, createTime, duration));
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
    private List<List<String>> processMP4Files(
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

                if (isValidMP4File(fileName)) {
                    String formattedFileSize = formatFileSize(fileSizeKb);
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
    private boolean isValidMP4File(String fileName) {
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
    private String formatFileSize(String fileSizeKb) {
        try {
            double sizeInKb = Double.parseDouble(fileSizeKb);
            double sizeInMb = sizeInKb / 1024.0;
            return FILE_SIZE_FORMAT.format(sizeInMb) + " MB";
        } catch (NumberFormatException e) {
            logger.warn("Invalid file size: {}", fileSizeKb);
            return "0.00 MB";
        }
    }
}
