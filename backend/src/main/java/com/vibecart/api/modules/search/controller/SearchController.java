package com.vibecart.api.modules.search.controller;

import com.vibecart.api.common.dto.ApiResponse;
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

/**
 * Controller xử lý các yêu cầu tìm kiếm sản phẩm, thành viên và quản lý lịch sử tìm kiếm.
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final SearchService searchService;

    /**
     * Khởi tạo SearchController với service tìm kiếm được cung cấp.
     *
     * @param searchService Service thực hiện các logic tìm kiếm
     */
    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Tìm kiếm sản phẩm theo các tiêu chí lọc và phân trang.
     *
     * @param q Từ khóa tìm kiếm
     * @param categoryId ID của danh mục sản phẩm cần lọc
     * @param minPrice Giá tối thiểu
     * @param maxPrice Giá tối đa
     * @param sort Tiêu chí sắp xếp (relevance, price_asc, price_desc, newest)
     * @param page Số trang hiện tại (mặc định 0)
     * @param size Số lượng phần tử mỗi trang (mặc định 20)
     * @return Kết quả tìm kiếm sản phẩm và các gợi ý sửa lỗi chính tả nếu không tìm thấy
     */
    @GetMapping
    public ResponseEntity<ApiResponse<SearchResultResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false, defaultValue = "relevance") String sort,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {

        log.info("REST request to search products. Query='{}'", q);

        String userId = SecurityUtils.getOptionalUsername();

        SearchResultResponse result = searchService.search(q, categoryId, minPrice, maxPrice, sort, page, size, userId);

        ApiResponse<SearchResultResponse> response = ApiResponse.<SearchResultResponse>builder()
                .code(1000)
                .message("Tìm kiếm sản phẩm thành công")
                .result(result)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Tự động gợi ý hoàn thành từ khóa sản phẩm dựa trên tiền tố.
     *
     * @param prefix Tiền tố từ khóa người dùng nhập vào
     * @return Danh sách các từ khóa gợi ý phù hợp
     */
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

    /**
     * Tìm kiếm thành viên (creator) hoạt động trong hệ thống.
     *
     * @param q Từ khóa tên hoặc username của thành viên
     * @param page Số trang hiện tại
     * @param size Số lượng phần tử mỗi trang
     * @return Kết quả tìm kiếm thành viên và trạng thái theo dõi hiện tại
     */
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

    /**
     * Tự động gợi ý hoàn thành từ khóa tên/username của thành viên.
     *
     * @param prefix Tiền tố từ khóa nhập vào
     * @return Danh sách các gợi ý tên/username thành viên
     */
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

    /**
     * Lấy danh sách các từ khóa tìm kiếm thịnh hành hàng tuần.
     *
     * @return Danh sách các từ khóa hot nhất trong tuần
     */
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

    /**
     * Lấy lịch sử tìm kiếm cá nhân của người dùng hiện tại.
     *
     * @return Danh sách các từ khóa cùng thời gian tìm kiếm tương ứng
     */
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

    /**
     * Xóa một từ khóa cụ thể khỏi lịch sử tìm kiếm cá nhân.
     *
     * @param keyword Từ khóa cần xóa
     * @return Phản hồi trống thể hiện xóa thành công
     */
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

    /**
     * Xóa sạch toàn bộ lịch sử tìm kiếm cá nhân của người dùng.
     *
     * @return Phản hồi trống thể hiện xóa thành công
     */
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

    /**
     * Đồng bộ lịch sử tìm kiếm lưu tạm ở localStorage trên client khi chưa đăng nhập.
     *
     * @param request Yêu cầu đồng bộ chứa danh sách từ khóa
     * @return Phản hồi trống thể hiện đồng bộ thành công
     */
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

    /**
     * Thực hiện lập chỉ mục lại toàn bộ dữ liệu sản phẩm và người dùng vào Elasticsearch.
     *
     * @return Phản hồi trống thể hiện lập chỉ mục lại thành công
     */
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
