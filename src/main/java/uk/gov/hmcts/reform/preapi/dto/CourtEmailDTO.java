package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.opencsv.bean.CsvBindByName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Court;

@Data
@NoArgsConstructor
@Schema(description = "CourtEmailDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CourtEmailDTO {
    @CsvBindByName(column = "Court")
    @Schema(description = "CourtName")
    private String name;

    @CsvBindByName(column = "PRE Inbox Address")
    @Schema(description = "CourtGroupEmail")
    private String groupEmail;

    public CourtEmailDTO(Court courtEntity) {
        this.name = courtEntity.getName();
        this.groupEmail = courtEntity.getGroupEmail();
    }
}
