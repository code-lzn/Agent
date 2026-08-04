package com.limou.agent.exception;

import com.limou.agent.common.BaseResponse;
import com.limou.agent.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Object businessExceptionHandler(BusinessException e, HttpServletResponse response) {
        log.error("BusinessException", e);
        if (response.isCommitted()) {
            log.warn("响应已提交(SSE)，无法返回 JSON 错误: {}", e.getMessage());
            return null;
        }
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public Object runtimeExceptionHandler(RuntimeException e, HttpServletResponse response) {
        log.error("RuntimeException", e);
        if (response.isCommitted()) {
            log.warn("响应已提交(SSE)，无法返回 JSON 错误: {}", e.getMessage());
            return null;
        }
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}
