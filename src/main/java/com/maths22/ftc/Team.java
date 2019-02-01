package com.maths22.ftc;

import org.json.JSONObject;

public class Team {
    private int number;
    private String name;
    private String organization;
    private String city;
    private String state;
    private String country;
    private int rookie;

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public int getRookie() {
        return rookie;
    }

    public void setRookie(int rookie) {
        this.rookie = rookie;
    }



    public JSONObject toJson() {
        JSONObject ret = new JSONObject();
        ret.put("number", number);
        ret.put("name", name);
        ret.put("organization", organization);
        ret.put("city", city);
        ret.put("state", state);
        ret.put("country", country);
        ret.put("rookie", rookie);
        return ret;
    }
}
