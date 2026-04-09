package com.rentwrangler.exception;

import com.rentwrangler.domain.enums.MaintenanceCategory;

public class UnsupportedMaintenanceCategoryException extends RuntimeException {

    public UnsupportedMaintenanceCategoryException(MaintenanceCategory category) {
        super("No maintenance strategy registered for category: " + category);
    }
}
