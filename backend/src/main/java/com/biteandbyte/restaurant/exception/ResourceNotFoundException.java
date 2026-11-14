package com.biteandbyte.restaurant.exception;

import org.springframework.http.HttpStatus;

/** Thrown when a resource does not exist or the caller does not own it. Maps to 404. */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", resource + " " + id + " was not found");
    }
}
