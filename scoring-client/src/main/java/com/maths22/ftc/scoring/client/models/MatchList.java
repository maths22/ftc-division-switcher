package com.maths22.ftc.scoring.client.models;

import java.util.List;

public record MatchList<T extends Alliance>(List<Match<T>> matches) {
}
