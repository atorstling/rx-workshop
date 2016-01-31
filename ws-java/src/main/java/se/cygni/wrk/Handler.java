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

    public void onConnectionOpen(
            Observable<String> goClicks,
            Observable<String> queryInputs,
            Observable<Boolean> instantSearchChanges,
            Observable<String> enterPresses,
            Observer<List<String>> links,
            Observer<String> status) {
        status.onNext("ready");
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
        doneRequests.map(o -> "search done").subscribe(status);
        doneRequests.subscribe(links);
        doneRequests.connect();
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
