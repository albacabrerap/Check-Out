package com.checkout.backend.exceptions;
import org.springframework.http.HttpStatus;

// 401: no valid credentials, token expired, ...
public class UnauthenticatedException extends ApiException {
    public UnauthenticatedException(String message){ super(HttpStatus.UNAUTHORIZED, message); }
}