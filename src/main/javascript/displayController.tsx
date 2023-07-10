import 'bootstrap/dist/css/bootstrap.min.css';

import { createRoot } from 'react-dom/client';
import React, { useState, useEffect } from 'react';
import {AuxInfo, Match, MatchData, Message, Result, SingleStep, State as StateMessage} from "./javaTypes";
import {PersistentWebsocket} from "persistent-websocket";
import {Button, Col, Nav, Row, Tab, Table, Form} from "react-bootstrap";

type State = 'prematch' | 'results' | 'timer';
type FullState = [State, string];
type Matches = Record<string, Match>;

export const PermissiveURLSearchParams = (init: Record<string, string | number | boolean | undefined>) => new URLSearchParams(init as Record<string, string>)

function sendCommand(args: [State | string, string, string?], matches?: Matches) {
    fetch("/api?" + PermissiveURLSearchParams({division: args[1][1], display: args[0], match: (matches && matches[args[1]].num) || args[2], data: JSON.stringify(args)}).toString());
}

function isMatchData(data: Message): data is MatchData {
    return data.messageType === 'MATCH_DATA';
}

function isAuxInfo(data: Message): data is AuxInfo {
    return data.messageType === 'AUX_INFO';
}

function isSingleStep(data: Message): data is SingleStep {
    return data.messageType === 'SINGLE_STEP';
}

function isState(data: Message): data is StateMessage {
    return data.messageType === 'STATE';
}

function App() {
    const [matchSelectVal, setMatchSelectVal] = useState<string>();
    const [uiSelectedMatch, setUiSelectedMatch] = useState<string>();
    const [state, setState] = useState<State>("prematch");
    const [prev, setPrev] = useState<FullState[]>([]);
    const [matches, setMatches] = useState<Matches>({});
    const [auxInfo, setAuxInfo] = useState<Result>();
    const [alwaysSingleStep, setAlwaysSingleStep] = useState(false);
    const [time, setTime] = useState({div: 'none', min: 0, sec: 0, phase: 'unknown'});
    const [announcementText, setAnnouncementText] = useState("")
    const matchNames = Object.keys(matches);
    console.log(matches)

    const cur: FullState = [state, matchSelectVal || ''];
    const nextState = getNextState(state, matchSelectVal, matchNames, alwaysSingleStep) || ["prematch", 0];
    let nextMatch = undefined;
    let tryAgain = true;
    let lastTry = false;
    while (tryAgain) {
        if (lastTry) tryAgain = false;
        if (nextState[1] === -1 && matchSelectVal) {
            nextMatch = matchNames[matchNames.indexOf(matchSelectVal) - 1];
        } else if (nextState[1] === 1 && matchSelectVal) {
            nextMatch = matchNames[matchNames.indexOf(matchSelectVal) + 1];
        } else if (nextState[1] === 2&& matchSelectVal) {
            nextMatch = matchNames[matchNames.indexOf(matchSelectVal) + 2];
        } else if (nextState[1] === 0) {
            nextMatch = matchSelectVal;
        }
        if (!nextMatch) {
            if(matchSelectVal) {
                nextState[0] = "results";
                nextState[1] = 1;
            } else {
                nextState[0] = "prematch";
                nextState[1] = 1;
            }
            lastTry = true;
        } else {
            tryAgain = false;
        }
    }

    const next: FullState = [nextState[0], nextMatch || ""];

    useEffect(() => {
        const socket = createWebSocket("/matchstream");
        socket.onmessage = (event) => {
            if(event.data === 'ping') return;
            const response: Message = JSON.parse(event.data);
            if(isMatchData(response)) {
                setMatches(Object.fromEntries(response.data.map(m => [m.id, m])));
                if(response.data.length > 0) {
                    setUiSelectedMatch((cur) => cur ? cur : response.data[0].id)
                }
            } else if(isState(response)) {
                const update: FullState = JSON.parse(response.data);
                setUiSelectedMatch(update[1]);
                setMatchSelectVal(update[1]);
                setState(update[0]);
            } else if(isAuxInfo(response)) {
                setAuxInfo(response.data);
            // } else if(response.type === 'time') {
            //     setTime(response.data);
            } else if(isSingleStep(response)) {
                setAlwaysSingleStep(response.data);
            }

        };
    }, [])

    function clickMatchPlayNext() {
        setMatchSelectVal(next[1]);
        setUiSelectedMatch(next[1]);
        setState(next[0]);
        if(cur[1]) {
            setPrev((prev) => [...prev, cur]);
        }
        sendCommand(next, matches);
    }

    function clickMatchPlayCur() {
        sendCommand(cur, matches);
    }

    function clickMatchPlayPrev() {
        const elem = prev[prev.length - 1];
        setMatchSelectVal(elem[1]);
        setUiSelectedMatch(elem[1]);
        setPrev((prev) => prev.slice(0, prev.length - 1));
        setState(elem[0]);

        sendCommand(elem, matches);
    }

    function clickMatchPlayPrematch() {
        if(!uiSelectedMatch) {
            return;
        }
        setMatchSelectVal(uiSelectedMatch);
        setState("prematch");
        if(cur[1]) {
            setPrev((prev) => [...prev, cur]);
        }
        sendCommand(["prematch", uiSelectedMatch], matches);
    }

    function clickMatchPlayTimer() {
        if(!uiSelectedMatch) {
            return;
        }
        setMatchSelectVal(uiSelectedMatch);
        setState("timer");
        if(cur[1]) {
            setPrev((prev) => [...prev, cur]);
        }
        sendCommand(["timer", uiSelectedMatch], matches);
    }

    function clickMatchPlayResults() {
        if(!uiSelectedMatch) {
            return;
        }
        setMatchSelectVal(uiSelectedMatch);
        setState("results");
        if(cur[1]) {
            setPrev((prev) => [...prev, cur]);
        }
        sendCommand(["results", uiSelectedMatch], matches);
    }

    return <>
        <h1>FTC Display Switcher</h1>
        <div>

        <Tab.Container id="left-tabs-example" defaultActiveKey={"#match-play"}>
            <Nav variant="pills">
                <Nav.Item>
                    <Nav.Link href="#match-play">Match Play</Nav.Link>
                </Nav.Item>
                <Nav.Item>
                    <Nav.Link href="#display">Display</Nav.Link>
                </Nav.Item>
            </Nav>

            <Tab.Content>
                <Tab.Pane eventKey="#match-play">
                    <div className="container-fluid">

                        <h3>Automatic Control</h3>
                        <Row>
                            <Col sm={4} className="d-grid">
                                <Button variant="outline-dark" onClick={clickMatchPlayPrev} disabled={prev.length === 0 || !prev[prev.length - 1]}>
                                    Previous
                                    <br/>
                                    {prev.length > 0 && prev[prev.length - 1] ? stateToLabel(prev[prev.length - 1][0]) + " " + prev[prev.length - 1][1] : ''}
                                    <br/>
                                    {prev.length > 0 && prev[prev.length - 1] && prev[prev.length - 1][0] === 'results' ? (matches[prev[prev.length - 1][1]].score || 'Match Not Yet Scored') : ''}
                                </Button>
                            </Col>
                            <Col sm={4} className="d-grid">
                                <Button variant="outline-dark" onClick={clickMatchPlayCur} disabled={!cur[1]}>
                                    Current
                                    <br/>
                                    {cur[1] ? (stateToLabel(cur[0]) + " " + cur[1]) : ''}
                                    <br/>
                                    {cur[0] === 'results' ? (matches[cur[1]].score || 'Match Not Yet Scored') : ''}
                                </Button>
                            </Col>
                            <Col sm={4} className="d-grid">
                                <Button variant="primary" onClick={clickMatchPlayNext} disabled={!next[1] || (next[0] === 'prematch' && !matches[next[1]]?.isActive)}>
                                    Next
                                    <br/>
                                    {next[1] ? (stateToLabel(next[0]) + " " + next[1]) : ''}
                                    <br/>
                                    {next[0] === 'results' ? (matches[next[1]].score || 'Match Not Yet Scored') : (next[0] === 'prematch' && !matches[next[1]]?.isActive ? 'Not loaded for play' : '')}
                                </Button>
                            </Col>
                        </Row>

                        <h4>{cur[1] ? (stateToLabel(cur[0]) + " " + cur[1]) : ''}</h4>
                        <h4>{cur[0] === 'results' ? (matches[cur[1]].score || 'Match Not Yet Scored') : ''}</h4>
                        <h4>{cur[0] === 'timer' ? `${time['min']}:${('0' + time['sec']).slice(-2)} (${time['phase']})` : ''}</h4>
                        {cur[0] === 'results' ? null : <Table>
                            <thead>
                            <tr>
                                <th>Position</th>
                                <th>Number</th>
                                <th>Name</th>
                                <th>Organization</th>
                                <th>City</th>
                                <th>State</th>
                                <th>Country</th>
                            </tr>
                            </thead>
                            <tbody>
                                {matches[cur[1]]?.redAlliance.map((team, idx) => <tr>
                                    <td>Red {idx + 1}</td>
                                    <td>{team.number}</td>
                                    <td>{team.name}</td>
                                    <td>{team.school}</td>
                                    <td>{team.city}</td>
                                    <td>{team.state}</td>
                                    <td>{team.country}</td>
                                </tr>)}
                                {matches[cur[1]]?.blueAlliance.map((team, idx) => <tr>
                                    <td>Blue {idx + 1}</td>
                                    <td>{team.number}</td>
                                    <td>{team.name}</td>
                                    <td>{team.school}</td>
                                    <td>{team.city}</td>
                                    <td>{team.state}</td>
                                    <td>{team.country}</td>
                                </tr>)}
                            </tbody>
                        </Table>}
                        <Table>
                            <thead>
                            <tr>
                                {auxInfo?.titles.map((title) => <th>{title}</th>)}
                            </tr>
                            </thead>
                            <tbody>
                                {matches[cur[1]]?.redAlliance.map((team) => auxInfo ? <tr>
                                    {auxInfo.titles.map((title) => <td>{auxInfo.entries[team.number] ? auxInfo.entries[team.number][title] : ""}</td>)}
                                </tr> : null)}
                                {matches[cur[1]]?.blueAlliance.map((team) => auxInfo ? <tr>
                                    {auxInfo.titles.map((title) => <td>{auxInfo.entries[team.number] ? auxInfo.entries[team.number][title] : ""}</td>)}
                                </tr> : null)}
                            </tbody>
                        </Table>

                        <h3>Manual Control</h3>
                        <div className="button-column">
                            <Form.Group className="mb-2" controlId="match-select">
                                <Form.Label>Match</Form.Label>
                                <Form.Select value={uiSelectedMatch} onChange={(e) => setUiSelectedMatch(e.target.value)}>
                                    {matchNames.map((value) => <option value={value}>{value} ({matches[value]?.score ? matches[value].score : "Not yet scored"})</option>)}
                                </Form.Select>
                            </Form.Group>
                            {uiSelectedMatch ? <>
                                <Button variant="outline-dark" onClick={clickMatchPlayPrematch} disabled={!matches[uiSelectedMatch]?.isActive}>Pre-Match Information {matches[uiSelectedMatch]?.isActive ? null : '(not loaded for play)'}</Button><br/>
                                {/*<button type="button" className="btn btn-outline-dark" onclick="clickMatchPlayTimer()">Match Timer</button><br/>*/}
                                <Button variant="outline-dark" onClick={clickMatchPlayResults}>Results</Button><br/>
                            </> : null}

                        </div>
                    </div>
                </Tab.Pane>
                <Tab.Pane eventKey="#display">
                    <Row>
                        <Col sm={6}>
                            <h3>Rankings</h3>
                            <Button variant="outline-dark" onClick={() => sendCommand(['rankings', 'D1'])}>D1</Button>
                            <Button variant="outline-dark" onClick={() => sendCommand(['rankings', 'D2'])}>D2</Button><br/>
                            {/*<Button variant="outline-dark" onClick={() => sendCommand(['rankings', 'Dr'])}>All</Button><br/>*/}
                            <h3>Alliance Selection</h3>
                            <Button variant="outline-dark" onClick={() => sendCommand(['alliance', 'D1'])}>D1</Button>
                            <Button variant="outline-dark" onClick={() => sendCommand(['alliance', 'D2'])}>D2</Button><br/>
                            <h3>Elimination Ladder</h3>
                            <Button variant="outline-dark" onClick={() => sendCommand(['elimination', 'D1'])}>D1</Button>
                            <Button variant="outline-dark" onClick={() => sendCommand(['elimination', 'D2'])}>D2</Button><br/>
                            <h3>Online Results</h3>
                            <Button variant="outline-dark" onClick={() => sendCommand(['online', 'D1'])}>D1</Button>
                            <Button variant="outline-dark" onClick={() => sendCommand(['online', 'D2'])}>D2</Button><br/>
                        </Col>
                        <Col sm={6}>
                            <h3>Sponsor Listing</h3>
                            <Button variant="outline-dark" onClick={() => sendCommand(['sponsor', 'D1'])}>Show</Button><br/>
                            <h3>Blank</h3>
                            <Button variant="outline-dark" onClick={() => sendCommand(['blank', 'D1'])}>Show</Button><br/>
                            <h3>Video only</h3>
                            <Button variant="outline-dark" onClick={() => sendCommand(['video', 'D1'])}>Show</Button><br/>
                            <h3>Wi-Fi reminder</h3>
                            <Button variant="outline-dark" onClick={() => sendCommand(['wifi', 'D1'])}>Show</Button><br/>
                            <h3>Audience Key</h3>
                            <Button variant="outline-dark" onClick={() => sendCommand(['key', 'D1'])}>Show</Button><br/>
                            <h3>Announcement</h3>
                            <textarea value={announcementText} onChange={(e) => setAnnouncementText(e.target.value)}></textarea>
                            <Button variant="outline-dark" onClick={() => sendCommand(['announcement', 'D1', announcementText])}>Show</Button><br/>
                        </Col>
                    </Row>
                </Tab.Pane>
            </Tab.Content>
         </Tab.Container>
        </div>
    </>;
}
const root = createRoot(document.body);
root.render(<App />);

function createWebSocket(path: string) {
    const protocolPrefix = (window.location.protocol === 'https:') ? 'wss:' : 'ws:';
    const ret = new PersistentWebsocket(protocolPrefix + '//' + location.host + path, {
        pingSendFunction: (pws) => { pws.send("ping")},
        pingIntervalSeconds: 10
    });
    ret.open();
    return ret;
}

function stateToLabel(state: State) {
    switch(state) {
        case "prematch":
            return "Pre-Match/Active:";
        case "results":
            return "Result:";
    }
}

function singleStep(state: State): [State, number] | null {
    if(state === "prematch") {
        return ["results", 0];
    } else if(state === "results") {
        return ["prematch", 1];
    }
    return null;
}

function getNextState(state: State, selectedMatch: string | undefined, matches: string[], alwaysSingleStep: boolean): [State, number] | null {
    if(!selectedMatch) {
        return null;
    }
    const phase = selectedMatch.replace(/[^A-Z]/g, '').substring(1);
    const nextMatch = matches[matches.findIndex(function(element) {return element === selectedMatch }) + 1];
    const haveD1AndD2 = matches.findIndex(function(element) { return element.indexOf("D1") >= 0 }) >= 0 &&
        matches.findIndex(function(element) { return element.indexOf("D2") >= 0 }) >= 0;
    if(alwaysSingleStep || (phase === 'F')) {
        return singleStep(state);
    }
    // disabling for round-robin
    // if(nextMatch) {
    //     const nextSeries = nextMatch.substring(0, nextMatch.lastIndexOf("-") + 1);
    //     const thisSeries = selectedMatch.substring(0, selectedMatch.lastIndexOf("-") + 1);
    //     if(phase !== 'Q' && nextSeries === thisSeries) {
    //         return singleStep(state);
    //     }
    // } else {
    //     if(phase === 'F' && !haveD1AndD2) {
    //         return singleStep(state);
    //     }
    // }
    if(state === "prematch") {
        let numofphase = 0;
        Object.values(matches).forEach((value) => {
            if(value.replace(/[^A-Z]/g, '').substring(1) === phase) {
                numofphase++;
            }
            if(selectedMatch === value) return false;
        });

        if(numofphase >= 2) {
            return ["results", -1];
        } else {
            return ["prematch", 1];
        }
    }
    if(state === "results") {
        let remainingInPhase = 0;
        let found = false;
        Object.values(matches).forEach((value) => {
            if(found && value.replace(/[^A-Z]/g, '').substring(1) === phase) {
                remainingInPhase++;
            }
            if(selectedMatch === value) found = true;

        });
        if(remainingInPhase === 1) {
            return ["results", 1];
        } else if(remainingInPhase === 0) {
            return ["prematch", 1];
        } else {
            return ["prematch", 2];
        }
    }
    return null;
}