package uk.gov.hmcts.reform.preapi.batch.processor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import uk.gov.hmcts.reform.preapi.services.batch.AzureBlobService;
import uk.gov.hmcts.reform.preapi.services.batch.CsvWriterService;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Service for processing XML files and writing data to CSV.
 * It fetches XML files stored in Azure Blob Storage, parses them, and writes the extracted data into a CSV file.
 */
@Service
public class XmlProcessingService {

    @Autowired
    private AzureBlobService azureBlobService;

    @Autowired
    private CsvWriterService csvWriterService;

    /**
     * Process XML files from a specified Azure Blob container and write extracted data to CSV.
     * @param containerName The Azure Blob container name.
     * @param outputDir The directory where the CSV files will be written.
     */
    public void processXmlAndWriteCsv(String containerName, String outputDir) {
        try {
            List<String> blobNames = azureBlobService.fetchBlobNames(containerName, "dev");
            List<List<String>> allDataRows = new ArrayList<>();

            for (String blobName : blobNames) {
                InputStream inputStream = azureBlobService.fetchSingleXmlBlob(containerName,"dev", blobName).getInputStream();
                List<List<String>> dataRows = parseXmlFromBlob(inputStream);
                allDataRows.addAll(dataRows);
            }

            if (!allDataRows.isEmpty()) {
                csvWriterService.writeToCsv(
                    List.of("archive_name", "create_time", "duration","file_name"), 
                    allDataRows, 
                    "Archive_List", 
                    outputDir, 
                    false
                );
            }
        } catch (Exception e) {
            Logger.getAnonymousLogger().severe("Error processing XML and writing to CSV: " + e.getMessage());
        }
    }

    /**
     * Parses XML content from an InputStream to extract data as rows.
     * @param inputStream The InputStream containing the XML data.
     * @return A list of lists, each list being a row of data.
     * @throws Exception If there is an error during XML parsing.
     */
    private List<List<String>> parseXmlFromBlob(InputStream inputStream) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(inputStream);
        doc.getDocumentElement().normalize();

        List<List<String>> dataRows = new ArrayList<>();
        NodeList nList = doc.getElementsByTagName("ArchiveFiles");

        for (int temp = 0; temp < nList.getLength(); temp++) {
            Node nNode = nList.item(temp);
            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) nNode;
                String displayName = eElement.getElementsByTagName("DisplayName").item(0).getTextContent();
                String creatTime = eElement.getElementsByTagName("CreatTime").item(0).getTextContent();
                String duration = eElement.getElementsByTagName("Duration").item(0).getTextContent();

                NodeList mp4List = eElement.getElementsByTagName("MP4FileGrp");
                if (mp4List.getLength() > 0) {
                    Element mp4Element = (Element) mp4List.item(0);
                    NodeList mp4Files = mp4Element.getElementsByTagName("MP4File");
                    for (int j = 0; j < mp4Files.getLength(); j++) {
                        Element fileElement = (Element) mp4Files.item(j);
                        String fileName = fileElement.getElementsByTagName("Name").item(0).getTextContent();
                        if (fileName.endsWith(".mp4") && fileName.startsWith("0x1e")) {
                            List<String> row = List.of(displayName, creatTime, duration, fileName);
                            dataRows.add(row);
                        }
                    }
                }
            }
        }
        return dataRows;
    }
}
