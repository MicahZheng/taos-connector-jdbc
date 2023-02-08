package com.taosdata.jdbc.ws;

import com.taosdata.jdbc.ws.entity.Action;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Unfinished execution
 */
public class InFlightRequest {
    private final int timeout;
    private final Semaphore semaphore;
    private final Map<String, ConcurrentHashMap<Long, FutureResponse>> futureMap = new HashMap<>();

    public InFlightRequest(int timeout, int concurrentNum) {
        this.timeout = timeout;
        this.semaphore = new Semaphore(concurrentNum);
        for (Action value : Action.values()) {
            String action = value.getAction();
            if (Action.CONN.getAction().equals(action))
                continue;
            futureMap.put(action, new ConcurrentHashMap<>());
        }
    }

    public void put(FutureResponse rf) throws InterruptedException, TimeoutException {
        if (semaphore.tryAcquire(timeout, TimeUnit.MILLISECONDS)) {
            futureMap.get(rf.getAction()).put(rf.getId(), rf);
        } else {
            throw new TimeoutException("websocket connection reached the max number of concurrent requests");
        }
    }

    public FutureResponse remove(String action, Long id) {
        FutureResponse future = futureMap.get(action).remove(id);
        if (null != future) {
            semaphore.release();
        }
        return future;
    }

    public void close() {
        futureMap.keySet().stream()
                .flatMap(k -> {
                    ConcurrentHashMap<Long, FutureResponse> futures = futureMap.get(k);
                    futureMap.put(k, new ConcurrentHashMap<>());
                    return futures.values().stream();
                })
                .parallel().map(FutureResponse::getFuture)
                .forEach(e -> {
                    e.completeExceptionally(new Exception("close all inFlightRequest"));
                });
    }
}
