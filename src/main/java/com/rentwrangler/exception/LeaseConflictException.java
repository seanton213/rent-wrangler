package com.rentwrangler.exception;

public class LeaseConflictException extends RuntimeException {

    public LeaseConflictException(Long unitId) {
        super("Unit " + unitId + " already has an active lease. Terminate the current lease before creating a new one.");
    }
}
