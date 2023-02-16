package com.uid2.admin.store.writer;

import com.uid2.shared.store.CloudPath;
import io.vertx.core.json.JsonObject;

public class Metadata {
    public JsonObject getJson() {
        return metadata;
    }

    private final JsonObject metadata;

    public Metadata(JsonObject metadata) {
        this.metadata = metadata;
    }

    public void setVersion(Long version) {
        metadata.put("version", version);
    }

    public void setGenerated(Long generated) {
        metadata.put("generated", generated);
    }

    public void addExtra(JsonObject extraMeta) {
        extraMeta.forEach(pair -> metadata.put(pair.getKey(), pair.getValue()));
    }

    public CloudPath locationOf(String dataType) {
        JsonObject locationContainer = metadata.getJsonObject(dataType);
        if (locationContainer == null) {
            return new CloudPath("");
        }
        return new CloudPath(locationContainer.getString("location"));
    }

    public void setLocation(String dataType, CloudPath location) {
        JsonObject locationContainer = new JsonObject();
        locationContainer.put("location", location.toString());
        metadata.put(dataType, locationContainer);
    }
}
