package com.psgitech.library.dto.librarian;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Standard API Response envelope adhering to Section 6:
 * {"success": bool, "data": {}, "error": {"code": "...", "message": "..."}, "meta": {"page": 1, "pageSize": 20, "total": 100}}
 */
public class ApiResponse<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean success;
    private T data;
    private ApiError error;
    private Map<String, Object> meta;

    public ApiResponse() {}

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.success = true;
        resp.data = data;
        return resp;
    }

    public static <T> ApiResponse<T> ok(T data, int page, int pageSize, int total) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.success = true;
        resp.data = data;
        resp.meta = new HashMap<>();
        resp.meta.put("page", page);
        resp.meta.put("pageSize", pageSize);
        resp.meta.put("total", total);
        return resp;
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.success = false;
        resp.error = new ApiError(code, message);
        return resp;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public ApiError getError() { return error; }
    public void setError(ApiError error) { this.error = error; }

    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) { this.meta = meta; }

    public static class ApiError implements Serializable {
        private static final long serialVersionUID = 1L;
        private String code;
        private String message;

        public ApiError() {}
        public ApiError(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
