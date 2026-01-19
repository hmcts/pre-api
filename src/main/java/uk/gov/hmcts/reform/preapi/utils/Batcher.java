package uk.gov.hmcts.reform.preapi.utils;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

@UtilityClass
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class Batcher {
    public static <E> void batchProcess(List<E> elements,
                                        int batchSize,
                                        Consumer<E> action,
                                        @Nullable Consumer<List<E>> afterBatch) {
        int totalElements = elements.size();
        IntStream.iterate(0, i -> i < totalElements, i -> i + batchSize)
            .mapToObj(i -> elements.subList(i, Math.min(i + batchSize, totalElements)))
            .forEach(batch -> {
                batch.forEach(action);
                if (afterBatch != null) {
                    afterBatch.accept(batch);
                }
            });
    }

    public static <E, R> void batchProcessFunc(List<E> elements,
                                               int batchSize,
                                               Function<E, R> action,
                                               @Nullable Consumer<List<R>> afterBatch) {
        int totalElements = elements.size();
        IntStream.iterate(0, i -> i < totalElements, i -> i + batchSize)
            .mapToObj(i -> elements.subList(i, Math.min(i + batchSize, totalElements)))
            .forEach(batch -> {
                List<R> results = batch.stream().map(action).toList();
                if (afterBatch != null) {
                    afterBatch.accept(results);
                }
            });
    }
}
