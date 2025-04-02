package uk.gov.hmcts.reform.preapi.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.hmcts.reform.preapi.dto.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.UnsupportedMediaTypeException;
import uk.gov.hmcts.reform.preapi.services.EditRequestService;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/edits")
public class EditController {
    private final EditRequestService editRequestService;

    public static final String CSV_FILE_TYPE = "text/csv";

    @Autowired
    public EditController(EditRequestService editRequestService) {
        this.editRequestService = editRequestService;
    }

    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER')")
    @PostMapping(value = "/from-csv/{sourceRecordingId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EditRequestDTO> createEditFromCsv(@PathVariable UUID sourceRecordingId,
                                                     @RequestParam("file") MultipartFile file) {
        var fileType = file.getContentType();
        if (fileType == null || !fileType.equals(CSV_FILE_TYPE)) {
            throw new UnsupportedMediaTypeException("Unsupported media type: Only CSV files are supported");
        }

        if (file.isEmpty()) {
            throw new BadRequestException("File must not be empty");
        }

        return ResponseEntity.ok(editRequestService.upsert(sourceRecordingId, file));
    }
}
