import {MatchType} from "./javaTypes";
import {Matches, State} from "./types";

function singleStep(state: State): [State, number] | null {
    if (state === "prematch") {
        return ["results", 0];
    } else if (state === "results") {
        return ["prematch", 1];
    }
    return null;
}

export function getNextState(state: State, selectedMatch: string | undefined, matches: Matches, alwaysSingleStep: boolean): [State, number] | null {
    if (!selectedMatch) {
        return null;
    }
    const match = matches[selectedMatch];
    const allMatchesInPhase = Object.values(matches).filter((m) => m.id.matchType == match.id.matchType || (m.id.matchType.includes("SEMIFINAL") && match.id.matchType.includes("SEMIFINAL")));
    const indexInPhase = allMatchesInPhase.indexOf(match);
    const nextMatchInPhase = indexInPhase + 1 >= allMatchesInPhase.length ? undefined : allMatchesInPhase[indexInPhase + 1];
    const haveD1AndD2 = Object.values(matches).some(m => m.id.division == 1) && Object.values(matches).some(m => m.id.division == 1) && Object.values(matches).some(m => m.id.division == 2);
    if (alwaysSingleStep || (match.id.matchType == MatchType.FINAL && !haveD1AndD2)) {
        return singleStep(state);
    }
    if (nextMatchInPhase) {
        // regular semis
        if (match.id.matchType.startsWith("SEMIFINAL") && match.id.matchType == nextMatchInPhase.id.matchType) {
            return singleStep(state);
        }
    } else {
        if (match.id.matchType == MatchType.FINAL && !haveD1AndD2) {
            return singleStep(state);
        }
    }
    if (state === "prematch") {
        if (indexInPhase >= 1) {
            return ["results", -1];
        } else {
            return ["prematch", 1];
        }
    }
    if (state === "results") {
        let remainingInPhase = allMatchesInPhase.length - indexInPhase - 1;
        if (remainingInPhase === 1) {
            return ["results", 1];
        } else if (remainingInPhase === 0) {
            return ["prematch", 1];
        } else {
            return ["prematch", 2];
        }
    }
    return null;
}