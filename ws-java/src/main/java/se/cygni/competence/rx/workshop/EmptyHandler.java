package se.cygni.competence.rx.workshop;

import com.google.common.collect.Lists;
import rx.Observable;
import rx.Observer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

public class EmptyHandler implements ConnectionHandler {

    private final DuckDuckGoClient duckDuckGoClient;

    public EmptyHandler(DuckDuckGoClient duckDuckGoClient) {
        this.duckDuckGoClient = duckDuckGoClient;
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
    @Override
    public void onConnectionOpen(
            Observable<String> goClicks,
            Observable<String> queryInputs,
            Observable<Boolean> instantSearchChanges,
            Observable<String> enterPresses,
            Observer<List<URI>> links,
            Observer<String> status) {
        //TODO: Implement me!

        status.onNext("Hej");
        //Observable<List<URI>> inputs = queryInputs.map(s ->  {
        Observable<Observable<List<URI>>> inputs1 = queryInputs.map(s -> {

            try {
                Thread.sleep(300);
                return search(s);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
        Observable<List<URI>> inputs = Observable.merge(inputs1);


        Observable<String> searchStatuses = queryInputs.map(s -> "Searching for " + s + "...");
        searchStatuses.subscribe(status);

        inputs.subscribe(list ->  {
                links.onNext(list);

        });

        inputs.map(s -> "Done").subscribe(status);
    }

    private Observable<List<URI>> search(String searchTerm) throws Exception {
        return duckDuckGoClient.searchRelated(searchTerm);
       /*
        List<URI> uris = Arrays.asList(new URI(searchTerm));
        Observable<List<URI>> o = Observable.just(uris);
        return o;
        */
    }
}
