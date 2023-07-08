package com.maths22.ftc;

import com.google.common.collect.ImmutableList;
import kong.unirest.*;
import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONObject;

import java.util.*;

public class FtcScoringClient {
    private final UnirestInstance unirest;
    private boolean loggedIn;
    private String basePath;
    private String event;
    private final int divisionId;

    public FtcScoringClient(int divisionId) {
        this.divisionId = divisionId;
        this.unirest = Unirest.spawnInstance();
        this.loggedIn = false;
    }


    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
        loggedIn = false;
    }

    public List<String> getEvents() {
        List<String> events = new ArrayList<>();
        get("api/v1/events").getObject().getJSONArray("eventCodes").forEach((obj) -> events.add(String.valueOf(obj)));
        return events;
    }

    public boolean login(String username, String password) {
        HttpResponse<String> response = unirest.post("http://" + basePath + "/callback/")
                .field("username", username)
                .field("password", password == null ? "" : password)
                .field("submit", "Login")
                .field("client_name", "FormClient")
                .asString();
        if(response.getBody().contains("alert-danger")) {
            return false;
        }

        try {
            get("/event/" + event + "/control/schedule/");
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
        loggedIn = true;
        return true;
    }

    private JsonNode get(String path) {
        try {
            HttpResponse<JsonNode> resp = unirest.get("http://" + basePath + "/" +  path)
                    .asJson();

            if(HttpStatus.OK == resp.getStatus()) {
                return resp.getBody();
            }
        } catch (UnirestException e) {
            e.printStackTrace();
        }
        return null;
    }

    private JsonNode post(String path, String payload) {
        try {
                HttpResponse<JsonNode> resp = unirest.post("http://" + basePath + "/" +  path)
                    .body(payload)
                    .asJson();

            if(HttpStatus.OK == resp.getStatus()) {
                return resp.getBody();
            }
        } catch (UnirestException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void showSponsors() {
        if(!loggedIn) {
            return;
        }
        post("event/" + event + "/control/sponsors/", "");
    }

    public void showBracket() {
        if(!loggedIn) {
            return;
        }
        post("event/" + event + "/control/bracket/", "");
    }

    public void showSelection() {
        if(!loggedIn) {
            return;
        }
        post("event/" + event + "/control/selection/show/", "");
    }

    public void showRanks() {
        if(!loggedIn) {
            return;
        }
        post("event/" + event + "/control/ranks/", "");
    }

    public void basicCommand(String cmd) {
        if(!loggedIn) {
            return;
        }
        post("event/" + event + "/control/command/" + cmd + "/", "");
    }

    public void showMessage(String m) {
        if(!loggedIn) {
            return;
        }
        unirest.post("http://" + basePath + "/event/" + event + "/control/message/").field("msg", m).asEmpty();
    }

    public void showMatch(String m) {
        if(!loggedIn) {
            return;
        }
        post("event/" + event + "/control/preview/" + m + "/", "");
    }

    public void showResults(String m) {
        if(!loggedIn) {
            return;
        }
        post("event/" + event + "/control/results/" + m + "/", "");
    }

    public List<Match> getMatches() {
        List<Match> ret = new ArrayList<>();
        ret.addAll(getQualMatches());
        ret.addAll(getElimMatches());
        return ret;
    }

    private List<Match> getQualMatches() {
        List<Match> ret = new ArrayList<>();
        JSONArray matches = Objects.requireNonNull(get("api/v1/events/" + event + "/matches/")).getObject().getJSONArray("matches");
        for(Object ob : matches) {
            JSONObject m = (JSONObject) ob;
            Match match = new Match();
            int num = m.getInt("matchNumber");
            match.setId("D" + divisionId + ": Q-" + num);
            match.setNum(num);
            List<Team> redAlliance = new ArrayList<>();
            List<Team> blueAlliance = new ArrayList<>();
            redAlliance.add(getTeam(m.getJSONObject("red").getInt("team1")));
            redAlliance.add(getTeam(m.getJSONObject("red").getInt("team2")));
            redAlliance.add(getTeam(m.getJSONObject("red").getInt("team3")));
            blueAlliance.add(getTeam(m.getJSONObject("blue").getInt("team1")));
            blueAlliance.add(getTeam(m.getJSONObject("blue").getInt("team2")));
            blueAlliance.add(getTeam(m.getJSONObject("blue").getInt("team3")));
            match.setRedAlliance(redAlliance);
            match.setBlueAlliance(blueAlliance);
            if(m.getBoolean("finished")) {
                JSONObject details = Objects.requireNonNull(get("api/v1/events/" + event + "/matches/" + num + "/")).getObject();
                int redScore = details.getInt("redScore");
                int blueScore = details.getInt("blueScore");
                char desc = 'T';
                if (redScore > blueScore) desc = 'R';
                if (redScore < blueScore) desc = 'B';
                match.setScore(redScore + "-" + blueScore + " " + desc);
             }
            ret.add(match);
        }
        return ret;
    }

    private List<Match> getElimMatches() {
        List<Match> ret = new ArrayList<>();
        JsonNode elimsMatches = get("api/v2/events/" + event + "/elims/");
        if(elimsMatches == null) {
            return ImmutableList.of();
        }
        JSONArray matches = Objects.requireNonNull(elimsMatches).getObject().getJSONArray("matches");
        JSONArray alliances = Objects.requireNonNull(get("api/v1/events/" + event + "/elim/alliances/")).getObject().getJSONArray("alliances");
        Map<Integer, JSONObject> allianceMap = new HashMap<>();
        alliances.forEach(ob -> {
            JSONObject a = (JSONObject) ob;
            int seed = a.getInt("seed");
            allianceMap.put(seed, a);
        });
        for(Object ob : matches) {
            JSONObject m = (JSONObject) ob;
            Match match = new Match();
            String name = m.getString("matchName");
            match.setId("D" + divisionId + ": " + name);
            int num = m.getInt("matchNumber");
            match.setNum(num);
            List<Team> redAlliance = new ArrayList<>();
            List<Team> blueAlliance = new ArrayList<>();
            JSONObject redAll = allianceMap.get(m.getJSONObject("red").getInt("seed"));
            JSONObject blueAll = allianceMap.get(m.getJSONObject("blue").getInt("seed"));
            redAlliance.add(getTeam(redAll.getInt("captain")));
            redAlliance.add(getTeam(redAll.getInt("pick1")));
            redAlliance.add(getTeam(redAll.getInt("pick2")));
            if (redAll.getInt("backup") > 0) {
                redAlliance.add(getTeam(redAll.getInt("backup")));
            }
            blueAlliance.add(getTeam(blueAll.getInt("captain")));
            blueAlliance.add(getTeam(blueAll.getInt("pick1")));
            blueAlliance.add(getTeam(blueAll.getInt("pick2")));
            if (blueAll.getInt("backup") > 0) {
                blueAlliance.add(getTeam(blueAll.getInt("backup")));
            }
            match.setRedAlliance(redAlliance);
            match.setBlueAlliance(blueAlliance);
            if(m.getBoolean("finished")) {
                JSONObject details = Objects.requireNonNull(get("api/v2/events/" + event + "/elims/" + name.toLowerCase() + "/")).getObject();
                int redScore = details.getInt("redScore");
                int blueScore = details.getInt("blueScore");
                char desc = 'T';
                if (redScore > blueScore) desc = 'R';
                if (redScore < blueScore) desc = 'B';
                match.setScore(redScore + "-" + blueScore + " " + desc);
            }
            ret.add(match);
        }
        return ret;
    }

    private Map<Integer, Team> teams = new HashMap<>();

    private Team getTeam(int id) {
        if(id == -1) return null;
        Team ret = teams.get(id);
        if(ret == null) {
            JsonNode team = get("api/v1/events/" + event + "/teams/" + id);
            JSONObject tm = Objects.requireNonNull(team).getObject();
            if(tm.has("errorCode") && "NO_SUCH_TEAM".equals(tm.getString("errorCode"))) {
                ret = new Team();
                ret.setNumber(id);
            } else if(tm.isNull("errorCode")) {
                ret = new Team();
                ret.setNumber(id);
                ret.setName(tm.getString("name"));
                ret.setOrganization(tm.getString("school"));
                ret.setCity(tm.getString("city"));
                ret.setState(tm.getString("state"));
                ret.setCountry(tm.getString("country"));
                ret.setRookie(tm.getInt("rookie"));
            }

            teams.put(id, ret);
        }
        return ret;
    }
}
