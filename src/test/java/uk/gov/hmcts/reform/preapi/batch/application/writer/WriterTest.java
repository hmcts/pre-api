package uk.gov.hmcts.reform.preapi.batch.application.writer;

import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.batch.entities.PassItem;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.CaseService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = { MigrationWriter.class })
public class WriterTest {
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

    @MockitoBean
    private MigrationTrackerService migrationTrackerService;

    @Autowired
    private MigrationWriter writer;

    @Test
    void writeMigratedItemsEmpty() {
        writer.write(Chunk.of());

        verify(loggingService, times(1)).logInfo("Processing chunk with %d migrated items", 0);
        verify(loggingService, times(1)).logWarning("No valid items found in the current chunk.");
    }

    @Test
    void writeItemGroupCaseUpsertFailure() {
        CreateCaseDTO createCaseDTO = new CreateCaseDTO();
        createCaseDTO.setId(UUID.randomUUID());
        createCaseDTO.setReference("REFERENCE");
        MigratedItemGroup itemGroup = MigratedItemGroup.builder()
            .acase(createCaseDTO)
            .build();

        doThrow(NotFoundException.class).when(caseService).upsert(any(CreateCaseDTO.class));

        writer.write(Chunk.of(itemGroup));

        verify(caseService, times(1)).upsert(any(CreateCaseDTO.class));
        verify(loggingService, times(1))
            .logError(eq("Failed to upsert case. Case id: %s | %s"), eq(createCaseDTO.getId()), any());
    }

    @Test
    void writeItemGroupCaseSuccess() {
        CreateCaseDTO createCaseDTO = new CreateCaseDTO();
        createCaseDTO.setId(UUID.randomUUID());
        createCaseDTO.setReference("REFERENCE");
        MigratedItemGroup itemGroup = MigratedItemGroup.builder()
            .acase(createCaseDTO)
            .build();

        when(caseService.upsert(any(CreateCaseDTO.class))).thenReturn(UpsertResult.CREATED);

        writer.write(Chunk.of(itemGroup));

        verify(caseService, times(1)).upsert(any(CreateCaseDTO.class));
        verify(loggingService, never())
            .logError(eq("Failed to upsert case. Case id: %s | %s"), eq(createCaseDTO.getId()), any());
    }

    @Test
    void writeItemGroupBookingFailure() {
        CreateCaseDTO createCaseDTO = new CreateCaseDTO();
        createCaseDTO.setId(UUID.randomUUID());
        createCaseDTO.setReference("REFERENCE");
        CreateBookingDTO createBookingDTO = new CreateBookingDTO();
        createBookingDTO.setId(UUID.randomUUID());
        MigratedItemGroup itemGroup = MigratedItemGroup.builder()
            .acase(createCaseDTO)
            .booking(createBookingDTO)
            .build();

        when(caseService.upsert(any(CreateCaseDTO.class))).thenReturn(UpsertResult.CREATED);
        doThrow(NotFoundException.class).when(bookingService).upsert(any(CreateBookingDTO.class));

        writer.write(Chunk.of(itemGroup));

        verify(caseService, times(1)).upsert(any(CreateCaseDTO.class));
        verify(bookingService, times(1)).upsert(any(CreateBookingDTO.class));
        verify(loggingService, times(1))
            .logError(eq("Failed to upsert booking. Booking id: %s | %s"), eq(createBookingDTO.getId()), any());
    }

    @Test
    void writeItemGroupCaptureSessionFailure() {
        CreateCaseDTO createCaseDTO = new CreateCaseDTO();
        createCaseDTO.setId(UUID.randomUUID());
        createCaseDTO.setReference("REFERENCE");
        CreateBookingDTO createBookingDTO = new CreateBookingDTO();
        createBookingDTO.setId(UUID.randomUUID());
        CreateCaptureSessionDTO createCaptureSessionDTO = new CreateCaptureSessionDTO();
        createCaptureSessionDTO.setId(UUID.randomUUID());

        when(caseService.upsert(any(CreateCaseDTO.class))).thenReturn(UpsertResult.CREATED);
        when(bookingService.upsert(any(CreateBookingDTO.class))).thenReturn(UpsertResult.CREATED);
        doThrow(NotFoundException.class).when(captureSessionService).upsert(any(CreateCaptureSessionDTO.class));

        MigratedItemGroup itemGroup = MigratedItemGroup.builder()
            .acase(createCaseDTO)
            .booking(createBookingDTO)
            .captureSession(createCaptureSessionDTO)
            .build();

        writer.write(Chunk.of(itemGroup));

        verify(caseService, times(1)).upsert(any(CreateCaseDTO.class));
        verify(bookingService, times(1)).upsert(any(CreateBookingDTO.class));
        verify(captureSessionService, times(1)).upsert(any(CreateCaptureSessionDTO.class));
        verify(loggingService, times(1))
            .logError(
                eq("Failed to upsert capture session. Capture Session id: %s | %s"),
                eq(createCaptureSessionDTO.getId()),
                any()
            );
    }

    @Test
    void writeItemGroupCaptureSessionSuccess() {
        CreateCaseDTO createCaseDTO = new CreateCaseDTO();
        createCaseDTO.setId(UUID.randomUUID());
        createCaseDTO.setReference("REFERENCE");
        CreateBookingDTO createBookingDTO = new CreateBookingDTO();
        createBookingDTO.setId(UUID.randomUUID());
        CreateCaptureSessionDTO createCaptureSessionDTO = new CreateCaptureSessionDTO();
        createCaptureSessionDTO.setId(UUID.randomUUID());

        when(caseService.upsert(any(CreateCaseDTO.class))).thenReturn(UpsertResult.CREATED);
        when(bookingService.upsert(any(CreateBookingDTO.class))).thenReturn(UpsertResult.CREATED);
        when(captureSessionService.upsert(any(CreateCaptureSessionDTO.class))).thenReturn(UpsertResult.CREATED);

        MigratedItemGroup itemGroup = MigratedItemGroup.builder()
            .acase(createCaseDTO)
            .booking(createBookingDTO)
            .captureSession(createCaptureSessionDTO)
            .build();

        writer.write(Chunk.of(itemGroup));

        verify(caseService, times(1)).upsert(any(CreateCaseDTO.class));
        verify(bookingService, times(1)).upsert(any(CreateBookingDTO.class));
        verify(captureSessionService, times(1)).upsert(any(CreateCaptureSessionDTO.class));
        verify(loggingService, never())
            .logError(
                eq("Failed to upsert capture session. Capture Session id: %s | %s"),
                eq(createCaptureSessionDTO.getId()),
                any()
            );
    }

    @Test
    void writeItemGroupRecordingFailure() {
        CreateCaseDTO createCaseDTO = new CreateCaseDTO();
        createCaseDTO.setId(UUID.randomUUID());
        createCaseDTO.setReference("REFERENCE");
        CreateBookingDTO createBookingDTO = new CreateBookingDTO();
        createBookingDTO.setId(UUID.randomUUID());
        CreateCaptureSessionDTO createCaptureSessionDTO = new CreateCaptureSessionDTO();
        createCaptureSessionDTO.setId(UUID.randomUUID());
        CreateRecordingDTO createRecordingDTO = new CreateRecordingDTO();
        createRecordingDTO.setId(UUID.randomUUID());

        when(caseService.upsert(any(CreateCaseDTO.class))).thenReturn(UpsertResult.CREATED);
        when(bookingService.upsert(any(CreateBookingDTO.class))).thenReturn(UpsertResult.CREATED);
        when(captureSessionService.upsert(any(CreateCaptureSessionDTO.class))).thenReturn(UpsertResult.CREATED);
        doThrow(NotFoundException.class).when(recordingService).upsert(any(CreateRecordingDTO.class));

        MigratedItemGroup itemGroup = MigratedItemGroup.builder()
            .acase(createCaseDTO)
            .booking(createBookingDTO)
            .captureSession(createCaptureSessionDTO)
            .recording(createRecordingDTO)
            .build();

        writer.write(Chunk.of(itemGroup));

        verify(caseService, times(1)).upsert(any(CreateCaseDTO.class));
        verify(bookingService, times(1)).upsert(any(CreateBookingDTO.class));
        verify(captureSessionService, times(1)).upsert(any(CreateCaptureSessionDTO.class));
        verify(recordingService, times(1)).upsert(any(CreateRecordingDTO.class));
        verify(loggingService, times(1))
            .logError(
                eq("Failed to upsert recording. Recording id: %s | %s"),
                eq(createRecordingDTO.getId()),
                any()
            );
    }

    @Test
    void writeItemGroupRecordingSuccess() {
        CreateCaseDTO createCaseDTO = new CreateCaseDTO();
        createCaseDTO.setId(UUID.randomUUID());
        createCaseDTO.setReference("REFERENCE");
        CreateBookingDTO createBookingDTO = new CreateBookingDTO();
        createBookingDTO.setId(UUID.randomUUID());
        CreateCaptureSessionDTO createCaptureSessionDTO = new CreateCaptureSessionDTO();
        createCaptureSessionDTO.setId(UUID.randomUUID());
        CreateRecordingDTO createRecordingDTO = new CreateRecordingDTO();
        createRecordingDTO.setId(UUID.randomUUID());

        when(caseService.upsert(any(CreateCaseDTO.class))).thenReturn(UpsertResult.CREATED);
        when(bookingService.upsert(any(CreateBookingDTO.class))).thenReturn(UpsertResult.CREATED);
        when(captureSessionService.upsert(any(CreateCaptureSessionDTO.class))).thenReturn(UpsertResult.CREATED);
        when(recordingService.upsert(any(CreateRecordingDTO.class))).thenReturn(UpsertResult.CREATED);

        MigratedItemGroup itemGroup = MigratedItemGroup.builder()
            .acase(createCaseDTO)
            .booking(createBookingDTO)
            .captureSession(createCaptureSessionDTO)
            .recording(createRecordingDTO)
            .build();

        writer.write(Chunk.of(itemGroup));

        verify(caseService, times(1)).upsert(any(CreateCaseDTO.class));
        verify(bookingService, times(1)).upsert(any(CreateBookingDTO.class));
        verify(captureSessionService, times(1)).upsert(any(CreateCaptureSessionDTO.class));
        verify(recordingService, times(1)).upsert(any(CreateRecordingDTO.class));
        verify(loggingService, never())
            .logError(
                eq("Failed to upsert recording. Recording id: %s | %s"),
                eq(createRecordingDTO.getId()),
                any()
            );
    }

    @Test
    void writeItemGroupInvitesFailure() {
        CreateCaseDTO createCaseDTO = new CreateCaseDTO();
        createCaseDTO.setId(UUID.randomUUID());
        createCaseDTO.setReference("REFERENCE");
        CreateBookingDTO createBookingDTO = new CreateBookingDTO();
        createBookingDTO.setId(UUID.randomUUID());
        CreateCaptureSessionDTO createCaptureSessionDTO = new CreateCaptureSessionDTO();
        createCaptureSessionDTO.setId(UUID.randomUUID());
        CreateRecordingDTO createRecordingDTO = new CreateRecordingDTO();
        createRecordingDTO.setId(UUID.randomUUID());

        when(caseService.upsert(any(CreateCaseDTO.class))).thenReturn(UpsertResult.CREATED);
        when(bookingService.upsert(any(CreateBookingDTO.class))).thenReturn(UpsertResult.CREATED);
        when(captureSessionService.upsert(any(CreateCaptureSessionDTO.class))).thenReturn(UpsertResult.CREATED);
        when(recordingService.upsert(any(CreateRecordingDTO.class))).thenReturn(UpsertResult.CREATED);

        MigratedItemGroup itemGroup = MigratedItemGroup.builder()
            .acase(createCaseDTO)
            .booking(createBookingDTO)
            .captureSession(createCaptureSessionDTO)
            .recording(createRecordingDTO)
            .build();

        writer.write(Chunk.of(itemGroup));

        verify(caseService, times(1)).upsert(any(CreateCaseDTO.class));
        verify(bookingService, times(1)).upsert(any(CreateBookingDTO.class));
        verify(captureSessionService, times(1)).upsert(any(CreateCaptureSessionDTO.class));
        verify(recordingService, times(1)).upsert(any(CreateRecordingDTO.class));
 
    }

    @Test
    void writeItemGroupInvitesSuccess() {
        CreateCaseDTO createCaseDTO = new CreateCaseDTO();
        createCaseDTO.setId(UUID.randomUUID());
        createCaseDTO.setReference("REFERENCE");
        CreateBookingDTO createBookingDTO = new CreateBookingDTO();
        createBookingDTO.setId(UUID.randomUUID());
        CreateCaptureSessionDTO createCaptureSessionDTO = new CreateCaptureSessionDTO();
        createCaptureSessionDTO.setId(UUID.randomUUID());
        CreateRecordingDTO createRecordingDTO = new CreateRecordingDTO();
        createRecordingDTO.setId(UUID.randomUUID());

        when(caseService.upsert(any(CreateCaseDTO.class))).thenReturn(UpsertResult.CREATED);
        when(bookingService.upsert(any(CreateBookingDTO.class))).thenReturn(UpsertResult.CREATED);
        when(captureSessionService.upsert(any(CreateCaptureSessionDTO.class))).thenReturn(UpsertResult.CREATED);
        when(recordingService.upsert(any(CreateRecordingDTO.class))).thenReturn(UpsertResult.CREATED);

        MigratedItemGroup itemGroup = MigratedItemGroup.builder()
            .acase(createCaseDTO)
            .booking(createBookingDTO)
            .captureSession(createCaptureSessionDTO)
            .recording(createRecordingDTO)
            .build();

        writer.write(Chunk.of(itemGroup));

        verify(caseService, times(1)).upsert(any(CreateCaseDTO.class));
        verify(bookingService, times(1)).upsert(any(CreateBookingDTO.class));
        verify(captureSessionService, times(1)).upsert(any(CreateCaptureSessionDTO.class));
        verify(recordingService, times(1)).upsert(any(CreateRecordingDTO.class));
    
    }

    @Test
    void writeItemGroupShareBookingFailure() {
        CreateCaseDTO createCaseDTO = new CreateCaseDTO();
        createCaseDTO.setId(UUID.randomUUID());
        createCaseDTO.setReference("REFERENCE");
        CreateBookingDTO createBookingDTO = new CreateBookingDTO();
        createBookingDTO.setId(UUID.randomUUID());
        CreateCaptureSessionDTO createCaptureSessionDTO = new CreateCaptureSessionDTO();
        createCaptureSessionDTO.setId(UUID.randomUUID());
        CreateRecordingDTO createRecordingDTO = new CreateRecordingDTO();
        createRecordingDTO.setId(UUID.randomUUID());

        when(caseService.upsert(any(CreateCaseDTO.class))).thenReturn(UpsertResult.CREATED);
        when(bookingService.upsert(any(CreateBookingDTO.class))).thenReturn(UpsertResult.CREATED);
        when(captureSessionService.upsert(any(CreateCaptureSessionDTO.class))).thenReturn(UpsertResult.CREATED);
        when(recordingService.upsert(any(CreateRecordingDTO.class))).thenReturn(UpsertResult.CREATED);
    
        MigratedItemGroup itemGroup = MigratedItemGroup.builder()
            .acase(createCaseDTO)
            .booking(createBookingDTO)
            .captureSession(createCaptureSessionDTO)
            .recording(createRecordingDTO)
            .build();

        writer.write(Chunk.of(itemGroup));

        verify(caseService, times(1)).upsert(any(CreateCaseDTO.class));
        verify(bookingService, times(1)).upsert(any(CreateBookingDTO.class));
        verify(captureSessionService, times(1)).upsert(any(CreateCaptureSessionDTO.class));
        verify(recordingService, times(1)).upsert(any(CreateRecordingDTO.class));
    }

    @Test
    void writeItemGroupShareBookingSuccess() {
        CreateCaseDTO createCaseDTO = new CreateCaseDTO();
        createCaseDTO.setId(UUID.randomUUID());
        createCaseDTO.setReference("REFERENCE");
        CreateBookingDTO createBookingDTO = new CreateBookingDTO();
        createBookingDTO.setId(UUID.randomUUID());
        CreateCaptureSessionDTO createCaptureSessionDTO = new CreateCaptureSessionDTO();
        createCaptureSessionDTO.setId(UUID.randomUUID());
        CreateRecordingDTO createRecordingDTO = new CreateRecordingDTO();
        createRecordingDTO.setId(UUID.randomUUID());


        when(caseService.upsert(any(CreateCaseDTO.class))).thenReturn(UpsertResult.CREATED);
        when(bookingService.upsert(any(CreateBookingDTO.class))).thenReturn(UpsertResult.CREATED);
        when(captureSessionService.upsert(any(CreateCaptureSessionDTO.class))).thenReturn(UpsertResult.CREATED);
        when(recordingService.upsert(any(CreateRecordingDTO.class))).thenReturn(UpsertResult.CREATED);


        MigratedItemGroup itemGroup = MigratedItemGroup.builder()
            .acase(createCaseDTO)
            .booking(createBookingDTO)
            .captureSession(createCaptureSessionDTO)
            .recording(createRecordingDTO)
            .passItem(mock(PassItem.class))
            .build();

        writer.write(Chunk.of(itemGroup));

        verify(caseService, times(1)).upsert(any(CreateCaseDTO.class));
        verify(bookingService, times(1)).upsert(any(CreateBookingDTO.class));
        verify(captureSessionService, times(1)).upsert(any(CreateCaptureSessionDTO.class));
        verify(recordingService, times(1)).upsert(any(CreateRecordingDTO.class));
        verify(migrationTrackerService, times(1)).addMigratedItem(any(PassItem.class));
    }
}
