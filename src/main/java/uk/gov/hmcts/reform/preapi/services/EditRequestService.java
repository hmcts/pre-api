package uk.gov.hmcts.reform.preapi.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.bean.CsvToBeanBuilder;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.EditInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.repositories.EditRequestRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class EditRequestService {

    private final EditRequestRepository editRequestRepository;
    private final RecordingRepository recordingRepository;

    @Autowired
    public EditRequestService(EditRequestRepository editRequestRepository, RecordingRepository recordingRepository) {
        this.editRequestRepository = editRequestRepository;
        this.recordingRepository = recordingRepository;
    }

    @Transactional
    public List<EditRequest> getPendingEditRequests() {
        return editRequestRepository.findAllByStatusIsOrderByCreatedAt(EditRequestStatus.PENDING);
    }

    @Transactional
    public EditRequestStatus performEdit(UUID editId) {
        // retrieves locked edit request
        var request = editRequestRepository.findById(editId)
            .orElseThrow(() -> new NotFoundException("Edit Request: " + editId));

        if (request.getStatus() != EditRequestStatus.PENDING) {
            throw new ResourceInWrongStateException(
                EditRequest.class.getSimpleName(),
                request.getId().toString(),
                request.getStatus().toString(),
                EditRequestStatus.PENDING.toString()
            );
        }

        request.setStartedAt(Timestamp.from(Instant.now()));
        request.setStatus(EditRequestStatus.PROCESSING);
        editRequestRepository.save(request);

        // todo ffmpeg happens here
        // Thread.sleep(10000);

        request.setFinishedAt(Timestamp.from(Instant.now()));
        request.setStatus(EditRequestStatus.COMPLETE);
        editRequestRepository.save(request);

        return request.getStatus();
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasUpsertAccess(authentication, #dto)")
    public UpsertResult upsert(CreateEditRequestDTO dto) {
        var sourceRecording = recordingRepository.findByIdAndDeletedAtIsNull(dto.getSourceRecordingId())
            .orElseThrow(() -> new NotFoundException("Recording: " + dto.getSourceRecordingId()));

        if (sourceRecording.getDuration() == null) {
            throw new ResourceInWrongStateException("Source Recording ("
                                                        + dto.getSourceRecordingId()
                                                        + ") does not have a valid duration");
        }

        var req = editRequestRepository.findById(dto.getId());
        var request = req.orElse(new EditRequest());

        request.setId(dto.getId());
        request.setSourceRecording(sourceRecording);
        request.setStatus(dto.getStatus());

        var editInstructions = invertInstructions(dto.getEditInstructions(), sourceRecording);
        request.setEditInstruction(toJson(editInstructions));

        var user = ((UserAuthentication) SecurityContextHolder.getContext().getAuthentication())
            .getAppAccess().getUser();
        request.setCreatedBy(user);

        editRequestRepository.save(request);
        var isUpdate = req.isPresent();
        return isUpdate ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasRecordingAccess(authentication, #sourceRecordingId)")
    public Object upsert(UUID sourceRecordingId, MultipartFile file) {
        // temporary code for create edit request with csv endpoint
        var id = UUID.randomUUID();
        upsert(CreateEditRequestDTO.builder()
                   .id(id)
                   .sourceRecordingId(sourceRecordingId)
                   .editInstructions(parseCsv(file))
                   .status(EditRequestStatus.PENDING)
                   .build());

        return editRequestRepository.findById(id)
            .map(EditRequestDTO::new)
            .orElseThrow(() -> new UnknownServerException("Edit Request failed to create"));
    }

    private List<EditInstructionDTO> parseCsv(MultipartFile file) {
        try {
            @Cleanup var reader = new BufferedReader(new InputStreamReader(file.getInputStream()));
            return new CsvToBeanBuilder<EditInstructionDTO>(reader)
                .withType(EditInstructionDTO.class)
                .build()
                .parse();
        } catch (Exception e) {
            log.error("Error when reading CSV file: {} ", e.getMessage());
            throw new UnknownServerException("Ensure uploaded CSV file has columns named 'Start' and 'End'");
        }
    }

    private List<EditInstructionDTO> invertInstructions(List<EditInstructionDTO> instructions, Recording recording) {
        instructions.sort(Comparator.comparing(EditInstructionDTO::getStart));

        var currentTime = 0L;
        var invertedInstructions = new ArrayList<EditInstructionDTO>();

        // invert
        for (var instruction : instructions) {
            if (currentTime < instruction.getStart()) {
                invertedInstructions.add(new EditInstructionDTO(currentTime, instruction.getStart()));
            }
            currentTime = instruction.getEnd();
        }
        invertedInstructions.add(new EditInstructionDTO(currentTime,  recording.getDuration().toSeconds()));

        // verify
        for (int i = 0; i < invertedInstructions.size() - 1; i++) {
            if (invertedInstructions.get(i).getEnd() > invertedInstructions.get(i + 1).getStart()) {
                // todo not this
                log.error("CONFLICT");
                break;
            }
        }

        return invertedInstructions;
    }

    private String toJson(List<EditInstructionDTO> instructions) {
        var obj = Map.of("instructions", instructions);
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new UnknownServerException("Something went wrong: " + e.getMessage());
        }
    }

    private List<EditInstructionDTO> fromJson(String json) {
        try {
            var objectMapper = new ObjectMapper();
            return objectMapper.readValue(objectMapper.readTree(json).get("instructions").toString(),
                                          new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new UnknownServerException("Something went wrong: " + e.getMessage());
        }
    }
}
