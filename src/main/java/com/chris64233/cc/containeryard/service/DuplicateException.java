package com.chris64233.cc.containeryard.service;

public class DuplicateException extends ConflictException {

    public DuplicateException(String message) {
        super(message);
    }
}
