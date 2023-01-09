package com.uid2.admin.model;

import com.uid2.shared.auth.OperatorKey;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        // If the object is compared with itself then return true
        if (o == this) return true;

        // If the object is of a different type, return false
        if (!(o instanceof Site)) return false;

        Site b = (Site) o;

        // Compare the data members and return accordingly
        return this.id == b.id
                && this.name.equals(b.name)
                && this.enabled == b.enabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, enabled);
    }

}
