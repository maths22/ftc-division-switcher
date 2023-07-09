package com.maths22.ftc.models;

import java.util.List;

public record QualsAlliance(int team1, int team2, int team3, boolean isTeam1Surrogate, boolean isTeam2Surrogate, boolean isTeam3Surrogate) implements Alliance {
    @Override
    public List<Integer> teamNumbers() {
        return List.of(team1, team2, team3);
    }
}
