package com.incepto.quredemo.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class QureConnectionException extends ResponseStatusException {

    private static final long serialVersionUID = -7489626439596468261L;

    public QureConnectionException(HttpStatus httpStatus, String message) {
        super(httpStatus, message);
    }
    
    public QureConnectionException(Exception e) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
    }
}
