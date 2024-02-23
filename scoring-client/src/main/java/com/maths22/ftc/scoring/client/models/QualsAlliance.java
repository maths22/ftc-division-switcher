package com.maths22.ftc.scoring.client.models;

import java.util.List;
import java.util.stream.Stream;

public record QualsAlliance(int team1, int team2, int team3, boolean isTeam1Surrogate, boolean isTeam2Surrogate, boolean isTeam3Surrogate) implements Alliance {
    @Override
    public List<Integer> teamNumbers() {
        return Stream.of(team1, team2, team3).filter(i -> i > 0).toList();
    }
}
