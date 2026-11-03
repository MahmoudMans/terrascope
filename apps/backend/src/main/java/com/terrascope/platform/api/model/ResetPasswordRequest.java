package com.terrascope.platform.api.model;

public record ResetPasswordRequest(
        String newPassword
) {

    @Override
    public String toString() {
        return "ResetPasswordRequest[****]";
    }
}
