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

@RestController
@RequestMapping("/batch")
public class BatchController {

    private final JobLauncher jobLauncher;
    private final Job importCsvJob;

    @Autowired
    public BatchController(JobLauncher jobLauncher,
                           @Qualifier("importCsvJob") Job importCsvJob) {
        this.jobLauncher = jobLauncher;
        this.importCsvJob = importCsvJob;
    }

    @PostMapping("/start")
    public ResponseEntity<String> startBatch() throws Exception {
        JobParametersBuilder jobParametersBuilder = new JobParametersBuilder();
        jobParametersBuilder.addLong("time", System.currentTimeMillis()); 

        jobLauncher.run(importCsvJob, jobParametersBuilder.toJobParameters());

        return ResponseEntity.ok("Batch job has been started and processing data from all CSV files.");
    }
}
