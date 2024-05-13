package uk.gov.hmcts.reform.preapi.media.dto;

import lombok.Data;

import java.util.List;

@Data
public class MkGetListResponse<E> {
    private List<E> value;
}
