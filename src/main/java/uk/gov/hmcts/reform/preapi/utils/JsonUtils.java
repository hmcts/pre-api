package uk.gov.hmcts.reform.preapi.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;

@UtilityClass
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class JsonUtils {
    public static <E> String toJson(E objectToWriteToJson) {
        try {
            return new ObjectMapper().writeValueAsString(objectToWriteToJson);
        } catch (JsonProcessingException e) {
            throw new UnknownServerException("Something went wrong: " + e.getMessage(), e);
        }
    }
}
