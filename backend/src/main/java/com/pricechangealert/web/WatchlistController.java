package com.pricechangealert.web;

import com.pricechangealert.model.Market;
import com.pricechangealert.model.WatchItem;
import com.pricechangealert.service.WatchlistService;
import com.pricechangealert.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    public record WatchItemRequest(
            @NotBlank String symbol,
            String name,
            @jakarta.validation.constraints.NotNull Market market,
            String triggerType,
            String direction,
            @Positive double thresholdValue,
            String currency) {
    }

    private final WatchlistService service;
    private final AuthService authService;

    public WatchlistController(WatchlistService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @GetMapping
    public List<WatchItem> list(HttpServletRequest request) {
        return service.findAll(authService.requireUser(request).getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WatchItem add(HttpServletRequest request, @Valid @RequestBody WatchItemRequest req) {
        return service.add(authService.requireUser(request).getId(), req.symbol(), req.name(), req.market(),
                req.triggerType() == null ? "PRICE" : req.triggerType(),
                req.direction(), req.thresholdValue(), req.currency());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(HttpServletRequest request, @PathVariable Long id) {
        service.delete(id, authService.requireUser(request).getId());
    }

    @PatchMapping("/{id}/active")
    public WatchItem setActive(HttpServletRequest request, @PathVariable Long id,
                               @RequestBody Map<String, Boolean> body) {
        return service.setActive(id, authService.requireUser(request).getId(),
                Boolean.TRUE.equals(body.get("active")));
    }
}

