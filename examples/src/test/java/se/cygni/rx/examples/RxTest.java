package se.cygni.rx.examples;

import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.internal.operators.OperatorMap;
import rx.observables.ConnectableObservable;
import rx.observables.GroupedObservable;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class RxTest {

    private ArrayBlockingQueue<String> result;

    @Before
    public void before() {
        result = new ArrayBlockingQueue<String>(1);
    }

    @org.junit.Test
    public void test() throws InterruptedException {
        doA("")
                .flatMap(this::doB)
                .flatMap(this::doC)
                .subscribe(this::done, ex -> giveUp());
        assertEquals("abc", result.poll(1, SECONDS));
    }

    @Test
    public void flipObservable() throws Exception {
        CountDownLatch threw = new CountDownLatch(1);
        List<Integer> produced = new ArrayList<>();
        Observable.just(3, 2, 1, 0, -1, -2, -3)
                .map(i -> {
                    assert i != 0;
                    return -i;
                })
                .subscribe(
                        produced::add,
                        e -> threw.countDown(),
                        () -> {
                        }
                );
        threw.await(1, SECONDS);
        assertEquals(Arrays.asList(-3, -2, -1), produced);
    }

    @Test
    public void unsubscribe() throws InterruptedException {
        CountDownLatch unsubscribed = new CountDownLatch(1);
        Observable<Integer> o = Observable.interval(1, MILLISECONDS).map(l -> Arrays.asList(1, 2, 3)).flatMap
                (Observable::from);
        Subscriber<Integer> s = new Subscriber<Integer>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(Integer i) {
                System.out.println(i);
                if (i == 3) {
                    unsubscribe();
                    unsubscribed.countDown();
                }
            }
        };
        o.subscribe(s);
        unsubscribed.await();
    }

    @Test
    public void testSubscriber() {
        Observable<Integer> o = Observable.interval(1, MILLISECONDS).map(l -> Arrays.asList(1, 2, 3)).flatMap
                (Observable::from).take(6);
        Subscriber<Integer> s = new Subscriber<Integer>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(Integer i) {
                System.out.println(i);
            }
        };
        o.subscribe(s);
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        o.subscribe(ts);
        ts.awaitTerminalEvent();
    }

    @Test
    public void create() {
        Observable.empty();
        Observable.never();
        Observable.error(new RuntimeException("error"));
        Observable.<String>create(subscriber -> {
            if (!subscriber.isUnsubscribed()) {
                subscriber.onNext("whatever");
            }
        });
        Observable.from(Executors.newSingleThreadExecutor().submit(() -> 3));
        Observable.from(new Integer[]{1, 2, 3});
        Observable.just(1, 2, 3);
        Observable.timer(1, SECONDS);
        Observable.interval(1, SECONDS);
        Observable.range(0, 10);
        Observable.defer(() -> Observable.just(1));
    }

    @Test
    public void transform() {
        final Observable<Integer> o = Observable
                .defer(() ->
                        Observable
                                .just(5)
                                .flatMap(i -> Observable.range(0, i))
                                .scan((i, j) -> i + j)
                );
        assertEquals(o.toList().toBlocking().first(),
                Arrays.asList(0, 1, 3, 6, 10));
        assertEquals(o.startWith(42).toList().toBlocking().first(), Arrays.asList(42, 0, 1, 3, 6, 10));
        assertEquals(Observable.zip(o, o, (a, b) -> a + b).toList().toBlocking().first(), Arrays.asList(0, 2, 6, 12, 20));
        assertEquals(o.buffer(3).toList().toBlocking().first(), Arrays.asList(Arrays.asList(0, 1, 3), Arrays.asList(6, 10)));

        final List<GroupedObservable<Boolean, Integer>> groups = o.groupBy(i -> (i % 2) == 0).toList().toBlocking().first();
        final GroupedObservable<Boolean, Integer> firstGroup = groups.get(0);
        final GroupedObservable<Boolean, Integer> secondGroup = groups.get(1);
        assertEquals(firstGroup.getKey(), true);
        assertEquals(firstGroup.toList().toBlocking().first(), Arrays.asList(0, 6, 10));
        assertEquals(secondGroup.getKey(), false);
        assertEquals(secondGroup.toList().toBlocking().first(), Arrays.asList(1, 3));
    }

    @Test
    public void aMoreElaborateContrivedExample() {
        final Observable<Integer> o = Observable
                .defer(() ->
                        Observable
                                .just(5)
                                .flatMap(i -> Observable.range(0, i))
                                .scan((i, j) -> i + j)
                                .startWith(42)
                ).concatWith(
                        Observable
                                .interval(1, MILLISECONDS)
                                .map(l -> (int) (long) l)
                                .skip(2)
                                .map(i -> 100 + i)
                )
                .take(9);
        assertEquals(Arrays.asList(42, 0, 1, 3, 6, 10, 102, 103, 104), o.toList().toBlocking().first());
    }

    @Test
    public void filter() {
        assert Observable.sequenceEqual(
                Observable.just(1, 2).filter(i -> (i % 2) == 0),
                Observable.just(2)
        ).toList().toBlocking().first().get(0);

        assertEquals(Observable.range(0, 100).take(3).toList().toBlocking().first(), Arrays.asList(0, 1, 2));

        final TestSubscriber<Integer> testSubscriber = new TestSubscriber<>();
        Observable.just(1, 2, 3).elementAt(2).subscribe(testSubscriber);
        testSubscriber.assertReceivedOnNext(Collections.singletonList(3));

        final TestScheduler testScheduler = new TestScheduler();
        final Observable<Long> o = Observable.interval(1, MILLISECONDS, testScheduler).sample(1, SECONDS, testScheduler)
                .take(3);
        final TestSubscriber<Object> testSubscriber2 = new TestSubscriber<>();
        o.subscribe(testSubscriber2);
        testScheduler.advanceTimeBy(100, SECONDS);

        testSubscriber2.awaitTerminalEvent();
        testSubscriber2.assertReceivedOnNext(Arrays.asList(998L, 1998L, 2998L));
    }

    @Test
    public void combine() {
        final TestScheduler ts = new TestScheduler();
        final Observable<String> a = Observable.timer(2, SECONDS, ts).map(t -> "a");
        final Observable<String> b = Observable.timer(1, SECONDS, ts).map(t -> "b");
        final Observable<String> combo = a.mergeWith(b);
        final TestSubscriber<String> suba = new TestSubscriber<>();
        combo.subscribe(suba);
        ts.advanceTimeBy(3, SECONDS);
        suba.assertReceivedOnNext(Arrays.asList("b", "a"));

        final TestScheduler ts2 = new TestScheduler();
        final Observable<String> everySecondAn = Observable.interval(1, SECONDS, ts2).lift(index()).map(i -> "a" + i);
        final Observable<String> everySecondBn = Observable.interval(1, SECONDS, ts2).lift(index()).map(i -> "b" + i);
        final Observable<Observable<String>> firstATThenB =
                Observable.timer(0, SECONDS, ts2).flatMap(t -> Observable.just(everySecondAn)).mergeWith(
                        Observable.timer(3, SECONDS, ts2).flatMap(t -> Observable.just(everySecondBn))
                );
        final Observable<String> flattened = Observable.switchOnNext(firstATThenB);
        final TestSubscriber<Object> subb = new TestSubscriber<>();
        flattened.subscribe(subb);
        ts2.advanceTimeBy(7, SECONDS);
        subb.assertReceivedOnNext(Arrays.asList("a1", "a2", "b1", "b2", "b3", "b4"));
    }

    @Test
    public void mergeNonTimed() {
        assertEquals(Arrays.asList(1, 2, 3, 4), Observable.just(1, 2).mergeWith(Observable.just(3, 4)).toList().toBlocking().first());
    }

    @Test
    public void conditional() {
        assert Observable.just(1, 2).all(x -> x > 0).toBlocking().first();
        assert !Observable.just(1, 2).contains(3).toBlocking().first();
        final TestScheduler sched = new TestScheduler();
        final TestSubscriber<Object> ts = new TestSubscriber<>();
        final Observable<Long> o = Observable.timer(0, 1, MILLISECONDS, sched).skipUntil(Observable.timer(3, MILLISECONDS, sched));
        o.subscribe(ts);
        sched.advanceTimeBy(10, MILLISECONDS);
        ts.assertReceivedOnNext(Arrays.asList(3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L));
        assertEquals(Arrays.asList(-2, -1), Observable.just(-2, -1, 0, 1, 2).takeWhile(i -> i < 0).toList().toBlocking().first());
    }

    @Test
    public void testingWithSchedulerAndTestSubscriber() {
        final TestScheduler sched = new TestScheduler();
        final TestSubscriber<Object> ts = new TestSubscriber<>();
        final Observable<Long> o = Observable
                .timer(0, 1, MILLISECONDS, sched)
                .skipUntil(Observable.timer(3, MILLISECONDS, sched));
        o.subscribe(ts);
        sched.advanceTimeBy(10, MILLISECONDS);
        ts.assertReceivedOnNext(Arrays.asList(3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L));
    }

    @Test
    public void testingByBlocking() {
        assertEquals(Arrays.asList(1, 2, 3), Observable.just(1, 2, 3).toList().toBlocking().first());
    }

    @Test
    public void abstracting() {
        assertEquals(Arrays.asList(1L, 2L), Observable.just("a", "b").lift(index()).toList().toBlocking().first());
    }

    @Test
    public void mathAggregate() {
        assertEquals(Arrays.asList(1, 2, 3, 4), Observable.just(1, 2).concatWith(Observable.just(3, 4)).toList().toBlocking().first());
        assertEquals(2, (long) Observable.just("a", "b").count().toBlocking().first());
        assertEquals(10, (long) Observable.just(1, 2, 3, 4).reduce(0, (a, b) -> a + b).toBlocking().first());
    }

    @Test
    public void indexByZip() {
        assertEquals(Arrays.asList("a1", "b2"),
                Observable.zip(Observable.just("a", "b"), Observable.range(1, 10), (a, b) -> a + b).toList().toBlocking().first());
    }

    @Test
    public void simpleFilter() {
        final Observable<Integer> o = Observable.just(0, 1, 2, 3).filter(i -> (i % 2) == 0);
        assertEquals(
                Arrays.asList(0, 2),
                o.toList().toBlocking().first()
        );
    }

    @Test
    public void simpleFlatMap() {
        final Observable<String> o = Observable.just("a,b", "c,d")
                .flatMap(s -> Observable.from(s.split(",")));
        assertEquals(
                Arrays.asList("a", "b", "c", "d"),
                o.toList().toBlocking().first()
        );
    }

    @Test
    public void simpleMerge() {
        final TestScheduler ts = new TestScheduler();
        final Observable<String> o = Observable
                .interval(1, SECONDS, ts)
                .map(i -> "a" + i)
                .mergeWith(
                        Observable
                                .interval(1, SECONDS, ts)
                                .delay(500, MILLISECONDS, ts)
                                .map(i -> "b" + i)
                ).takeUntil(
                        Observable
                                .timer(3500, MILLISECONDS, ts)
                );
        final TestSubscriber<Object> sub = new TestSubscriber<>();
        o.subscribe(sub);
        ts.advanceTimeBy(4, SECONDS);
        sub.assertReceivedOnNext(
                Arrays.asList("a0", "b0", "a1", "b1", "a2")
        );
        sub.assertTerminalEvent();
    }

    @Test
    public void simpleScan() {
        final Observable<String> o = Observable
                .just("a", "b", "c")
                .scan((s1, s2) -> s1 + s2);
        assertEquals(
                Arrays.asList("a", "ab", "abc"),
                o.toList().toBlocking().first()
        );
    }

    //@Test
    public void backpressureOom() {
        // Publish subscribes and broadcasts to all current listeners (non-durable topic)
        // Replay subscribes, buffers, and makes sure all listeners are up to date (durable topic)
        final ConnectableObservable<Long> hotSource = Observable.interval(1, MILLISECONDS).replay();
        // subscribeOn determines from which thread the subscribers will get the values pushed
        final Observable<Long> o = hotSource.take(3).map(i -> {
            sleep(100L);
            return i;
        }).subscribeOn(Schedulers.from(Executors.newSingleThreadExecutor(r -> new Thread(r, "subscribeOn"))))
                .observeOn(Schedulers.from(Executors.newSingleThreadExecutor(r -> new Thread(r, "observeOn"))));
        final TestSubscriber<Object> ts = new TestSubscriber<>();
        final Subscriber<Long> s = new Subscriber<Long>() {
            @Override
            public void onCompleted() {
                ts.onCompleted();
            }

            @Override
            public void onError(Throwable e) {
                ts.onError(e);
            }

            @Override
            public void onNext(Long aLong) {
                ts.onNext(aLong);
            }
        };
        o.subscribe(s);
        hotSource.connect();
        sleep(100000L);
        ts.assertReceivedOnNext(Arrays.asList(0L, 1L, 2L));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    private <T> OperatorMap<T, Long> index() {
        final AtomicLong l = new AtomicLong();
        return new OperatorMap<>(new Func1<T, Long>() {
            @Override
            public Long call(T t) {
                return l.incrementAndGet();
            }
        });
    }

    private void done(String param) {
        result.add(param);
    }

    private void giveUp() {
    }

    private Observable<String> doA(String param) {
        return doit(param, "a");
    }

    private Observable<String> doB(String param) {
        return doit(param, "b");
    }

    private Observable<String> doC(String param) {
        return doit(param, "c");
    }

    private Observable<String> doit(String param, String suffix) {
        return Observable.just(param + suffix);
    }

}
