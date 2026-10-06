package com.djt.jukeanator_engine.domain.location.client;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

/**
 * A {@link StandardWebSocketClient} whose sessions accept sends from several threads at once.
 *
 * <p>A slave writes to its one {@code /ws-slave} connection from several threads: command
 * replies on the STOMP client's own thread, playback and queue events from the player's thread,
 * location-info pushes from the UI, and STOMP heartbeats from the task scheduler. A plain
 * WebSocket session allows only one write at a time -- a second, overlapping write fails
 * ({@code TEXT_PARTIAL_WRITING}), losing that event or command reply. Every session this client
 * opens is wrapped in a {@link ConcurrentWebSocketSessionDecorator}, so concurrent writes are
 * queued and sent one after another instead.
 */
public class SerializedSendWebSocketClient extends StandardWebSocketClient {

  /** How long one send may block others before the connection is treated as stuck. */
  static final int SEND_TIME_LIMIT_MILLIS = 10_000;

  /** How much may queue up behind a slow send before the connection is treated as stuck. */
  static final int BUFFER_SIZE_LIMIT_BYTES = 1024 * 1024;

  @Override
  protected CompletableFuture<WebSocketSession> executeInternal(WebSocketHandler webSocketHandler,
      HttpHeaders headers, URI uri, List<String> subProtocols, List<WebSocketExtension> extensions,
      Map<String, Object> attributes) {

    SerializingHandler handler = new SerializingHandler(webSocketHandler);
    return super.executeInternal(handler, headers, uri, subProtocols, extensions, attributes)
        .thenApply(handler::serialized);
  }

  /** Hands the wrapped handler (the STOMP client) the serialized session in every callback. */
  private static final class SerializingHandler extends WebSocketHandlerDecorator {

    private volatile WebSocketSession serialized;

    SerializingHandler(WebSocketHandler delegate) {
      super(delegate);
    }

    WebSocketSession serialized(WebSocketSession session) {
      WebSocketSession current = serialized;
      if (current == null) {
        synchronized (this) {
          current = serialized;
          if (current == null) {
            current = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MILLIS,
                BUFFER_SIZE_LIMIT_BYTES);
            serialized = current;
          }
        }
      }
      return current;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
      super.afterConnectionEstablished(serialized(session));
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message)
        throws Exception {
      super.handleMessage(serialized(session), message);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception)
        throws Exception {
      super.handleTransportError(serialized(session), exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus)
        throws Exception {
      super.afterConnectionClosed(serialized(session), closeStatus);
    }
  }
}
