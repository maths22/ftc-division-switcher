package com.maths22.ftc;

import com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver;
import io.javalin.config.JavalinConfig;
import io.javalin.plugin.Plugin;
import org.eclipse.jetty.server.ServerConnector;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class JavalinVerificationCodeReciever extends Plugin<Void> implements VerificationCodeReceiver {
    private final Map<UUID, CompletableFuture<String>> futures;
    private final ThreadLocal<UUID> threadFutureId = new ThreadLocal<>();
    private int port;

    public JavalinVerificationCodeReciever() {
        futures = new ConcurrentHashMap<>();
    }

    @Override
    public void onStart(@NotNull JavalinConfig config) {
        config.router.mount(r -> r.get("/oauth/verify/{id}", ctx -> {
            UUID id = UUID.fromString(ctx.pathParam("id"));
            CompletableFuture<String> future = futures.get(id);
            if(future == null) {
                ctx.status(404);
                return;
            }
            future.complete(ctx.queryParam("code"));
        }));
        config.events(evt -> evt.serverStarted(() -> port = ((ServerConnector) config.pvt.jetty.server.getConnectors()[0]).getLocalPort()));
    }

    @Override
    public String getRedirectUri() {
        CompletableFuture<String> future = new CompletableFuture<>();
        UUID id = UUID.randomUUID();
        futures.put(id, future);
        threadFutureId.set(id);
        return "http://127.0.0.1:" + port + "/oauth/verify/" + id;
    }

    @Override
    public String waitForCode() {
        UUID id = threadFutureId.get();
        if(id == null) {
            return null;
        }

        CompletableFuture<String> future = futures.get(id);
        if(future == null) {
            return null;
        }
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        UUID id = threadFutureId.get();
        if(id == null) {
            return;
        }

        CompletableFuture<String> future = futures.remove(id);
        if(future == null) {
            return;
        }
        future.cancel(true);
    }
}
