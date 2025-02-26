package com.maths22.ftc;

import com.maths22.ftc.scoring.client.FtcScoringClient;
import com.maths22.ftc.scoring.client.models.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class MatchManager {
    private final Map<Integer, Team> teams = new HashMap<>();
    private final Map<Integer, ElimsAlliance> alliances = new HashMap<>();
    private final FtcScoringClient client;
    private List<Match> matches = null;
    private final Runnable onUpdate;

    public MatchManager(FtcScoringClient client, Runnable onUpdate) {
        this.client = client;
        client.setOnMatchUpdate((update) -> {
            switch (update.updateType()) {
                case MATCH_LOAD -> updateMatch(update.payload().shortName(), true);
                case MATCH_COMMIT -> updateMatch(update.payload().shortName(), false);
                case MATCH_START -> updateMatchStart(update.payload().shortName(), update.updateTime());
                case MATCH_ABORT -> updateMatchStart(update.payload().shortName(), -1);
                case MATCH_POST, SHOW_PREVIEW, SHOW_MATCH, SHOW_RANDOM -> {
                    // intentional noop, at least for now
                }
            }
        });
        this.onUpdate = onUpdate;
    }

    public void start() {
        if (matches == null) {
            throw new IllegalStateException("loadMatches must be called before startSocketListener");
        }
        client.startSocketListener();
    }

    public void stop() {
        client.stopSocketListener();
    }

    public void loadMatches() {
        FullEvent full = client.getFullEvent();
        MatchList<? extends Alliance> activeMatches = client.getActiveMatches();
        MatchList<ElimsAlliance> allElimsMatches = client.getElimsMatches();

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

        return new Match(new MatchId(client.getEvent().division(), MatchType.parseFromName(name), Integer.parseInt(m.group(1))), num, score, redAlliance, blueAlliance, isActive, -1);
    }

    private void updateMatch(String shortName, boolean isActive) {
        Match replacement = matchFromDetails(client.getMatchDetails(shortName), isActive);
        updateMatch(replacement);
    }

    private void updateMatchStart(String shortName, long start) {
        MatchId id = new MatchId(client.getEvent().division(), shortName);
        Match replacement = matches.stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow();
        replacement = replacement.withStartTime(start);
        updateMatch(replacement);
    }

    private void updateMatch(Match replacement) {
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
        MatchList<QualsAlliance> allQualsMatches = client.getQualsMatches();
        matches = Stream.concat(matches.stream(), allQualsMatches.matches().stream()
                .map(com.maths22.ftc.scoring.client.models.Match::toPartialMatchDetails)
                .map(m -> matchFromDetails(m, false))
                .filter(m -> matches.stream().noneMatch(m2 -> m.id().equals(m2.id())))).toList();
    }

    private void loadElims() {
        MatchList<ElimsAlliance> allElimsMatches = client.getElimsMatches();
        matches = Stream.concat(matches.stream(), allElimsMatches.matches().stream()
                .map(com.maths22.ftc.scoring.client.models.Match::toPartialMatchDetails)
                .map(m -> matchFromDetails(m, false))
                .filter(m -> matches.stream().noneMatch(m2 -> m.id().equals(m2.id())))).toList();
    }

    private Alliance getAlliance(int seed) {
        if(!alliances.containsKey(seed)) {
            client.getAlliances().forEach(t -> alliances.put(t.seed(), t));
        }
        return alliances.get(seed);
    }

    private Team getTeam(int id) {
        if(id == -1) return null;
        Team ret = teams.get(id);
        if(ret == null) {
            ret = client.getTeam(id);
            teams.put(id, ret);
        }
        return ret;
    }


    public Event getEvent() {
        return client.getEvent();
    }

    public boolean login(String username, String password) {
        return client.login(username, password);
    }

    public List<Match> getMatches() {
        if(matches == null) {
            return List.of();
        }
        return matches;
    }

    public FtcScoringClient getClient() {
        return client;
    }
}
