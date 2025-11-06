package com.andrewproject.filechat.enums;

public enum NodeStatus {
    RUNNING("running"),

    COMPLETED("completed"),

    FAILED("failed");

    String code;

    NodeStatus(String code) {
        this.code = code;
    }
}
