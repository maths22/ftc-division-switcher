package com.maths22.ftc.models;

import java.util.List;
public record MatchDetailsList<T extends Alliance>(List<MatchDetails<T>> matches) {
}
