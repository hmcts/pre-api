package uk.gov.hmcts.reform.preapi.utils;

import lombok.experimental.UtilityClass;
import org.jsoup.Jsoup;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

@UtilityClass
public class InputSanitizer {

    public static String sanitize(String input) {

        Cleaner cleaner = new Cleaner(Safelist.none());
        String text = cleaner.clean(Jsoup.parse(input)).text();

        return text;
    }
}
