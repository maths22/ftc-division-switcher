package com.maths22.ftc.scoring.client.models;

import java.util.List;
import java.util.stream.Stream;

public record ElimsAlliance(int seed, int captain, int pick1, int pick2, int backup, boolean dq) implements Alliance {
    @Override
    public List<Integer> teamNumbers() {
        return Stream.of(captain, pick1, pick2, backup).filter(i -> i > 0).toList();
    }
}
