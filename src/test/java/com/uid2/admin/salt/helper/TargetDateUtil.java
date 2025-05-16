package com.uid2.admin.salt.helper;

import com.uid2.admin.salt.TargetDate;

public class TargetDateUtil {
    private static final TargetDate TARGET_DATE = TargetDate.of(2025, 1, 1);

    public static TargetDate daysEarlier(int days) {
        return TARGET_DATE.minusDays(days);
    }

    public static TargetDate daysLater(int days) {
        return TARGET_DATE.plusDays(days);
    }

    public static TargetDate targetDate() {
        return TARGET_DATE;
    }
}
