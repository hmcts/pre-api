package uk.gov.hmcts.reform.preapi.media.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MkGetListResponse<E> {
    private List<E> value;
}
