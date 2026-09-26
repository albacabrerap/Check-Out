package com.checkout.backend.exceptions;
import org.springframework.http.HttpStatus;

// (400)
public class InvalidRequestException extends ApiException {
    public InvalidRequestException(String message){ super(HttpStatus.BAD_REQUEST, message); }
}