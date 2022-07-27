package com.uid2.admin;

public class Constants {
    public static final String QLDB_LEDGER_NAME = "audit-logs";
    public static final String QLDB_TABLE_NAME = "logs";
    public static final String DEFAULT_ITEM_KEY = "$all_items&"; //needs to be unlikely to be a name of a current or future user
    public static final boolean ENABLE_ADMIN_LOGGING = true;
}
