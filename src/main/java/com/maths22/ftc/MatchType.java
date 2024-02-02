package com.maths22.ftc;

import java.util.Arrays;

public enum MatchType {
    PRACTICE("P", false),
    QUALS("Q", false),
    SEMIFINAL1("SF1", true),
    SEMIFINAL2("SF2", true),
    INTER_SEMIFINAL1("ISF1", true),
    INTER_SEMIFINAL2("ISF2", true),
    RR_SEMIFINAL("R", true),
    FINAL("F", false),
    INTER_FINAL("IF", false);

    private final String namePrefix;
    private final boolean isSemiFinal;

    MatchType(String namePrefix, boolean isSemiFinal) {
        this.namePrefix = namePrefix;
        this.isSemiFinal = isSemiFinal;
    }

    public static MatchType parseFromName(String name) {
        return Arrays.stream(MatchType.values()).filter(m -> name.startsWith(m.namePrefix)).findFirst().orElseThrow();
    }

    public boolean isSemiFinal() {
        return isSemiFinal;
    }
}
