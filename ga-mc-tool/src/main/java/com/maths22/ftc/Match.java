package com.maths22.ftc;

import com.maths22.ftc.scoring.client.models.Team;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record Match (MatchId id, int num, String score, List<Team> redAlliance, List<Team> blueAlliance, boolean isActive) implements Comparable<Match> {
    public Match(MatchId id, int num, String score, List<Team> redAlliance, List<Team> blueAlliance) {
        this(id, num, score, redAlliance, blueAlliance, false);
    }

    public Match withActive(boolean newActive) {
        return new Match(id, num, score, redAlliance, blueAlliance, newActive);
    }

    @Override
    public int compareTo(@NotNull Match o) {
        return id.compareTo(o.id);
    }
}
