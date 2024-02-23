package com.maths22.ftc.scoring.client.models;

import java.util.List;
public record MatchDetailsList<T extends Alliance>(List<MatchDetails<T>> matches) {
}
