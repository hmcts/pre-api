package uk.gov.hmcts.reform.preapi.dto.media;

import com.azure.resourcemanager.mediaservices.models.LiveEventEndpoint;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.media.dto.MkLiveEvent;

import java.util.Collection;
import java.util.stream.Stream;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "LiveEventDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class LiveEventDTO {
    @Schema(description = "LiveEventId")
    private String id;

    @Schema(description = "LiveEventName")
    private String name;

    @Schema(description = "LiveEventDescription")
    private String description;

    @Schema(description = "LiveEventResourceState")
    private String resourceState;

    @Schema(description = "LiveEventInputRtmp")
    private String inputRtmp;

    public LiveEventDTO(MkLiveEvent liveEvent) {
        id = liveEvent.getId();
        name = liveEvent.getName();
        description = liveEvent.getProperties().getDescription();
        resourceState = liveEvent.getProperties().getResourceState();
        inputRtmp = Stream.ofNullable(liveEvent
                .getProperties()
                .getInput()
                .getEndpoints())
            .flatMap(Collection::stream)
            .filter(e -> e.url().startsWith("rtmps://"))
            .findFirst()
            .map(LiveEventEndpoint::url)
            .orElse(null);
    }
}
