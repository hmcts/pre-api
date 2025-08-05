package uk.gov.hmcts.reform.preapi.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BatcherTest {
    @Test
    void testBatchProcessingWithAfterBatch() {
        var elements = Arrays.asList(1, 2, 3, 4, 5, 6);
        int batchSize = 2;
        List<Integer> processedElements = new ArrayList<>();
        List<List<Integer>> afterBatchCalls = new ArrayList<>();

        Consumer<Integer> action = processedElements::add;
        Consumer<List<Integer>> afterBatch = afterBatchCalls::add;

        Batcher.batchProcess(elements, batchSize, action, afterBatch);

        assertThat(processedElements).isEqualTo(elements);
        assertThat(afterBatchCalls).hasSize(3);
        assertThat(afterBatchCalls.get(0)).containsExactly(1, 2);
        assertThat(afterBatchCalls.get(1)).containsExactly(3, 4);
        assertThat(afterBatchCalls.get(2)).containsExactly(5, 6);
    }

    @Test
    void testBatchProcessingWithoutAfterBatch() {
        List<Integer> elements = Arrays.asList(1, 2, 3, 4, 5);
        int batchSize = 2;
        List<Integer> processedElements = new ArrayList<>();

        Consumer<Integer> action = processedElements::add;

        Batcher.batchProcess(elements, batchSize, action, null);

        assertThat(processedElements).isEqualTo(elements);
    }

    @Test
    void testBatchProcessingWithEmptyList() {
        List<Integer> elements = new ArrayList<>();
        int batchSize = 2;
        List<Integer> processedElements = new ArrayList<>();

        Consumer<Integer> action = processedElements::add;
        Consumer<List<Integer>> afterBatch = batch -> {
        };

        Batcher.batchProcess(elements, batchSize, action, afterBatch);

        assertThat(processedElements).isEmpty();
    }

    @Test
    void testBatchProcessingWithSingleElement() {
        List<Integer> elements = List.of(42);
        int batchSize = 2;
        List<Integer> processedElements = new ArrayList<>();
        List<List<Integer>> afterBatchCalls = new ArrayList<>();

        Consumer<Integer> action = processedElements::add;
        Consumer<List<Integer>> afterBatch = afterBatchCalls::add;

        Batcher.batchProcess(elements, batchSize, action, afterBatch);

        assertThat(processedElements).isEqualTo(elements);
        assertThat(afterBatchCalls).hasSize(1);
        assertThat(afterBatchCalls.getFirst()).containsExactly(42);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testBatchProcessingWithMockito() {
        List<Integer> elements = Arrays.asList(1, 2, 3, 4);
        int batchSize = 2;

        Consumer<Integer> action = mock(Consumer.class);
        Consumer<List<Integer>> afterBatch = mock(Consumer.class);

        Batcher.batchProcess(elements, batchSize, action, afterBatch);

        verify(action, times(1)).accept(1);
        verify(action, times(1)).accept(2);
        verify(action, times(1)).accept(3);
        verify(action, times(1)).accept(4);
        verify(afterBatch, times(1)).accept(Arrays.asList(1, 2));
        verify(afterBatch, times(1)).accept(Arrays.asList(3, 4));
    }

    @Test
    void testBatchProcessFuncWithAfterBatch() {
        List<Integer> elements = Arrays.asList(1, 2, 3, 4, 5, 6);
        int batchSize = 2;
        List<String> processedElements = new ArrayList<>();
        List<List<String>> afterBatchCalls = new ArrayList<>();

        Function<Integer, String> action = String::valueOf;
        Consumer<List<String>> afterBatch = afterBatchCalls::add;

        Batcher.batchProcessFunc(elements, batchSize, action, afterBatch);

        assertThat(afterBatchCalls).hasSize(3);
        assertThat(afterBatchCalls.get(0)).containsExactly("1", "2");
        assertThat(afterBatchCalls.get(1)).containsExactly("3", "4");
        assertThat(afterBatchCalls.get(2)).containsExactly("5", "6");
    }

    @Test
    void testBatchProcessFuncWithoutAfterBatch() {
        List<Integer> elements = Arrays.asList(1, 2, 3, 4, 5);
        int batchSize = 2;

        Function<Integer, String> action = String::valueOf;

        Batcher.batchProcessFunc(elements, batchSize, action, null);
    }

    @Test
    void testBatchProcessFuncWithEmptyList() {
        List<Integer> elements = new ArrayList<>();
        int batchSize = 2;

        Function<Integer, String> action = String::valueOf;
        Consumer<List<String>> afterBatch = batch -> { };

        Batcher.batchProcessFunc(elements, batchSize, action, afterBatch);
    }

    @Test
    void testBatchProcessFuncWithSingleElement() {
        List<Integer> elements = List.of(42);
        int batchSize = 2;
        List<List<String>> afterBatchCalls = new ArrayList<>();

        Function<Integer, String> action = String::valueOf;
        Consumer<List<String>> afterBatch = afterBatchCalls::add;

        Batcher.batchProcessFunc(elements, batchSize, action, afterBatch);

        assertThat(afterBatchCalls).hasSize(1);
        assertThat(afterBatchCalls.getFirst()).containsExactly("42");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testBatchProcessFuncWithMockito() {
        Function<Integer, String> action = mock(Function.class);
        when(action.apply(1)).thenReturn("1");
        when(action.apply(2)).thenReturn("2");
        when(action.apply(3)).thenReturn("3");
        when(action.apply(4)).thenReturn("4");

        int batchSize = 2;
        List<Integer> elements = Arrays.asList(1, 2, 3, 4);
        Consumer<List<String>> afterBatch = mock(Consumer.class);

        Batcher.batchProcessFunc(elements, batchSize, action, afterBatch);

        verify(action, times(1)).apply(1);
        verify(action, times(1)).apply(2);
        verify(action, times(1)).apply(3);
        verify(action, times(1)).apply(4);
        verify(afterBatch, times(1)).accept(Arrays.asList("1", "2"));
        verify(afterBatch, times(1)).accept(Arrays.asList("3", "4"));
    }
}
