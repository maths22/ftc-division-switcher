
function createWebSocket(path) {
    var protocolPrefix = (window.location.protocol === 'https:') ? 'wss:' : 'ws:';
    var ret = new window.persistentwebsocket.PersistentWebsocket(protocolPrefix + '//' + location.host + path, {
        pingSendFunction: function(pws) { pws.send("ping")},
        pingIntervalSeconds: 10
    });
    ret.open();
    return ret;
}

var matchSelectVal = '';
var state = "prematch";
var prev = [];
var next = [];
var cur = [];
var matches = [];
var scores = {};
var alliances = {};
var auxInfo = {};
var matchNums = {};
var alwaysSingleStep = false;
var time = {div: 'none'};

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

function getNextState() {
    var selectedMatch = $("#match-select").val();
    var phase = selectedMatch.replace(/[^A-Z]/g, '').substring(1);
    var nextMatch = matches[matches.findIndex(function(element) {return element === selectedMatch }) + 1];
    var haveD1AndD2 = matches.findIndex(function(element) { return element.indexOf("D1") >= 0 }) >= 0 &&
        matches.findIndex(function(element) { return element.indexOf("D2") >= 0 }) >= 0;
    if(alwaysSingleStep || (phase !== 'Q' && nextSeries === thisSeries)) {
        return singleStep(state);
    }
    if(nextMatch) {
        var nextSeries = nextMatch.substring(0, nextMatch.lastIndexOf("-") + 1);
        var thisSeries = selectedMatch.substring(0, selectedMatch.lastIndexOf("-") + 1);
        if(phase !== 'Q' && nextSeries === thisSeries) {
            return singleStep(state);
        }
    } else {
        if(phase === 'F' && !haveD1AndD2) {
            return singleStep(state);
        }
    }
    if(state === "prematch") {
        var numofphase = 0;
        $.each(matches, function( index, value ) {
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
        var remainingInPhase = 0;
        var found = false;
        $.each(matches, function( index, value ) {
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

function updatePrevNextCur() {
    cur = [state, matchSelectVal];
    var nextState = getNextState();
    var nextMatch = undefined;
    var tryAgain = true;
    var lastTry = false;
    var matchSelect = $("#match-select");
    while (tryAgain) {
        if (lastTry) tryAgain = false;
        if (nextState[1] === -1) {
            nextMatch = matchSelect.find(":selected").prev().val();
        } else if (nextState[1] === 1) {
            nextMatch = matchSelect.find(":selected").next().val();
        } else if (nextState[1] === 2) {
            nextMatch = matchSelect.find(":selected").next().next().val();
        } else if (nextState[1] === 0) {
            nextMatch = matchSelect.find(":selected").val();
        }
        if (!nextMatch) {
            nextState[0] = "results";
            nextState[1] = 1;
            lastTry = true;
        } else {
            tryAgain = false;
        }
    }

    next = [nextState[0], nextMatch];
    if (prev[prev.length - 1]) {
        if (prev[prev.length - 1][1]) {
            $("#prevDescr").text(stateToLabel(prev[prev.length - 1][0]) + " " + prev[prev.length - 1][1]);
            $("#prevButton").prop("disabled", false);
        } else {
            $("#prevDescr").text("");
            $("#prevButton").prop("disabled", true);
        }
    }
    if(cur[1]) {
        $("#current-display").text(stateToLabel(cur[0]) + " " + cur[1]);
        $("#curDescr").text(stateToLabel(cur[0]) + " " + cur[1]);
        $("#curButton").prop("disabled", false);
    } else {
        $("#current-display").text("");
        $("#curDescr").text("");
        $("#curButton").prop("disabled", true);
    }
    if(next[1]) {
        $("#nextDescr").text(stateToLabel(next[0])  + " " + next[1]);
        $("#nextButton").prop("disabled", false);
    } else {
        $("#nextDescr").text("");
        $("#nextButton").prop("disabled", true);
    }

    if(next[0] === 'results') {
        if(scores[next[1]]) {
            $("#scoreAuto").text(scores[next[1]]);
        } else {
            $("#scoreAuto").text("Match Not Yet Scored");
        }
    } else {
        $("#scoreAuto").text("");
    }

    var teamList = $("#team-list");
    var teamAuxList = $("#team-aux-list");
    if(cur[0] === 'results') {
        if(scores[cur[1]]) {
            $("#scoreCur").text(scores[cur[1]]);
            $("#current-score").text(scores[cur[1]]);
        } else {
            $("#scoreCur").text("Match Not Yet Scored");
            $("#current-score").text("Match Not Yet Scored");
        }
        teamList.parent().addClass("hidden");
        teamAuxList.parent().addClass("hidden");
    } else {
        $("#scoreCur").text("");
        $("#current-score").text("");
        teamList.parent().removeClass("hidden");
        teamAuxList.parent().removeClass("hidden");
        teamList.children().remove();
        teamAuxList.children().remove();
        var auxHeadingRow = $("#aux-row");
        auxHeadingRow.children().remove();
        if(auxInfo.titles) {
            $.each(auxInfo.titles, function (key, value) {
                auxHeadingRow.append($("<th></th>").text(value));
            });
        }
        $.each(alliances[cur[1]].red, function(key, value) {
            teamList
                .append($("<tr></tr>")
                    .append($("<td></td>").text("Red " + (key + 1)))
                    .append($("<td></td>").text(value.number))
                    .append($("<td></td>").text(value.name))
                    .append($("<td></td>").text(value.organization))
                    .append($("<td></td>").text(value.city))
                    .append($("<td></td>").text(value.state))
                    .append($("<td></td>").text(value.country)));
            if(auxInfo.titles) {
                var auxRow = $("<tr></tr>");
                $.each(auxInfo.titles, function (k, v) {
                    auxRow.append($("<td></td>").text(auxInfo.entries[value.number] ? auxInfo.entries[value.number][v] : ""));
                });
                teamAuxList.append(auxRow);
            }
        });
        $.each(alliances[cur[1]].blue, function(key, value) {
            teamList
                .append($("<tr></tr>")
                    .append($("<td></td>").text("Blue " + (key + 1)))
                    .append($("<td></td>").text(value.number))
                    .append($("<td></td>").text(value.name))
                    .append($("<td></td>").text(value.organization))
                    .append($("<td></td>").text(value.city))
                    .append($("<td></td>").text(value.state))
                    .append($("<td></td>").text(value.country)));
            if(auxInfo.titles) {
                var auxRow = $("<tr></tr>");
                $.each(auxInfo.titles, function (k, v) {
                    auxRow.append($("<td></td>").text(auxInfo.entries[value.number] ? auxInfo.entries[value.number][v] : ""));
                });
                teamAuxList.append(auxRow);
            }
        });
    }
    var timer = $("#timer");
    if(cur[0] == 'timer' && cur[1][1] == time['div']) {
        timer.removeClass("hidden");
        timer.text(time['min'] + ":" + ('0' + time['sec']).slice(-2) + " (" + time['phase'] + ")");
    } else {
        timer.addClass("hidden");
    }

    if(prev[prev.length - 1]) {
        if (prev[prev.length - 1][0] === 'results') {
            if (scores[prev[prev.length - 1][1]]) {
                $("#scorePrev").text(scores[prev[prev.length - 1][1]]);
            } else {
                $("#scorePrev").text("Match Not Yet Scored");
            }
        } else {
            $("#scorePrev").text("");
        }
    }

}

function clickMatchPlayNext() {
    $("#match-select").val(next[1]);
    matchSelectVal = $("#match-select").val();
    state = next[0];
    prev.push(cur);
    updatePrevNextCur();
    sendCommand(cur);
}

function clickMatchPlayCur() {
    sendCommand(cur);
}

function clickMatchPlayPrev() {
    var elem = prev.pop();
    $("#match-select").val(elem[1]);
    matchSelectVal = $("#match-select").val();
    state = elem[0];
    // console.log(state + ":" + elem[1]);
    updatePrevNextCur();
    sendCommand(cur);
}

function clickMatchPlayPrematch() {
    matchSelectVal = $("#match-select").val();
    state = "prematch";
    prev.push(cur);
    updatePrevNextCur();
    sendCommand(cur);
}

function clickMatchPlayTimer() {
    matchSelectVal = $("#match-select").val();
    state = "timer";
    prev.push(cur);
    updatePrevNextCur();
    sendCommand(cur);
}

function clickMatchPlayResults() {
    matchSelectVal = $("#match-select").val();
    state = "results";
    prev.push(cur);
    updatePrevNextCur();
    sendCommand(cur);
}

function sendCommand(args) {

    $.get("/api", {division: args[1][1], display: args[0], match: matchNums[args[1]] || args[2], data: JSON.stringify(args)});
}

$(function() {

    var socket = createWebSocket("/matchstream");
    socket.onmessage = function (event) {
        if(event.data === 'ping') return;
        var response = JSON.parse(event.data);
        if(response.type === 'matchData') {
            var fullMatches = response.data;
            scores = {};
            alliances = {};
            matchNums = {};
            $.each(fullMatches, function(_, m) {
                scores[m.id] = m.score;
                var myAlliances = {};
                myAlliances['red'] = m.redAlliance;
                myAlliances['blue'] = m.blueAlliance;
                alliances[m.id] = myAlliances;
                matchNums[m.id] = m.num;
            });
            matches = fullMatches.map(function(val) {return val.id});
            var matchSelect = $("#match-select");
            var selectedMatch = matchSelect.val();
            matchSelect.children().remove();
            $.each(matches, function(key, value) {
                matchSelect
                    .append($("<option></option>")
                        .attr("value",value)
                        .text(value + " (" + (scores[value] ? scores[value] : "Not yet scored") + ")"));
            });
            if(selectedMatch === null) {
                matchSelect.val(matches[0]);
            } else {
                matchSelect.val(selectedMatch);
            }
            updatePrevNextCur();
        } else if(response.type === 'state') {
            var update = JSON.parse(response.data);
            $("#match-select").val(update[1]);
            matchSelectVal = $("#match-select").val();
            state = update[0];
            // if(prev.length > 0 && (prev[prev.length-1][0] !== update[0] || prev[prev.length-1][1] !== update[1])) {
            //     prev.push(update);
            // }
            updatePrevNextCur();
        } else if(response.type === 'auxInfo') {
            auxInfo = response.data;
            updatePrevNextCur();
        } else if(response.type === 'time') {
            time = response.data;
            updatePrevNextCur();
        } else if(response.type === 'singleStep') {
            alwaysSingleStep = parseInt(response.data);
            updatePrevNextCur();
        }

    };
    socket.onopen = function () {
        $.get("/load");
    };

});