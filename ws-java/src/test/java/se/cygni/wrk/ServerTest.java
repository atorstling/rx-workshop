package se.cygni.wrk;

import jdk.nashorn.internal.objects.NativeJSON;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * Created by alext on 2016-01-30.
 */
public class ServerTest {

    @Test
    public void doSearch() throws UnknownHostException, URISyntaxException, InterruptedException {
        final Server s = new Server(0, new Handler());
        s.start();
        Thread.sleep(100);
        final int port = s.getPort();
        final URI uri = new URI("http://localhost:" + port);
        System.out.println(uri);
        final CountDownLatch answersProcessed = new CountDownLatch(4);
        final WebSocketClient client = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {

            }

            @Override
            public void onMessage(String s) {
                if (answersProcessed.getCount() == 4) {
                    //welcome message
                    assertEquals(s, "{\"type\":\"backend.status\",\"status\":\"ready\"}");
                } else if (answersProcessed.getCount() == 3) {
                    assertEquals("{\"type\":\"backend.status\",\"status\":\"searching\"}", s);
                } else if (answersProcessed.getCount() == 2) {
                    assertEquals("{\"type\":\"backend.status\",\"status\":\"search done\"}", s);
                } else if (answersProcessed.getCount() == 1) {
                    assertEquals("{\"type\":\"new.links\",\"links\":[\"https://duckduckgo.com/Software_testing\",\"https://duckduckgo.com/statistical_hypothesis_testing\",\"https://duckduckgo.com/Test_(wrestler)\",\"https://duckduckgo.com/Test_(assessment)\"]}", s);
                } else {
                    assert false;
                }
                answersProcessed.countDown();
            }

            @Override
            public void onClose(int i, String s, boolean b) {

            }

            @Override
            public void onError(Exception e) {
                e.printStackTrace();
            }
        };
        assert client.connectBlocking();
        client.send("{\n" +
                "  \"type\": \"query.input\",\n" +
                "  \"text\": \"test\"\n" +
                "}");
        client.send("{\n" +
                "  \"type\": \"go.click\"\n" +
                "}");
        assert answersProcessed.await(3, TimeUnit.SECONDS);
    }
}
