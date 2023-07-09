package com.maths22.ftc.models;

import java.util.List;

public record MatchList<T extends Alliance>(List<Match<T>> matches) {
}
