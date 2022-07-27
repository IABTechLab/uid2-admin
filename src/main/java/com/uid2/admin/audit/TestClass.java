package com.uid2.admin.audit;

import com.uid2.admin.Constants;
import software.amazon.qldb.QldbDriver;

public class TestClass {

    private static final QldbDriver qldbDriver = QldbDriver.builder()
            .ledger(Constants.QLDB_LEDGER_NAME)
            .build();

    public static void main(String[] args){

    }
}

