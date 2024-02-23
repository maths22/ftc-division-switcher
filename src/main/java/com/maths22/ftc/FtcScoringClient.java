package com.maths22.ftc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.maths22.ftc.models.*;
import io.javalin.http.HttpStatus;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private final Runnable onUpdate;
    private final Event event;
    private WebSocket socketListener;
    private boolean loggedIn;
    private final String basePath;
    private final Map<Integer, Team> teams = new HashMap<>();
    private final Map<Integer, ElimsAlliance> alliances = new HashMap<>();
    private List<Match> matches = null;

    public FtcScoringClient(String basePath, String eventCode, Runnable onUpdate) {
        this.basePath = basePath;
        this.loggedIn = false;
        this.onUpdate = onUpdate;
        this.event = gson.fromJson(get(basePath, "api/v1/events/" + eventCode + "/"), Event.class);
        testLogin();
    }

    public void startSocketListener() {
        if(matches == null) {
            throw new IllegalStateException("loadMatches must be called before startSocketListener");
        }
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

                        if(!message.equals("pong")) {
                            try {
                                MatchUpdate update = gson.fromJson(message, MatchUpdate.class);
                                switch (update.updateType()) {
                                    case MATCH_LOAD -> updateMatch(update.payload().shortName(), true);
                                    case MATCH_COMMIT -> updateMatch(update.payload().shortName(), false);
                                    case MATCH_START, MATCH_ABORT, MATCH_POST, SHOW_PREVIEW, SHOW_MATCH, SHOW_RANDOM -> {
                                        // intentional noop, at least for now
                                    }
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
            return false;
        }

        return testLogin();
    }

    private boolean testLogin() {
        try {
            get(basePath, "/event/" + event.eventCode() + "/control/schedule/");
        } catch (Exception ex) {
            LOG.info("Login failed", ex);
            return false;
        }
        loggedIn = true;
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

            if(resp.statusCode() == HttpStatus.OK.getCode()) {
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

            if(resp.statusCode() == HttpStatus.OK.getCode()) {
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
        post(basePath, "event/" + event.eventCode() + "/control/sponsors/", "");
    }

    public void showBracket() {
        if(!loggedIn) {
            return;
        }
        post(basePath, "event/" + event.eventCode() + "/control/bracket/", "");
    }

    public void showSelection() {
        if(!loggedIn) {
            return;
        }
        post(basePath, "event/" + event.eventCode() + "/control/selection/show/", "");
    }

    public void showRanks() {
        if(!loggedIn) {
            return;
        }
        post(basePath, "event/" + event.eventCode() + "/control/ranks/", "");
    }

    public void showInspectionStatus() {
        if(!loggedIn) {
            return;
        }
        post(basePath, "event/" + event.eventCode() + "/control/status/", "");
    }

    public void basicCommand(String cmd) {
        if(!loggedIn) {
            return;
        }
        post(basePath, "event/" + event.eventCode() + "/control/command/" + cmd + "/", "");
    }

    public void showMessage(String m) {
        if(!loggedIn) {
            return;
        }
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
        post(basePath, "event/" + event.eventCode() + "/control/preview/" + m + "/", "");
    }

    public void showResults(String m) {
        if(!loggedIn) {
            return;
        }
        post(basePath, "event/" + event.eventCode() + "/control/results/" + m + "/", "");
    }

    public void loadMatches() {
        FullEvent full = gson.fromJson(Objects.requireNonNull(get(basePath, "api/v2/events/" + event.eventCode() + "/full/")), FullEvent.class);
        MatchList<? extends Alliance> activeMatches = gson.fromJson(
                Objects.requireNonNull(get(basePath, "api/v1/events/" + event.eventCode() + "/matches/active/")),
                new TypeToken<MatchList<? extends Alliance>>(){}.getType());
        MatchList<ElimsAlliance> allElimsMatches = loadAllElimsMatches();

        full.teamList().teams().forEach(t -> teams.put(t.number(), t));
        full.allianceList().alliances().forEach(t -> alliances.put(t.seed(), t));

        // loadAllElimsMatches returns elims matches in the correct order; full event does not but includes results data
        matches = Stream.concat(full.matchList().matches().stream(), allElimsMatches.matches().stream().map(m -> full.elimsMatchDetailedList().matches().stream().filter(m2 -> m2.matchBrief().matchName().equals(m.matchName())).findFirst().orElse(m.toPartialMatchDetails())))
                .map(match -> matchFromDetails(match, activeMatches.matches().stream().anyMatch(am -> match.matchBrief().matchName().equals(am.matchName())))).toList();
        onUpdate.run();
    }

    private Match matchFromDetails(MatchDetails<? extends Alliance> match, boolean isActive) {
        String name = match.matchBrief().matchName();
        int num = match.matchBrief().matchNumber();
        List<Team> redAlliance = ((match.matchBrief().red() instanceof ElimsAlliance ea) ? getAlliance(ea.seed()) : match.matchBrief().red()).teamNumbers().stream().map(this::getTeam).toList();
        List<Team> blueAlliance = ((match.matchBrief().blue() instanceof ElimsAlliance ea) ? getAlliance(ea.seed()) : match.matchBrief().blue()).teamNumbers().stream().map(this::getTeam).toList();
        String score = null;
        if(match.matchBrief().finished()) {
            int redScore = match.redScore();
            int blueScore = match.blueScore();
            char desc = 'T';
            if (redScore > blueScore) desc = 'R';
            if (redScore < blueScore) desc = 'B';
            score = redScore + "-" + blueScore + " " + desc;
        }
        Pattern trailingNumber = Pattern.compile("([0-9]+)$");
        Matcher m = trailingNumber.matcher(name);
        if(!m.find()) {
            throw new IllegalStateException("Match name " + name + " does not end with a number");
        }

        return new Match(new MatchId(event.division(), MatchType.parseFromName(name), Integer.parseInt(m.group(1))), num, score, redAlliance, blueAlliance, isActive);
    }

    private void updateMatch(String shortName, boolean isActive) {
        Match replacement;
        if(shortName.startsWith("Q")) {
            MatchDetails<QualsAlliance> details = gson.fromJson(
                    Objects.requireNonNull(get(basePath, "api/v1/events/" + event.eventCode() + "/matches/" + shortName.replace("Q", "") + "/")),
                    new TypeToken<MatchDetails<QualsAlliance>>(){}.getType());
            replacement = matchFromDetails(details, isActive);
        } else {
            MatchDetails<ElimsAlliance> details = gson.fromJson(
                    Objects.requireNonNull(get(basePath, "api/v2/events/" + event.eventCode() + "/elims/" + shortName.toLowerCase() + "/")),
                    new TypeToken<MatchDetails<ElimsAlliance>>(){}.getType());
            replacement = matchFromDetails(details, isActive);
        }
        boolean existingMatch = matches.stream().anyMatch(m -> m.id().equals(replacement.id()));
        if(!existingMatch && matches.stream().noneMatch(m -> m.id().matchType() == replacement.id().matchType())) {
            if (replacement.id().matchType() == MatchType.QUALS) {
                loadQuals();
            } else {
                loadElims();
            }
            existingMatch = matches.stream().anyMatch(m -> m.id().equals(replacement.id()));
        }
        matches = existingMatch ? matches.stream().map(m -> m.id().equals(replacement.id()) ? replacement : m).toList() :
                Stream.concat(matches.stream(), Stream.of(replacement)).sorted().toList();
        onUpdate.run();
    }

    // These methods are only going to be used when no matches have been played, so we don't need to load results
    private void loadQuals() {
        MatchList<QualsAlliance> allQualsMatches = loadAllQualsMatches();
        matches = Stream.concat(matches.stream(), allQualsMatches.matches().stream()
                .map(com.maths22.ftc.models.Match::toPartialMatchDetails)
                .map(m -> matchFromDetails(m, false))
                .filter(m -> matches.stream().noneMatch(m2 -> m.id().equals(m2.id())))).toList();
    }

    private void loadElims() {
        MatchList<ElimsAlliance> allElimsMatches = loadAllElimsMatches();
        matches = Stream.concat(matches.stream(), allElimsMatches.matches().stream()
                .map(com.maths22.ftc.models.Match::toPartialMatchDetails)
                .map(m -> matchFromDetails(m, false))
                .filter(m -> matches.stream().noneMatch(m2 -> m.id().equals(m2.id())))).toList();
    }

    private MatchList<QualsAlliance> loadAllQualsMatches() {
        String rawQualsMatches = get(basePath, "api/v1/events/" + event.eventCode() + "/matches/");
        return rawQualsMatches == null ? new MatchList<>(List.of()) : gson.fromJson(
                rawQualsMatches,
                new TypeToken<MatchList<QualsAlliance>>(){}.getType());
    }

    private MatchList<ElimsAlliance> loadAllElimsMatches() {
        String rawElimsMatches = get(basePath, "api/v2/events/" + event.eventCode() + "/elims/");
        return rawElimsMatches == null ? new MatchList<>(List.of()) : gson.fromJson(
                rawElimsMatches,
                new TypeToken<MatchList<ElimsAlliance>>(){}.getType());
    }

    private Alliance getAlliance(int seed) {
        if(!alliances.containsKey(seed)) {
            gson.fromJson(Objects.requireNonNull(get(basePath, "api/v1/events/" + event.eventCode() + "/elim/alliances/")), AllianceList.class).alliances().forEach(t -> alliances.put(t.seed(), t));
        }
        return alliances.get(seed);
    }

    private Team getTeam(int id) {
        if(id == -1) return null;
        Team ret = teams.get(id);
        if(ret == null) {
            String rawTeam = get(basePath, "api/v1/events/" + event.eventCode() + "/teams/" + id + "/");
            ret = gson.fromJson(Objects.requireNonNull(rawTeam), Team.class);
            teams.put(id, ret);
        }
        return ret;
    }

    public List<Match> matches() {
        if(matches == null) {
            return List.of();
        }
        return matches;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }
}
