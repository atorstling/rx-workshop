package se.cygni.wrk;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.Observable;
import rx.functions.Action2;
import rx.functions.Func0;
import rx.functions.Func2;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DuckDuckGo {
    public Observable<List<String>> searchRelated(String text) {
        final String relativeUrl = String.format("/?q=%s&format=json&pretty=1", Util.urlEncode(text));
        //final String url = String.format(
        //        "http://api.duckduckgo.com/?q=%s&format=json&pretty=1", Util.urlEncode(text)
        //);
        //final String url = "http://bleargh.doesnotexist";
        System.out.println("Running request:" + relativeUrl + " on " + Thread.currentThread().getName());
        final HttpClientRequest<ByteBuf, ByteBuf> req = HttpClient.newClient("api.duckduckgo.com", 80)
                .createGet(relativeUrl);
        System.out.println("Created request");
        return req
                .flatMap(HttpClientResponse::getContent)
                .reduce("", (s, byteBuf) -> s+ byteBuf.toString(Charsets.UTF_8))
                .flatMap(all -> Observable.just(parseLinks(all)));
    }

    private List<String> parseLinks(String s) {
        final JsonNode j = Util.toJson(s);
        final JsonNode relatedTopics = j.get("RelatedTopics");

        final ArrayList<JsonNode> relatedTopicsList = Lists.newArrayList(relatedTopics);
        return relatedTopicsList.stream().filter(r -> r.has("FirstURL")).map(r -> r.get("FirstURL").textValue()).collect(Collectors.toList());
    }
}
