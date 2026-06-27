package com.vibecart.api.modules.search.controller;

import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.modules.search.dto.request.ProductSearchRequest;
import com.vibecart.api.modules.search.dto.request.SearchMergeRequest;
import com.vibecart.api.modules.search.dto.response.SearchHistoryResponse;
import com.vibecart.api.modules.search.dto.response.SearchResultResponse;
import com.vibecart.api.modules.search.dto.response.UserSearchResultResponse;
import com.vibecart.api.modules.search.service.SearchService;
import com.vibecart.api.common.util.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final SearchService searchService;
    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }
    @GetMapping
    public ResponseEntity<ApiResponse<SearchResultResponse>> search(ProductSearchRequest request) {
        log.info("REST request to search products. Query='{}'", request.getActiveQuery());

        String userId = SecurityUtils.getOptionalUsername();
        SearchResultResponse result = searchService.search(request, userId);

        ApiResponse<SearchResultResponse> response = ApiResponse.<SearchResultResponse>builder()
                .code(1000)
                .message("Tìm kiếm sản phẩm thành công")
                .result(result)
                .build();

        return ResponseEntity.ok(response);
    }
    @GetMapping("/autocomplete")
    public ResponseEntity<ApiResponse<List<String>>> autocomplete(@RequestParam String prefix) {
        log.info("REST request for autocomplete prefix: '{}'", prefix);
        List<String> suggestions = searchService.autocomplete(prefix);

        ApiResponse<List<String>> response = ApiResponse.<List<String>>builder()
                .code(1000)
                .message("Gợi ý từ khóa thành công")
                .result(suggestions)
                .build();

        return ResponseEntity.ok(response);
    }
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<UserSearchResultResponse>> searchUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {

        log.info("REST request to search users. Query='{}'", q);

        String currentUsername = SecurityUtils.getOptionalUsername();

        UserSearchResultResponse result = searchService.searchUsers(q, page, size, currentUsername);

        ApiResponse<UserSearchResultResponse> response = ApiResponse.<UserSearchResultResponse>builder()
                .code(1000)
                .message("Tìm kiếm thành viên thành công")
                .result(result)
                .build();

        return ResponseEntity.ok(response);
    }
    @GetMapping("/users/autocomplete")
    public ResponseEntity<ApiResponse<List<String>>> autocompleteUsers(@RequestParam String prefix) {
        log.info("REST request for user autocomplete prefix: '{}'", prefix);
        List<String> suggestions = searchService.autocompleteUsers(prefix);

        ApiResponse<List<String>> response = ApiResponse.<List<String>>builder()
                .code(1000)
                .message("Gợi ý từ khóa thành viên thành công")
                .result(suggestions)
                .build();

        return ResponseEntity.ok(response);
    }
    @GetMapping("/trending")
    public ResponseEntity<ApiResponse<List<String>>> getTrendingKeywords() {
        log.info("REST request to fetch weekly trending searches");
        List<String> trending = searchService.getTrendingKeywords();

        ApiResponse<List<String>> response = ApiResponse.<List<String>>builder()
                .code(1000)
                .message("Lấy danh sách xu hướng thành công")
                .result(trending)
                .build();

        return ResponseEntity.ok(response);
    }
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<SearchHistoryResponse>>> getPersonalHistory() {
        String userId = SecurityUtils.getCurrentUsername();
        log.info("REST request to get personal search history for user {}", userId);

        List<SearchHistoryResponse> history = searchService.getPersonalHistory(userId);

        ApiResponse<List<SearchHistoryResponse>> response = ApiResponse.<List<SearchHistoryResponse>>builder()
                .code(1000)
                .message("Lấy lịch sử tìm kiếm thành công")
                .result(history)
                .build();

        return ResponseEntity.ok(response);
    }
    @DeleteMapping("/history")
    @PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteHistoryKeyword(@RequestParam String keyword) {
        String userId = SecurityUtils.getCurrentUsername();
        log.info("REST request to delete keyword '{}' from history for user {}", keyword, userId);

        searchService.deleteHistoryKeyword(userId, keyword);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .code(1000)
                .message("Xóa từ khóa lịch sử thành công")
                .build();

        return ResponseEntity.ok(response);
    }
    @DeleteMapping("/history/clear")
    @PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> clearHistory() {
        String userId = SecurityUtils.getCurrentUsername();
        log.info("REST request to clear history for user {}", userId);

        searchService.clearHistory(userId);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .code(1000)
                .message("Xóa sạch lịch sử tìm kiếm thành công")
                .build();

        return ResponseEntity.ok(response);
    }
    @PostMapping("/history/merge")
    @PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> mergeHistory(@RequestBody SearchMergeRequest request) {
        String userId = SecurityUtils.getCurrentUsername();
        log.info("REST request to merge history from localStorage for user {}", userId);

        searchService.mergeHistory(userId, request);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .code(1000)
                .message("Đồng bộ lịch sử tìm kiếm thành công")
                .build();

        return ResponseEntity.ok(response);
    }
    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> reindexAll() {
        log.info("REST request to bulk reindex all products into Elasticsearch");

        searchService.reindexAll();

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .code(1000)
                .message("Đồng bộ lại toàn bộ chỉ mục tìm kiếm thành công")
                .build();

        return ResponseEntity.ok(response);
    }
}
