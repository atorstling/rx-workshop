package se.cygni.wrk;

import rx.Observable;
import rx.Observer;
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
            Observer<List<String>> linksObserver, PublishSubject<String> status) {
        status.onNext("ready");
        linksObserver.onNext(Collections.singletonList("http://java.sun.com"));
        Observable<String> textOnGoClick = queryInputs.sample(goClicks);
        final Observable<String> textOnEnterPress = queryInputs.sample(enterPresses);
        Observable<String> textOnTypeWhenInstantEnabled = Observable.combineLatest(queryInputs,
                instantSearchChanges, InstantType::new).filter(ie -> ie.instantEnabled)
                .map(ie -> ie.text).debounce(1, TimeUnit.SECONDS);
        Observable<String> textOnAction = textOnGoClick.mergeWith(textOnEnterPress).mergeWith(textOnTypeWhenInstantEnabled);
        final Observable<List<String>> requests = textOnAction.flatMap(duckDuckGo::searchRelated);
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
