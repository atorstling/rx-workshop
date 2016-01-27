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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Created by alext on 2016-01-27.
 */
public class Handler2 {

    public void onConnectionOpen(Observable<String> goClicks, Observable<String> queryInputs, Observable<Boolean> instantSearchChanges, Observable<String> enterPresses, Observer<JsonNode> messages) {
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
        return relatedTopicsList.stream().filter(r -> r.has("FirstURL")).map(r -> r.get("FirstURL").textValue()).collect(Collectors.toList());
    }

    private ObjectNode createLinksMessage(List<String> links) {
        final JsonNodeFactory nf = JsonNodeFactory.instance;
        ObjectNode msg = nf.objectNode();
        msg.put("type", "new.links");
        ArrayNode jsonLinks = nf.arrayNode();
        jsonLinks.addAll(links.stream().map(nf::textNode).collect(Collectors.toList()));
        msg.set("links", jsonLinks);
        return msg;
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
