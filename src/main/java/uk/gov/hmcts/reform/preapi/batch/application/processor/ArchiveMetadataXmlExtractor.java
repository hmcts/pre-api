package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.ReportCsvWriter;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.media.storage.AzureVodafoneStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Service for processing XML files and writing data to CSV.
 * It fetches XML files stored in Azure Blob Storage, parses them, and writes the extracted data into a CSV file.
 */
@Service
@SuppressWarnings("PMD.GodClass")
public class ArchiveMetadataXmlExtractor {
    private final AzureVodafoneStorageService azureVodafoneStorageService;
    private final MigrationRecordService migrationRecordService;
    private final LoggingService loggingService;

    @Autowired
    public ArchiveMetadataXmlExtractor(final AzureVodafoneStorageService azureVodafoneStorageService,
                                       final MigrationRecordService migrationRecordService,
                                       final LoggingService loggingService) {
        this.azureVodafoneStorageService = azureVodafoneStorageService;
        this.migrationRecordService = migrationRecordService;
        this.loggingService = loggingService;
    }

    /**
     * Process XML files from a specified Azure Blob container and write extracted data to CSV.
     *
     * @param containerName The Azure Blob container name.
     * @param outputDir     The directory where the CSV files will be written.
     */
    @SuppressWarnings("PMD.CognitiveComplexity")
    public void extractAndReportArchiveMetadata(
        String containerName, String folderPrefix, String outputDir, String filename) {
        try {
            loggingService.logInfo("Starting extraction for container: %s", containerName);

            List<String> blobNames = azureVodafoneStorageService.fetchBlobNamesWithPrefix(containerName, folderPrefix);
            loggingService.logDebug(
                "Found %d blobs in container: %s with prefix: %s", blobNames.size(), containerName, folderPrefix);

            if (blobNames.isEmpty()) {
                loggingService.logWarning("No XML blobs found in container: %s" + containerName);
                return;
            }

            List<List<String>> allArchiveMetadata = extractMetadataFromBlobs(containerName, blobNames);
            loggingService.logDebug("Extracted metadata for %d recordings", allArchiveMetadata.size());

            if (!allArchiveMetadata.isEmpty()) {
                loggingService.logDebug("Generating archive metadata report in directory: %s", outputDir);

                allArchiveMetadata.sort((a, b) -> {
                    String nameA = a.get(1).toLowerCase(Locale.UK);
                    String nameB = b.get(1).toLowerCase(Locale.UK);

                    String versionA = extractVersion(nameA);
                    String versionB = extractVersion(nameB);

                    boolean aIsOrig = Constants.VALID_ORIG_TYPES.contains(versionA.toUpperCase(Locale.UK));
                    boolean bIsOrig = Constants.VALID_ORIG_TYPES.contains(versionB.toUpperCase(Locale.UK));

                    if (aIsOrig && !bIsOrig) {
                        return -1;
                    }
                    if (!aIsOrig && bIsOrig) {
                        return 1;
                    }
                    return nameA.compareTo(nameB);
                });

                int insertCount = 0;
                for (List<String> row : allArchiveMetadata) {
                    try {
                        boolean inserted = migrationRecordService.insertPendingFromXml(
                            row.get(0), // archiveId
                            row.get(1), // displayName
                            row.get(2), // createTime
                            row.get(3), // duration
                            row.get(4), // fileName
                            row.get(5)  // fileSizeMb
                        );
                        if (inserted) {
                            insertCount++;
                        }
                    } catch (Exception e) {
                        loggingService.logError("Failed to insert row into migration records: %s", e.getMessage());
                    }
                }

                generateArchiveMetadataReport(allArchiveMetadata, outputDir, filename);
                loggingService.logInfo(
                    "Successfully generated %s.csv with %d entries",filename, allArchiveMetadata.size());
                loggingService.logInfo(
                    "ArchiveMetadataXmlExtractor - Inserted %d records into migration table", insertCount);

            } else {
                loggingService.logWarning("No archive metadata found to generate report");
            }

        } catch (Exception e) {
            loggingService.logError("Error processing XML and writing to CSV: %s", e.getMessage());
        }
    }

    /**
     * Extracts metadata from XML blobs.
     *
     * @param containerName Azure Blob container name
     * @param blobNames     List of blob names to process
     * @return List of metadata rows
     */
    private List<List<String>> extractMetadataFromBlobs(String containerName, List<String> blobNames) {
        List<List<String>> allArchiveMetadata = new ArrayList<>();
        for (String blobName : blobNames) {
            try (InputStream xmlStream = azureVodafoneStorageService.fetchSingleXmlBlob(
                containerName, blobName).getInputStream()) {

                String blobPrefix = blobName.contains("/") ? blobName.substring(0, blobName.indexOf('/')) : "";
                List<List<String>> blobMetadata = parseArchiveMetadataFromXml(xmlStream, blobPrefix);
                if (!blobMetadata.isEmpty()) {
                    allArchiveMetadata.addAll(blobMetadata);
                    loggingService.logDebug("Processing blob: %s", blobName);
                } else {
                    loggingService.logWarning("Blob contains no metadata: " + blobName);
                }

            } catch (Exception e) {
                loggingService.logError("Failed to process blob: %s", blobName, e);
            }
        }
        return allArchiveMetadata;
    }

    private void generateArchiveMetadataReport(List<List<String>> extractedArchiveMetadata,
                                               String reportOutputDirectory,
                                               String filename) throws IOException {

        List<String> headers = List.of(
            "archive_id",
            "archive_name",
            "create_time",
            "duration",
            "file_name",
            "file_size"
        );

        ReportCsvWriter.writeToCsv(
            headers,
            extractedArchiveMetadata,
            filename,
            reportOutputDirectory,
            false
        );
    }

    /**
     * Parses XML content from an InputStream to extract data as rows.
     *
     * @param inputStream The InputStream containing the XML data.
     * @return A list of lists, each list being a row of data.
     * @throws Exception If there is an error during XML parsing.
     */
    private List<List<String>> parseArchiveMetadataFromXml(InputStream inputStream, String blobPrefix)
        throws IOException, SAXException, ParserConfigurationException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(inputStream);
        doc.getDocumentElement().normalize();

        return extractArchiveFileDetails(doc, blobPrefix);
    }

    /**
     * Extracts detailed metadata for archive files.
     *
     * @param document Parsed XML document
     * @return List of metadata rows
     */
    private List<List<String>> extractArchiveFileDetails(Document document, String blobPrefix) {
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
            if (createTime == null || createTime.isEmpty() || createTime.equals("0") || createTime.equals("3600000")) {
                createTime = extractTextContent(archiveElement, "updatetime");
            }
            String duration = extractTextContent(archiveElement, "Duration");

            if (displayName.isEmpty()) {
                loggingService.logWarning("Missing DisplayName in ArchiveFiles element.");
            }
            if (createTime.isEmpty()) {
                loggingService.logWarning("Missing CreatTime for archive: " + displayName);
            }
            String archiveId = extractTextContent(archiveElement, "ArchiveID");
            metadataRows.addAll(processMP4Files(
                archiveElement, blobPrefix, archiveId, displayName, createTime, duration));
        }
        return metadataRows;
    }

    /**
     * Processes MP4 files within an archive element.
     *
     * @param archiveElement Archive XML element
     * @param displayName    Archive display name
     * @param createTime     Creation time
     * @param duration       Recording duration
     * @return List of metadata rows for MP4 files
     */
    @SuppressWarnings("PMD.CognitiveComplexity")
    private List<List<String>> processMP4Files(
        Element archiveElement,
        String blobPrefix,
        String archiveId,
        String displayName,
        String createTime,
        String duration
    ) {
        List<List<String>> fileRows = new ArrayList<>();
        NodeList mp4FileGroups = archiveElement.getElementsByTagName("MP4FileGrp");

        for (int j = 0; j < mp4FileGroups.getLength(); j++) {
            Element mp4Group = (Element) mp4FileGroups.item(j);
            NodeList mp4Files = mp4Group.getElementsByTagName("MP4File");

            Element fileElement = selectPreferredMp4File(mp4Files);

            if (fileElement != null) {
                String fileName = extractTextContent(fileElement, "Name");
                String fileSizeKb = extractTextContent(fileElement, "Size");

                if (fileName.isEmpty() || fileSizeKb.isEmpty()) {
                    loggingService.logWarning(
                        "MP4 file missing required fields: Name=%s, Size=%s" + fileName + fileSizeKb
                    );
                    continue;
                }
                if (isValidMP4File(fileName)) {
                    String formattedFileSize = formatFileSize(fileSizeKb);

                    String fullPath = (blobPrefix == null || blobPrefix.isBlank())
                        ? archiveId + "/" + fileName
                        : blobPrefix + "/" + archiveId + "/" + fileName;

                    fileRows.add(List.of(archiveId, displayName, createTime, duration, fullPath, formattedFileSize));
                }
            }
        }
        return fileRows;
    }

    @SuppressWarnings({"PMD.AvoidLiteralsInIfCondition", "PMD.CognitiveComplexity", "PMD.CyclomaticComplexity"})
    private Element selectPreferredMp4File(NodeList mp4Files) {
        List<Element> files = new ArrayList<>();
        for (int i = 0; i < mp4Files.getLength(); i++) {
            Node node = mp4Files.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                files.add((Element) node);
            }
        }

        if (files.isEmpty()) {
            return null;
        }
        if (files.size() == 1) {
            return files.get(0);
        }

        if (files.size() == 2) {
            Element elementA = files.get(0);
            Element elementB = files.get(1);

            boolean aWatermarked = getBoolean(elementA, "watermark");
            boolean bWatermarked = getBoolean(elementB, "watermark");

            // (1) Prefer watermark = true when only one has it
            if (aWatermarked ^ bWatermarked) {
                return aWatermarked ? elementA : elementB;
            }

            // (2) If neither or both have watermark=true, prefer name starting with "UGC" when only one has it;
            String nameA = getName(elementA).toUpperCase(Locale.UK);
            String nameB = getName(elementB).toUpperCase(Locale.UK);
            boolean aUGC = nameA.contains("UGC");
            boolean bUGC = nameB.contains("UGC");
            if (aUGC && !bUGC) {
                return elementA;
            }

            if (!aUGC && bUGC) {
                return elementB;
            }

            // (3) Otherwise pick the largest mp4 file size
            long aSize = getLong(elementA, "Size");
            long bSize = getLong(elementB, "Size");
            if (aSize != bSize) {
                return (aSize > bSize) ? elementA : elementB;
            }

            // (4) Duration should be equal or within 1s (sanity check / log only)
            long aDur = getLong(elementA, "Duration");
            long bDur = getLong(elementB, "Duration");

            boolean aDurValid = aDur >= 0;
            boolean bDurValid = bDur >= 0;

            if (aDurValid && bDurValid && aDur != bDur) {
                return (aDur > bDur) ? elementA : elementB;
            }
            if (aDurValid != bDurValid) {
                return aDurValid ? elementA : elementB;
            }

            // (5) If duration ties (or both invalid), prefer longer filename
            int lenA = nameA != null ? nameA.length() : 0;
            int lenB = nameB != null ? nameB.length() : 0;
            if (lenA != lenB) {
                return (lenA > lenB) ? elementA : elementB;
            }

            // Fallback: pick the second file (as you had)
            return elementB;
        }

        return files.get(0);
    }

    /**
     * Checks if the file is a valid MP4 file.
     *
     * @param fileName File name to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidMP4File(String fileName) {
        return fileName.endsWith(".mp4");
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
            return Constants.FILE_SIZE_FORMAT.format(sizeInMb);
        } catch (NumberFormatException e) {
            loggingService.logWarning("Invalid file size: " + fileSizeKb);
            return "0.00";
        }
    }

    private String extractVersion(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "";
        }

        String cleanName = displayName.toUpperCase(Locale.UK)
            .replaceAll("\\.(MP4|RAW|M4A|MOV|AVI)$", "");

        String[] parts = cleanName.split("-");

        for (int i = parts.length - 1; i >= 0; i--) {
            String part = parts[i];
            for (String valid : Constants.VALID_VERSION_TYPES) {
                if (part.startsWith(valid)) {
                    return valid;
                }
            }
        }

        return "";
    }

    //---------- helpers ----------

    private String getName(Element el) {
        String content = extractTextContent(el, "Name");
        return content == null ? "" : content.trim();
    }

    private boolean getBoolean(Element el, String tag) {
        String content = extractTextContent(el, tag);
        if (content == null) {
            return false;
        }
        content = content.trim().toLowerCase(Locale.UK);
        return content.equals("true") || content.equals("1") || content.equals("yes");
    }

    private long getLong(Element el, String tag) {
        try {
            String content = extractTextContent(el, tag);
            return content == null ? 0L : Long.parseLong(content.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

}
