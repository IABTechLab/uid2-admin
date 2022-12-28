package com.uid2.admin.model;

import java.util.Objects;

public class Site {
    private final int id;
    private final String name;
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

    @Override
    public String toString() {
        return "Site{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", enabled=" + enabled +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Site site = (Site) o;
        return id == site.id && name.equals(site.name) && enabled.equals(site.enabled);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, enabled);
    }
}
