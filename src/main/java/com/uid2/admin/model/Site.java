package com.uid2.admin.model;

import java.util.Objects;
import java.util.Set;

public class Site {
    private final int id;
    private final String name;
    private Boolean enabled;

    private Set<ClientType> types;

    public Site(int id, String name, Boolean enabled, Set<ClientType> types) {
        this.id = id;
        this.name = name;
        this.enabled = enabled;
        this.types = types;
    }

    public int getId() { return id; }

    public String getName() { return name; }

    public Boolean isEnabled() { return enabled; }

    public Set<ClientType> getTypes() { return types; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    @Override
    public String toString() {
        return "Site{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", enabled=" + enabled +
                ", types=" + types.toString() +
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
