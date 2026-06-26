package uk.gov.hmcts.reform.preapi.controllers.base;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.RequestedPageOutOfRangeException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;

import java.net.URI;
import java.util.UUID;
import java.util.function.Supplier;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PreApiController {
    public static final String CSV_FILE_TYPE = "text/csv";

    protected ResponseEntity<Void> getUpsertResponse(UpsertResult result, UUID id) {
        URI location = ServletUriComponentsBuilder
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

    protected <E> HttpEntity<PagedModel<EntityModel<E>>> getPagedResponse(Supplier<Page<E>> resultSupplier,
                                                                          PagedResourcesAssembler<E> assembler,
                                                                          Pageable pageable) {
        final Page<E> resultPage = resultSupplier.get();

        if (pageable.getPageNumber() > resultPage.getTotalPages()) {
            throw new RequestedPageOutOfRangeException(pageable.getPageNumber(), resultPage.getTotalPages());
        }

        return ResponseEntity.ok(assembler.toModel(resultPage));
    }
}
