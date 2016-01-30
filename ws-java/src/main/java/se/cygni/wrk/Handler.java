package se.cygni.wrk;

import rx.Observable;
import rx.Observer;
import rx.functions.Action1;
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
            Observer<List<String>> linksObserver,
            Observer<String> statusObserver) {
        statusObserver.onNext("ready");
        Observable<String> textOnGoClick = queryInputs.sample(goClicks);
        final Observable<String> textOnEnterPress = queryInputs.sample(enterPresses);
        final Observable<String> textOnTypeWhenInstantEnabled = Observable.combineLatest(queryInputs,
                instantSearchChanges, InstantType::new).filter(ie -> ie.instantEnabled)
                .map(ie -> ie.text);
        textOnTypeWhenInstantEnabled.map(o -> "listening").subscribe(statusObserver);
        Observable<String> debouncedTextOnTypeWhenInstantEnabled = textOnTypeWhenInstantEnabled.debounce(1, TimeUnit.SECONDS);
        Observable<String> textOnAction = textOnGoClick.mergeWith(textOnEnterPress).mergeWith(debouncedTextOnTypeWhenInstantEnabled);
        textOnAction.map(o -> "searching").subscribe(statusObserver);
        textOnAction.subscribe(s -> {
            System.out.println("About to search");
        });
        final Observable<List<String>> requests = textOnAction.flatMap((text) -> {
            System.out.println("Searching for " + text);
            return duckDuckGo.searchRelated(text);
        });
        requests.map(o -> "search done").subscribe(statusObserver);
        requests.subscribe(linksObserver);
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
