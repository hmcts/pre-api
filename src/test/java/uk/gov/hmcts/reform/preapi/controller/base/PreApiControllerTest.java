package uk.gov.hmcts.reform.preapi.controllers.base;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpEntity;
import uk.gov.hmcts.reform.preapi.exception.RequestedPageOutOfRangeException;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PreApiControllerTest {
    @Mock
    private PagedResourcesAssembler<String> assembler;

    private PreApiController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new PreApiController();
    }

    @Test
    @DisplayName("Should return paged response successfully when page request is within range")
    void shouldReturnPagedResponseSuccessfullyWhenPageRequestIsWithinRange() {
        Supplier<Page<String>> resultSupplier =
            () -> new PageImpl<>(List.of("Item1", "Item2"), PageRequest.of(0, 2), 4);
        PagedModel<EntityModel<String>> mockPagedModel = PagedModel.of(
            List.of(EntityModel.of("Item1"), EntityModel.of("Item2")),
            new PagedModel.PageMetadata(2, 0, 4)
        );
        PageRequest pageable = PageRequest.of(0, 2);

        when(assembler.toModel(any())).thenReturn(mockPagedModel);

        HttpEntity<PagedModel<EntityModel<String>>> response =
            controller.getPagedResponse(resultSupplier, assembler, pageable);


        assertEquals(mockPagedModel, response.getBody());
        verify(assembler, times(1)).toModel(any());
    }

    @Test
    @DisplayName("Should throw RequestedPageOutOfRangeException when page request exceeds total pages")
    void shouldThrowRequestedPageOutOfRangeExceptionWhenPageRequestExceedsTotalPages() {
        Supplier<Page<String>> resultSupplier = () -> new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
        PageRequest pageable = PageRequest.of(1, 10);

        assertThrows(
            RequestedPageOutOfRangeException.class,
            () -> controller.getPagedResponse(resultSupplier, assembler, pageable)
        );
        verify(assembler, never()).toModel(any());
    }

    @Test
    @DisplayName("Should handle empty result page successfully")
    void shouldHandleEmptyResultPageSuccessfully() {
        Supplier<Page<String>> resultSupplier = () -> new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
        PagedModel<EntityModel<String>> mockPagedModel = PagedModel.of(
            Collections.emptyList(),
            new PagedModel.PageMetadata(10, 0, 0)
        );
        PageRequest pageable = PageRequest.of(0, 10);

        when(assembler.toModel(any())).thenReturn(mockPagedModel);

        HttpEntity<PagedModel<EntityModel<String>>> response =
            controller.getPagedResponse(resultSupplier, assembler, pageable);

        assertEquals(mockPagedModel, response.getBody());
        verify(assembler, times(1)).toModel(any());
    }

    @Test
    @DisplayName("Should not throw exception for valid pageable and non-empty result")
    void shouldNotThrowExceptionForValidPageableAndNonEmptyResult() {
        Supplier<Page<String>> resultSupplier = () -> new PageImpl<>(List.of("Item1"), PageRequest.of(0, 1), 1);
        PageRequest pageable = PageRequest.of(0, 1);

        when(assembler.toModel(any())).thenReturn(PagedModel.empty());

        assertDoesNotThrow(() -> controller.getPagedResponse(resultSupplier, assembler, pageable));
    }
}
