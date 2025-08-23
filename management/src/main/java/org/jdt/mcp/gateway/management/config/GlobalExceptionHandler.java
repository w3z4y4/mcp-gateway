package org.jdt.mcp.gateway.management.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Order(-2)
@Slf4j
public class GlobalExceptionHandler extends AbstractErrorWebExceptionHandler {

    public GlobalExceptionHandler(ErrorAttributes errorAttributes,
                                  ApplicationContext applicationContext,
                                  ServerCodecConfigurer serverCodecConfigurer) {
        super(errorAttributes, new WebProperties.Resources(), applicationContext);
        super.setMessageWriters(serverCodecConfigurer.getWriters());
        super.setMessageReaders(serverCodecConfigurer.getReaders());
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
    }

    private Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
        Throwable error = getError(request);
        log.error("Error occurred: ", error);

        Map<String, Object> errorResponse = buildErrorResponse(error);
        HttpStatus status = determineHttpStatus(error);

        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(errorResponse));
    }

    private Map<String, Object> buildErrorResponse(Throwable error) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());

        if (error instanceof IllegalArgumentException) {
            response.put("error", "Bad Request");
            response.put("message", error.getMessage());
        } else if (error instanceof IllegalStateException) {
            response.put("error", "Conflict");
            response.put("message", error.getMessage());
        } else if (error instanceof WebExchangeBindException bindException) {
            String errors = bindException.getBindingResult().getFieldErrors().stream()
                    .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                    .collect(Collectors.joining(", "));
            response.put("error", "Validation Failed");
            response.put("message", errors);
        } else if (error instanceof ResponseStatusException statusException) {
            response.put("error", statusException.getStatusCode());
            response.put("message", statusException.getReason());
        } else {
            response.put("error", "Internal Server Error");
            response.put("message", "An unexpected error occurred");
        }

        return response;
    }

    private HttpStatus determineHttpStatus(Throwable error) {
        if (error instanceof IllegalArgumentException) {
            return HttpStatus.BAD_REQUEST;
        } else if (error instanceof IllegalStateException) {
            return HttpStatus.CONFLICT;
        } else if (error instanceof WebExchangeBindException) {
            return HttpStatus.BAD_REQUEST;
        } else if (error instanceof ResponseStatusException statusException) {
            return HttpStatus.valueOf(statusException.getStatusCode().value());
        } else {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}