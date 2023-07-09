package com.maths22.ftc.models;

public record FullEvent(TeamList teamList, MatchDetailsList<QualsAlliance> matchList, MatchDetailsList<ElimsAlliance> elimsMatchDetailedList, AllianceList allianceList) {
}
