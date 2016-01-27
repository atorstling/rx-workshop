package se.cygni.wrk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.AbstractHttpContentHolder;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.Observable;
import rx.Observer;
import rx.functions.FuncN;
import rx.subjects.PublishSubject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Handler {
    private final PublishSubject<String> goClicks;
    private final PublishSubject<String> queryInputs;
    private final JsonNodeFactory nf;
    private final PublishSubject<JsonNode> messages;
    private final PublishSubject<Boolean> instantSearchChanges;
    private final PublishSubject<String> enterPresses;

    public Handler(PublishSubject<JsonNode> messages) {
        nf = JsonNodeFactory.instance;
        this.messages = messages;
        goClicks = PublishSubject.create();
        queryInputs = PublishSubject.create();
        instantSearchChanges = PublishSubject.create();
        enterPresses = PublishSubject.create();
        messages.onNext(createLinksMessage(Collections.singletonList("http://java.sun.com")));
        Observable<String> textOnGoClick = queryInputs.sample(goClicks);
        Observable<String> textOnTypeWhenInstantEnabled = Observable.combineLatest(queryInputs,
                instantSearchChanges, InstantType::new).filter(ie -> ie.instantEnabled)
                .map(ie -> ie.text).debounce(1, TimeUnit.SECONDS);
        Observable<String> shouldRunRequest = textOnGoClick.mergeWith(textOnTypeWhenInstantEnabled);
        final Observable<HttpClientResponse<ByteBuf>> requests = shouldRunRequest.flatMap(text -> {
            final String url = String.format(
                    "http://api.duckduckgo.com/?q=%s&format=json&pretty=1", Util.urlEncode(text)
            );
            System.out.println("Running request:" + url + " on " + Thread.currentThread().getName());
            final Observable<HttpClientResponse<ByteBuf>> o = RxNetty.createHttpGet(url);
            System.out.println("Created request");
            return o;
        });
        requests.flatMap(AbstractHttpContentHolder::getContent).map(bb -> {
                    final List<String> links = parseLinks(bb);
                    System.out.println(links);
                    return createLinksMessage(links);
                }
            ).subscribe(messages);
    }

    private List<String> parseLinks(ByteBuf bb) {
        final String s = bb.toString(Charsets.UTF_8);
        final JsonNode j = Util.toJson(s);
        final JsonNode relatedTopics = j.get("RelatedTopics");

        final ArrayList<JsonNode> relatedTopicsList = Lists.newArrayList(relatedTopics);
        return (List<String>) relatedTopicsList.stream().filter(r -> r.has("FirstURL")).map(r -> r.get("FirstURL").textValue()).collect(Collectors.toList());
    }

    private ObjectNode createLinksMessage(List<String> links) {
        ObjectNode msg = nf.objectNode();
        msg.put("type", "new.links");
        ArrayNode jsonLinks = nf.arrayNode();
        jsonLinks.addAll(links.stream().map(nf::textNode).collect(Collectors.toList()));
        msg.set("links", jsonLinks);
        return msg;
    }

    public PublishSubject<String> getGoClicks() {
        return goClicks;
    }

    public PublishSubject<String> getQueryInputs() {
        return queryInputs;
    }

    public Observer<Boolean> getInstantSearchChanges() {
        return instantSearchChanges;
    }

    public Observer<String> getEnterPresses() {
        return enterPresses;
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
