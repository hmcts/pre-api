package uk.gov.hmcts.reform.preapi.utils;

import lombok.experimental.UtilityClass;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

/**
 * Utility class for sanitizing user input to prevent XSS and other injection attacks.
 * Uses JSoup library to parse and clean HTML content.
 */
@UtilityClass
@SuppressWarnings({"checkstyle:HideUtilityClassConstructor", "checkstyle:JavadocParagraph"})
public class InputSanitizerUtils {

    // Reuse Cleaner instances for better performance
    private static final Cleaner STRICT_CLEANER = new Cleaner(Safelist.none());
    private static final Cleaner BASIC_CLEANER = new Cleaner(Safelist.basic());

    /**
     * Sanitizes input by removing all HTML tags and returning plain text.
     * This is the strictest sanitization mode.
     *
     * @param input The string to sanitize
     * @return Sanitized plain text, or null if input is null
     */
    public static String sanitize(String input) {
        return sanitize(input, false);
    }

    /**
     * Sanitizes input with optional support for basic text formatting.
     * If allowBasicFormatting is true, it allows safe HTML tags
     * If allowBasicFormatting is false it will strip HTML/Script tags fron input.
     * @param input The string to sanitize
     * @param allowBasicFormatting If true, allows safe HTML tags like the ones for bold, emphasis, paragraphs etc.
     * @return Sanitized text, or null if input is null
     */
    private static String sanitize(String input, boolean allowBasicFormatting) {
        if (input == null) {
            return null;
        }

        Cleaner cleaner = allowBasicFormatting ? BASIC_CLEANER : STRICT_CLEANER;

        // If strict mode (no formatting), return just the text content
        if (!allowBasicFormatting) {
            return Jsoup.clean(input, "", Safelist.none(), new Document.OutputSettings().prettyPrint(false));
        }

        // Parse the input as HTML and clean it
        return cleaner.clean(Jsoup.parse(input)).body().html();
    }

    public static boolean isValid(String value, boolean allowBasicFormatting) {
        if (value == null) {
            return true;
        }

        // Check if sanitization would change the string
        // If it changes, it means there was potentially malicious content
        String sanitized = InputSanitizerUtils.sanitize(value, allowBasicFormatting);
        return value.equals(sanitized);
    }
}
