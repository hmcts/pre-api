package uk.gov.hmcts.reform.preapi.utils;

import lombok.experimental.UtilityClass;
import org.jsoup.Jsoup;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@UtilityClass
public class InputSanitizer {

    public static String sanitize(String input) {

        Cleaner cleaner = new Cleaner(Safelist.none());
        String text = cleaner.clean(Jsoup.parse(input)).text();

        return text;
    }

    public static Object sanitizeObject(Object dto) throws IllegalAccessException {
        if (dto == null) {
            return null;
        }
        Field[] fields = getAllFields(dto.getClass());
        for (Field field : fields) {
            if (field.getType() == String.class && !(field.get(dto) == null)) {
                field.setAccessible(true);
                field.set(dto, sanitize((String) field.get(dto)));
            }
        }

        return dto;
    }


    public static Field[] getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();

        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }

        return fields.toArray(new Field[0]);
    }

}
