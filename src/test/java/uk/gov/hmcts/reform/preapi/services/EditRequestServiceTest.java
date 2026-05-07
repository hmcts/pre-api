package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.util.Pair;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchEditRequests;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.services.edit.EditRequestCrudService;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = EditRequestService.class)
class EditRequestServiceTest {

    @MockitoBean
    private EditRequestCrudService editRequestCrudService;

    @MockitoBean
    private RecordingRepository recordingRepository;

    @MockitoBean
    private RecordingService recordingService;

    @MockitoBean
    private EditNotificationService editNotificationService;

    @MockitoBean
    private Recording mockRecording;

    @MockitoBean
    private Recording mockParentRecording;

    @MockitoBean
    private UserAuthentication mockAuth;

    @MockitoBean
    private AppAccess mockAppAccess;

    @MockitoBean
    private CaptureSession mockCaptureSession;

    @MockitoBean
    private EditRequest mockEditRequest;

    @MockitoBean
    private EditRequestDTO editRequestDTO;

    @MockitoBean
    private CreateEditRequestDTO dto;

    @Autowired
    private EditRequestService underTest;

    private User courtClerkUser;

    private static final UUID mockRecordingId = UUID.randomUUID();
    private static final UUID mockParentRecId = UUID.randomUUID();
    private static final UUID mockCaptureSessionId = UUID.randomUUID();
    private static final UUID mockEditRequestId = UUID.randomUUID();

    @BeforeEach
    void setup() throws InterruptedException {
        User shareWith1 = HelperFactory.createUser(
            "First", "User", "example1@example.com",
            new Timestamp(System.currentTimeMillis()), null, null
        );

        User shareWith2 = HelperFactory.createUser(
            "Second", "User", "example2@example.com",
            new Timestamp(System.currentTimeMillis()), null, null
        );

        courtClerkUser = HelperFactory.createUser(
            "Court", "Clerk", "court.clerk@example.com",
            new Timestamp(System.currentTimeMillis()), null, null
        );

        Court court = HelperFactory.createCourt(CourtType.CROWN, "Test Court", "TC");

        Case testCase = HelperFactory.createCase(court, "Test Case", false, null);

        Booking booking = HelperFactory.createBooking(testCase, new Timestamp(System.currentTimeMillis()), null);

        ShareBooking shareBooking1 = HelperFactory.createShareBooking(
            shareWith1, courtClerkUser, booking,
            new Timestamp(System.currentTimeMillis())
        );

        ShareBooking shareBooking2 = HelperFactory.createShareBooking(
            shareWith2, courtClerkUser, booking,
            new Timestamp(System.currentTimeMillis())
        );

        booking.setShares(Set.of(shareBooking1, shareBooking2));

        when(mockAuth.getAppAccess()).thenReturn(mockAppAccess);
        when(mockAuth.isAppUser()).thenReturn(true);
        when(mockAppAccess.getUser()).thenReturn(courtClerkUser);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        when(mockCaptureSession.getBooking()).thenReturn(booking);
        when(mockCaptureSession.getId()).thenReturn(mockCaptureSessionId);

        when(mockRecording.getId()).thenReturn(mockRecordingId);
        when(mockRecording.getCaptureSession()).thenReturn(mockCaptureSession);
        when(mockRecording.getParentRecording()).thenReturn(mockParentRecording);
        when(mockRecording.getDuration()).thenReturn(Duration.ofMinutes(3));
        when(mockRecording.getFilename()).thenReturn("filename");

        when(mockParentRecording.getId()).thenReturn(mockParentRecId);
        when(mockParentRecording.getCaptureSession()).thenReturn(mockCaptureSession);

        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(120L)
                             .build());

        when(dto.getId()).thenReturn(UUID.randomUUID());
        when(dto.getSourceRecordingId()).thenReturn(mockRecordingId);
        when(dto.getStatus()).thenReturn(EditRequestStatus.PENDING);
        when(dto.getEditInstructions()).thenReturn(instructions);

        when(recordingRepository.findByIdAndDeletedAtIsNull(mockRecordingId)).thenReturn(Optional.of(mockRecording));

        when(mockEditRequest.getId()).thenReturn(mockEditRequestId);
        when(mockEditRequest.getSourceRecording()).thenReturn(mockRecording);
        when(mockEditRequest.getCreatedBy()).thenReturn(courtClerkUser);
        when(mockEditRequest.getEditInstruction()).thenReturn("{}");

        when(editRequestCrudService.upsert(dto, mockRecording, courtClerkUser))
            .thenReturn(Pair.of(UpsertResult.CREATED, mockEditRequest));

        when(editRequestCrudService.findAll(any(), any()))
            .thenReturn(new PageImpl<>(List.of(editRequestDTO)));

        when(editRequestCrudService.findById(mockEditRequestId))
            .thenReturn(editRequestDTO);
        when(editRequestDTO.getId()).thenReturn(mockEditRequestId);
    }

    @Test
    @DisplayName("Should create a new edit request")
    void createEditRequestSuccess() {
        when(recordingRepository.findByIdAndDeletedAtIsNull(mockRecording.getId()))
            .thenReturn(Optional.of(mockRecording));
        when(editRequestCrudService.upsert(dto, mockRecording, courtClerkUser))
            .thenReturn(Pair.of(UpsertResult.CREATED, mockEditRequest));

        UpsertResult response = underTest.upsert(dto);
        assertThat(response).isEqualTo(UpsertResult.CREATED);

        verify(recordingService, times(1)).syncRecordingMetadataWithStorage(mockRecording.getId());
        verify(recordingRepository, times(1)).findByIdAndDeletedAtIsNull(mockRecording.getId());
        verify(mockAuth, times(1)).getAppAccess();
        verify(editRequestCrudService, times(1)).upsert(dto, mockRecording, courtClerkUser);
    }

    @Test
    @DisplayName("Should throw not found when source recording does not exist")
    void createEditRequestSourceRecordingNotFound() {
        when(dto.getSourceRecordingId()).thenReturn(UUID.randomUUID());

        when(recordingRepository.findByIdAndDeletedAtIsNull(dto.getSourceRecordingId()))
            .thenReturn(Optional.empty());

        String message = assertThrows(
            NotFoundException.class,
            () -> underTest.upsert(dto)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Source Recording: " + dto.getSourceRecordingId());

        verify(recordingService, times(1)).syncRecordingMetadataWithStorage(dto.getSourceRecordingId());
        verify(recordingRepository, times(1)).findByIdAndDeletedAtIsNull(dto.getSourceRecordingId());
        verify(mockAuth, never()).getAppAccess();
        verify(editRequestCrudService, never()).upsert(any(),  any(), any());
    }

    @Test
    @DisplayName("Should throw error when source recording does not have a duration")
    void createEditRequestDurationIsNullError() {
        when(mockRecording.getDuration()).thenReturn(null);

        String message = assertThrows(
            ResourceInWrongStateException.class,
            () -> underTest.upsert(dto)
        ).getMessage();
        assertThat(message)
            .isEqualTo("Source Recording (" + mockRecordingId + ") does not have a valid duration");

        verify(recordingService, times(1)).syncRecordingMetadataWithStorage(mockRecordingId);
        verify(recordingRepository, times(1)).findByIdAndDeletedAtIsNull(mockRecordingId);
        verify(mockAuth, never()).getAppAccess();
        verify(editRequestCrudService, never()).upsert(any(), any(), any());
    }

    @Test
    @DisplayName("Search edit requests as admin user should not set additional filters")
    void findAllAsAdminUseSetsNullFilters() {
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(false);
        when(mockAuth.isPortalUser()).thenReturn(false);

        SearchEditRequests params = new SearchEditRequests();
        Page<EditRequestDTO> result = underTest.findAll(params, Pageable.unpaged());

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(editRequestCrudService).findAll(
            argThat(search ->
                        search.getAuthorisedBookings() == null
                            && search.getAuthorisedCourt() == null),
            any(Pageable.class)
        );
    }

    @Test
    @DisplayName("Search edit requests as app user should set additional filters")
    void findAllAsAppUserSetsCourtFilterOnly() {
        when(mockAuth.isAdmin()).thenReturn(false);
        when(mockAuth.isAppUser()).thenReturn(true);
        when(mockAuth.isPortalUser()).thenReturn(false);
        when(mockAuth.getCourtId()).thenReturn(UUID.randomUUID());

        SearchEditRequests params = new SearchEditRequests();

        underTest.findAll(params, Pageable.unpaged());

        verify(editRequestCrudService).findAll(
            argThat(p ->
                        p.getAuthorisedBookings() == null
                            && p.getAuthorisedCourt().equals(mockAuth.getCourtId())),
            any(Pageable.class)
        );
    }

    @Test
    @DisplayName("Search edit requests as portal user should set additional filters")
    void findAllAsPortalUserSetsAuthedBookingFilterOnly() {
        when(mockAuth.isAdmin()).thenReturn(false);
        when(mockAuth.isAppUser()).thenReturn(false);
        when(mockAuth.isPortalUser()).thenReturn(true);
        when(mockAuth.getSharedBookings()).thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));

        SearchEditRequests params = new SearchEditRequests();
        underTest.findAll(params, Pageable.unpaged());

        verify(editRequestCrudService).findAll(
            argThat(p ->
                        p.getAuthorisedBookings().containsAll(mockAuth.getSharedBookings())
                            && p.getAuthorisedCourt() == null),
            any(Pageable.class)
        );
    }

    @Test
    @DisplayName("Should be able to upsert edit instructions with CSV file")
    void upsertEditInstructionsWithCSVFile() {
        final String fileContents = """
            Edit Number,Start time of cut,End time of cut,Total time removed,Reason
            1,00:00:00,00:00:30,00:30:00,first thirty seconds reason
            2,00:01:01,00:02:00,00:00:59,
            """;

        final List<EditCutInstructionDTO> expectedEditInstructions = List.of(
            new EditCutInstructionDTO(0, 30, "first thirty seconds reason"),
            new EditCutInstructionDTO(61, 120, "")
        );

        final MockMultipartFile file = new MockMultipartFile(
            "file", "edit_instructions.csv",
            PreApiController.CSV_FILE_TYPE, fileContents.getBytes()
        );

        EditRequest returnedByDb = new EditRequest();
        returnedByDb.setCreatedBy(courtClerkUser);

        when(editRequestCrudService.upsert(any(), any(), any()))
            .thenReturn(Pair.of(UpsertResult.CREATED, mockEditRequest));
        when(editRequestCrudService.findByIdIfExists(any())).thenReturn(Optional.of(returnedByDb));
        when(editRequestCrudService.findById(any(UUID.class))).thenReturn(editRequestDTO);

        EditRequestDTO upsert = underTest.upsert(mockRecordingId, file);
        assertThat(upsert).isEqualTo(editRequestDTO);

        ArgumentCaptor<CreateEditRequestDTO> savedEditRequest = ArgumentCaptor.forClass(CreateEditRequestDTO.class);
        verify(editRequestCrudService, times(1)).upsert(savedEditRequest.capture(), any(), any());

        assertThat(savedEditRequest.getValue().getId()).isNotNull();
        assertThat(savedEditRequest.getValue().getSourceRecordingId()).isEqualTo(mockRecordingId);
        assertThat(savedEditRequest.getValue().getStatus()).isEqualTo(EditRequestStatus.PENDING);

        List<EditCutInstructionDTO> editInstructions = savedEditRequest.getValue().getEditInstructions();
        assertThat(editInstructions).hasSize(2);
        assertThat(editInstructions).isEqualTo(expectedEditInstructions);
    }

    @DisplayName("Should throw an exception if updating edit instructions with non-CSV")
    @Test
    void upsertEditInstructionsWithNotCSVFile() {
        final String fileContents = """
            Region,Court,PRE Inbox Address
            South East,Example Court,PRE.Edits.Example@justice.gov.uk
            """;

        MockMultipartFile file = new MockMultipartFile(
            "file", "edits.csv",
            "text/xml", fileContents.getBytes()
        );

        assertThrows(
            BadRequestException.class,
            () -> underTest.upsert(mockRecordingId, file)
        );
    }

    @DisplayName("Should throw an exception if updating edit instructions with empty file")
    @Test
    void upsertEditInstructionsWithEmptyFile() {
        final String fileContents = "";

        MockMultipartFile file = new MockMultipartFile(
            "file", "edits.csv",
            PreApiController.CSV_FILE_TYPE, fileContents.getBytes()
        );

        assertThrows(
            BadRequestException.class,
            () -> underTest.upsert(mockRecordingId, file)
        );
    }

    @Test
    @DisplayName("Should trigger request submission jointly agreed email on submission")
    void upsertOnSubmittedJointlyAgreed() {
        when(dto.getStatus()).thenReturn(EditRequestStatus.SUBMITTED);
        when(dto.getJointlyAgreed()).thenReturn(true);

        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.DRAFT);

        when(editRequestCrudService.upsert(dto, mockRecording, courtClerkUser))
            .thenReturn(Pair.of(UpsertResult.UPDATED, mockEditRequest));

        UpsertResult response = underTest.upsert(dto);
        assertThat(response).isEqualTo(UpsertResult.UPDATED);

        verify(recordingRepository, times(1)).findByIdAndDeletedAtIsNull(mockRecordingId);
        verify(mockAuth, times(1)).getAppAccess();
        verify(editRequestCrudService, times(1)).upsert(dto, mockRecording, courtClerkUser);
        verify(editNotificationService, times(1)).onEditRequestSubmitted(mockEditRequest);
    }

    @Test
    @DisplayName("Should trigger request submission not jointly agreed email on submission")
    void upsertOnSubmittedNotJointlyAgreed() {
        when(dto.getStatus()).thenReturn(EditRequestStatus.SUBMITTED);
        when(dto.getJointlyAgreed()).thenReturn(false);
        when(editRequestCrudService.upsert(dto, mockRecording, courtClerkUser))
            .thenReturn(Pair.of(UpsertResult.UPDATED, mockEditRequest));

        UpsertResult response = underTest.upsert(dto);
        assertThat(response).isEqualTo(UpsertResult.UPDATED);

        verify(recordingRepository, times(1)).findByIdAndDeletedAtIsNull(mockRecordingId);
        verify(mockAuth, times(1)).getAppAccess();
        verify(editRequestCrudService, times(1)).upsert(dto, mockRecording, courtClerkUser);
        verify(editNotificationService, times(1)).onEditRequestSubmitted(mockEditRequest);
    }

    @Test
    @DisplayName("Should trigger request rejection email on edit request rejection")
    void upsertOnRejected() {
        when(editRequestCrudService.upsert(dto, mockRecording, courtClerkUser))
            .thenReturn(Pair.of(UpsertResult.UPDATED, mockEditRequest));

        when(dto.getStatus()).thenReturn(EditRequestStatus.REJECTED);
        when(dto.getJointlyAgreed()).thenReturn(false);

        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.SUBMITTED);

        UpsertResult response = underTest.upsert(dto);
        assertThat(response).isEqualTo(UpsertResult.UPDATED);

        verify(recordingRepository, times(1)).findByIdAndDeletedAtIsNull(mockRecordingId);
        verify(mockAuth, times(1)).getAppAccess();
        verify(editRequestCrudService, times(1)).upsert(dto, mockRecording, courtClerkUser);
        verify(editNotificationService, times(1)).onEditRequestRejected(mockEditRequest);
    }

    @DisplayName("Should throw an exception if edit instructions have unsafe data")
    @Test
    void upsertEditInstructionsWithUnsafeCSVFile() {
        final String fileContents = """
            Edit Number,Start time of cut,End time of cut,Total time removed,Reason
            1,00:00:00,00:00:30,00:30:00,<script></script>
            2,00:01:01,00:02:00,00:00:59,
            """;

        final MockMultipartFile file = new MockMultipartFile(
            "file", "edit_instructions.csv",
            PreApiController.CSV_FILE_TYPE, fileContents.getBytes()
        );

        assertThrows(
            BadRequestException.class,
            () -> underTest.upsert(mockRecordingId, file)
        );
    }


}
