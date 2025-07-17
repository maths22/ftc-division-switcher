package com.maths22.ftc;

import com.maths22.ftc.scoring.client.models.Team;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record Match (MatchId id, int num, String score, AllianceColor winner, List<Team> redAlliance, List<Team> blueAlliance, boolean isActive, long startTime) implements Comparable<Match> {
    public Match(MatchId id, int num, String score, AllianceColor winner, List<Team> redAlliance, List<Team> blueAlliance) {
        this(id, num, score, winner, redAlliance, blueAlliance, false, -1);
    }

    public Match withActive(boolean newActive) {
        return new Match(id, num, score, winner, redAlliance, blueAlliance, newActive, -1);
    }

    public Match withStartTime(long newStartTime) {
        return new Match(id, num, score, winner, redAlliance, blueAlliance, isActive, newStartTime);
    }

    @Override
    public int compareTo(@NotNull Match o) {
        return id.compareTo(o.id);
    }
}
