package com.uid2.admin.store;

public class FileName {
    // Usually text of the filename e.g. `abc` from `abc.txt`
    private final String prefix;
    // Usually the extension including the dot e.g. `.txt` from `abc.txt`
    private final String suffix;

    public FileName(String prefix, String suffix) {
        this.prefix = prefix;
        this.suffix = suffix;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getSuffix() {
        return suffix;
    }

    @Override
    public String toString() {
        return prefix + suffix;
    }
}
