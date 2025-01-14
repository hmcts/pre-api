package uk.gov.hmcts.reform.preapi.batch.reader;

import org.springframework.batch.item.xml.StaxEventItemReader;
import org.springframework.core.io.Resource;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.entities.batch.XMLArchiveFileData;

@Component
public class XMLReader {

    public StaxEventItemReader<XMLArchiveFileData> createReader(
        Resource resource, 
        Class<XMLArchiveFileData> targetType
    ) {
        StaxEventItemReader<XMLArchiveFileData> reader = new StaxEventItemReader<>();
        reader.setResource(resource); 
        reader.setFragmentRootElementName("ArchiveFiles");

        Jaxb2Marshaller unmarshaller = new Jaxb2Marshaller();
        unmarshaller.setClassesToBeBound(targetType);
        reader.setUnmarshaller(unmarshaller);

        return reader;
    }

}
