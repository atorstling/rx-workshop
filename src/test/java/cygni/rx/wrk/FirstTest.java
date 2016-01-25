package cygni.rx.wrk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.AbstractHttpContentHolder;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import rx.Observable;
import rx.observables.ConnectableObservable;
import rx.observables.JavaFxObservable;
import rx.schedulers.JavaFxScheduler;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FirstTest extends javafx.application.Application {


    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Overwrk");
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/cygni.png")));
        final GridPane gp = new GridPane();
        gp.setAlignment(Pos.TOP_LEFT);
        gp.setHgap(10);
        gp.setVgap(10);
        gp.setPadding(new Insets(25));
        final Scene scene = new Scene(gp);
        primaryStage.setScene(scene);
        gp.add(new Label("Query"), 0, 0);
        final TextField searchField = new TextField();
        gp.add(searchField, 1, 0);
        final Button searchButton = new Button("Search");
        gp.add(searchButton, 2, 0);
        final ListView<Object> lw = new ListView<>();
        gp.add(lw, 0, 1, 3, 1);
        primaryStage.show();


        final Observable<ActionEvent> searchButtonClicks = JavaFxObservable.fromNodeEvents(searchButton, ActionEvent.ACTION);
        //searchButtonClicks.connect();
        final Observable<KeyEvent> searchKeyPresses = JavaFxObservable.fromNodeEvents(searchField, KeyEvent.KEY_TYPED);


        searchButtonClicks.subscribe(e -> {
            System.out.println("search click: " + e);
        });
        searchKeyPresses.subscribe(e -> {
            System.out.println("search key press: " + e);
        });

        final Observable<KeyEvent> stoppedTyping = searchKeyPresses.debounce(300, TimeUnit.MILLISECONDS);

        final Observable<String> searchTexts = JavaFxObservable.fromObservableValue(searchField.textProperty());
        searchTexts.subscribe(e -> {
            System.out.println("Search text changed:" + e);
        });

        final Observable<ActionEvent> triggerRequest = searchButtonClicks;
        final Observable<HttpClientResponse<ByteBuf>> requests = triggerRequest.flatMap(c -> {
            final String text = searchField.getText();
            final String url = String.format(
                    "http://api.duckduckgo.com/?q=%s&format=json&pretty=1", urlEncode(text)
            );
            //final String url = "http://durat.io";
            System.out.println("Running request:" + url + " on " + Thread.currentThread().getName());
            final Observable<HttpClientResponse<ByteBuf>> o = RxNetty.createHttpGet(url);
            System.out.println("Created request");
            return o;
        });

        //final Observable<ObservableHttpResponse> timeout = triggerRequest.flatMap(r -> Observable.<ObservableHttpResponse>error(new RuntimeException("timeout")).delay(2, TimeUnit.SECONDS));
        //Observable.amb(timeout, requests)
        /*
        requests.subscribe(r -> {
            System.out.println("got it");
        }, e -> e.printStackTrace(System.err));
          */
        //Bad idea: Think that the stream of click events is done just because one request is done.
        requests.subscribe(response -> {
            response.getContent().subscribe(bb -> {
                        final String s = bb.toString(Charsets.UTF_8);
                        final JsonNode j = toJson(s);
                        final JsonNode relatedTopics = j.get("RelatedTopics");

                        final ArrayList<JsonNode> relatedTopicsList = Lists.newArrayList(relatedTopics);
                        final List<String> links = relatedTopicsList.stream().filter(r -> r.has("FirstURL")).map(r -> r.get("FirstURL").textValue()).collect(Collectors.toList());
                        Platform.runLater(() -> {
                            //final ObservableList<Object> newItems = FXCollections.observableArrayList(links);
                            lw.getItems().clear();
                            lw.getItems().addAll(links);
                        });
                        System.out.println(links);
                    }, (e) -> {
                        e.printStackTrace(System.err);
                    }, () -> {
                        System.out.println("complete");
                    }
            );
        });


        /*
        requests.flatMap(ObservableHttpResponse::getContent)

                .map(String::new)
                .reduce((a, b) -> {
                    System.out.println(a + b);
                    return a + b;
                }).subscribe(body -> {
            System.out.println(body);
        });
          */
                /*
                .map(body -> {
                    System.out.println(body);
                    return body;
                })
                .subscribe( root -> {
            Platform.runLater(() -> lw.getItems().add("Blehbleh"));
        }, e -> {
            System.out.println("error");
            e.printStackTrace(System.err);
        }, () -> {
            System.out.println("complete");
        });
        */


        //1. Make button trigger load. Transform from click to text from field
        //   (make observable with latest text from
        //2. Merge with keypresses
    }

    private JsonNode toJson(String s) {
        try {
            return new ObjectMapper().readTree(s);
        } catch (IOException e1) {
            throw new RuntimeException(e1);
        }
    }

    private String urlEncode(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        Application.launch(args);
    }
}
