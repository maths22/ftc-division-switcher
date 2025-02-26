import {AuxInfo, EventInfo, Match, MatchData, Message, SingleStep, State as StateMessage} from "./javaTypes.ts";

export type State = 'prematch' | 'results' | 'timer';
export type FullState = [State, string];
export type Matches = Record<string, Match>;

export function isMatchData(data: Message): data is MatchData {
    return data.messageType === 'MATCH_DATA';
}

export function isAuxInfo(data: Message): data is AuxInfo {
    return data.messageType === 'AUX_INFO';
}

export function isEventInfo(data: Message): data is EventInfo {
    return data.messageType === 'EVENT_INFO';
}

export function isSingleStep(data: Message): data is SingleStep {
    return data.messageType === 'SINGLE_STEP';
}

export function isState(data: Message): data is StateMessage {
    return data.messageType === 'STATE';
}