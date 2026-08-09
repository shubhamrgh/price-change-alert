package com.pricedrop.web;

import com.pricedrop.model.Market;
import com.pricedrop.model.WatchItem;
import com.pricedrop.service.WatchlistService;
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
import org.springframework.web.bind.annotation.RequestHeader;

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

    public WatchlistController(WatchlistService service) {
        this.service = service;
    }

    @GetMapping
    public List<WatchItem> list(@RequestHeader(value = "X-Visitor-Id", required = false) String visitorId) {
        return service.findAll(VisitorId.normalize(visitorId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WatchItem add(@RequestHeader(value = "X-Visitor-Id", required = false) String visitorId,
                         @Valid @RequestBody WatchItemRequest req) {
        return service.add(VisitorId.normalize(visitorId), req.symbol(), req.name(), req.market(),
                req.triggerType() == null ? "PRICE" : req.triggerType(),
                req.direction(), req.thresholdValue(), req.currency());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(value = "X-Visitor-Id", required = false) String visitorId,
                       @PathVariable Long id) {
        service.delete(id, VisitorId.normalize(visitorId));
    }

    @PatchMapping("/{id}/active")
    public WatchItem setActive(@RequestHeader(value = "X-Visitor-Id", required = false) String visitorId,
                               @PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return service.setActive(id, VisitorId.normalize(visitorId), Boolean.TRUE.equals(body.get("active")));
    }
}
