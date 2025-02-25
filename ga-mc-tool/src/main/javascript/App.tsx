import React, {useEffect, useState} from "react";
import {FullState, isAuxInfo, isEventInfo, isMatchData, isSingleStep, isState, Matches, State} from "./types";
import {Event, Message, Result, Team} from "./javaTypes";
import {getNextState} from "./calculationHelpers";
import {Button, Col, Form, Nav, Row, Tab} from "react-bootstrap";
import {createWebSocket, matchDisplayName, ordinalSuffixed, PermissiveURLSearchParams, stateToLabel} from "./utils";

import './App.css'

function sendCommand(args: [State | string, string, string?], matches?: Matches) {
    fetch("/api?" + PermissiveURLSearchParams({
        division: args[1][1],
        display: args[0],
        match: (matches && matches[args[1]].num) || args[2] || "",
        data: JSON.stringify(args)
    }).toString());
}

function AllianceTeamInfo({alliance, auxInfo} : {alliance: Team[], auxInfo?: Result}) {
    return <>
    {alliance.map((team) => <tr>
        <td>
            <b>{team.number}</b><br/>
            <small>{team.rookie}</small><br/>
            <small>({ordinalSuffixed(2025 - team.rookie)} season)</small>
        </td>
        <td>
            <span className={"fs-4"}>{team.name}</span><br/>
            <small>{team.city}, {team.state}, {team.country}</small>
        </td>
        <td>
            {team.school}<br/>
            {auxInfo?.titles.map((title) => {
                const teamData = auxInfo.entries[team.number];
                const entry = teamData ? teamData[title] || "" : "";
                if(entry == team.number.toString() || entry.toLowerCase() == team.name.toLowerCase() || entry.trim().length == 0) {
                    return null;
                }
                return <><small>{title}: {entry}</small><br/></>;
            })}
        </td>
    </tr>)}
    </>
}

export default function App() {
    const [matchSelectVal, setMatchSelectVal] = useState<string>();
    const [uiSelectedMatch, setUiSelectedMatch] = useState<string>();
    const [state, setState] = useState<State>("prematch");
    const [prev, setPrev] = useState<FullState[]>([]);
    const [matches, setMatches] = useState<Matches>({});
    const [auxInfo, setAuxInfo] = useState<Result>();
    const [eventInfo, setEventInfo] = useState<Event[]>([]);
    const [alwaysSingleStep, setAlwaysSingleStep] = useState(false);
    const [time, setTime] = useState({div: 'none', min: 0, sec: 0, phase: 'unknown'});
    const [announcementText, setAnnouncementText] = useState("")
    const matchNames = Object.keys(matches);

    const cur: FullState | undefined = matchSelectVal ? [state, matchSelectVal] : undefined;
    const nextState = getNextState(state, matchSelectVal, matches, alwaysSingleStep) || ["prematch", 0];
    let nextMatch = undefined;
    let tryAgain = true;
    let lastTry = false;
    while (tryAgain) {
        if (lastTry) tryAgain = false;
        if (nextState[1] === -1) {
            nextMatch = matchNames[matchNames.indexOf(matchSelectVal || '_not_found') - 1];
        } else if (nextState[1] === 1) {
            nextMatch = matchNames[matchNames.indexOf(matchSelectVal || '_not_found') + 1];
        } else if (nextState[1] === 2) {
            nextMatch = matchNames[matchNames.indexOf(matchSelectVal || '_not_found') + 2];
        } else if (nextState[1] === 0) {
            nextMatch = matchSelectVal;
        }
        if (!nextMatch) {
            if (matchSelectVal) {
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

    const next: FullState | undefined = nextMatch ? [nextState[0], nextMatch] : undefined;

    useEffect(() => {
        const socket = createWebSocket("/matchstream");
        socket.onmessage = (event) => {
            if (event.data === 'ping') return;
            const response: Message = JSON.parse(event.data);
            if (isMatchData(response)) {
                setMatches(Object.fromEntries(response.data.map(m => [matchDisplayName(m.id), m])));
                if (response.data.length > 0) {
                    setUiSelectedMatch((cur) => cur ? cur : matchDisplayName(response.data[0].id))
                }
            } else if (isState(response)) {
                const update: FullState = JSON.parse(response.data);
                setUiSelectedMatch(update[1]);
                setMatchSelectVal(update[1]);
                setState(update[0]);
            } else if (isAuxInfo(response)) {
                setAuxInfo(response.data);
            } else if (isEventInfo(response)) {
                setEventInfo(response.events);
                // } else if(response.type === 'time') {
                //     setTime(response.data);
            } else if (isSingleStep(response)) {
                setAlwaysSingleStep(response.data);
            }

        };
    }, [])

    function clickMatchPlayNext() {
        if (next) {
            setMatchSelectVal(next[1]);
            setUiSelectedMatch(next[1]);
            setState(next[0]);
            if (cur) {
                setPrev((prev) => [...prev, cur]);
            }
            sendCommand(next, matches);
        }
    }

    function clickMatchPlayCur() {
        if (cur) {
            sendCommand(cur, matches);
        }
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
        if (!uiSelectedMatch) {
            return;
        }
        setMatchSelectVal(uiSelectedMatch);
        setState("prematch");
        if (cur) {
            setPrev((prev) => [...prev, cur]);
        }
        sendCommand(["prematch", uiSelectedMatch], matches);
    }

    function clickMatchPlayTimer() {
        if (!uiSelectedMatch) {
            return;
        }
        setMatchSelectVal(uiSelectedMatch);
        setState("timer");
        if (cur) {
            setPrev((prev) => [...prev, cur]);
        }
        sendCommand(["timer", uiSelectedMatch], matches);
    }

    function clickMatchPlayResults() {
        if (!uiSelectedMatch) {
            return;
        }
        setMatchSelectVal(uiSelectedMatch);
        setState("results");
        if (cur) {
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
                                    <Button variant="outline-dark" onClick={clickMatchPlayPrev}
                                            disabled={prev.length === 0 || !prev[prev.length - 1]}>
                                        Previous
                                        <br/>
                                        {prev.length > 0 && prev[prev.length - 1] ? stateToLabel(prev[prev.length - 1][0]) + " " + prev[prev.length - 1][1] : ''}
                                        <br/>
                                        {prev.length > 0 && prev[prev.length - 1] && prev[prev.length - 1][0] === 'results' ? (matches[prev[prev.length - 1][1]].score || 'Match Not Yet Scored') : ''}
                                    </Button>
                                </Col>
                                <Col sm={4} className="d-grid">
                                    <Button variant="outline-dark" onClick={clickMatchPlayCur} disabled={!cur}>
                                        Current
                                        <br/>
                                        {cur ? (stateToLabel(cur[0]) + " " + cur[1]) : ''}
                                        <br/>
                                        {cur && cur[0] === 'results' ? (matches[cur[1]].score || 'Match Not Yet Scored') : ''}
                                    </Button>
                                </Col>
                                <Col sm={4} className="d-grid">
                                    <Button variant="primary" onClick={clickMatchPlayNext}
                                            disabled={!next || (next[0] === 'prematch' && !matches[next[1]]?.isActive) || (next && next[0] === 'results' && !matches[next[1]].score)}>
                                        Next
                                        <br/>
                                        {next ? (stateToLabel(next[0]) + " " + next[1]) : ''}
                                        <br/>
                                        {next && next[0] === 'results' ? (matches[next[1]].score || 'Match Not Yet Scored') : (next && next[0] === 'prematch' && !matches[next[1]]?.isActive ? 'Not loaded for play' : '')}
                                    </Button>
                                </Col>
                            </Row>

                            <h4>{cur ? (stateToLabel(cur[0]) + " " + cur[1]) : ''}</h4>
                            <h4>{cur && cur[0] === 'results' ? (matches[cur[1]].score || 'Match Not Yet Scored') : ''}</h4>
                            <h4>{cur && cur[0] === 'timer' ? `${time['min']}:${('0' + time['sec']).slice(-2)} (${time['phase']})` : ''}</h4>

                            {!cur || cur[0] === 'results' || !matches[cur[1]] ? null : <table className={"w-100 team-table"}>
                                <thead>
                                <tr>
                                    <td>Team #</td>
                                    <td>Team Name</td>
                                    <td style={{width: '60%'}}>Organizations, Sponsors & Awards</td>
                                </tr>
                                </thead>
                                <tbody className={"red"}>
                                    <AllianceTeamInfo alliance={matches[cur[1]].redAlliance} auxInfo={auxInfo} />
                                </tbody>
                                <tbody className={"blue"}>
                                    <AllianceTeamInfo alliance={matches[cur[1]].blueAlliance} auxInfo={auxInfo} />
                                </tbody>
                            </table>}

                            <h3>Manual Control</h3>
                            <div className="button-column">
                                <Form.Group className="mb-2" controlId="match-select">
                                    <Form.Label>Match</Form.Label>
                                    <Form.Select value={uiSelectedMatch}
                                                 onChange={(e) => setUiSelectedMatch(e.target.value)}>
                                        {matchNames.map((value) => <option
                                            value={value}>{value} ({matches[value]?.score ? matches[value].score : "Not yet scored"})</option>)}
                                    </Form.Select>
                                </Form.Group>
                                {uiSelectedMatch ? <>
                                    <Button variant="outline-dark" onClick={clickMatchPlayPrematch}
                                            disabled={!matches[uiSelectedMatch]?.isActive}>Pre-Match
                                        Information {matches[uiSelectedMatch]?.isActive ? null : '(not loaded for play)'}</Button><br/>
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
                                {eventInfo.map((e) =>
                                <Button variant="outline-dark"
                                        onClick={() => sendCommand(['rankings', `D${e.division}`])}>{eventInfo.length > 1 ? (e.division > 0 ? e.name : 'Parent') : 'Show'}</Button>)}
                                <h3>Alliance Selection</h3>
                                {eventInfo.filter((e) => eventInfo.length > 1 ? e.division > 0 : true).map((e) =>
                                <Button variant="outline-dark"
                                        onClick={() => sendCommand(['alliance', `D${e.division}`])}>{eventInfo.length > 1 ? (e.division > 0 ? e.name : 'Parent') : 'Show'}</Button>)}
                                <h3>Elimination Bracket</h3>
                                {eventInfo.map((e) =>
                                    <Button variant="outline-dark"
                                            onClick={() => sendCommand(['elimination', `D${e.division}`])}>{eventInfo.length > 1 ? (e.division > 0 ? e.name : 'Parent') : 'Show'}</Button>)}
                                <h3>Online Results</h3>
                                {eventInfo.map((e) =>
                                    <Button variant="outline-dark"
                                            onClick={() => sendCommand(['online', `D${e.division}`])}>{eventInfo.length > 1 ? (e.division > 0 ? e.name : 'Parent') : 'Show'}</Button>)}
                                <h3>Inspection Status</h3>
                                {eventInfo.map((e) =>
                                    <Button variant="outline-dark"
                                            onClick={() => sendCommand(['status', `D${e.division}`])}>{eventInfo.length > 1 ? (e.division > 0 ? e.name : 'Parent') : 'Show'}</Button>)}
                            </Col>
                            <Col sm={6}>
                                <h3>Sponsor Listing</h3>
                                <Button variant="outline-dark" disabled={eventInfo.length == 0}
                                        onClick={() => sendCommand(['sponsor', `D${eventInfo[0]?.division}`])}>Show</Button><br/>
                                <h3>Blank</h3>
                                <Button variant="outline-dark" disabled={eventInfo.length == 0}
                                        onClick={() => sendCommand(['blank', `D${eventInfo[0]?.division}`])}>Show</Button><br/>
                                <h3>Video only</h3>
                                <Button variant="outline-dark" disabled={eventInfo.length == 0}
                                        onClick={() => sendCommand(['video', `D${eventInfo[0]?.division}`])}>Show</Button><br/>
                                <h3>Wi-Fi reminder</h3>
                                <Button variant="outline-dark" disabled={eventInfo.length == 0}
                                        onClick={() => sendCommand(['wifi', `D${eventInfo[0]?.division}`])}>Show</Button><br/>
                                <h3>Audience Key</h3>
                                <Button variant="outline-dark" disabled={eventInfo.length == 0}
                                        onClick={() => sendCommand(['key', `D${eventInfo[0]?.division}`])}>Show</Button><br/>
                                <h3>Safety and Security</h3>
                                <Button variant="outline-dark" disabled={eventInfo.length == 0}
                                        onClick={() => sendCommand(['safety_security', `D${eventInfo[0]?.division}`])}>Show</Button><br/>
                                <h3>Slideshow (sponsors/wifi/key/safety)</h3>
                                <Button variant="outline-dark" disabled={eventInfo.length == 0}
                                        onClick={() => sendCommand(['slideshow', `D${eventInfo[0]?.division}`])}>Show</Button><br/>
                                <h3>Announcement</h3>
                                <textarea value={announcementText}
                                          onChange={(e) => setAnnouncementText(e.target.value)}></textarea>
                                <Button variant="outline-dark" disabled={eventInfo.length == 0}
                                        onClick={() => sendCommand(['announcement', `D${eventInfo[0]?.division}`, announcementText])}>Show</Button><br/>
                            </Col>
                        </Row>
                    </Tab.Pane>
                </Tab.Content>
            </Tab.Container>
        </div>
    </>;
}