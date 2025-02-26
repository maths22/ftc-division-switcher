package com.maths22.ftc;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record MatchId(int division, MatchType matchType, int matchNum) implements Comparable<MatchId> {
    private static final Pattern trailingNumber = Pattern.compile("([0-9]+)$");
    private static int extractNumber(String shortName) {
        Matcher m = trailingNumber.matcher(shortName);
        if(!m.find()) {
            throw new IllegalStateException("Match name " + shortName + " does not end with a number");
        }
        return Integer.parseInt(m.group(1));
    }

    public MatchId(int division, String shortName) {
        this(division, MatchType.parseFromName(shortName), extractNumber(shortName));
    }

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
