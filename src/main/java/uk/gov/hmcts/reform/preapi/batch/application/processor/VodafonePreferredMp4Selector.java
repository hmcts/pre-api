package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class VodafonePreferredMp4Selector {
    private VodafonePreferredMp4Selector() {
    }

    @SuppressWarnings({"PMD.AvoidLiteralsInIfCondition", "PMD.CognitiveComplexity", "PMD.CyclomaticComplexity"})
    public static Element selectPreferredMp4File(NodeList mp4Files) {
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

        int bestIdx = 0;
        for (int i = 1; i < files.size(); i++) {
            Element currentBest = files.get(bestIdx);
            Element challenger = files.get(i);
            int cmp = compareMp4(challenger, currentBest);
            if (cmp > 0 || cmp == 0) {
                bestIdx = i;
            }
        }
        return files.get(bestIdx);
    }

    private static int compareMp4(Element a, Element b) {
        int watermarkCompare = Boolean.compare(getBoolean(a, "watermark"), getBoolean(b, "watermark"));
        if (watermarkCompare != 0) {
            return watermarkCompare;
        }

        String nameA = String.valueOf(getName(a)).toUpperCase(Locale.UK);
        String nameB = String.valueOf(getName(b)).toUpperCase(Locale.UK);

        int ugcCompare = Boolean.compare(nameA.contains("UGC"), nameB.contains("UGC"));
        if (ugcCompare != 0) {
            return ugcCompare;
        }

        long sizeA = getLong(a, "Size");
        long sizeB = getLong(b, "Size");
        int sizeCompare = Long.compare(sizeA, sizeB);
        if (sizeCompare != 0) {
            return sizeCompare;
        }

        long durationA = getLong(a, "Duration");
        long durationB = getLong(b, "Duration");
        boolean durationAValid = durationA >= 0;
        boolean durationBValid = durationB >= 0;

        int validityCompare = Boolean.compare(durationAValid, durationBValid);
        if (validityCompare != 0) {
            return validityCompare;
        }

        if (durationAValid && durationBValid) {
            int durationCompare = Long.compare(durationA, durationB);
            if (durationCompare != 0) {
                return durationCompare;
            }
        }

        return Integer.compare(nameA.length(), nameB.length());
    }

    private static String getName(Element element) {
        return extractTextContent(element, "Name").trim();
    }

    private static boolean getBoolean(Element element, String tag) {
        String content = extractTextContent(element, tag).trim().toLowerCase(Locale.UK);
        return content.equals("true") || content.equals("1") || content.equals("yes");
    }

    private static long getLong(Element element, String tag) {
        try {
            return Long.parseLong(extractTextContent(element, tag).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String extractTextContent(Element element, String tagName) {
        return Optional.ofNullable(element.getElementsByTagName(tagName).item(0))
            .map(Node::getTextContent)
            .orElse("");
    }
}
