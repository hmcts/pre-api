package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

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

@Component
public class VodafonePreferredMp4XmlParser {

    public List<VodafonePreferredMp4Metadata> parsePreferredMp4Files(InputStream inputStream,
                                                                     String blobPrefix,
                                                                     String xmlBlobName)
        throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document = documentBuilder.parse(inputStream);
        document.getDocumentElement().normalize();

        List<VodafonePreferredMp4Metadata> selectedMp4Files = new ArrayList<>();
        NodeList archiveFileNodes = document.getElementsByTagName("ArchiveFiles");

        for (int i = 0; i < archiveFileNodes.getLength(); i++) {
            Node archiveNode = archiveFileNodes.item(i);
            if (archiveNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element archiveElement = (Element) archiveNode;
            String archiveId = extractTextContent(archiveElement, "ArchiveID");
            NodeList mp4FileGroups = archiveElement.getElementsByTagName("MP4FileGrp");

            for (int j = 0; j < mp4FileGroups.getLength(); j++) {
                Element mp4Group = (Element) mp4FileGroups.item(j);
                Element selectedFileElement =
                    VodafonePreferredMp4Selector.selectPreferredMp4File(mp4Group.getElementsByTagName("MP4File"));

                if (selectedFileElement == null) {
                    continue;
                }

                String fileName = extractTextContent(selectedFileElement, "Name");
                if (archiveId.isBlank() || !isValidMp4File(fileName)) {
                    continue;
                }

                selectedMp4Files.add(new VodafonePreferredMp4Metadata(
                    xmlBlobName,
                    archiveId,
                    buildBlobPath(blobPrefix, archiveId, fileName),
                    fileName
                ));
            }
        }

        return selectedMp4Files;
    }

    private String buildBlobPath(String blobPrefix, String archiveId, String fileName) {
        if (blobPrefix == null || blobPrefix.isBlank()) {
            return archiveId + "/mp4/" + fileName;
        }
        return blobPrefix + "/" + archiveId + "/mp4/" + fileName;
    }

    private boolean isValidMp4File(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.UK).endsWith(".mp4");
    }

    private String extractTextContent(Element element, String tagName) {
        return Optional.ofNullable(element.getElementsByTagName(tagName).item(0))
            .map(Node::getTextContent)
            .orElse("");
    }
}
