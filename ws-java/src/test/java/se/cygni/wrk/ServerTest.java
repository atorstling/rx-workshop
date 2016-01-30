package se.cygni.wrk;

import jdk.nashorn.internal.objects.NativeJSON;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * Created by alext on 2016-01-30.
 */
public class ServerTest {

    @Test
    public void doSearch() throws UnknownHostException, URISyntaxException, InterruptedException {
        final Server s = new Server(0, new Handler());
        s.start();
        Thread.sleep(25);
        final int port = s.getPort();
        final URI uri = new URI("http://localhost:" + port);
        System.out.println(uri);
        final WebSocketClient client = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {

            }

            @Override
            public void onMessage(String s) {

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
        client.send(Util.toString(Util.createStatusMessage("hello")));

    }
}
