package net.zeevox.myhome.websocket;

import android.util.Log;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;

public class WebSocketUtils {

    private WebSocket webSocket;
    private CustomWebSocketListener webSocketListener;

    public boolean connectWebSocket(String url, CustomWebSocketListener webSocketListener) {
        if (url == null) return false;
        this.webSocketListener = webSocketListener;
        Request request = new Request.Builder().url(url).build();
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(1000, TimeUnit.MILLISECONDS).build();
        webSocket = okHttpClient.newWebSocket(request, webSocketListener);
        Log.d(getClass().getSimpleName(), "WebSocket created");
        return true;
    }

    public WebSocket getWebSocket() {
        return webSocket;
    }

    public void clearWebSocket() {
        webSocket = null;
    }

    public boolean isWebSocketConnected() {
        if (webSocketListener == null) return false;
        return webSocketListener.connected;
    }
}
