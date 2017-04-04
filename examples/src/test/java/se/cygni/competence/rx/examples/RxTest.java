package se.cygni.competence.rx.examples;

import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.exceptions.Exceptions;
import rx.exceptions.OnErrorNotImplementedException;
import rx.functions.Func1;
import rx.observables.ConnectableObservable;
import rx.observables.GroupedObservable;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.PublishSubject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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
    public void parallelizeWithConcatDoesNotWork() {
        // Show that concat doesn't start all source observables at the start,
        // preventing parallelism.
        //
        // We create 4 observables with a delay of 4,3,2,1 * 100 ms each. If
        // concat would start them all at once, the last one would complete first
        // and "doneOrder" should be 1,2,3,4 while the returned order would be
        // kept as 4,3,2,1. As we see, concat does not do this, the source observables
        // are started in sequence.
        final ConcurrentLinkedQueue<Integer> doneOrder = new ConcurrentLinkedQueue<>();
        final Observable<Observable<Integer>> delayed = Observable
                .just(4, 3, 2, 1)
                .map(
                        i -> Observable.just(i)
                                .delay(i * 100, TimeUnit.MILLISECONDS)
                                .map(integer -> {
                                    doneOrder.add(integer);
                                    return integer;
                                })
                );
        final List<Integer> returned = Observable.concat(delayed).toList().toBlocking().first();
        assertEquals(Arrays.asList(4, 3, 2, 1), returned);
        assertEquals(Arrays.asList(4, 3, 2, 1), Arrays.asList(doneOrder.toArray()));
    }

    @Test
    public void parallelizeWithConcatEagerDoesWork() {
        // Same as the previous test, but show that concatEager does start all sources
        // at once, making "doneOrder" into 1,2,3,4
        final ConcurrentLinkedQueue<Integer> doneOrder = new ConcurrentLinkedQueue<>();
        final Observable<Observable<Integer>> delayed = Observable
                .just(4, 3, 2, 1)
                .map(
                        i -> Observable.just(i)
                                .delay(i * 100, TimeUnit.MILLISECONDS)
                                .map(integer -> {
                                    doneOrder.add(integer);
                                    return integer;
                                })
                );
        final List<Integer> returned = Observable.concatEager(delayed).toList().toBlocking().first();
        assertEquals(Arrays.asList(4, 3, 2, 1), returned);
        assertEquals(Arrays.asList(1, 2, 3, 4), Arrays.asList(doneOrder.toArray()));
    }

    @Test
    public void parallelizeWithFlatMap() {
        final TestSubscriber<Integer> ts = new TestSubscriber<>();
        final TestScheduler sch = new TestScheduler();
        Observable
                .just(1, 2, 3, 4)
                .flatMap(
                        i -> Observable.just(i)
                                .delay(4 - i, TimeUnit.SECONDS, sch)
                )
                .subscribe(ts);
        sch.advanceTimeBy(4, TimeUnit.SECONDS);
        ts.awaitTerminalEvent();
        ts.assertReceivedOnNext(Arrays.asList(4, 3, 2, 1));
    }

    @Test
    public void sequencing() {
        final CompletableFuture<String> cf1 = new CompletableFuture<>();
        final CompletableFuture<String> cf2 = new CompletableFuture<>();
        final List<Observable<String>> cfs = Arrays.asList(fromCompletableFuture(cf1), fromCompletableFuture(cf2));
        final TestSubscriber<Object> ts = new TestSubscriber<>();
        Observable
                .just(0, 1)
                .flatMap(cfs::get)
                .subscribe(ts);
        ts.assertReceivedOnNext(Collections.emptyList());
        cf2.complete("2");
        ts.assertReceivedOnNext(Collections.singletonList("2"));
        cf1.complete("1");
        ts.assertReceivedOnNext(Arrays.asList("2", "1"));
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final String name;

        public NamedThreadFactory(String name) {
            this.name = name;
        }

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, name);
        }
    }

    class Intermediate {
        private final Observable<String> finalValue;

        public Intermediate(final Observable<String> finalValue) {
            this.finalValue = finalValue;
        }

        public Observable<String> getFinal() {
            return finalValue;
        }
    }

    @Test
    public void sequencing2() {
        //Trying to mimick the web requests, where you get one observable for
        //the arrival of the response, and from that intermediate can request
        //a new observable from the body.
        //
        //Check that the second request isn't blocked if the first request never replies.
        final CompletableFuture<Intermediate> cf1a = new CompletableFuture<>();
        final CompletableFuture<Intermediate> cf2a = new CompletableFuture<>();
        final CompletableFuture<String> cf1b = new CompletableFuture<>();
        final CompletableFuture<String> cf2b = new CompletableFuture<>();
        final List<Observable<Intermediate>> cfs = Arrays.asList(
                fromCompletableFuture(cf1a),
                fromCompletableFuture(cf2a)
        );
        final TestSubscriber<String> ts = new TestSubscriber<>();
        Observable
                .just(0, 1)
                .flatMap(cfs::get)
                .flatMap(Intermediate::getFinal)
                .subscribe(ts);
        ts.assertReceivedOnNext(Collections.emptyList());
        cf1a.complete(new Intermediate(fromCompletableFuture(cf1b)));
        cf2a.complete(new Intermediate(fromCompletableFuture(cf2b)));
        cf2b.complete("2");
        ts.assertReceivedOnNext(Collections.singletonList("2"));
        cf1b.complete("1");
        ts.assertReceivedOnNext(Arrays.asList("2", "1"));
    }

    private static <T> Observable<T> fromCompletableFuture(CompletableFuture<T> cf1) {
        //Observable.from(Future) is blocking, leading to subscribe blocking
        return Observable.create(new Observable.OnSubscribe<T>() {
            @Override
            public void call(Subscriber<? super T> subscriber) {
                if (!subscriber.isUnsubscribed()) {
                    cf1.thenAccept((t) -> {
                        subscriber.onNext(t);
                        subscriber.onCompleted();
                    });
                }
            }
        });
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
    public void simpleMap() {
        final Observable<String> o = Observable.just(0, 10, 15, 20)
                .map(i -> Integer.toHexString(i));
        assertEquals(
                Arrays.asList("0", "a", "f", "14"),
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
    public void betterFlatMap() {
        final TestScheduler sched = new TestScheduler();
        final Observable<String> squashed = Observable.just("3,1", "4,2")
                .flatMap(s -> {
                    final String[] parts = s.split(",");
                    final Long firstDelay = Long.valueOf(parts[0]);
                    final Long secondDelay = Long.valueOf(parts[1]);
                    final Observable<String> t1Observable = Observable
                            .timer(firstDelay, SECONDS, sched).map(i -> parts[0]);
                    final Observable<String> t2Observable = Observable
                            .timer(secondDelay, SECONDS, sched).map(i -> parts[1]);
                    return Observable.merge(t1Observable, t2Observable);
                });
        final TestSubscriber<String> ts = new TestSubscriber<>();
        squashed.subscribe(ts);
        sched.advanceTimeBy(5, TimeUnit.SECONDS);
        ts.assertReceivedOnNext(Arrays.asList("1", "2", "3", "4"));
    }

    @Test
    public void betterMapToObservableMerge() {
        final TestScheduler sched = new TestScheduler();
        final Observable<Observable<String>> o = Observable.just("3,1", "4,2")
                .map(s -> {
                    final String[] parts = s.split(",");
                    final Long firstDelay = Long.valueOf(parts[0]);
                    final Long secondDelay = Long.valueOf(parts[1]);
                    final Observable<String> t1Observable = Observable
                            .timer(firstDelay, SECONDS, sched).map(i -> parts[0]);
                    final Observable<String> t2Observable = Observable
                            .timer(secondDelay, SECONDS, sched).map(i -> parts[1]);
                    return Observable.merge(t1Observable, t2Observable);
                });
        final Observable<String> squashed = Observable.merge(o);
        final TestSubscriber<String> ts = new TestSubscriber<>();
        squashed.subscribe(ts);
        sched.advanceTimeBy(5, TimeUnit.SECONDS);
        ts.assertReceivedOnNext(Arrays.asList("1", "2", "3", "4"));
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
    public void simpleZip() {
        final Observable<String> letters = Observable.just("a", "b");
        final Observable<String> numbers = Observable.just("1", "2");
        final Observable<String> zipped = letters.zipWith(numbers, (letter, number) -> letter + number);
        final TestSubscriber<String> ts = new TestSubscriber<>();
        zipped.subscribe(ts);
        ts.assertReceivedOnNext(Arrays.asList("a1", "b2"));
    }

    @Test
    public void simplerMerge() {
        final CompletableFuture<String> a = new CompletableFuture<>();
        final CompletableFuture<String> b = new CompletableFuture<>();
        final Observable<String> oa = fromCompletableFuture(a);
        final Observable<String> ob = fromCompletableFuture(b);
        final Observable<String> total = oa.mergeWith(ob);
        final TestSubscriber<String> ts = new TestSubscriber<>();
        total.subscribe(ts);
        b.complete("b");
        a.complete("a");
        ts.assertReceivedOnNext(Arrays.asList("b", "a"));
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

    /**
     * Also see {@link rx.observers.SafeSubscriber}
     */
    @Test
    public void nonTerminalExceptionUnsubscribesAndReportsOnlyFirstError() {
        final TestSubscriber<Integer> ts = new TestSubscriber<>();
        final IllegalStateException e = new IllegalStateException("injected");
        final PublishSubject<Integer> s = PublishSubject.<Integer>create();
        final Observable<Integer> o = s.map(i -> {
            if (i == 0) {
                throw e;
            }
            return i;
        });
        o.subscribe(ts);
        s.onNext(1);
        ts.assertNoErrors();
        ts.assertReceivedOnNext(Collections.singletonList(1));
        s.onNext(0);
        assertEquals(Collections.singletonList(e), ts.getOnErrorEvents());
        s.onNext(2);
        ts.assertReceivedOnNext(Collections.singletonList(1));
        assertEquals(Collections.singletonList(e), ts.getOnErrorEvents());
    }

    @Test
    public void nonTerminalExceptionWithoutOnErrorThrowsAndUnsubscribes() {
        final IllegalStateException e = new IllegalStateException("injected");
        final PublishSubject<Integer> s = PublishSubject.<Integer>create();
        final Observable<Integer> o = s.map(i -> {
            if (i == 0) {
                throw e;
            }
            return i;
        });
        final ArrayList<Object> is = new ArrayList<>();
        o.subscribe(is::add);
        s.onNext(1);
        assertEquals(Collections.singletonList(1), is);
        try {
            s.onNext(0);
            fail("expected exception");
        } catch (OnErrorNotImplementedException oe) {
            //expected
        }
        assertEquals(Collections.singletonList(1), is);
        //Now we are unsubscribed, so onNext won't throw another error
        s.onNext(0);
    }

    @Test
    public void retryAllowsYouToSkipErrors() {
        final TestSubscriber<Integer> ts = new TestSubscriber<>();
        final IllegalStateException e = new IllegalStateException("injected");
        final PublishSubject<Integer> s = PublishSubject.<Integer>create();
        final Observable<Integer> o = s.map(i -> {
            if (i == 0) {
                throw e;
            }
            return i;
        }).retry();
        o.subscribe(ts);
        s.onNext(1);
        ts.assertNoErrors();
        ts.assertReceivedOnNext(Collections.singletonList(1));
        s.onNext(0);
        ts.assertNoErrors();
        s.onNext(2);
        ts.assertReceivedOnNext(Arrays.asList(1, 2));
        ts.assertNoErrors();
    }

    @Test
    public void exceptionsAndThreads() throws InterruptedException {
        final AtomicReference<String> threwErrorThreadName = new AtomicReference<>();
        final AtomicReference<String> gotNotificationThreadName = new AtomicReference<>();
        final CountDownLatch gotErrorNotification = new CountDownLatch(1);
        Observable.just(1)
                .map(i -> {
                    threwErrorThreadName.compareAndSet(null, Thread.currentThread().getName());
                    throw new IllegalStateException("injected");
                })
                .observeOn(schedulerOn("observe"))
                .subscribeOn(schedulerOn("subscribe"))
                .subscribe(i -> {
                }, e -> {
                    final String actualName = Thread.currentThread().getName();
                    assert gotNotificationThreadName.compareAndSet(null, actualName);
                    gotErrorNotification.countDown();
                });
        assert gotErrorNotification.await(1, SECONDS);
        assertEquals("subscribe", threwErrorThreadName.get());
        assertEquals("observe", gotNotificationThreadName.get());
    }

    private Scheduler schedulerOn(String threadName) {
        return Schedulers.from(Executors.newFixedThreadPool(1, new NamedThreadFactory(threadName)));
    }

    @Test
    public void exceptionsAndThreadsWithHot() throws InterruptedException {
        final AtomicReference<String> threwErrorThreadName = new AtomicReference<>();
        final AtomicReference<String> gotNotificationThreadName = new AtomicReference<>();
        final CountDownLatch gotErrorNotification = new CountDownLatch(1);
        final PublishSubject<Integer> ps = PublishSubject.create();
        final Subscription subscription = ps
                .map(i -> {
                    threwErrorThreadName.compareAndSet(null, Thread.currentThread().getName());
                    throw new IllegalStateException("injected");
                })
                .observeOn(schedulerOn("observe"))
                .subscribeOn(schedulerOn("subscribe"))
                .subscribe(i -> {
                    System.out.println("hello");
                }, e -> {
                    final String actualName = Thread.currentThread().getName();
                    assert gotNotificationThreadName.compareAndSet(null, actualName);
                    gotErrorNotification.countDown();
                });
        //Subscribing happens async, so we have to sleep for a while here
        Thread.sleep(100);
        //In the case of a cold observable, producing happens on the main thread at subscribe time.
        //But in the case of hot observable, subscribe merely wires everything up. Sending happens later.
        //We now deliver an item from the main thread:
        ps.onNext(1);
        assert gotErrorNotification.await(4, SECONDS);
        assertEquals("observe", gotNotificationThreadName.get());
        assertEquals("main", threwErrorThreadName.get());
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


    private <T> Observable.Operator<Long, T> index() {
        final AtomicLong l = new AtomicLong();
        return new Observable.Operator<Long, T>() {
            @Override
            public Subscriber<? super T> call(Subscriber<? super Long> o) {
                return new Subscriber<T>(o) {

                    @Override
                    public void onCompleted() {
                        o.onCompleted();
                    }

                    @Override
                    public void onError(Throwable e) {
                        o.onError(e);
                    }

                    @Override
                    public void onNext(T t) {
                        try {
                            o.onNext(l.incrementAndGet());
                        } catch (Throwable e) {
                            Exceptions.throwOrReport(e, this, t);
                        }
                    }

                };
            }
        };
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
