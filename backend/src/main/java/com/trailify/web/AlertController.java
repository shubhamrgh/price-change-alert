package com.trailify.web;

import com.trailify.model.AlertLog;
import com.trailify.repository.AlertLogRepository;
import com.trailify.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertLogRepository repository;
    private final AuthService authService;

    public AlertController(AlertLogRepository repository, AuthService authService) {
        this.repository = repository;
        this.authService = authService;
    }

    @GetMapping
    public List<AlertLog> recent(HttpServletRequest request) {
        return repository.findRecentOwnedBy(authService.requireUser(request).getId(), PageRequest.of(0, 50));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(HttpServletRequest request, @PathVariable Long id) {
        repository.findOwnedById(id, authService.requireUser(request).getId()).ifPresent(repository::delete);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearAll(HttpServletRequest request) {
        repository.deleteAll(repository.findRecentOwnedBy(authService.requireUser(request).getId()));
    }
}

