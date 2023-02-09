package com.maths22.ftc;

import org.json.JSONObject;

import java.util.List;
import java.util.stream.Collectors;

public class Match {
    public String getScore() {
        return score;
    }

    public void setScore(String score) {
        this.score = score;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<Team> getRedAlliance() {
        return redAlliance;
    }

    public void setRedAlliance(List<Team> redAlliance) {
        this.redAlliance = redAlliance;
    }

    public List<Team> getBlueAlliance() {
        return blueAlliance;
    }

    public void setBlueAlliance(List<Team> blueAlliance) {
        this.blueAlliance = blueAlliance;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }

    private String score;
    private String id;
    private int num;
    private List<Team> redAlliance;
    private List<Team> blueAlliance;

    public JSONObject toJson() {
        JSONObject ret = new JSONObject();
        ret.put("score", score);
        ret.put("id", id);
        ret.put("num", num);
        ret.put("redAlliance", redAlliance.stream().map(Team::toJson).collect(Collectors.toList()));
        ret.put("blueAlliance", blueAlliance.stream().map(Team::toJson).collect(Collectors.toList()));
        return ret;
    }
}
