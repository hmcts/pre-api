package uk.gov.hmcts.reform.preapi.utils;

public class StringTools {
    public static boolean isBlankString(String string) {
        return string == null || string.trim().isEmpty();
    }
}
