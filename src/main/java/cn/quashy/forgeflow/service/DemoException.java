package cn.quashy.forgeflow.service;

import org.springframework.http.HttpStatus;

public class DemoException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public DemoException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
