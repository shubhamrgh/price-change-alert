package com.trailify.web;

import com.trailify.model.Market;
import com.trailify.model.Suggestion;
import com.trailify.service.SearchService;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Symbol search for the add-symbol picker (live suggestion lists). */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ResponseEntity<List<Suggestion>> search(@RequestParam String q, @RequestParam Market market) {
        List<Suggestion> suggestions = searchService.search(q, market);
        Duration maxAge = suggestions.isEmpty() ? Duration.ofSeconds(30) : Duration.ofMinutes(10);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(maxAge).cachePublic())
                .body(suggestions);
    }
}
