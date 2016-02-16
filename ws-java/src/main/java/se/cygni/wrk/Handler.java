package se.cygni.wrk;

import rx.Observable;
import rx.Observer;
import rx.functions.Action1;
import rx.observables.ConnectableObservable;
import rx.subjects.PublishSubject;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Handler {

    private final DuckDuckGo duckDuckGo;

    public Handler() {
        duckDuckGo = new DuckDuckGo();
    }

    /**
     * Notifies the handler that a new connection was opened.
     * <p>
     * You are provided with a number of input {@link Observable}s as
     * well as a number of output {@link Observer}s.
     * The different {@link Observable}s represent different <b>input</b> UI controls and will emit events as these change state.
     * The different {@link Observer}s represent <b>output</b> controls. Pushing to these will change output controls in the UI.
     * <p>
     * Your task in this handler is to implement the business logic for the UI by wiring the input
     * {@link Observable}s through a Rx pipeline to to the output {@link Observer}s.
     * <p>
     * The wiring which you perform in this method will have effect throughout the lifetime of the
     * connection. The {@link Server} keeps track of the {@link Observable}s and {@link Observer}s
     * for any given connection, and since they will become wired together through your pipeline,
     * the pipeline will persist.
     * @param goClicks emits an empty string whenever the "Go" button in the GUI is clicked.
     * @param queryInputs emits query phrases from the search field. Will emit the complete query phrase whenever
     *                    it is changed in the GUI.
     * @param instantSearchChanges emits a boolean representing the checkbox "instant search" state whenever it changes.
     * @param enterPresses emits an empty string whenever the enter key is pressed in the search field.
     * @param links pushing a list of URL strings to this observer will replace the result list with the given links.
     * @param status pushing a string to this observer will update the "backend status" field.
     */
    public void onConnectionOpen(
            Observable<String> goClicks,
            Observable<String> queryInputs,
            Observable<Boolean> instantSearchChanges,
            Observable<String> enterPresses,
            Observer<List<String>> links,
            Observer<String> status) {
        Observable<String> textOnGoClick = queryInputs.sample(goClicks);
        final Observable<String> textOnEnterPress = queryInputs.sample(enterPresses);
        final Observable<String> textOnTypeWhenInstantEnabled = Observable.combineLatest(queryInputs,
                instantSearchChanges, InstantType::new).filter(ie -> ie.instantEnabled)
                .map(ie -> ie.text);
        textOnTypeWhenInstantEnabled.map(o -> "listening").subscribe(status);
        Observable<String> debouncedTextOnTypeWhenInstantEnabled = textOnTypeWhenInstantEnabled.debounce(1, TimeUnit.SECONDS);
        Observable<String> textOnAction = textOnGoClick.mergeWith(textOnEnterPress).mergeWith(debouncedTextOnTypeWhenInstantEnabled);
        textOnAction.map(o -> "searching").subscribe(status);
        textOnAction.subscribe(s -> {
            System.out.println("About to search");
        });
        final Observable<List<String>> requests = textOnAction.flatMap(duckDuckGo::searchRelated);
        //Don't want two subscribers on requests since it triggers double requests being issued
        final ConnectableObservable<List<String>> doneRequests = requests.publish();
        doneRequests.zipWith(textOnAction, (o, text) -> "search for '" + text + "' done").subscribe(status);
        doneRequests.subscribe(links);
        doneRequests.connect();
        status.onNext("ready");
    }

    private class InstantType {
        private final String text;
        private final boolean instantEnabled;

        public InstantType(String text, boolean instantEnabled) {
            this.text = text;
            this.instantEnabled = instantEnabled;
        }
    }
}
