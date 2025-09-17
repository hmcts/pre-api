package uk.gov.hmcts.reform.preapi.batch.application.writer;

import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.CaseService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = { MigrationWriter.class })
@Transactional
public class MigrationWriterTest {

    @MockitoBean
    private LoggingService loggingService;

    @MockitoBean
    private CaseService caseService;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private RecordingService recordingService;

    @MockitoBean
    private CaptureSessionService captureSessionService;

    @Autowired
    private MigrationWriter writer;

    @Test
    void stepCommitThrowsUnexpectedRollbackException() {
        when(caseService.upsert(any()))
            .thenReturn(UpsertResult.CREATED) // First call succeeds
            .thenThrow(new Exception("failed to merge participants")); // Second call throws

        // Create test data that will trigger caseService.upsert calls
        var caseData = new CreateCaseDTO();
        caseData.setReference("TEST123456");
        caseData.setCourtId(UUID.randomUUID());
        caseData.setTest(true);
        caseData.setState(CaseState.OPEN);

        var item = MigratedItemGroup.builder()
            .acase(caseData)
            .build();

        Chunk<MigratedItemGroup> chunk = new Chunk<>(List.of(item));

        writer.write(chunk);


    }
}
