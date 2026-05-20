package uk.gov.hmcts.reform.preapi.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Slf4j
public class ListOfStringsCaseInsensitiveSorter implements Comparator<String> {

    private final List<String> columnOrder;

    public ListOfStringsCaseInsensitiveSorter(List<String> columnOrder) {
        this.columnOrder = columnOrder.stream()
            .map(item -> item.toLowerCase(Locale.UK))
            .toList();
    }

    @Override
    public int compare(String o1, String o2) {
        log.info("Comparing {} and {}", o1, o2);
        return columnOrder.indexOf(o1.toLowerCase(Locale.UK))
            - columnOrder.indexOf(o2.toLowerCase(Locale.UK));
    }
}
