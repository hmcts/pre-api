package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
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
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
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
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.services.edit.AssetGenerationService;
import uk.gov.hmcts.reform.preapi.services.edit.EditRequestCrudService;
import uk.gov.hmcts.reform.preapi.services.edit.IEditingService;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = EditRequestService.class)
public class EditRequestServiceTest {

    @MockitoBean
    private EditRequestCrudService editRequestCrudService;

    @MockitoBean
    private RecordingRepository recordingRepository;

    @MockitoBean
    private IEditingService editingService;

    @MockitoBean
    private RecordingService recordingService;

    @MockitoBean
    private AssetGenerationService assetGenerationService;

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

    @MockitoBean
    private CaptureSession captureSession;

    @Autowired
    private EditRequestService underTest;

    private User courtClerkUser;
    private Booking booking;

    private static final UUID mockRecordingId = UUID.randomUUID();
    private static final UUID mockParentRecId = UUID.randomUUID();
    private static final UUID mockCaptureSessionId = UUID.randomUUID();
    private static final UUID mockEditRequestId = UUID.randomUUID();

    @BeforeEach
    void setup() {
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

        booking = HelperFactory.createBooking(testCase, new Timestamp(System.currentTimeMillis()), null);

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

        try {
            when(assetGenerationService.generateAsset(any(UUID.class), any(EditRequest.class)))
                .thenReturn("filename");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

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

        when(editingService.prepareEditRequestToCreateOrUpdate(
            any(CreateEditRequestDTO.class), any(Recording.class),
            any(EditRequest.class)
        )).thenReturn(mockEditRequest);

        when(editingService.prepareEditRequestToCreateOrUpdate(
            dto, mockRecording, mockEditRequest
        )).thenReturn(mockEditRequest);

        when(editRequestCrudService.upsert(dto, mockRecording, courtClerkUser))
            .thenReturn(Pair.of(UpsertResult.CREATED, mockEditRequest));

        when(editRequestCrudService.findAll(any(), any()))
            .thenReturn(new PageImpl<>(List.of(editRequestDTO)));

        when(editRequestCrudService.findById(mockEditRequestId))
            .thenReturn(editRequestDTO);
        when(editRequestDTO.getId()).thenReturn(mockEditRequestId);
    }

    @Test
    @DisplayName("Should attempt to perform edit request and return error on ffmpeg service error")
    void performEditFfmpegError() {
        doThrow(UnknownServerException.class)
            .when(editingService).performEdit(any(UUID.class), eq(mockEditRequest));

        assertThrows(
            Exception.class,
            () -> underTest.performEdit(mockEditRequest)
        );

        ArgumentCaptor<UUID> uuidCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<EditRequestStatus> statusCaptor = ArgumentCaptor.forClass(EditRequestStatus.class);
        verify(editRequestCrudService, times(1)).updateEditRequestStatus(uuidCaptor.capture(),
                                                                         statusCaptor.capture());
        assertThat(uuidCaptor.getValue()).isEqualTo(mockEditRequestId);
        assertThat(statusCaptor.getValue()).isEqualTo(EditRequestStatus.ERROR);

        ArgumentCaptor<EditRequest> performEditCaptor = ArgumentCaptor.forClass(EditRequest.class);
        verify(editingService, times(1)).performEdit(any(UUID.class), performEditCaptor.capture());
        EditRequest performedEditRequest = performEditCaptor.getValue();
        assertThat(performedEditRequest.getId()).isEqualTo(mockEditRequestId);

        verify(recordingService, never()).upsert(any());
    }

    // TODO: Move to performing service when ready
    // This should probably be an integration test
    @Test
    @DisplayName("Should perform edit request and return created recording")
    void performEditSuccess() throws InterruptedException {
        when(assetGenerationService.generateAsset(any(UUID.class), any(EditRequest.class))).thenReturn("filename");
        when(recordingService.getNextVersionNumber(mockRecording.getId())).thenReturn(2);

        when(mockEditRequest.getEditInstruction()).thenReturn("{ \"testJson\" :\"edit instructions\"}");

        when(mockEditRequest.getEditInstruction()).thenReturn(
                "{\"requestedInstructions\":null,"
                    + "\"ffmpegInstructions\":null,\"forceReencode\":false,"
                    + "\"sendNotifications\":true}");


        RecordingDTO newRecordingDto = new RecordingDTO();
        newRecordingDto.setParentRecordingId(mockRecording.getId());
        when(recordingService.findById(any(UUID.class))).thenReturn(newRecordingDto);

        RecordingDTO res = underTest.performEdit(mockEditRequest);
        assertThat(res).isNotNull();
        assertThat(res.getParentRecordingId()).isEqualTo(mockRecording.getId());

        ArgumentCaptor<UUID> newRecIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<EditRequest> saveCaptor = ArgumentCaptor.forClass(EditRequest.class);
        verify(editingService, times(1))
            .performEdit(newRecIdCaptor.capture(), saveCaptor.capture());
        EditRequest updatedEditRequest = saveCaptor.getValue();
        assertThat(updatedEditRequest).isEqualTo(mockEditRequest);

        verify(editRequestCrudService, times(1))
            .updateEditRequestStatus(mockEditRequestId, EditRequestStatus.COMPLETE);

        ArgumentCaptor<CreateRecordingDTO> newRecCaptor = ArgumentCaptor.forClass(CreateRecordingDTO.class);
        verify(recordingService, times(1)).upsert(newRecCaptor.capture());

        assertThat(newRecCaptor.getValue().getId()).isEqualTo(newRecIdCaptor.getValue());
        assertThat(newRecCaptor.getValue().getFilename()).isEqualTo("filename");

        String expectedInstructions = format("""
            {"editRequestId":"%s","editInstructions":{"requestedInstructions":null,
            "ffmpegInstructions":null,"forceReencode":false,"sendNotifications":true}}
            """, mockEditRequestId);
        JSONAssert.assertEquals(newRecCaptor.getValue().getEditInstructions(),
                                expectedInstructions, JSONCompareMode.LENIENT);

        verify(recordingService, times(1)).findById(any(UUID.class));

        // Notification is sent by RecordingListener instead
        verify(editNotificationService, times(0)).sendNotifications(booking);
    }

    @Test
    @DisplayName("Should throw not found error when edit request cannot be found with specified id")
    void performEditNotFound() {
        UUID id = UUID.randomUUID();
        doThrow(NotFoundException.class).when(editRequestCrudService).findById(any(UUID.class));

        assertThrows(NotFoundException.class, () -> underTest.markAsProcessing(id));

        verify(editRequestCrudService, times(1)).findById(any(UUID.class));
        verifyNoMoreInteractions(editRequestCrudService);
        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editingService);
    }

    @Test
    @DisplayName("Should not perform edit and return null when status of edit request is not PENDING")
    void performEditStatusNotPending() {
        when(editRequestDTO.getStatus()).thenReturn(EditRequestStatus.PROCESSING);

        String message = assertThrows(
            ResourceInWrongStateException.class,
            () -> underTest.markAsProcessing(mockEditRequestId)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Resource EditRequest("
                           + mockEditRequestId
                           + ") is in a PROCESSING state. Expected state is PENDING.");

        verify(editRequestCrudService, times(1)).findById(mockEditRequestId);
        verify(editRequestCrudService, never()).upsert(any(), any(), any());
        verify(editingService, never()).performEdit(any(UUID.class), any(EditRequest.class));
        verify(recordingService, never()).upsert(any(CreateRecordingDTO.class));
        verify(recordingService, never()).findById(any(UUID.class));
    }

    @Test
    @DisplayName("Should throw lock error when encounters locked edit request")
    void performEditLocked() {
        doThrow(PessimisticLockingFailureException.class)
            .when(editRequestCrudService).findById(mockEditRequestId);

        assertThrows(
            PessimisticLockingFailureException.class,
            () -> underTest.markAsProcessing(mockEditRequestId)
        );

        verify(editRequestCrudService, times(1)).findById(mockEditRequestId);
        verify(editRequestCrudService, never()).upsert(any(), any(), any());
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
    @DisplayName("Should return new create recording dto for the edit request")
    void createRecordingSuccess() {
        when(mockEditRequest.getEditInstruction()).thenReturn("{}");
        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.COMPLETE);

        when(mockRecording.getFilename()).thenReturn("index.mp4");
        when(recordingService.getNextVersionNumber(mockParentRecId)).thenReturn(2);

        CreateRecordingDTO dto = underTest.createRecordingDto(mockRecordingId, "index.mp4", mockEditRequest);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(mockRecordingId);
        assertThat(dto.getParentRecordingId()).isEqualTo(mockParentRecId);
        assertThat(dto.getVersion()).isEqualTo(2);
        assertThat(dto.getEditInstructions())
            .isEqualTo(format(
                "{\"editRequestId\":\"%s\",\"editInstructions\":{\"requestedInstructions\":null,"
                    + "\"ffmpegInstructions\":null,\"forceReencode\":false,"
                    + "\"sendNotifications\":true}}", mockEditRequestId
            ));

        assertThat(dto.getCaptureSessionId()).isEqualTo(mockCaptureSessionId);
        assertThat(dto.getFilename()).isEqualTo("index.mp4");

        verify(recordingService, times(1)).getNextVersionNumber(mockParentRecId);
    }

    @Test
    @DisplayName("Should return create recording dto with parent recording")
    void createRecordingDtoWithParentRecording() {
        when(mockRecording.getFilename()).thenReturn("source.mp4");

        when(mockEditRequest.getEditInstruction()).thenReturn("""
                                                                  {
                                                                      "requestedInstructions": [ ],
                                                                      "ffmpegInstructions": [ ]
                                                                  }
                                                                  """);

        UUID newRecordingId = UUID.randomUUID();
        when(recordingService.getNextVersionNumber(mockParentRecId)).thenReturn(3);

        CreateRecordingDTO dto = underTest.createRecordingDto(newRecordingId, "newFile.mp4", mockEditRequest);
        assertThat(dto.getId()).isEqualTo(newRecordingId);
        assertThat(dto.getParentRecordingId()).isEqualTo(mockParentRecId);
        assertThat(dto.getFilename()).isEqualTo("newFile.mp4");
        assertThat(dto.getVersion()).isEqualTo(3);
        assertThat(dto.getEditInstructions())
            .isEqualTo(format(
                "{\"editRequestId\":\"%s\",\"editInstructions\":{\"requestedInstructions\":[],"
                    + "\"ffmpegInstructions\":[],\"forceReencode\":false,"
                    + "\"sendNotifications\":true}}", mockEditRequestId
            ));
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
        assertEquals(0, result.getContent().size());
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
        when(editRequestCrudService.findById(any())).thenReturn(editRequestDTO);

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

        when(editingService.prepareEditRequestToCreateOrUpdate(dto, mockRecording, mockEditRequest))
            .thenReturn(mockEditRequest);

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

        when(editingService.prepareEditRequestToCreateOrUpdate(dto, mockRecording, mockEditRequest))
            .thenReturn(mockEditRequest);

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
