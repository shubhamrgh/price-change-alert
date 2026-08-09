package com.pricedrop.web;

import com.pricedrop.model.Market;
import com.pricedrop.model.Suggestion;
import com.pricedrop.service.SearchService;
import java.util.List;
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
    public List<Suggestion> search(@RequestParam String q, @RequestParam Market market) {
        return searchService.search(q, market);
    }
}