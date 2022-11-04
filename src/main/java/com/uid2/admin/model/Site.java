package com.uid2.admin.model;

public class Site {
    private int id;
    private String name;
    private Boolean enabled;

    public Site(int id, String name, Boolean enabled) {
        this.id = id;
        this.name = name;
        this.enabled = enabled;
    }

    public int getId() { return id; }

    public String getName() { return name; }

    public Boolean isEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
