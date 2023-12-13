package uk.gov.hmcts.reform.preapi.dto;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.enums.CourtType;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateCourtDTO {
    private UUID id;
    private String name;
    private CourtType courtType;
    private String locationCode;
    private List<UUID> regions = List.of();
    private List<UUID> rooms = List.of();
}
