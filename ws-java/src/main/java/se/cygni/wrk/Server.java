package se.cygni.wrk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import rx.subjects.PublishSubject;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class Server extends WebSocketServer {

    class ConnectionState {
        private final PublishSubject<String> goClicks;
        private final PublishSubject<String> queryInputs;
        private final PublishSubject<List<String>> links;
        private final PublishSubject<Boolean> instantSearchChanges;
        private final PublishSubject<String> enterPresses;
        private final PublishSubject<String> status;

        public ConnectionState() {
            goClicks = PublishSubject.create();
            queryInputs = PublishSubject.create();
            links = PublishSubject.create();
            status = PublishSubject.create();
            instantSearchChanges = PublishSubject.create();
            enterPresses = PublishSubject.create();
        }
    }

    private final Map<WebSocket, ConnectionState> stateBySocket;
    private final Handler handler;


    public Server(int port, final Handler handler) throws UnknownHostException {
        super(new InetSocketAddress(port));
        this.handler = handler;
        stateBySocket = new ConcurrentHashMap<>();
    }

    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
        System.out.println("connect from " + Util.getAddress(webSocket));
        ConnectionState state = new ConnectionState();
        state.links.subscribe(links -> {
            send(webSocket, Util.createLinksMessage(links));
        });
        state.status.subscribe(status -> {
            send(webSocket, Util.createStatusMessage(status));
        });
        stateBySocket.put(webSocket, state);
        handler.onConnectionOpen(state.goClicks, state.queryInputs, state.instantSearchChanges, state.enterPresses, state.links, state.status);
    }

    private void send(WebSocket webSocket, ObjectNode jsonMessage) {
        String jsonString = Util.toString(jsonMessage);
        System.out.println("sending to " + Util.getAddress(webSocket) + " :" + jsonString);
        webSocket.send(jsonString);
    }

    @Override
    public void onClose(WebSocket webSocket, int i, String s, boolean b) {
        System.out.println("close from " + Util.getAddress(webSocket));
        stateBySocket.remove(webSocket);
    }

    @Override
    public void onMessage(WebSocket webSocket, String s) {
        System.out.println("message from " + Util.getAddress(webSocket) + ": " + s);
        ConnectionState state = stateBySocket.get(webSocket);
        JsonNode message = Util.toJson(s);
        String type = message.get("type").textValue();
        switch (type) {
            case "go.click":
                state.goClicks.onNext("");
                break;
            case "query.input":
                state.queryInputs.onNext(message.get("text").textValue());
                break;
            case "instant.enable":
                state.instantSearchChanges.onNext(message.get("value").asBoolean());
                break;
            case "enter.press":
                state.enterPresses.onNext("");
                break;
            default:
                System.out.println("WARN: Unknown message type '" + type + "': '" + s + "'");
        }
    }

    @Override
    public void onError(WebSocket webSocket, Exception e) {
        if (webSocket != null) {
            System.err.println("error:" + Util.getAddress(webSocket));
        }
        e.printStackTrace(System.err);
    }

    public static void main(String[] args) throws UnknownHostException, InterruptedException {
        WebSocketImpl.DEBUG = false;
        final Server s = new Server(4739, new Handler());
        s.start();
        System.out.println("Server started");
        final CountDownLatch shuttingDown = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(shuttingDown::countDown));
        shuttingDown.await();
    }
}
