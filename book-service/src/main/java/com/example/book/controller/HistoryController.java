package com.example.book.controller;

import com.example.book.dto.BrowsingHistoryItem;
import com.example.book.service.BrowsingHistoryService;
import com.example.common.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/books/me/history")
public class HistoryController {

    private final BrowsingHistoryService browsingHistoryService;

    public HistoryController(BrowsingHistoryService browsingHistoryService) {
        this.browsingHistoryService = browsingHistoryService;
    }

    /** USER — recently viewed books, newest first (DynamoDB sort key descending). */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<BrowsingHistoryItem> myHistory(@RequestParam(defaultValue = "20") int limit) {
        return browsingHistoryService.recentForUser(CurrentUser.require().userId(), limit);
    }
}
