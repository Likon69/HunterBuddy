package com.hunterbuddy.behavior.core;

public final class OperationFailure extends IllegalStateException {
    private final FailureCode code;

    public OperationFailure(FailureCode code, String message) {
        super(message);
        this.code = code;
    }

    public FailureCode code() {
        return code;
    }
}
