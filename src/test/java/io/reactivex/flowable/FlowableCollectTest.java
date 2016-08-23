package io.reactivex.flowable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import io.reactivex.Flowable;
import io.reactivex.functions.BiConsumer;
import io.reactivex.functions.Consumer;
import io.reactivex.plugins.RxJavaPlugins;

public class FlowableCollectTest {
    
    @Test
    public void testCollectToList() {
        Flowable<List<Integer>> o = Flowable.just(1, 2, 3)
        .collect(new Callable<List<Integer>>() {
            @Override
            public List<Integer> call() {
                return new ArrayList<Integer>();
            }
        }, new BiConsumer<List<Integer>, Integer>() {
            @Override
            public void accept(List<Integer> list, Integer v) {
                list.add(v);
            }
        });
        
        List<Integer> list =  o.blockingLast();

        assertEquals(3, list.size());
        assertEquals(1, list.get(0).intValue());
        assertEquals(2, list.get(1).intValue());
        assertEquals(3, list.get(2).intValue());
        
        // test multiple subscribe
        List<Integer> list2 =  o.blockingLast();

        assertEquals(3, list2.size());
        assertEquals(1, list2.get(0).intValue());
        assertEquals(2, list2.get(1).intValue());
        assertEquals(3, list2.get(2).intValue());
    }

    @Test
    public void testCollectToString() {
        String value = Flowable.just(1, 2, 3)
            .collect(
                new Callable<StringBuilder>() {
                    @Override
                    public StringBuilder call() {
                        return new StringBuilder();
                    }
                }, 
                new BiConsumer<StringBuilder, Integer>() {
                    @Override
                    public void accept(StringBuilder sb, Integer v) {
                    if (sb.length() > 0) {
                        sb.append("-");
                    }
                    sb.append(v);
                }
            }).blockingLast().toString();

        assertEquals("1-2-3", value);
    }


    @Test
    public void testFactoryFailureResultsInErrorEmission() {
        final RuntimeException e = new RuntimeException();
        Flowable.just(1).collect(new Callable<List<Integer>>() {

            @Override
            public List<Integer> call() throws Exception {
                throw e;
            }
        }, new BiConsumer<List<Integer>, Integer>() {

            @Override
            public void accept(List<Integer> list, Integer t) {
                list.add(t);
            }
        })
        .test()
        .assertNoValues()
        .assertError(e)
        .assertNotComplete();
    }

    @Test
    public void testCollectorFailureDoesNotResultInTwoErrorEmissions() {
        try {
            final List<Throwable> list = new CopyOnWriteArrayList<Throwable>();
            RxJavaPlugins.setErrorHandler(addToList(list));
            final RuntimeException e1 = new RuntimeException();
            final RuntimeException e2 = new RuntimeException();

            Burst.items(1).error(e2) //
                    .collect(callableListCreator(), biConsumerThrows(e1)) //
                    .test() //
                    .assertError(e1) //
                    .assertNotComplete();
            assertEquals(Arrays.asList(e2), list);
        } finally {
            RxJavaPlugins.reset();
        }
    }

    @Test
    public void testCollectorFailureDoesNotResultInErrorAndCompletedEmissions() {
        final RuntimeException e = new RuntimeException();
        Burst.item(1).create() //
                .collect(callableListCreator(), biConsumerThrows(e)) //
                .test() //
                .assertError(e) //
                .assertNotComplete();
    }

    @Test
    public void testCollectorFailureDoesNotResultInErrorAndOnNextEmissions() {
        final RuntimeException e = new RuntimeException();
        final AtomicBoolean added = new AtomicBoolean();
        BiConsumer<Object, Integer> throwOnFirstOnly = new BiConsumer<Object, Integer>() {

            boolean once = true;

            @Override
            public void accept(Object o, Integer t) {
                if (once) {
                    once = false;
                    throw e;
                } else {
                    added.set(true);
                }
            }
        };
        Burst.items(1, 2).create() //
                .collect(callableListCreator(), throwOnFirstOnly)//
                .test() //
                .assertError(e) //
                .assertNoValues() //
                .assertNotComplete();
        assertFalse(added.get());
    }

    private static Consumer<Throwable> addToList(final List<Throwable> list) {
        return new Consumer<Throwable>() {

            @Override
            public void accept(Throwable t) {
                list.add(t);
            }
        };
    }

    private static <T> Callable<List<T>> callableListCreator() {
        return new Callable<List<T>>() {

            @Override
            public List<T> call() {
                return new ArrayList<T>();
            }
        };
    }

    private static <T> BiConsumer<Object, T> biConsumerThrows(final RuntimeException e) {
        return new BiConsumer<Object, T>() {

            @Override
            public void accept(Object t1, T t2) {
                throw e;
            }
        };
    }

}
