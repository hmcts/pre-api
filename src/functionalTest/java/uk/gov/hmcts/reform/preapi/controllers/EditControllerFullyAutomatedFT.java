package uk.gov.hmcts.reform.preapi.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

// I split this out to capture the first half of the editing process, which doesn't yet have any tests
public class EditControllerFullyAutomatedFT extends FunctionalTestBase {
    private static final String EDIT_CSV_DIR = "src/functionalTest/resources/test/edit/";
    private static final String EDIT_ENDPOINT = "/edits";

    @MockitoBean
    private AzureFinalStorageService azureFinalStorageService;

    @Test
    @DisplayName("Should create a DRAFT edit request")
    void editDraftSuccess() {
        // Should be able to keep updating an edit request
    }

    @Test
    @DisplayName("Should be able to submit an edit request")
    void editRequestSubmission() {
        // The instructions should be read-only once submitted
        // Should send an email notification to shared-with users and the court group email address
        // Should audit-log who submitted it
    }

    @Test
    @DisplayName("When an edit request has been rejected, the submitter and shared-with users should be notified")
    void rejectedEditRequest(){

    }

    @Test
    @DisplayName("When an edit request has been approved, it should be picked up for processing")
    void approvedEditRequest(){
        // Not sure how this transition works in practice. Perhaps we won't need the PENDING status any more?
    }


    @Test
    @DisplayName("Should not create an edit request with unsafe data in fields")
    void editRequestWithUnsafeData() {
        // Copy and rewrite the existing test to use the `PUT edits/{id}` endpoint instead of the CSV endpoint
    }



}
