package com.checkout.backend.exceptions;

import org.springframework.http.HttpStatus;

 // 403: user is authenticated but can't do this operation.
    // 1. User doesn't have role (Check GlobalExceptionHandler).
    // 2. Resource it's of another user.

 // Compares de owner of the row of the request
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}