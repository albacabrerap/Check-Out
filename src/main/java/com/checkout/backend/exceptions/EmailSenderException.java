package com.checkout.backend.exceptions;

public class EmailSenderException extends RuntimeException {
    public EmailSenderException(String message, Exception e) { super(message,e); }
}