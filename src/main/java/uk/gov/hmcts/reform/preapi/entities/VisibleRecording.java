package uk.gov.hmcts.reform.preapi.entities;

import com.opencsv.bean.CsvBindByName;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class VisibleRecording {
    @CsvBindByName(column = "recording_id")
    public UUID recordingId;

    @CsvBindByName(column = "visible")
    public String visible;
}
