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
    private LoggingService loggingService;

    @Autowired
    public BatchController(JobLauncher jobLauncher,
                           @Qualifier("importCsvJob") Job importCsvJob,
                           @Qualifier("fetchXmlJob") Job fetchXmlJob,
                           LoggingService loggingService
                           ) {
        this.jobLauncher = jobLauncher;
        this.importCsvJob = importCsvJob;
        this.fetchXmlJob = fetchXmlJob;
        this.loggingService = loggingService;
    }

    @PostMapping("/startXml")
    public ResponseEntity<String> startXmlBatch(@RequestParam(value = "debug", defaultValue = "false") boolean debug) {
        try {   
            JobParametersBuilder jobParametersBuilder = new JobParametersBuilder();
            jobParametersBuilder.addLong("time", System.currentTimeMillis()); 
            jobParametersBuilder.addString("debug", String.valueOf(debug));

            jobLauncher.run(fetchXmlJob, jobParametersBuilder.toJobParameters());
            
            return ResponseEntity.ok("Successfully completed Fetch XML batch job ");

        } catch (Exception e) {
            loggingService.logError("Error starting fetch XML batch job: {}", e);
            return ResponseEntity.status(500).body("Failed to start fetch XML batch job");
        }
    }

    @PostMapping("/startTransform")
    public ResponseEntity<String> startBatch(@RequestParam(value = "debug", defaultValue = "false") boolean debug) {
        try {   
            
            JobParametersBuilder jobParametersBuilder = new JobParametersBuilder();
            jobParametersBuilder.addLong("time", System.currentTimeMillis()); 
            jobParametersBuilder.addString("debug", String.valueOf(debug));

            jobLauncher.run(importCsvJob, jobParametersBuilder.toJobParameters());
            return ResponseEntity.ok("Successfully completed Transform batch job ");

        } catch (Exception e) {
            loggingService.logError("Error starting Transform batch job: {}", e);
            return ResponseEntity.status(500).body("Failed to start batch job");
        }
    }

    @PostMapping("/postMigrationJob")
    public ResponseEntity<String> postMigration(@RequestParam(value = "debug", defaultValue = "false") boolean debug) {
        try {   
            
            JobParametersBuilder jobParametersBuilder = new JobParametersBuilder();
            jobParametersBuilder.addLong("time", System.currentTimeMillis()); 
            jobParametersBuilder.addString("debug", String.valueOf(debug));

            jobLauncher.run(importCsvJob, jobParametersBuilder.toJobParameters());
            return ResponseEntity.ok("Successfully completed Post Migration batch job ");

        } catch (Exception e) {
            loggingService.logError("Error starting Post Migration batch job: {}", e);
            return ResponseEntity.status(500).body("Failed to start batch job");
        }
    }

}
