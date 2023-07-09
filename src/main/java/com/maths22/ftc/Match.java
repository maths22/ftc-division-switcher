package com.maths22.ftc;

import com.maths22.ftc.models.Team;

import java.util.List;

public record Match (String id, int num, String score, List<Team> redAlliance, List<Team> blueAlliance, boolean isActive) {
    public Match(String id, int num, String score, List<Team> redAlliance, List<Team> blueAlliance) {
        this(id, num, score, redAlliance, blueAlliance, false);
    }

    public Match withActive(boolean newActive) {
        return new Match(id, num, score, redAlliance, blueAlliance, newActive);
    }
}
