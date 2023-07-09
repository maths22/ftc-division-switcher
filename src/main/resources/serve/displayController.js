import { h, render } from 'preact';
import { useState, useEffect } from 'preact/hooks';
import htm from 'htm';

const html = htm.bind(h);


function sendCommand(args, matches) {
    fetch("/api?" + new URLSearchParams({division: args[1][1], display: args[0], match: (matches && matches[args[1]].num) || args[2], data: JSON.stringify(args)}).toString());
}

function App() {
    const [matchSelectVal, setMatchSelectVal] = useState();
    const [uiSelectedMatch, setUiSelectedMatch] = useState();
    const [state, setState] = useState("prematch");
    const [prev, setPrev] = useState([]);
    const [matches, setMatches] = useState({});
    const [auxInfo, setAuxInfo] = useState({});
    const [alwaysSingleStep, setAlwaysSingleStep] = useState(false);
    const [time, setTime] = useState({div: 'none'});
    const matchNames = Object.keys(matches);

    console.log(matches)

    const cur = [state, matchSelectVal];
    const nextState = getNextState(state, matchSelectVal, matchNames, alwaysSingleStep) || ["prematch", 0];
    let nextMatch = undefined;
    let tryAgain = true;
    let lastTry = false;
    while (tryAgain) {
        if (lastTry) tryAgain = false;
        if (nextState[1] === -1) {
            nextMatch = matchNames[matchNames.indexOf(matchSelectVal) - 1];
        } else if (nextState[1] === 1) {
            nextMatch = matchNames[matchNames.indexOf(matchSelectVal) + 1];
        } else if (nextState[1] === 2) {
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

    const next = [nextState[0], nextMatch];

    useEffect(() => {
        const socket = createWebSocket("/matchstream");
        socket.onmessage = function (event) {
            if(event.data === 'ping') return;
            const response = JSON.parse(event.data);
            if(response.type === 'matchData') {
                setMatches(Object.fromEntries(response.data.map(m => [m.id, m])));
            } else if(response.type === 'state') {
                const update = JSON.parse(response.data);
                setUiSelectedMatch(update[1]);
                setMatchSelectVal(update[1]);
                setState(update[0]);
            } else if(response.type === 'auxInfo') {
                setAuxInfo(response.data);
            } else if(response.type === 'time') {
                setTime(response.data);
            } else if(response.type === 'singleStep') {
                setAlwaysSingleStep(parseInt(response.data));
            }

        };
        socket.onopen = function () {
            fetch("/load");
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
        setMatchSelectVal(uiSelectedMatch);
        setState("prematch");
        if(cur[1]) {
            setPrev((prev) => [...prev, cur]);
        }
        sendCommand(["prematch", uiSelectedMatch], matches);
    }

    function clickMatchPlayTimer() {
        setMatchSelectVal(uiSelectedMatch);
        setState("timer");
        if(cur[1]) {
            setPrev((prev) => [...prev, cur]);
        }
        sendCommand(["timer", uiSelectedMatch], matches);
    }

    function clickMatchPlayResults() {
        setMatchSelectVal(uiSelectedMatch);
        setState("results");
        if(cur[1]) {
            setPrev((prev) => [...prev, cur]);
        }
        sendCommand(["results", uiSelectedMatch], matches);
    }

    return html`<h1>FTC Display Switcher</h1>
    <div>

        <!-- Nav tabs -->
        <ul class="nav nav-pills" role="tablist">
            <li class="nav-item" role="presentation">
                <button class="nav-link active" id="match-play-tab" data-bs-toggle="pill" data-bs-target="#match-play" type="button" role="tab" aria-controls="home" aria-selected="true">Match Play</button>
            </li>
            <li class="nav-item" role="presentation">
                <button class="nav-link" id="display-tab" data-bs-toggle="tab" data-bs-target="#display" type="button" role="tab" aria-controls="profile" aria-selected="false">Display</button>
            </li>
        </ul>

        <!-- Tab panes -->
        <div class="tab-content">
            <div role="tabpanel" class="tab-pane active form" id="match-play">
                <div class="container-fluid">

                    <h3>Automatic Control</h3>
                    <div class="row auto-btns button-column">
                        <div class="col-sm-4 d-grid">
                            <button type="button" class="btn btn-outline-dark btn-block" onclick="${clickMatchPlayPrev}" disabled=${prev.length === 0 || !prev[prev.length - 1]}>
                                Previous
                                <br/>
                                ${prev.length > 0 && prev[prev.length - 1] ? stateToLabel(prev[prev.length - 1][0]) + " " + prev[prev.length - 1][1] : ''}
                                <br/>
                                ${prev.length > 0 && prev[prev.length - 1] && prev[prev.length - 1][0] === 'results' ? (matches[prev[prev.length - 1][1]].score || 'Match Not Yet Scored') : ''}
                            </button>
                        </div>
                        <div class="col-sm-4 d-grid">
                            <button type="button" class="btn btn-outline-dark btn-block" onclick="${clickMatchPlayCur}" disabled=${!cur[1]}>
                                Current
                                <br/>
                                ${cur[1] ? (stateToLabel(cur[0]) + " " + cur[1]) : ''}
                                <br/>
                                ${cur[0] === 'results' ? (matches[cur[1]].score || 'Match Not Yet Scored') : ''}
                            </button>
                        </div>
                        <div class="col-sm-4 d-grid">
                            <button type="button" class="btn btn-primary btn-block" onclick="${clickMatchPlayNext}" disabled=${!next[1] || (next[0] === 'prematch' && !matches[next[1]]?.isActive)}>
                                Next
                                <br/>
                                ${next[1] ? (stateToLabel(next[0]) + " " + next[1]) : ''}
                                <br/>
                                ${next[0] === 'results' ? (matches[next[1]].score || 'Match Not Yet Scored') : (next[0] === 'prematch' && !matches[next[1]]?.isActive ? 'Not loaded for play' : '')}
                            </button>
                        </div>
                    </div>

                    <h4>${cur[1] ? (stateToLabel(cur[0]) + " " + cur[1]) : ''}</h4>
                    <h4>${cur[0] === 'results' ? (matches[cur[1]].score || 'Match Not Yet Scored') : ''}</h4>
                    <h4>${cur[0] === 'timer' ? `${time['min']}:${('0' + time['sec']).slice(-2)} (${time['phase']})` : ''}</h4>
                    ${cur[0] === 'results' ? null : html`<table class="table">
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
                            ${matches[cur[1]]?.redAlliance.map((team, idx) => html`<tr>
                                <td>Red ${idx + 1}</td>
                                <td>${team.number}</td>
                                <td>${team.name}</td>
                                <td>${team.school}</td>
                                <td>${team.city}</td>
                                <td>${team.state}</td>
                                <td>${team.country}</td>
                            </tr>`)}
                            ${matches[cur[1]]?.blueAlliance.map((team, idx) => html`<tr>
                                <td>Blue ${idx + 1}</td>
                                <td>${team.number}</td>
                                <td>${team.name}</td>
                                <td>${team.school}</td>
                                <td>${team.city}</td>
                                <td>${team.state}</td>
                                <td>${team.country}</td>
                            </tr>`)}
                        </tbody>
                    </table>
                    <table class="table">
                        <thead>
                        <tr>
                            ${auxInfo.titles?.map((title) => html`<th>${title}</th>`)}
                        </tr>
                        </thead>
                        <tbody>
                            ${matches[cur[1]]?.redAlliance.map((team) => auxInfo.titles ? html`<tr>
                                ${auxInfo.titles.map((title) => html`<td>${auxInfo.entries[team.number] ? auxInfo.entries[team.number][title] : ""}</td>`)}
                            </tr>` : null)}
                            ${matches[cur[1]]?.blueAlliance.map((team) => auxInfo.titles ? html`<tr>
                                ${auxInfo.titles.map((title) => html`<td>${auxInfo.entries[team.number] ? auxInfo.entries[team.number][title] : ""}</td>`)}
                            </tr>` : null)}
                        </tbody>
                    </table>`}

                    <h3>Manual Control</h3>
                    <div class="button-column">
                        <div class="form-group">
                            <label for="match-select" >Match</label>
                            <select class="form-control" value=${uiSelectedMatch} onchange=${(e) => setUiSelectedMatch(e.target.value)}>
                                ${matchNames.map((value) => html`<option value="${value}">${value} (${matches[value]?.score ? matches[value].score : "Not yet scored"})</option>`)}
                            </select>
                        </div>
                        <button type="button" class="btn btn-outline-dark" onclick=${clickMatchPlayPrematch} disabled=${!matches[uiSelectedMatch]?.isActive}>Pre-Match Information ${matches[uiSelectedMatch]?.isActive ? null : '(not loaded for play)'}</button><br/>
                        <!--<button type="button" class="btn btn-outline-dark" onclick="clickMatchPlayTimer()">Match Timer</button><br/>-->
                        <button type="button" class="btn btn-outline-dark" onclick=${clickMatchPlayResults}>Results</button><br/>

                    </div>
                </div>
            </div>
            <div role="tabpanel" class="tab-pane form" id="display">
                <div class="container-fluid row">
                    <div class="col-sm-6">
                        <h3>Rankings</h3>
                        <button type="button" class="btn btn-outline-dark" onclick=${() => sendCommand(['rankings', 'D1'])}>D1</button>
                        <button type="button" class="btn btn-outline-dark" onclick=${() => sendCommand(['rankings', 'D2'])}>D2</button><br/>
                        <!--<button type="button" class="btn btn-outline-dark" onclick="sendCommand(['rankings', 'Dr'])">All</button><br/>-->
                        <h3>Alliance Selection</h3>
                        <button type="button" class="btn btn-outline-dark" onclick=${() => sendCommand(['alliance', 'D1'])}>D1</button>
                        <button type="button" class="btn btn-outline-dark" onclick=${() => sendCommand(['alliance', 'D2'])}>D2</button><br/>
                        <h3>Elimination Ladder</h3>
                        <button type="button" class="btn btn-outline-dark" onclick=${() => sendCommand(['elimination', 'D1'])}>D1</button>
                        <button type="button" class="btn btn-outline-dark" onclick=${() => sendCommand(['elimination', 'D2'])}>D2</button><br/>
                        <h3>Online Results</h3>
                        <button type="button" class="btn btn-outline-dark" onclick=${() => sendCommand(['online', 'D1'])}>D1</button>
                        <button type="button" class="btn btn-outline-dark" onclick=${() => sendCommand(['online', 'D2'])}>D2</button><br/>
                    </div>
                    <div class="col-sm-6">
                        <!--                    case "online":-->
                        <h3>Sponsor Listing</h3>
                        <button type="button" class="btn btn-outline-dark" onclick=${() => sendCommand(['sponsor', 'D1'])}>Show</button><br/>
                        <h3>Blank</h3>
                        <button type="button" class="btn btn-outline-dark" onclick=${() => sendCommand(['blank', 'D1'])}>Show</button><br/>
                        <h3>Video only</h3>
                        <button type="button" class="btn btn-outline-dark" onclick=${() => sendCommand(['video', 'D1'])}>Show</button><br/>
                        <h3>Wi-Fi reminder</h3>
                        <button type="button" class="btn btn-outline-dark" onclick=${() => sendCommand(['wifi', 'D1'])}>Show</button><br/>
                        <h3>Audience Key</h3>
                        <button type="button" class="btn btn-outline-dark" onclick=${() => sendCommand(['key', 'D1'])}>Show</button><br/>
                        <h3>Announcement</h3>
                        <textarea id="announcementText"></textarea>
                        <button type="button" class="btn btn-outline-dark" onclick=${() => sendCommand(['announcement', 'D1', document.getElementById('announcementText').value])}>Show</button><br/>
                    </div>
                </div>
            </div>
        </div>

    </div>`;
}

render(html`<${App} />`, document.body);

function createWebSocket(path) {
    const protocolPrefix = (window.location.protocol === 'https:') ? 'wss:' : 'ws:';
    const ret = new window.persistentwebsocket.PersistentWebsocket(protocolPrefix + '//' + location.host + path, {
        pingSendFunction: function(pws) { pws.send("ping")},
        pingIntervalSeconds: 10
    });
    ret.open();
    return ret;
}

function stateToLabel(state) {
    switch(state) {
        case "prematch":
            return "Pre-Match/Active:";
        case "results":
            return "Result:";
    }
}

function singleStep(state) {
    if(state === "prematch") {
        return ["results", 0];
    } else if(state === "results") {
        return ["prematch", 1];
    }
}

function getNextState(state, selectedMatch, matches, alwaysSingleStep) {
    if(!selectedMatch) {
        return;
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
}