package se.cygni.wrk;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.AbstractHttpContentHolder;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.Observable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DuckDuckGo {
    public Observable<List<String>> searchRelated(String text) {
        final String url = String.format(
                "http://api.duckduckgo.com/?q=%s&format=json&pretty=1", Util.urlEncode(text)
        );
        System.out.println("Running request:" + url + " on " + Thread.currentThread().getName());
        final Observable<HttpClientResponse<ByteBuf>> o = RxNetty.createHttpGet(url);
        System.out.println("Created request");
        return o.flatMap(AbstractHttpContentHolder::getContent).flatMap(bb -> Observable.just(parseLinks(bb)));
    }

    private List<String> parseLinks(ByteBuf bb) {
        final String s = bb.toString(Charsets.UTF_8);
        final JsonNode j = Util.toJson(s);
        final JsonNode relatedTopics = j.get("RelatedTopics");

        final ArrayList<JsonNode> relatedTopicsList = Lists.newArrayList(relatedTopics);
        return relatedTopicsList.stream().filter(r -> r.has("FirstURL")).map(r -> r.get("FirstURL").textValue()).collect(Collectors.toList());
    }
}
