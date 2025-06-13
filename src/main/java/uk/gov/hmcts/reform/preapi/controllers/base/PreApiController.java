package uk.gov.hmcts.reform.preapi.controllers.base;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;

import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PreApiController {
    protected ResponseEntity<Void> getUpsertResponse(UpsertResult result, UUID id) {
        var location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("")
            .buildAndExpand(id)
            .toUri();

        if (result == UpsertResult.CREATED) {
            return ResponseEntity.created(location).build();
        } else if (result == UpsertResult.UPDATED) {
            return ResponseEntity.noContent().location(location).build();
        }
        throw new UnknownServerException("Unexpected result: " + result);
    }
}
