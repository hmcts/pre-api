package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Room;

import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "RoomDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RoomDTO {
    @Schema(description = "RoomId")
    private UUID id;

    @Schema(description = "RoomName")
    private String name;

    public RoomDTO(Room room) {
        id = room.getId();
        name = room.getName();
    }
}
