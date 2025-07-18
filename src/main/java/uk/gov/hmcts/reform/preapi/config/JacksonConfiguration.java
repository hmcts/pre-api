package uk.gov.hmcts.reform.preapi.config;

import com.azure.core.serializer.json.jackson.JacksonJsonProvider;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;

import static com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS;
import static com.fasterxml.jackson.databind.MapperFeature.INFER_BUILDER_TYPE_BINDINGS;

@Configuration
public class JacksonConfiguration {

    @Primary
    @Bean
    public ObjectMapper getMapper() {
        ObjectMapper mapper = JsonMapper.builder()
                                        .configure(ACCEPT_CASE_INSENSITIVE_ENUMS, true)
                                        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                                        .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
                                        .enable(INFER_BUILDER_TYPE_BINDINGS)
                                        .serializationInclusion(JsonInclude.Include.NON_NULL)
                                        .addModule(JacksonJsonProvider.getJsonSerializableDatabindModule())
                                        .build();

        SimpleModule deserialization = new SimpleModule();
        mapper.registerModule(deserialization);

        JavaTimeModule datetime = new JavaTimeModule();
        datetime.addSerializer(LocalDateSerializer.INSTANCE);
        datetime.addDeserializer(Timestamp.class, new TimestampDeserializer());

        mapper.registerModule(datetime);
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));

        mapper.registerModule(new ParameterNamesModule());

        return mapper;
    }

    private static class TimestampDeserializer extends JsonDeserializer<Timestamp> {
        @Override
        public Timestamp deserialize(JsonParser p, DeserializationContext cxt) throws IOException {
            String timestampStr = p.getText();
            try {
                var instant = Instant.parse(timestampStr);
                return Timestamp.from(instant);
            } catch (Exception e) {
                throw new IOException("Failed to parse Date value '" + timestampStr + "'", e);
            }
        }
    }
}
