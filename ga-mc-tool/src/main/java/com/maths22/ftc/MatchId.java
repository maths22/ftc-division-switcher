package com.maths22.ftc;

import org.jetbrains.annotations.NotNull;

public record MatchId(int division, MatchType matchType, int matchNum) implements Comparable<MatchId> {
    @Override
    public int compareTo(@NotNull MatchId o) {
        if(o.division != division) {
            return Integer.compare(division, o.division);
        }
        if(!o.matchType.equals(matchType)) {
            return matchType.compareTo(o.matchType);
        }
        return Integer.compare(matchNum, o.matchNum);
    }
}
