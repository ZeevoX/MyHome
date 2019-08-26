package net.zeevox.myhome.websocket;

import android.util.Log;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Use this lightly-modified extension of {@link WebSocketListener} to provide an easy way for checking whether the {@link WebSocket} is currently connected
 */
public class CustomWebSocketListener extends WebSocketListener {
    public boolean connected = false;

    public CustomWebSocketListener() {
        super();
    }
    /**
     * Invoked when a web socket has been accepted by the remote peer and may begin transmitting
     * messages.
     *
     * @param webSocket The {@link WebSocket} that this call refers to
     * @param response {@link Response}
     */
    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        super.onOpen(webSocket, response);
        connected = true;
    }

    /**
     * Invoked when both peers have indicated that no more messages will be transmitted and the
     * connection has been successfully released. No further calls to this listener will be made.
     *
     * @param webSocket The {@link WebSocket} that this call refers to
     * @param code An integer with a unique {@link WebSocket} closing code
     * @param reason A reason -- possibly null -- as to why the {@link WebSocket} was closed
     */
    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        super.onClosed(webSocket, code, reason);
        Log.w(getClass().getSimpleName(), "WebSocket closed, reason: " + reason);
        connected = false;
    }

    /**
     * Invoked when a web socket has been closed due to an error reading from or writing to the
     * network. Both outgoing and incoming messages may have been lost. No further calls to this
     * listener will be made.
     *
     * @param webSocket The {@link WebSocket} that this call refers to
     * @param t {@link Throwable} with the error itself
     * @param response {@link Response}
     */
    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        super.onFailure(webSocket, t, response);
        t.printStackTrace();
        connected = false;
    }
}
