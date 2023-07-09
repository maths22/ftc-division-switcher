package com.maths22.ftc.models;

public record MatchDetails<T extends Alliance>(Match<T> matchBrief, long startTime, long scheduledTime, long resultPostedTime, int redScore, int blueScore,
                           AllianceDetails red, AllianceDetails blue, int randomization) {
    public record AllianceDetails(int auto, int teleop, int end, int penalty, boolean dq1, boolean dq2, boolean dq3, int robot1, int robot2, int robot3) {
    }
}
