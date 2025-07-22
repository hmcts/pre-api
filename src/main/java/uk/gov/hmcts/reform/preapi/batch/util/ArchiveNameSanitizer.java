package uk.gov.hmcts.reform.preapi.batch.util;

public final class ArchiveNameSanitizer {

    private ArchiveNameSanitizer() {
    } 

    public static String sanitize(String archiveName) {
        if (archiveName == null || archiveName.isEmpty()) {
            return "";
        }

        return archiveName
            .replaceAll("[-_\\s]?(?:CP-Case|AS URN)[-_\\s]?$", "")
            .replaceAll("_(?=\\.[^.]+$)", "")
            .replaceAll("[-_\\s]{2,}", "-")
            .replaceAll("\\s*&\\s*", " & ")
            .replaceAll("(?<!&)\\s+(?!&)", "")
            .replaceAll("CP_", "")
            .replaceAll("CP-", "")
            .replaceAll("CP ", "")
            .replaceAll("CP Case", "")
            .replaceAll("(?i)As Urn[-_\\s]*", "")
            .replaceAll("(?i)See Urn[-_\\s]*", "")
            .replaceAll("(?i)As-Urn[-_\\s]*", "")
            .replaceAll("[\\.]+[-_\\s]*[\\.]+", "-")
            .trim();
    }
}