package br.com.fiap.ford.journey.web;

import br.com.fiap.ford.journey.application.JourneyNotFoundException;
import br.com.fiap.ford.journey.domain.DomainException;
import br.com.fiap.ford.journey.infra.IntelligenceUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(JourneyNotFoundException.class)
    ProblemDetail notFound(JourneyNotFoundException error, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Journey not found", error.getMessage(), request);
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException error, HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violation", error.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException error, HttpServletRequest request) {
        String detail = error.getBindingResult().getFieldErrors().stream()
                .map(item -> item.getField() + ": " + item.getDefaultMessage())
                .sorted()
                .collect(java.util.stream.Collectors.joining(", "));
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", detail, request);
    }

    @ExceptionHandler(IntelligenceUnavailableException.class)
    ProblemDetail unavailable(IntelligenceUnavailableException error, HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Dependency unavailable", error.getMessage(), request);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://ford.example/problems/" + status.value()));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}

