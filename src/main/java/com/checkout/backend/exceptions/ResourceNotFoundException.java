package com.checkout.backend.exceptions;

import org.springframework.http.HttpStatus;

// 404: request of a non-existant resource.
public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String message){ super(HttpStatus.NOT_FOUND, message); }

    // Shortcut for a frequent case.
    public ResourceNotFoundException(String resource, Object identifier) {
        super(HttpStatus.NOT_FOUND, resource + " no encontrado: " + identifier);
    }
}