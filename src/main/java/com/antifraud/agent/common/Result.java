package com.antifraud.agent.common;

import lombok.Data;

/**
 * 统一响应结构
 */
@Data
public class Result<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data);
    }

    public static <T> Result<T> success() {
        return new Result<>(0, "success", null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(-1, message, null);
    }
}
