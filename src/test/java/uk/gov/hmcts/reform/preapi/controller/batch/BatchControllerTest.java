// package uk.gov.hmcts.reform.preapi.controller.batch;

// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Test;
// import org.mockito.Mockito;
// import org.springframework.batch.core.Job;
// import org.springframework.batch.core.JobExecution;
// import org.springframework.batch.core.launch.JobLauncher;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
// import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
// import org.springframework.boot.test.mock.mockito.MockBean;
// import org.springframework.http.MediaType;
// import org.springframework.test.web.servlet.MockMvc;

// import uk.gov.hmcts.reform.preapi.batch.controllers.BatchController;
// import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
// import uk.gov.hmcts.reform.preapi.services.ScheduledTaskRunner;

// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.ArgumentMatchers.eq;
// import static org.mockito.Mockito.times;
// import static org.mockito.Mockito.verify;
// import static org.mockito.Mockito.when;
// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
// import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
// import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @WebMvcTest(BatchController.class)
// @AutoConfigureMockMvc(addFilters = false)
// class BatchControllerTest {

//     @Autowired
//     private MockMvc mockMvc;

//     @MockBean
//     private JobLauncher jobLauncher;

//     @MockBean(name = "importCsvJob")
//     private Job importCsvJob;

//     @MockBean
//     private UserAuthenticationService userAuthenticationService;

//     @MockBean
//     private ScheduledTaskRunner taskRunner;

//     @DisplayName("Start Batch Job - Success")
//     @Test
//     void startBatchJob_success() throws Exception {
//         JobExecution mockJobExecution = Mockito.mock(JobExecution.class); 
//         when(jobLauncher.run(eq(importCsvJob), any())).thenReturn(mockJobExecution);

//         mockMvc.perform(post("/batch/start")
//                 .contentType(MediaType.APPLICATION_JSON))
//                 .andExpect(status().isOk())
//                 .andExpect(content().string("Batch job has been started and processing data from all CSV files."));

//         verify(jobLauncher, times(1)).run(eq(importCsvJob), any());
//     }

//     @DisplayName("Start Batch Job - Failure")
//     @Test
//     void startBatchJob_failure() throws Exception {
//         when(jobLauncher.run(eq(importCsvJob), any())).thenThrow(new RuntimeException("Job failed"));

//         mockMvc.perform(post("/batch/start")
//                 .contentType(MediaType.APPLICATION_JSON))
//                 .andExpect(status().isInternalServerError());

//         verify(jobLauncher, times(1)).run(eq(importCsvJob), any());
//     }

// }
