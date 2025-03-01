package com.taosdata.jdbc.ws;

import com.taosdata.jdbc.TSDBError;
import com.taosdata.jdbc.TSDBErrorNumbers;
import com.taosdata.jdbc.common.SerializeBlock;
import com.taosdata.jdbc.enums.WSFunction;
import com.taosdata.jdbc.rs.ConnectionParam;
import com.taosdata.jdbc.utils.CompletableFutureTimeout;
import com.taosdata.jdbc.ws.entity.Request;
import com.taosdata.jdbc.ws.entity.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static com.taosdata.jdbc.TSDBErrorNumbers.ERROR_CONNECTION_TIMEOUT;

/**
 * send message
 */
public class Transport implements AutoCloseable {

    public static final int DEFAULT_MESSAGE_WAIT_TIMEOUT = 10_000;

    private final WSClient client;
    private final InFlightRequest inFlightRequest;
    private long timeout;
    private boolean closed = false;

    public Transport(WSFunction function, ConnectionParam param, InFlightRequest inFlightRequest) throws SQLException {
        this.client = WSClient.getInstance(param, function);
        this.inFlightRequest = inFlightRequest;
        this.timeout = param.getRequestTimeout();
    }

    public void setTextMessageHandler(Consumer<String> textMessageHandler) {
        client.setTextMessageHandler(textMessageHandler);
    }

    public void setBinaryMessageHandler(Consumer<ByteBuffer> binaryMessageHandler) {
        client.setBinaryMessageHandler(binaryMessageHandler);
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    @SuppressWarnings("all")
    public Response send(Request request) throws SQLException {

        Response response = null;
        CompletableFuture<Response> completableFuture = new CompletableFuture<>();
        try {
            inFlightRequest.put(new FutureResponse(request.getAction(), request.id(), completableFuture));
            client.send(request.toString());
        } catch (InterruptedException | TimeoutException e) {
            throw new SQLException(e);
        }
        CompletableFuture<Response> responseFuture = CompletableFutureTimeout.orTimeout(completableFuture, timeout, TimeUnit.MILLISECONDS);
        try {
            response = responseFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            inFlightRequest.remove(request.getAction(), request.id());
            throw TSDBError.createSQLException(TSDBErrorNumbers.ERROR_RESTFul_Client_IOException, e.getMessage());
        }
        return response;
    }

    public Response send(String action, long reqId, long stmtId, long type, byte[] rawData) throws SQLException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            buffer.write(SerializeBlock.longToBytes(reqId));
            buffer.write(SerializeBlock.longToBytes(stmtId));
            buffer.write(SerializeBlock.longToBytes(type));
            buffer.write(rawData);
        } catch (IOException e) {
            throw new SQLException("data serialize error!", e);
        }

        Response response = null;
        CompletableFuture<Response> completableFuture = new CompletableFuture<>();
        try {
            inFlightRequest.put(new FutureResponse(action, reqId, completableFuture));
            client.send(buffer.toByteArray());
        } catch (InterruptedException | TimeoutException e) {
            throw new SQLException(e);
        }
        CompletableFuture<Response> responseFuture = CompletableFutureTimeout.orTimeout(completableFuture, timeout, TimeUnit.MILLISECONDS);
        try {
            response = responseFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            inFlightRequest.remove(action, reqId);
            throw TSDBError.createSQLException(TSDBErrorNumbers.ERROR_RESTFul_Client_IOException, e.getMessage());
        }
        return response;
    }

    public void sendWithoutRep(Request request) {
        client.send(request.toString());
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
        inFlightRequest.close();
        client.close();
    }

    public static void checkConnection(Transport transport, int connectTimeout) throws SQLException {
        try {
            if (!transport.client.connectBlocking(connectTimeout, TimeUnit.MILLISECONDS)) {
                transport.close();
                throw TSDBError.createSQLException(ERROR_CONNECTION_TIMEOUT,
                        "can't create connection with server within: " + connectTimeout + " milliseconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            transport.close();
            throw new SQLException("create websocket connection has been Interrupted ", e);
        }
    }

    public void shutdown() {
        closed = true;
        if (inFlightRequest.hasInFlightRequest()) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    TimeUnit.MILLISECONDS.sleep(timeout);
                } catch (InterruptedException e) {
                    // ignore
                }
            });
            future.thenRun(this::close);
        } else {
            close();
        }
    }
}
