package uk.gov.hmcts.reform.preapi.services.edit;

import com.opencsv.bean.CsvToBeanBuilder;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.hmcts.reform.preapi.dto.edit.EditCutInstructionsDTO;
import uk.gov.hmcts.reform.preapi.dto.edit.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Deprecated
@Slf4j
@Service
public class EditRequestFromCsv {

    private final EditRequestCrudService editRequestCrudService;

    @Autowired
    public EditRequestFromCsv(EditRequestCrudService editRequestCrudService) {
        this.editRequestCrudService = editRequestCrudService;
    }

    @Transactional
    public EditRequestDTO upsert(UUID sourceRecordingId, MultipartFile file, User user) {
        UUID id = UUID.randomUUID();
        EditRequestDTO dto = new EditRequestDTO();
        dto.setId(id);
        dto.setSourceRecordingId(sourceRecordingId);
        dto.setEditInstructions(parseCsv(file));
        dto.setStatus(EditRequestStatus.PENDING);

        editRequestCrudService.createOrUpsertDraftEditRequestInstructions(dto, user);

        try {
            return editRequestCrudService.findById(id);
        } catch (Exception e) {
            throw new UnknownServerException("Edit Request failed to create");
        }
    }

    private List<EditCutInstructionsDTO> parseCsv(MultipartFile file) {
        try {
            @Cleanup BufferedReader reader = new BufferedReader(new InputStreamReader(
                file.getInputStream(),
                StandardCharsets.UTF_8
            ));
            return new CsvToBeanBuilder<EditCutInstructionsDTO>(reader)
                .withType(EditCutInstructionsDTO.class)
                .build()
                .parse();
        } catch (Exception e) {
            log.error("Error when reading CSV file: {} ", e.getMessage());
            throw new UnknownServerException("Uploaded CSV file incorrectly formatted", e);
        }
    }

}
