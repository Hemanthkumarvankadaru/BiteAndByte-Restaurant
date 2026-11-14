package com.biteandbyte.restaurant.security;

import com.biteandbyte.restaurant.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes 401 and 403 responses from the security filters in the same {@link ErrorResponse}
 * shape the controllers use, so the frontend handles every error the same way.
 */
@Component
public class JsonAuthErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    public JsonAuthErrorHandlers(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** 401: no token, or a token that is invalid or expired. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        boolean sentToken = request.getHeader(HttpHeaders.AUTHORIZATION) != null;
        String message = sentToken ? "Token is invalid or expired. Please log in again." : "Login required";
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message, request);
    }

    /** 403: logged in, but the role does not allow this request. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to do this", request);
    }

    private void write(HttpServletResponse response, HttpStatus status, String code, String message,
                       HttpServletRequest request) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(),
                ErrorResponse.of(status.value(), code, message, request.getRequestURI()));
    }
}
