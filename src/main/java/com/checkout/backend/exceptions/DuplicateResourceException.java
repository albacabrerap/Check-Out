package com.checkout.backend.exceptions;

import org.springframework.http.HttpStatus;

// 409: resource overlaps with an existant one, issue with the actual server state.
public class DuplicateResourceException extends ApiException {
    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}