package uk.gov.hmcts.reform.preapi.controllers.params;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Data
public class SearchEditRequests {
    private UUID sourceRecordingId;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date lastModifiedAfter;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date lastModifiedBefore;

    private List<UUID> authorisedBookings;
    private UUID authorisedCourt;
}
