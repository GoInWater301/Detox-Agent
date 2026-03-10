package com.pnu.detox_agent.webserver.controller;

import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;

import java.util.Map;

@RestControllerAdvice
public class GlobalErrorAdvice {

    private static final Logger logger = LoggerFactory.getLogger(GlobalErrorAdvice.class);

    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<Map<String, String>> handleGrpcError(StatusRuntimeException ex) {
        logger.error("gRPC error: {}", ex.getStatus());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "AI Agent service is unavailable: " + ex.getStatus().getCode()));
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<Map<String, String>> handleInputError(ServerWebInputException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid input: " + ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAll(Exception ex) {
        logger.error("Unexpected error", ex);
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "Internal server error: " + ex.getMessage()));
    }
}
