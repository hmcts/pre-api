package uk.gov.hmcts.reform.preapi.controller;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.PropertyNamingStrategy;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationRecordingVersion;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.controllers.VfMigrationController;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchMigrationRecords;
import uk.gov.hmcts.reform.preapi.dto.migration.CreateVfMigrationRecordDTO;
import uk.gov.hmcts.reform.preapi.dto.migration.VfMigrationRecordDTO;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.ScheduledTaskRunner;
import uk.gov.hmcts.reform.preapi.tasks.migration.MigrateResolved;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VfMigrationController.class)
@TestPropertySource(properties = {
    "feature-flags.enable-migration-admin-endpoints=true"
})
@AutoConfigureMockMvc(addFilters = false)
public class VfMigrationControllerTest {
    @Autowired
    private transient MockMvc mockMvc;

    @MockitoBean
    private MigrationRecordService migrationRecordService;

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    @MockitoBean
    private MigrateResolved migrateResolved;

    @MockitoBean
    private ScheduledTaskRunner taskRunner;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @BeforeAll
    static void beforeAll() {
        OBJECT_MAPPER.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SS'Z'"));
        OBJECT_MAPPER.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
    }

    @Test
    @DisplayName("Should return empty page when no records found")
    public void shouldReturnEmptyPageWhenNoRecordsFound() throws Exception {
        when(migrationRecordService.findAllBy(any(SearchMigrationRecords.class), any(Pageable.class)))
            .thenReturn(Page.empty());

        mockMvc.perform(get("/vf-migration-records?page=0&size=10")
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    @DisplayName("Should return page with migration records")
    public void shouldReturnPageWithMigrationRecords() throws Exception {
        UUID mockId = UUID.randomUUID();
        VfMigrationRecordDTO record = VfMigrationRecordDTO.builder()
            .id(mockId)
            .archiveName("archive-name")
            .status(VfMigrationStatus.SUCCESS)
            .createdAt(Timestamp.from(Instant.now()))
            .build();

        Page<VfMigrationRecordDTO> recordPage = new PageImpl<>(List.of(record), Pageable.unpaged(), 1);

        when(migrationRecordService.findAllBy(any(SearchMigrationRecords.class), any(Pageable.class)))
            .thenReturn(recordPage);

        mockMvc.perform(get("/vf-migration-records?page=0&size=10")
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.page.totalPages").value(1))
            .andExpect(jsonPath("$._embedded.vfMigrationRecordDTOList[0].id").value(mockId.toString()))
            .andExpect(jsonPath("$._embedded.vfMigrationRecordDTOList[0].archive_name").value("archive-name"))
            .andExpect(jsonPath("$._embedded.vfMigrationRecordDTOList[0].status").value("SUCCESS"));
    }

    @Test
    @DisplayName("Should support filtering records by create date range")
    public void shouldSupportFilteringRecordsByCreateDateRange() throws Exception {
        UUID mockId = UUID.randomUUID();
        VfMigrationRecordDTO record = VfMigrationRecordDTO.builder()
            .id(mockId)
            .archiveName("archive-name")
            .status(VfMigrationStatus.SUCCESS)
            .createdAt(Timestamp.from(Instant.now()))
            .build();

        Page<VfMigrationRecordDTO> recordPage = new PageImpl<>(List.of(record), Pageable.unpaged(), 1);

        when(migrationRecordService.findAllBy(any(SearchMigrationRecords.class), any(Pageable.class)))
            .thenReturn(recordPage);

        mockMvc.perform(get("/vf-migration-records")
                            .param("createDateFrom", "2025-01-01")
                            .param("createDateTo", "2025-12-31")
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$._embedded.vfMigrationRecordDTOList[0].id").value(mockId.toString()))
            .andExpect(jsonPath("$._embedded.vfMigrationRecordDTOList[0].archive_name").value("archive-name"))
            .andExpect(jsonPath("$._embedded.vfMigrationRecordDTOList[0].status").value("SUCCESS"));
    }

    @Test
    @DisplayName("Should handle invalid date filter gracefully")
    public void shouldHandleInvalidDateFilterGracefully() throws Exception {
        mockMvc.perform(get("/vf-migration-records")
                            .param("createDateFrom", "invalid-date")
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should update VF migration record when valid request")
    public void shouldUpdateVfMigrationRecordWhenValidRequest() throws Exception {
        UUID mockId = UUID.randomUUID();

        CreateVfMigrationRecordDTO createDto = new CreateVfMigrationRecordDTO();
        createDto.setId(mockId);
        createDto.setStatus(VfMigrationStatus.SUCCESS);
        createDto.setUrn("URN1234567");
        createDto.setExhibitReference("EXHIBIT");
        createDto.setDefendantName("defendant-name");
        createDto.setWitnessName("witness-name");
        createDto.setCourtId(UUID.randomUUID());
        createDto.setRecordingVersion(VfMigrationRecordingVersion.ORIG);

        when(migrationRecordService.update(any(CreateVfMigrationRecordDTO.class))).thenReturn(UpsertResult.UPDATED);

        mockMvc.perform(put("/vf-migration-records/" + mockId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(createDto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNoContent());

        verify(migrationRecordService, times(1)).update(any(CreateVfMigrationRecordDTO.class));
    }

    @Test
    @DisplayName("Should handle path payload mismatch gracefully")
    public void shouldHandlePathPayloadMismatchGracefully() throws Exception {
        UUID payloadId = UUID.randomUUID();

        CreateVfMigrationRecordDTO createDto = new CreateVfMigrationRecordDTO();
        createDto.setId(payloadId);
        createDto.setCourtId(UUID.randomUUID());
        createDto.setUrn("URN1234567");
        createDto.setExhibitReference("EXHIBIT987");
        createDto.setDefendantName("defendant name");
        createDto.setWitnessName("witness name");
        createDto.setRecordingVersion(VfMigrationRecordingVersion.ORIG);
        createDto.setStatus(VfMigrationStatus.PENDING);

        UUID pathId = UUID.randomUUID();
        mockMvc.perform(put("/vf-migration-records/" + pathId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(createDto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when passed null migration record id")
    public void putMigrationRecordIdNull() throws Exception {
        CreateVfMigrationRecordDTO createDto = new CreateVfMigrationRecordDTO();
        createDto.setStatus(VfMigrationStatus.SUCCESS);
        createDto.setUrn("URN1234567");
        createDto.setExhibitReference("EXHIBIT");
        createDto.setDefendantName("defendant-name");
        createDto.setWitnessName("witness-name");
        createDto.setCourtId(UUID.randomUUID());
        createDto.setRecordingVersion(VfMigrationRecordingVersion.ORIG);

        UUID mockId = UUID.randomUUID();
        mockMvc.perform(put("/vf-migration-records/" + mockId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(createDto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.id").value("must not be null"));
    }

    @Test
    @DisplayName("Should return 400 when passed null court id")
    public void putMigrationRecordCourtIdNull() throws Exception {
        UUID mockId = UUID.randomUUID();
        CreateVfMigrationRecordDTO createDto = new CreateVfMigrationRecordDTO();
        createDto.setId(mockId);
        createDto.setStatus(VfMigrationStatus.SUCCESS);
        createDto.setUrn("URN1234567");
        createDto.setExhibitReference("EXHIBIT");
        createDto.setDefendantName("defendant-name");
        createDto.setWitnessName("witness-name");
        createDto.setRecordingVersion(VfMigrationRecordingVersion.ORIG);

        mockMvc.perform(put("/vf-migration-records/" + mockId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(createDto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.courtId").value("must not be null"));
    }

    @Test
    @DisplayName("Should return 400 when passed null urn")
    public void putMigrationRecordUrnNull() throws Exception {
        UUID mockId = UUID.randomUUID();
        CreateVfMigrationRecordDTO createDto = new CreateVfMigrationRecordDTO();
        createDto.setId(mockId);
        createDto.setStatus(VfMigrationStatus.SUCCESS);
        createDto.setExhibitReference("EXHIBIT");
        createDto.setDefendantName("defendant-name");
        createDto.setWitnessName("witness-name");
        createDto.setCourtId(UUID.randomUUID());
        createDto.setRecordingVersion(VfMigrationRecordingVersion.ORIG);

        mockMvc.perform(put("/vf-migration-records/" + mockId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(createDto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.urn").value("must be alphanumeric"));
    }

    @Test
    @DisplayName("Should return 400 when passed non-alphanumeric urn")
    public void putMigrationRecordUrnNotAlphanumeric() throws Exception {
        UUID mockId = UUID.randomUUID();
        CreateVfMigrationRecordDTO createDto = new CreateVfMigrationRecordDTO();
        createDto.setId(mockId);
        createDto.setStatus(VfMigrationStatus.SUCCESS);
        createDto.setUrn("URN-1234567");
        createDto.setExhibitReference("EXHIBIT");
        createDto.setDefendantName("defendant-name");
        createDto.setWitnessName("witness-name");
        createDto.setCourtId(UUID.randomUUID());
        createDto.setRecordingVersion(VfMigrationRecordingVersion.ORIG);

        mockMvc.perform(put("/vf-migration-records/" + mockId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(createDto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.urn").value("must be alphanumeric"));
    }

    @Test
    @DisplayName("Should return 400 when passed urn that is too short")
    public void putMigrationRecordUrnTooShort() throws Exception {
        UUID mockId = UUID.randomUUID();
        CreateVfMigrationRecordDTO createDto = new CreateVfMigrationRecordDTO();
        createDto.setId(mockId);
        createDto.setStatus(VfMigrationStatus.SUCCESS);
        createDto.setUrn("URN");
        createDto.setExhibitReference("EXHIBIT");
        createDto.setDefendantName("defendant-name");
        createDto.setWitnessName("witness-name");
        createDto.setCourtId(UUID.randomUUID());
        createDto.setRecordingVersion(VfMigrationRecordingVersion.ORIG);

        mockMvc.perform(put("/vf-migration-records/" + mockId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(createDto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.urn").value("length must be between 9 and 13"));
    }

    @Test
    @DisplayName("Should return 400 when passed urn that is too long")
    public void putMigrationRecordUrnTooLong() throws Exception {
        UUID mockId = UUID.randomUUID();
        CreateVfMigrationRecordDTO createDto = new CreateVfMigrationRecordDTO();
        createDto.setId(mockId);
        createDto.setStatus(VfMigrationStatus.SUCCESS);
        createDto.setUrn("URN12345678901234567890");
        createDto.setExhibitReference("EXHIBIT");
        createDto.setDefendantName("defendant-name");
        createDto.setWitnessName("witness-name");
        createDto.setCourtId(UUID.randomUUID());
        createDto.setRecordingVersion(VfMigrationRecordingVersion.ORIG);

        mockMvc.perform(put("/vf-migration-records/" + mockId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(createDto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.urn").value("length must be between 9 and 13"));
    }

    @Test
    @DisplayName("Should return 400 when passed null exhibit reference")
    public void putMigrationRecordExhibitReferenceNull() throws Exception {
        UUID mockId = UUID.randomUUID();
        CreateVfMigrationRecordDTO createDto = new CreateVfMigrationRecordDTO();
        createDto.setId(mockId);
        createDto.setStatus(VfMigrationStatus.SUCCESS);
        createDto.setUrn("URN1234567");
        createDto.setDefendantName("defendant-name");
        createDto.setWitnessName("witness-name");
        createDto.setCourtId(UUID.randomUUID());
        createDto.setRecordingVersion(VfMigrationRecordingVersion.ORIG);

        mockMvc.perform(put("/vf-migration-records/" + mockId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(createDto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.exhibitReference").value("must be alphanumeric"));
    }

    @Test
    @DisplayName("Should return 400 when passed non-alphanumeric exhibit reference")
    public void putMigrationRecordExhibitReferenceNotAlphanumeric() throws Exception {
        UUID mockId = UUID.randomUUID();
        CreateVfMigrationRecordDTO createDto = new CreateVfMigrationRecordDTO();
        createDto.setId(mockId);
        createDto.setStatus(VfMigrationStatus.SUCCESS);
        createDto.setUrn("URN1234567");
        createDto.setExhibitReference("EXHIBIT--");
        createDto.setDefendantName("defendant-name");
        createDto.setWitnessName("witness-name");
        createDto.setCourtId(UUID.randomUUID());
        createDto.setRecordingVersion(VfMigrationRecordingVersion.ORIG);

        mockMvc.perform(put("/vf-migration-records/" + mockId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(createDto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.exhibitReference").value("must be alphanumeric"));
    }

    @Test
    @DisplayName("Should return 400 when passed exhibit reference that is too short")
    public void putMigrationRecordExhibitReferenceTooShort() throws Exception {
        UUID mockId = UUID.randomUUID();
        CreateVfMigrationRecordDTO createDto = new CreateVfMigrationRecordDTO();
        createDto.setId(mockId);
        createDto.setStatus(VfMigrationStatus.SUCCESS);
        createDto.setUrn("URN1234567");
        createDto.setExhibitReference("EX");
        createDto.setDefendantName("defendant-name");
        createDto.setWitnessName("witness-name");
        createDto.setCourtId(UUID.randomUUID());
        createDto.setRecordingVersion(VfMigrationRecordingVersion.ORIG);

        mockMvc.perform(put("/vf-migration-records/" + mockId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(createDto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.exhibitReference").value("length must be between 7 and 11"));
    }

    @Test
    @DisplayName("Should return 400 when passed exhibit reference that is too long")
    public void putMigrationRecordExhibitReferenceTooLong() throws Exception {
        UUID mockId = UUID.randomUUID();
        CreateVfMigrationRecordDTO createDto = new CreateVfMigrationRecordDTO();
        createDto.setId(mockId);
        createDto.setStatus(VfMigrationStatus.SUCCESS);
        createDto.setUrn("URN1234567");
        createDto.setExhibitReference("EXHIBIT12345678901234567890");
        createDto.setDefendantName("defendant-name");
        createDto.setWitnessName("witness-name");
        createDto.setCourtId(UUID.randomUUID());
        createDto.setRecordingVersion(VfMigrationRecordingVersion.ORIG);

        mockMvc.perform(put("/vf-migration-records/" + mockId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(createDto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.exhibitReference").value("length must be between 7 and 11"));
    }

    @Test
    @DisplayName("Should return 400 when passed null defendant name")
    public void putMigrationRecordDefendantNameNull() throws Exception {
        UUID mockId = UUID.randomUUID();
        CreateVfMigrationRecordDTO createDto = new CreateVfMigrationRecordDTO();
        createDto.setId(mockId);
        createDto.setStatus(VfMigrationStatus.SUCCESS);
        createDto.setUrn("URN1234567");
        createDto.setExhibitReference("EXHIBIT123");
        createDto.setWitnessName("witness-name");
        createDto.setCourtId(UUID.randomUUID());
        createDto.setRecordingVersion(VfMigrationRecordingVersion.ORIG);

        mockMvc.perform(put("/vf-migration-records/" + mockId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(createDto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.defendantName").value("must not be empty"));
    }

    @Test
    @DisplayName("Should return 400 when passed null witness name")
    public void putMigrationRecordWitnessNameNull() throws Exception {
        UUID mockId = UUID.randomUUID();
        CreateVfMigrationRecordDTO createDto = new CreateVfMigrationRecordDTO();
        createDto.setId(mockId);
        createDto.setStatus(VfMigrationStatus.SUCCESS);
        createDto.setUrn("URN1234567");
        createDto.setExhibitReference("EXHIBIT123");
        createDto.setDefendantName("defendant-name");
        createDto.setCourtId(UUID.randomUUID());
        createDto.setRecordingVersion(VfMigrationRecordingVersion.ORIG);

        mockMvc.perform(put("/vf-migration-records/" + mockId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(createDto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.witnessName").value("must not be empty"));
    }

    @Test
    @DisplayName("Should return 400 when passed null recording version")
    public void putMigrationRecordRecordingVersionNull() throws Exception {
        UUID mockId = UUID.randomUUID();
        CreateVfMigrationRecordDTO createDto = new CreateVfMigrationRecordDTO();
        createDto.setId(mockId);
        createDto.setStatus(VfMigrationStatus.SUCCESS);
        createDto.setUrn("URN1234567");
        createDto.setExhibitReference("EXHIBIT123");
        createDto.setDefendantName("defendant-name");
        createDto.setWitnessName("witness-name");
        createDto.setCourtId(UUID.randomUUID());

        mockMvc.perform(put("/vf-migration-records/" + mockId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(createDto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.recordingVersion").value("must not be null"));
    }

    @Test
    @DisplayName("Should return 400 when passed null status")
    public void putMigrationRecordStatusNull() throws Exception {
        UUID mockId = UUID.randomUUID();
        CreateVfMigrationRecordDTO createDto = new CreateVfMigrationRecordDTO();
        createDto.setId(mockId);
        createDto.setUrn("URN1234567");
        createDto.setExhibitReference("EXHIBIT123");
        createDto.setDefendantName("defendant-name");
        createDto.setWitnessName("witness-name");
        createDto.setCourtId(UUID.randomUUID());
        createDto.setRecordingVersion(VfMigrationRecordingVersion.ORIG);

        mockMvc.perform(put("/vf-migration-records/" + mockId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(createDto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("must not be null"));
    }

    @Test
    @DisplayName("Should return 400 when recording version number is less than 1")
    public void putMigrationRecordVersionNumberZero() throws Exception {
        UUID mockId = UUID.randomUUID();
        CreateVfMigrationRecordDTO createDto = new CreateVfMigrationRecordDTO();
        createDto.setId(mockId);
        createDto.setUrn("URN1234567");
        createDto.setExhibitReference("EXHIBIT123");
        createDto.setDefendantName("defendant-name");
        createDto.setWitnessName("witness-name");
        createDto.setCourtId(UUID.randomUUID());
        createDto.setRecordingVersion(VfMigrationRecordingVersion.ORIG);
        createDto.setRecordingVersionNumber(0);

        mockMvc.perform(put("/vf-migration-records/" + mockId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(createDto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.recordingVersionNumber").value("must be greater than or equal to 1"));
    }


    @Test
    @DisplayName("Should submit migration records successfully")
    public void shouldSubmitMigrationRecordsSuccessfully() throws Exception {
        mockMvc.perform(post("/vf-migration-records/submit")
                            .with(csrf())
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNoContent());

        verify(migrateResolved, times(1)).asyncMigrateResolved();
    }
}
