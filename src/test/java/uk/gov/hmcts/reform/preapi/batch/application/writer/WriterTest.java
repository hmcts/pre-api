package uk.gov.hmcts.reform.preapi.batch.application.writer;

import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.batch.entities.PassItem;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.CaseService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;

import java.util.List;
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

    // ---------- helpers ----------

    private static CaseDTO persistedCase(UUID id, UUID courtId, String reference) {
        var c = new CaseDTO();
        c.setId(id != null ? id : UUID.randomUUID());
        c.setReference(reference);
        var court = new CourtDTO();
        court.setId(courtId);
        c.setCourt(court);
        c.setState(CaseState.OPEN);
        return c;
    }

    private static Page<CaseDTO> pageOf(CaseDTO... items) {
        return new PageImpl<>(List.of(items));
    }

    // ---------- tests ----------

    @Test
    void writeMigratedItemsEmpty() {
        writer.write(Chunk.of());

        verify(loggingService, times(1)).logInfo("Processing chunk with %d migrated items", 0);
        verify(loggingService, times(1)).logWarning("No valid items found in the current chunk.");
    }

    @Test
    void writeItemGroupCaseUpsertFailure() {
        var courtId = UUID.randomUUID();
        CreateCaseDTO createCaseDTO = new CreateCaseDTO();
        createCaseDTO.setId(UUID.randomUUID());
        createCaseDTO.setReference("REFERENCE");
        createCaseDTO.setCourtId(courtId);

        when(caseService.searchBy(eq("REFERENCE"), eq(courtId), eq(false), any(PageRequest.class)))
            .thenReturn(Page.empty());

        MigratedItemGroup itemGroup = MigratedItemGroup.builder()
            .acase(createCaseDTO)
            .build();

        doThrow(NotFoundException.class).when(caseService).upsert(any(CreateCaseDTO.class));

        writer.write(Chunk.of(itemGroup));

        verify(caseService, times(1)).upsert(any(CreateCaseDTO.class));
        verify(loggingService, times(1))
            .logError(eq("Create case failed (safe path). ref=%s court=%s | %s"),
                eq("REFERENCE"), eq(courtId), any());
    
    }

    @Test
    void writeItemGroupCaseSuccess() {
        var courtId = UUID.randomUUID();
        CreateCaseDTO createCaseDTO = new CreateCaseDTO();
        createCaseDTO.setId(UUID.randomUUID());
        createCaseDTO.setReference("REFERENCE");
        createCaseDTO.setCourtId(courtId);

        var persisted = persistedCase(UUID.randomUUID(), courtId, "REFERENCE");
        when(caseService.searchBy(eq("REFERENCE"), eq(courtId), eq(false), any(PageRequest.class)))
            .thenReturn(pageOf(persisted)) 
            .thenReturn(pageOf(persisted));

        MigratedItemGroup itemGroup = MigratedItemGroup.builder()
            .acase(createCaseDTO)
            .build();

        when(caseService.upsert(any(CreateCaseDTO.class))).thenReturn(UpsertResult.CREATED);

        writer.write(Chunk.of(itemGroup));

        verify(caseService, times(1)).upsert(any(CreateCaseDTO.class));
        verify(loggingService, never())
            .logError(eq("Merge participants failed (safe path). caseId=%s | %s"), any(), any());
    }

    @Test
    void writeItemGroupBookingFailure() {
        var courtId = UUID.randomUUID();
        CreateCaseDTO createCaseDTO = new CreateCaseDTO();
        createCaseDTO.setId(UUID.randomUUID());
        createCaseDTO.setReference("REFERENCE");
        createCaseDTO.setCourtId(courtId);

        var persisted = persistedCase(UUID.randomUUID(), courtId, "REFERENCE");
        when(caseService.searchBy(eq("REFERENCE"), eq(courtId), eq(false), any(PageRequest.class)))
            .thenReturn(pageOf(persisted))
            .thenReturn(pageOf(persisted));

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
        var courtId = UUID.randomUUID();
        CreateCaseDTO createCaseDTO = new CreateCaseDTO();
        createCaseDTO.setId(UUID.randomUUID());
        createCaseDTO.setReference("REFERENCE");
        createCaseDTO.setCourtId(courtId);

        var persisted = persistedCase(UUID.randomUUID(), courtId, "REFERENCE");
        when(caseService.searchBy(eq("REFERENCE"), eq(courtId), eq(false), any(PageRequest.class)))
            .thenReturn(pageOf(persisted))
            .thenReturn(pageOf(persisted));

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
        var courtId = UUID.randomUUID();
        CreateCaseDTO createCaseDTO = new CreateCaseDTO();
        createCaseDTO.setId(UUID.randomUUID());
        createCaseDTO.setReference("REFERENCE");
        createCaseDTO.setCourtId(courtId);

        var persisted = persistedCase(UUID.randomUUID(), courtId, "REFERENCE");
        when(caseService.searchBy(eq("REFERENCE"), eq(courtId), eq(false), any(PageRequest.class)))
            .thenReturn(pageOf(persisted))
            .thenReturn(pageOf(persisted));

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
        var courtId = UUID.randomUUID();
        CreateCaseDTO createCaseDTO = new CreateCaseDTO();
        createCaseDTO.setId(UUID.randomUUID());
        createCaseDTO.setReference("REFERENCE");
        createCaseDTO.setCourtId(courtId);

        var persisted = persistedCase(UUID.randomUUID(), courtId, "REFERENCE");
        when(caseService.searchBy(eq("REFERENCE"), eq(courtId), eq(false), any(PageRequest.class)))
            .thenReturn(pageOf(persisted))
            .thenReturn(pageOf(persisted));

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
        var courtId = UUID.randomUUID();
        CreateCaseDTO createCaseDTO = new CreateCaseDTO();
        createCaseDTO.setId(UUID.randomUUID());
        createCaseDTO.setReference("REFERENCE");
        createCaseDTO.setCourtId(courtId);

        var persisted = persistedCase(UUID.randomUUID(), courtId, "REFERENCE");
        when(caseService.searchBy(eq("REFERENCE"), eq(courtId), eq(false), any(PageRequest.class)))
            .thenReturn(pageOf(persisted))
            .thenReturn(pageOf(persisted));

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
        var courtId = UUID.randomUUID();
        CreateCaseDTO createCaseDTO = new CreateCaseDTO();
        createCaseDTO.setId(UUID.randomUUID());
        createCaseDTO.setReference("REFERENCE");
        createCaseDTO.setCourtId(courtId);

        var persisted = persistedCase(UUID.randomUUID(), courtId, "REFERENCE");
        when(caseService.searchBy(eq("REFERENCE"), eq(courtId), eq(false), any(PageRequest.class)))
            .thenReturn(pageOf(persisted))
            .thenReturn(pageOf(persisted));

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
        var courtId = UUID.randomUUID();
        CreateCaseDTO createCaseDTO = new CreateCaseDTO();
        createCaseDTO.setId(UUID.randomUUID());
        createCaseDTO.setReference("REFERENCE");
        createCaseDTO.setCourtId(courtId);

        var persisted = persistedCase(UUID.randomUUID(), courtId, "REFERENCE");
        when(caseService.searchBy(eq("REFERENCE"), eq(courtId), eq(false), any(PageRequest.class)))
            .thenReturn(pageOf(persisted))
            .thenReturn(pageOf(persisted));


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
        var courtId = UUID.randomUUID();
        CreateCaseDTO createCaseDTO = new CreateCaseDTO();
        createCaseDTO.setId(UUID.randomUUID());
        createCaseDTO.setReference("REFERENCE");
        createCaseDTO.setCourtId(courtId);

        var persisted = persistedCase(UUID.randomUUID(), courtId, "REFERENCE");
        when(caseService.searchBy(eq("REFERENCE"), eq(courtId), eq(false), any(PageRequest.class)))
            .thenReturn(pageOf(persisted))
            .thenReturn(pageOf(persisted));

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
        var courtId = UUID.randomUUID();
        CreateCaseDTO createCaseDTO = new CreateCaseDTO();
        createCaseDTO.setId(UUID.randomUUID());
        createCaseDTO.setReference("REFERENCE");
        createCaseDTO.setCourtId(courtId);

        var persisted = persistedCase(UUID.randomUUID(), courtId, "REFERENCE");
        when(caseService.searchBy(eq("REFERENCE"), eq(courtId), eq(false), any(PageRequest.class)))
            .thenReturn(pageOf(persisted))
            .thenReturn(pageOf(persisted));

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
