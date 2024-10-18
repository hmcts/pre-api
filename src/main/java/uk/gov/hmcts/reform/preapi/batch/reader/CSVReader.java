package uk.gov.hmcts.reform.preapi.batch.reader;

import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CSVReader {


    public <T> FlatFileItemReader<T> createReader(Resource resource, String[] fieldNames, 
        Class<T> targetClass, String delimiter) throws IOException {

        try {
            return new FlatFileItemReaderBuilder<T>()
                    .name(targetClass.getSimpleName() + "Reader") 
                    .resource(resource) 
                    .linesToSkip(1) 
                    .delimited().delimiter(delimiter)
                    .names(fieldNames) 
                    .fieldSetMapper(new BeanWrapperFieldSetMapper<T>() {{
                            setTargetType(targetClass);
                        }
                    })
                    .build();
        } catch (Exception e) {
            throw new IOException("Error while reading the CSV file: " + e.getMessage(), e);
        }
    }
}
