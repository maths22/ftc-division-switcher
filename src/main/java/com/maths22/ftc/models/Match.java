package com.maths22.ftc.models;

public record Match<T extends Alliance>(String matchName, int matchNumber, int field, T red, T blue, boolean finished, String matchState, long time) {
    public MatchDetails<T> toPartialMatchDetails() {
        return new MatchDetails<>(this, -1, -1, -1, -1, -1, null, null, -1);
    }
}
