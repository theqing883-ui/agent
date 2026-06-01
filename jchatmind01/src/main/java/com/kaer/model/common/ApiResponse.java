package com.kaer.model.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

/**
 * API响应封装类
 * <p>用于统一封装接口返回的数据格式，包含响应码、响应消息和响应数据</p>
 *
 * @param <T> 响应数据的泛型类型
 */
@Data
public class ApiResponse<T> {

    /**
     * 响应状态码
     */
    private int code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 私有构造函数
     *
     * @param code    响应状态码
     * @param message 响应消息
     * @param data    响应数据
     */
    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功响应（带数据）
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return ApiResponse 响应对象
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ApiCode.SUCCESS.code, ApiCode.SUCCESS.message, data);
    }

    /**
     * 成功响应（无数据）
     *
     * @param <T> 数据类型
     * @return ApiResponse 响应对象
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(ApiCode.SUCCESS.code, ApiCode.SUCCESS.message, null);
    }

    /**
     * 成功响应（带数据和自定义消息）
     *
     * @param data    响应数据
     * @param message 自定义消息
     * @param <T>     数据类型
     * @return ApiResponse 响应对象
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(ApiCode.SUCCESS.code, message, data);
    }

    /**
     * 错误响应（指定错误码和消息）
     *
     * @param code    错误码枚举
     * @param message 错误消息
     * @param <T>     数据类型
     * @return ApiResponse 响应对象
     */
    public static <T> ApiResponse<T> error(ApiCode code, String message) {
        return new ApiResponse<>(code.getCode(), message, null);
    }

    /**
     * 错误响应（默认错误码）
     *
     * @param message 错误消息
     * @param <T>     数据类型
     * @return ApiResponse 响应对象
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(ApiCode.ERROR.getCode(), message, null);
    }

    /**
     * API响应码枚举
     * <p>定义系统支持的响应状态码</p>
     */
    @Getter
    @AllArgsConstructor
    public enum ApiCode {

        /**
         * 成功
         */
        SUCCESS(200, "success"),

        /**
         * 失败
         */
        ERROR(500, "error");

        /**
         * 状态码
         */
        private final int code;

        /**
         * 状态消息
         */
        private final String message;

        /**
         * 根据状态码获取对应的ApiCode枚举
         *
         * @param code 状态码
         * @return ApiCode 对应的枚举值
         * @throws IllegalArgumentException 当状态码无效时抛出
         */
        public static ApiCode fromCode(int code) {
            for (ApiCode value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Invalid code: " + code);
        }
    }
}