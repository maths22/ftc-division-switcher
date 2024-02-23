package com.maths22.ftc.scoring.client.models;

public record Event(String eventCode, String name, String type, String status, boolean finals, int division, long start, long end) {
}
