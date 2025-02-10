package uk.gov.hmcts.reform.preapi.controllers.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.logging.Logger;

@RestController
@RequestMapping("/batch")
public class BatchController {

    private final JobLauncher jobLauncher;
    private final Job importCsvJob;
    private final Job fetchXmlJob;

    @Autowired
    public BatchController(JobLauncher jobLauncher,
                           @Qualifier("importCsvJob") Job importCsvJob,
                           @Qualifier("fetchXmlJob") Job fetchXmlJob) {
        this.jobLauncher = jobLauncher;
        this.importCsvJob = importCsvJob;
        this.fetchXmlJob = fetchXmlJob;
    }

    @PostMapping("/startXml")
    public ResponseEntity<String> startXmlBatch() {
        try {   
            JobParametersBuilder jobParametersBuilder = new JobParametersBuilder();
            jobParametersBuilder.addLong("time", System.currentTimeMillis()); 

            jobLauncher.run(fetchXmlJob, jobParametersBuilder.toJobParameters());
            Logger.getAnonymousLogger().info("Fetch XML batch job successfully started.");
            return ResponseEntity.ok(
                "Fetch XML batch job job has been started and processing data from all CSV files.");

        } catch (Exception e) {
            Logger.getAnonymousLogger().severe("Error starting fetch XML batch job: " + e.getMessage());
            return ResponseEntity.status(500).body("Failed to start fetch XML batch job: " + e.getMessage());
        }
    }

    @PostMapping("/startTransform")
    public ResponseEntity<String> startBatch() {
        try {   
            JobParametersBuilder jobParametersBuilder = new JobParametersBuilder();
            jobParametersBuilder.addLong("time", System.currentTimeMillis()); 

            jobLauncher.run(importCsvJob, jobParametersBuilder.toJobParameters());
            Logger.getAnonymousLogger().info("Batch job successfully started.");
            return ResponseEntity.ok("Batch job has been started and processing data from all CSV files.");

        } catch (Exception e) {
            Logger.getAnonymousLogger().severe("Error starting batch job: " + e.getMessage());
            return ResponseEntity.status(500).body("Failed to start batch job: " + e.getMessage());
        }
    }

}
