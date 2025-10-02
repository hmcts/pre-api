package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.data.web.SortDefault;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchMigrationRecords;
import uk.gov.hmcts.reform.preapi.dto.migration.CreateVfMigrationRecordDTO;
import uk.gov.hmcts.reform.preapi.dto.migration.VfMigrationRecordDTO;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.tasks.migration.BatchImportMissingMkAssets;
import uk.gov.hmcts.reform.preapi.tasks.migration.MigrateResolved;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/vf-migration-records")
public class VfMigrationController extends PreApiController {
    private final MigrationRecordService migrationRecordService;
    private final MigrateResolved migrateResolved;
    private final BatchImportMissingMkAssets batchImportMissingMkAssets;

    @Autowired
    public VfMigrationController(final MigrationRecordService migrationRecordService,
                                 final MigrateResolved migrateResolved,
                                 final BatchImportMissingMkAssets batchImportMissingMkAssets) {
        this.migrationRecordService = migrationRecordService;
        this.migrateResolved = migrateResolved;
        this.batchImportMissingMkAssets = batchImportMissingMkAssets;
    }

    @GetMapping
    @Operation(operationId = "getVfMigrationRecords", summary = "Search all migration records")
    @Parameter(
        name = "caseReference",
        description = "The case reference to search for",
        schema = @Schema(implementation = String.class))
    @Parameter(
        name = "witnessName",
        description = "The witness name to search for",
        schema = @Schema(implementation = String.class))
    @Parameter(
        name = "defendantName",
        description = "The defendant name to search for",
        schema = @Schema(implementation = String.class))
    @Parameter(
        name = "courtId",
        description = "The court id to search for",
        schema = @Schema(implementation = UUID.class))
    @Parameter(
        name = "status",
        description = "The status to search for",
        schema = @Schema(implementation = VfMigrationStatus.class))
    @Parameter(
        name = "createDateFrom",
        description = "The date the record was created to search from",
        schema = @Schema(implementation = Date.class, format = "date"),
        example = "2024-04-27")
    @Parameter(
        name = "createDateTo",
        description = "The date the record was created to search to",
        schema = @Schema(implementation = Date.class, format = "date"),
        example = "2024-04-27")
    @Parameter(
        name = "reasonIn",
        description = "Search by a list of reasons",
        schema = @Schema(implementation = List.class))
    @Parameter(
        name = "reasonNotIn",
        description = "Search by a list of reasons that should not be included",
        schema = @Schema(implementation = List.class))
    @Parameter(
        name = "sort",
        description = "Sort by",
        schema = @Schema(implementation = String.class),
        example = "archiveName,desc")
    @Parameter(
        name = "page",
        description = "The page number of search result to return",
        schema = @Schema(implementation = Integer.class),
        example = "0")
    @Parameter(
        name = "size",
        description = "The number of search results to return per page",
        schema = @Schema(implementation = Integer.class),
        example = "10")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER')")
    public HttpEntity<PagedModel<EntityModel<VfMigrationRecordDTO>>> getVfMigrationRecords(
        @Parameter(hidden = true) @ModelAttribute SearchMigrationRecords params,
        @SortDefault.SortDefaults(
            @SortDefault(sort = "archiveName", direction = Sort.Direction.DESC)
        ) @Parameter(hidden = true) Pageable pageable,
        @Parameter(hidden = true) PagedResourcesAssembler<VfMigrationRecordDTO> assembler
    ) {
        return getPagedResponse(() -> migrationRecordService.findAllBy(params, pageable), assembler, pageable);
    }

    @PutMapping("/{id}")
    @Operation(operationId = "putVfMigrationRecord", summary = "Update vf migration record")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER')")
    public ResponseEntity<Void> updateVfMigrationRecord(@PathVariable UUID id,
                                                        @Valid @RequestBody CreateVfMigrationRecordDTO dto) {
        if (!id.equals(dto.getId())) {
            throw new PathPayloadMismatchException("id", "createMigrationRecordDto.id");
        }

        return getUpsertResponse(migrationRecordService.update(dto), id);
    }

    @PostMapping("/submit")
    @Operation(operationId = "submitMigrationRecords", summary = "Submits resolved migration records and runs import")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER')")
    public ResponseEntity<Void> submitMigrationRecords() {
        if (migrationRecordService.markReadyAsSubmitted()) {
            migrateResolved.asyncMigrateResolved();
        }

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import-assets")
    @Operation(operationId = "importVodafoneAssets", summary = "Imports Vodafone for resolbed migration records")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER')")
    public ResponseEntity<Void> importVodafoneAssets() {
        batchImportMissingMkAssets.asyncRun();

        return ResponseEntity.noContent().build();
    }
}
