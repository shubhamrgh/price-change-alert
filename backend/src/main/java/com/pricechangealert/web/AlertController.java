package com.pricechangealert.web;

import com.pricechangealert.model.AlertLog;
import com.pricechangealert.repository.AlertLogRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertLogRepository repository;

    public AlertController(AlertLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<AlertLog> recent(@RequestHeader(value = "X-Visitor-Id", required = false) String visitorId) {
        return repository.findRecentOwnedBy(VisitorId.normalize(visitorId), PageRequest.of(0, 50));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(value = "X-Visitor-Id", required = false) String visitorId,
                       @PathVariable Long id) {
        repository.findOwnedById(id, VisitorId.normalize(visitorId)).ifPresent(repository::delete);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearAll(@RequestHeader(value = "X-Visitor-Id", required = false) String visitorId) {
        repository.deleteAll(repository.findRecentOwnedBy(VisitorId.normalize(visitorId)));
    }
}

