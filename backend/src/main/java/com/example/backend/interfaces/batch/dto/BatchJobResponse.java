package com.example.backend.interfaces.batch.dto;

public record BatchJobResponse(String jobName, String status, String message) {

    public static BatchJobResponse accepted(String jobName) {
        return new BatchJobResponse(jobName, "ACCEPTED", "배치 Job이 비동기로 실행됩니다");
    }

    public static BatchJobResponse alreadyRunning(String jobName) {
        return new BatchJobResponse(jobName, "ALREADY_RUNNING", "해당 Job이 이미 실행 중입니다");
    }
}
