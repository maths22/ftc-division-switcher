package com.maths22.ftc.scoring.client.models;

public record FullEvent(TeamList teamList, MatchDetailsList<QualsAlliance> matchList, MatchDetailsList<ElimsAlliance> elimsMatchDetailedList, AllianceList allianceList) {
}
