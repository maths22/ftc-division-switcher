package com.maths22.ftc.models;

public record MatchUpdate(UpdateType updateType, long updateTime, MatchUpdatePayload payload) {
    public record MatchUpdatePayload(int number, int field, String shortName) {
    }
}
