package uk.gov.hmcts.reform.preapi.batch.util;

public final class ArchiveNameSanitizer {

    private ArchiveNameSanitizer() {
    } 

    public static String sanitize(String archiveName) {
        if (archiveName == null || archiveName.isEmpty()) {
            return "";
        }

        return archiveName
            .replaceFirst("^(?i)OLD[-_\\s]+", "") 
            .replaceFirst("^(?i)NEW[-_\\s]+", "") 
            .replaceAll("[-_\\s]?(?:CP-Case|AS URN)[-_\\s]?$", "")
            .replaceAll("_(?=\\.[^.]+$)", "")
            .replaceAll("[-_\\s]{2,}", "-")
            .replaceAll("\\s*&\\s*", " & ")
            .replaceAll("(?<!&)\\s+(?!&)", "")
            .replaceAll("(?i)^AS[-_\\s]*URN[-_\\s]*", "")
            .replaceAll("(?i)^CP[-_\\s]*CASE[-_\\s]*", "")
            .replaceAll("(?i)CPCASE[-_\\s]*", "")
            .replaceAll("CP_", "")
            .replaceAll("CP-", "")
            .replaceAll("CP ", "")
            .replaceAll("(?i)As Urn[-_\\s]*", "")
            .replaceAll("(?i)asurn[-_\\s]*", "")
            .replaceAll("(?i)See Urn[-_\\s]*", "")
            .replaceAll("(?i)As-Urn[-_\\s]*", "")
            .replaceAll("[\\.]+[-_\\s]*[\\.]+", "-")
            .trim();
    }
}