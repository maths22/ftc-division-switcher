package com.maths22.ftc;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import io.undertow.util.StatusCodes;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public class FtcScoringClient {
    private String basePath;
    private String event;
    private final int divisionId;

    public FtcScoringClient(int divisionId) {
        this.divisionId = divisionId;
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
    }

    public List<String> getEvents() {
        List<String> events = new ArrayList<>();
        get("apiv1/events").getObject().getJSONArray("eventCodes").forEach((obj) -> events.add(String.valueOf(obj)));
        return events;
    }

    private JsonNode get(String path) {
        try {
            HttpResponse<JsonNode> resp = Unirest.get("http://" + basePath + "/" +  path)
                    .asJson();

            if(StatusCodes.OK == resp.getStatus()) {
                return resp.getBody();
            }
        } catch (UnirestException e) {
            e.printStackTrace();
        }
        return null;
    }

    private JsonNode post(String path, String payload) {
        try {
                HttpResponse<JsonNode> resp = Unirest.post("http://" + basePath + "/" +  path)
                    .body(payload)
                    .asJson();

            if(StatusCodes.OK == resp.getStatus()) {
                return resp.getBody();
            }
        } catch (UnirestException e) {
            e.printStackTrace();
        }
        return null;
    }


    public void sendDisplay(String screen, String match) {
        if(basePath == null) {
            return;
        }
        //TODO special case rankings, blank
        JSONObject obj = new JSONObject();
        obj.put("screen", screen);
        obj.put("match", match.replaceFirst("-",""));
        post("_il_api/" + event + "/set_display/", obj.toString());
    }

    public List<Match> getMatches() {
        List<Match> ret = new ArrayList<>();
        ret.addAll(getQualMatches());
        ret.addAll(getElimMatches());
        return ret;
    }

    private List<Match> getQualMatches() {
        List<Match> ret = new ArrayList<>();
        JSONArray matches = Objects.requireNonNull(get("apiv1/events/" + event + "/matches/")).getObject().getJSONArray("matches");
        for(Object ob : matches) {
            JSONObject m = (JSONObject) ob;
            Match match = new Match();
            int num = m.getInt("matchNumber");
            match.setId("D" + divisionId + ": Q-" + num);
            List<Team> redAlliance = new ArrayList<>();
            List<Team> blueAlliance = new ArrayList<>();
            redAlliance.add(getTeam(m.getJSONObject("red").getInt("team1")));
            redAlliance.add(getTeam(m.getJSONObject("red").getInt("team2")));
            blueAlliance.add(getTeam(m.getJSONObject("blue").getInt("team1")));
            blueAlliance.add(getTeam(m.getJSONObject("blue").getInt("team2")));
            match.setRedAlliance(redAlliance);
            match.setBlueAlliance(blueAlliance);
            if(m.getBoolean("finished")) {
                JSONObject details = Objects.requireNonNull(get("apiv1/events/" + event + "/matches/" + num)).getObject();
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
        JSONArray matches = Objects.requireNonNull(get("_il_api/" + event + "/elim_matches/")).getArray();
        for(Object ob : matches) {
            JSONObject m = (JSONObject) ob;
            Match match = new Match();
            String matchStr = m.getString("match");
            matchStr = String.join("-", matchStr.split("(?<=[A-Z])(?=[0-9])"));
            match.setId("D" + divisionId + ": " + matchStr);
            List<Team> redAlliance = new ArrayList<>();
            List<Team> blueAlliance = new ArrayList<>();
            redAlliance.add(getTeam(m.getJSONObject("red").getInt("captain")));
            redAlliance.add(getTeam(m.getJSONObject("red").getInt("pick1")));
            if (m.getJSONObject("red").getInt("pick2") != -1) {
                redAlliance.add(getTeam(m.getJSONObject("red").getInt("pick2")));
            }
            blueAlliance.add(getTeam(m.getJSONObject("blue").getInt("captain")));
            blueAlliance.add(getTeam(m.getJSONObject("blue").getInt("pick1")));
            if (m.getJSONObject("blue").getInt("pick2") != -1) {
                blueAlliance.add(getTeam(m.getJSONObject("blue").getInt("pick2")));
            }
            match.setRedAlliance(redAlliance);
            match.setBlueAlliance(blueAlliance);
            match.setScore(m.has("winString") ? m.getString("winString") : null);
            ret.add(match);
        }
        return ret;
    }

    private Map<Integer, Team> teams = new HashMap<>();

    private Team getTeam(int id) {
        if(id == -1) return null;
        Team ret = teams.get(id);
        if(ret == null) {
            JSONObject tm = Objects.requireNonNull(get("apiv1/teams/" + id)).getObject();
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
