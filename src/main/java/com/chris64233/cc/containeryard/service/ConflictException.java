package com.chris64233.cc.containeryard.service;

/** 资源冲突或乐观/悲观并发冲突（HTTP 409）。 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
