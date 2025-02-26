package com.maths22.ftc.scoring.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.maths22.ftc.scoring.client.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class FtcScoringClient {
    private static final Logger LOG = LoggerFactory.getLogger(FtcScoringClient.class);
    private static final CookieHandler cookieHandler = new CookieManager();
    private static final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(cookieHandler)
            .build();
    private static final Gson gson;
    static {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Alliance.class, (JsonDeserializer<Alliance>) (json, typeOfT, context) -> {
            JsonObject object = json.getAsJsonObject();
            boolean isQuals = object.has("team1");
            if(isQuals) {
                return context.deserialize(json, new TypeToken<QualsAlliance>(){}.getType());
            } else {
                return context.deserialize(json, new TypeToken<ElimsAlliance>(){}.getType());
            }
        });
        gson = builder.create();
    }

    private Consumer<MatchUpdate> onMatchUpdate;
    private final Event event;
    private WebSocket socketListener;
    private boolean loggedIn;
    private final String basePath;
    private final Map<UUID, CompletableFuture<Long>> timesyncRequests = new HashMap<>();

    public FtcScoringClient(String basePath, String eventCode) {
        this.basePath = basePath;
        this.loggedIn = false;
        this.event = gson.fromJson(get(basePath, "api/v1/events/" + eventCode + "/"), Event.class);
        testLogin();
    }

    public void startSocketListener() {
        stopSocketListener();
        try {
            socketListener = client.newWebSocketBuilder().buildAsync(URI.create("ws://" + basePath + "/api/v2/stream/?code=" + event.eventCode()), new WebSocket.Listener() {
                private StringBuilder buffer = new StringBuilder();
                @Override
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    buffer.append(data);
                    if(last) {
                        String message = buffer.toString();
                        buffer = new StringBuilder();
                        if(message.equals("pong")) {
                            // Ignore pings
                        } else if (message.startsWith("TIMESYNC:")) {
                            try {
                                Map<String, Object> res = gson.fromJson(
                                        message.substring("TIMESYNC:".length()),
                                        new TypeToken<Map<String, Object>>() {}.getType()
                                );
                                UUID id = UUID.fromString((String) res.get("id"));
                                long time = ((Number) res.get("result")).longValue();
                                CompletableFuture<Long> future = timesyncRequests.remove(id);
                                if(future != null) {
                                    future.complete(time);
                                }
                            } catch (Exception e) {
                                LOG.error("Error processing timesync response", e);
                            }
                        } else {
                            try {
                                MatchUpdate update = gson.fromJson(message, MatchUpdate.class);
                                if(onMatchUpdate != null) {
                                    onMatchUpdate.accept(update);
                                }
                            } catch (Exception ex) {
                                LOG.error("Error processing match update", ex);
                            }
                        }
                    }
                    return WebSocket.Listener.super.onText(webSocket, data, last);
                }
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void stopSocketListener() {
        if(socketListener != null) {
            socketListener.abort();
            socketListener = null;
        }
    }

    public Event getEvent() {
        return event;
    }

    public static List<String> getEvents(String basePath) {
        LOG.debug("Loading events from {}", basePath);
        return gson.fromJson(get(basePath, "api/v1/events/"), EventList.class).eventCodes();
    }

    public boolean login(String username, String password) {
        String params = Map.of(
                        "username", username,
                        "password", password == null ? "" : password,
                        "submit", "Login",
                        "client_name", "FormClient")
                .entrySet()
                .stream()
                .map(entry -> String.join("=",
                        URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8),
                        URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                ).collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + basePath + "/callback/"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(params))
                .build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        if(response.body().contains("alert-danger")) {
            LOG.warn("Login failed (invalid username/password)");
            return false;
        }

        return testLogin();
    }

    private boolean testLogin() {
        try {
            get(basePath, "/event/" + event.eventCode() + "/control/schedule/");
        } catch (Exception ex) {
            LOG.warn("Login failed", ex);
            return false;
        }
        if(!loggedIn) {
            loggedIn = true;
            LOG.debug("Login successful");
        }
        return true;
    }

    private static String get(String basePath, String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + basePath + "/" +  path))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());

            if(resp.statusCode() == 200) {
                return resp.body();
            }
        } catch (IOException | InterruptedException e) {
            LOG.warn("Request failed", e);
        }
        return null;
    }

    private static String post(String basePath, String path, String payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + basePath + "/" +  path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());

            if(resp.statusCode() == 200) {
                return resp.body();
            }
        } catch (IOException | InterruptedException e) {
            LOG.warn("Request failed", e);
        }
        return null;
    }

    public void showSponsors() {
        if(!loggedIn) {
            return;
        }
        LOG.debug("Showing sponsors for {}", event.eventCode());
        post(basePath, "event/" + event.eventCode() + "/control/sponsors/", "");
    }

    public void showBracket() {
        if(!loggedIn) {
            return;
        }
        LOG.debug("Showing bracket for {}", event.eventCode());
        post(basePath, "event/" + event.eventCode() + "/control/bracket/", "");
    }

    public void showSelection() {
        if(!loggedIn) {
            return;
        }
        LOG.debug("Showing selection for {}", event.eventCode());
        post(basePath, "event/" + event.eventCode() + "/control/selection/show/", "");
    }

    public void showRanks() {
        if(!loggedIn) {
            return;
        }
        LOG.debug("Showing ranks for {}", event.eventCode());
        post(basePath, "event/" + event.eventCode() + "/control/ranks/", "");
    }

    public void showInspectionStatus() {
        if(!loggedIn) {
            return;
        }
        LOG.debug("Showing inspection status for {}", event.eventCode());
        post(basePath, "event/" + event.eventCode() + "/control/status/", "");
    }

    public void basicCommand(String cmd) {
        if(!loggedIn) {
            return;
        }
        LOG.debug("Sending basic command {} for {}", cmd, event.eventCode());
        post(basePath, "event/" + event.eventCode() + "/control/command/" + cmd + "/", "");
    }

    public void showMessage(String m) {
        if(!loggedIn) {
            return;
        }
        LOG.debug("Sending message {} for {}", m, event.eventCode());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + basePath + "/event/" + event.eventCode() + "/control/message/"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("msg=" + URLEncoder.encode(m, StandardCharsets.UTF_8)))
                .build();
        try {
            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void showMatch(String m) {
        if(!loggedIn) {
            return;
        }
        LOG.debug("Showing match {} for {}", m, event.eventCode());
        post(basePath, "event/" + event.eventCode() + "/control/preview/" + m + "/", "");
    }

    public void showResults(String m) {
        if(!loggedIn) {
            return;
        }
        LOG.debug("Showing results for {} for {}", m, event.eventCode());
        post(basePath, "event/" + event.eventCode() + "/control/results/" + m + "/", "");
    }

    public MatchList<QualsAlliance> getQualsMatches() {
        LOG.debug("Loading quals matches for {}", event.eventCode());
        String rawQualsMatches = get(basePath, "api/v1/events/" + event.eventCode() + "/matches/");
        return rawQualsMatches == null ? new MatchList<>(List.of()) : gson.fromJson(
                rawQualsMatches,
                new TypeToken<MatchList<QualsAlliance>>(){}.getType());
    }

    public MatchList<ElimsAlliance> getElimsMatches() {
        LOG.debug("Loading elim matches for {}", event.eventCode());
        String rawElimsMatches = get(basePath, "api/v2/events/" + event.eventCode() + "/elims/");
        return rawElimsMatches == null ? new MatchList<>(List.of()) : gson.fromJson(
                rawElimsMatches,
                new TypeToken<MatchList<ElimsAlliance>>(){}.getType());
    }

    public List<ElimsAlliance> getAlliances() {
        LOG.debug("Loading alliances for {}", event.eventCode());
        return gson.fromJson(Objects.requireNonNull(get(basePath, "api/v1/events/" + event.eventCode() + "/elim/alliances/")), AllianceList.class).alliances();
    }

    public Team getTeam(int id) {
        if(id == -1) return null;
        LOG.debug("Loading team {} (event {})", id, event.eventCode());
        String rawTeam = get(basePath, "api/v1/events/" + event.eventCode() + "/teams/" + id + "/");
        return gson.fromJson(Objects.requireNonNull(rawTeam), Team.class);
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public MatchDetails<? extends Alliance> getMatchDetails(String shortName) {
        LOG.debug("Loading match info for {} - {}", event.eventCode(), shortName);
        if(shortName.startsWith("Q")) {
            return gson.fromJson(
                    Objects.requireNonNull(get(basePath, "api/v1/events/" + event.eventCode() + "/matches/" + shortName.replace("Q", "") + "/")),
                    new TypeToken<MatchDetails<QualsAlliance>>(){}.getType());
        } else {
            return gson.fromJson(
                    Objects.requireNonNull(get(basePath, "api/v2/events/" + event.eventCode() + "/elims/" + shortName.toLowerCase() + "/")),
                    new TypeToken<MatchDetails<ElimsAlliance>>(){}.getType());
        }
    }

    public MatchList<? extends Alliance> getActiveMatches() {
        LOG.debug("Loading active matches for {}", event.eventCode());
        return gson.fromJson(
                Objects.requireNonNull(get(basePath, "api/v1/events/" + event.eventCode() + "/matches/active/")),
                new TypeToken<MatchList<? extends Alliance>>(){}.getType());
    }

    public FullEvent getFullEvent() {
        LOG.debug("Loading full event info for {}", event.eventCode());
        return gson.fromJson(Objects.requireNonNull(get(basePath, "api/v2/events/" + event.eventCode() + "/full/")), FullEvent.class);
    }

    public void setOnMatchUpdate(Consumer<MatchUpdate> onMatchUpdate) {
        this.onMatchUpdate = onMatchUpdate;
    }

    public CompletableFuture<Long> timesync() {
        UUID id = UUID.randomUUID();
        CompletableFuture<Long> ret = new CompletableFuture<>();
        ret = ret.orTimeout(10, java.util.concurrent.TimeUnit.SECONDS).whenComplete((Long aLong, Throwable throwable) -> {
            if(throwable != null) {
                timesyncRequests.remove(id);
            }
        });
        timesyncRequests.put(id, ret);
        Map<String, Object> req = new HashMap<>();
        req.put("jsonrpc", "2.0");
        req.put("id", id);
        req.put("method", "timesync");
        try {
            socketListener.sendText("TIMESYNC:" + gson.toJson(req), true).get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
        return ret;
    }
}
