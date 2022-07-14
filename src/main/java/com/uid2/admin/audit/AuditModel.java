package com.uid2.admin.audit;

import io.vertx.core.json.JsonObject;

/**
 * An AuditModel contains fields that collectively logs all necessary details of an action
 * that reads or writes sensitive information in the uid2-admin server. AuditModel objects should
 * be <b>unmodifiable</b> and answer the following questions:
 *
 * • what happened?
 * • when did it happen?
 * • who initiated it?
 * • on what did it happen?
 * • where was it observed?
 * • from where was it initiated?
 * • to where was it going?
 */
public interface AuditModel {

    /**
     * Converts the AuditModel to JSON format to be used in document-store databases.
     * Every field should be a key in the resulting JSON object.
     *
     * @return a JSON representation of this AuditModel.
     */
    JsonObject writeToJson();

    /**
     * Converts the AuditModel into a readable String format to be used in text logs.
     *
     * @return a String representation of this AuditModel.
     */
    String writeToString();
}
