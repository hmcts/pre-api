package uk.gov.hmcts.reform.preapi.dto.reports;

import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.User;

public record PlaybackReportArgsRecord(Audit audit, User user, Recording recording) { }
