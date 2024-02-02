import {PersistentWebsocket} from "persistent-websocket";
import {State} from "./types";
import {MatchId, MatchType} from "./javaTypes";

export const PermissiveURLSearchParams = (init: Record<string, string | number | boolean | undefined>) => new URLSearchParams(init as Record<string, string>)

export function createWebSocket(path: string) {
    const protocolPrefix = (window.location.protocol === 'https:') ? 'wss:' : 'ws:';
    const ret = new PersistentWebsocket(protocolPrefix + '//' + location.host + path, {
        pingSendFunction: (pws) => {
            pws.send("ping")
        },
        pingIntervalSeconds: 10
    });
    ret.open();
    return ret;
}


export function stateToLabel(state: State) {
    switch (state) {
        case "prematch":
            return "Pre-Match/Active:";
        case "results":
            return "Result:";
    }
}

export function matchNamePrefix(matchType: MatchType) {
    switch (matchType) {
        case MatchType.PRACTICE:
            return "P";
        case MatchType.QUALS:
            return "Q";
        case MatchType.SEMIFINAL1:
            return "SF1-";
        case MatchType.SEMIFINAL2:
            return "SF2-";
        case MatchType.INTER_SEMIFINAL1:
            return "ISF1-";
        case MatchType.INTER_SEMIFINAL2:
            return "ISF2-";
        case MatchType.RR_SEMIFINAL:
            return "R";
        case MatchType.FINAL:
            return "F";
        case MatchType.INTER_FINAL:
            return "IF";
    }
}
export function matchDisplayName(matchId: MatchId) {
    return `D${matchId.division} ${matchNamePrefix(matchId.matchType)}${matchId.matchNum}`
}

export function ordinalSuffixed(i: number) {
    const j = i % 10,
        k = i % 100;
    if (j == 1 && k != 11) {
        return i + "st";
    }
    if (j == 2 && k != 12) {
        return i + "nd";
    }
    if (j == 3 && k != 13) {
        return i + "rd";
    }
    return i + "th";
}