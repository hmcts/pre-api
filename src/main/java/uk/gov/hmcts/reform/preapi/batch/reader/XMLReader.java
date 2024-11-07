package uk.gov.hmcts.reform.preapi.batch.reader;

import org.springframework.batch.item.xml.StaxEventItemReader;
import org.springframework.core.io.Resource;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;

@Component
public class XMLReader {

    public <T> StaxEventItemReader<T> createReader(Resource resource, Class<T> targetClass) {
        StaxEventItemReader<T> reader = new StaxEventItemReader<>();
        reader.setResource(resource);
        reader.setFragmentRootElementName("ArchiveFiles"); 

        Jaxb2Marshaller unmarshaller = new Jaxb2Marshaller();
        unmarshaller.setClassesToBeBound(targetClass);
        reader.setUnmarshaller(unmarshaller);

        return reader;
    }
}