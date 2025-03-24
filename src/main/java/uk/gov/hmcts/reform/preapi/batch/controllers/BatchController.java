package uk.gov.hmcts.reform.preapi.batch.controllers;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;

@RestController
@RequestMapping("/batch")
public class BatchController {

    private final JobLauncher jobLauncher;
    private final Job importCsvJob;
    private final Job fetchXmlJob;
    private final Job postMigrationJob;
    private final Job processExclusionsJob;
    private LoggingService loggingService;

    @Autowired
    public BatchController(JobLauncher jobLauncher,
                           @Qualifier("importCsvJob") Job importCsvJob,
                           @Qualifier("fetchXmlJob") Job fetchXmlJob,
                           @Qualifier("postMigrationJob") Job postMigrationJob,
                           @Qualifier("processExclusionsJob") Job processExclusionsJob,
                           LoggingService loggingService
                           ) {
        this.jobLauncher = jobLauncher;
        this.importCsvJob = importCsvJob;
        this.fetchXmlJob = fetchXmlJob;
        this.postMigrationJob = postMigrationJob;
        this.processExclusionsJob = processExclusionsJob;
        this.loggingService = loggingService;
    }

    private ResponseEntity<String> startJob(Job job, String jobName, boolean debug, String migrationType) {
        try {
            JobParametersBuilder jobParametersBuilder = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("debug", String.valueOf(debug))
                    .addString("migrationType", migrationType);

            jobLauncher.run(job, jobParametersBuilder.toJobParameters());
            return ResponseEntity.ok("Successfully completed " + jobName + " batch job");

        } catch (Exception e) {
            loggingService.logError("Error starting " + jobName + " batch job: {}", e);
            return ResponseEntity.status(500).body("Failed to start " + jobName + " batch job");
        }
    }

    @PostMapping("/fetch-xml")
    public ResponseEntity<String> startXmlBatch(
        @RequestParam(value = "debug", defaultValue = "false") boolean debug,
        @RequestParam(value = "migrationType", defaultValue = "FULL") String migrationType
    ) {
        return startJob(fetchXmlJob, "Fetch XML", debug, migrationType);
    }

    @PostMapping("/process-migration")
    public ResponseEntity<String> startBatch(
        @RequestParam(value = "debug", defaultValue = "false") boolean debug,
        @RequestParam(value = "migrationType", defaultValue = "FULL") String migrationType
    ) {
        return startJob(importCsvJob, "Transform", debug, migrationType);
    }

    @PostMapping("/post-migration-tasks")
    public ResponseEntity<String> postMigration(
        @RequestParam(value = "debug", defaultValue = "false") boolean debug
    ) {
        return startJob(postMigrationJob, "Post Migration", debug, "FULL");
    }

    @PostMapping("/migrate-exclusions")
    public ResponseEntity<String> processExclusions(
        @RequestParam(value = "debug", defaultValue = "false") boolean debug
    ) {
        return startJob(processExclusionsJob, "Process Exclusions", debug, "FULL");
    }

}
