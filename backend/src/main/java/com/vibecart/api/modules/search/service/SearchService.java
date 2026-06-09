package com.vibecart.api.modules.search.service;

import com.vibecart.api.modules.search.dto.request.SearchMergeRequest;
import com.vibecart.api.modules.search.dto.response.SearchHistoryResponse;
import com.vibecart.api.modules.search.dto.response.SearchResultResponse;
import com.vibecart.api.modules.search.dto.response.UserSearchResultResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service định nghĩa các thao tác tìm kiếm sản phẩm, thành viên và quản lý lịch sử tìm kiếm.
 */
public interface SearchService {
    /**
     * Tìm kiếm sản phẩm theo từ khóa và bộ lọc.
     *
     * @param query Từ khóa tìm kiếm
     * @param categoryId ID danh mục
     * @param minPrice Giá tối thiểu
     * @param maxPrice Giá tối đa
     * @param sort Loại sắp xếp
     * @param page Trang hiện tại
     * @param size Số lượng phần tử mỗi trang
     * @param userId ID người dùng đang đăng nhập (nếu có) để ghi nhận lịch sử tìm kiếm
     * @return Kết quả tìm kiếm sản phẩm kèm gợi ý sửa lỗi chính tả
     */
    SearchResultResponse search(String query, String categoryId, BigDecimal minPrice, BigDecimal maxPrice, 
                                String sort, int page, int size, String userId);
    
    /**
     * Gợi ý từ khóa tự động dựa trên tiền tố cho sản phẩm.
     *
     * @param prefix Tiền tố từ khóa
     * @return Danh sách gợi ý từ khóa
     */
    List<String> autocomplete(String prefix);
    
    /**
     * Lấy danh sách từ khóa tìm kiếm thịnh hành hàng tuần.
     *
     * @return Danh sách từ khóa hot
     */
    List<String> getTrendingKeywords();
    
    /**
     * Lấy lịch sử tìm kiếm cá nhân của người dùng.
     *
     * @param userId ID người dùng
     * @return Danh sách lịch sử tìm kiếm
     */
    List<SearchHistoryResponse> getPersonalHistory(String userId);
    
    /**
     * Xóa một từ khóa cụ thể khỏi lịch sử tìm kiếm cá nhân.
     *
     * @param userId ID người dùng
     * @param keyword Từ khóa cần xóa
     */
    void deleteHistoryKeyword(String userId, String keyword);
    
    /**
     * Xóa sạch toàn bộ lịch sử tìm kiếm cá nhân.
     *
     * @param userId ID người dùng
     */
    void clearHistory(String userId);
    
    /**
     * Đồng bộ lịch sử tìm kiếm từ localStorage vào tài khoản.
     *
     * @param userId ID người dùng
     * @param request Yêu cầu chứa danh sách từ khóa cần đồng bộ
     */
    void mergeHistory(String userId, SearchMergeRequest request);
    
    /**
     * Đồng bộ lại chỉ mục toàn bộ sản phẩm và người dùng vào Elasticsearch.
     */
    void reindexAll();
    
    /**
     * Tổng hợp từ khóa tìm kiếm hot hàng tuần.
     */
    void aggregateWeeklyTrending();

    /**
     * Tìm kiếm thành viên (creator) hoạt động trong hệ thống.
     *
     * @param query Từ khóa tên/username
     * @param page Trang hiện tại
     * @param size Số lượng phần tử mỗi trang
     * @param currentUsername Username người thực hiện tìm kiếm (để ẩn chính mình)
     * @return Kết quả tìm kiếm thành viên và trạng thái follow
     */
    UserSearchResultResponse searchUsers(String query, int page, int size, String currentUsername);
    
    /**
     * Gợi ý từ khóa tự động dựa trên tiền tố cho thành viên.
     *
     * @param prefix Tiền tố từ khóa
     * @return Danh sách gợi ý từ khóa
     */
    List<String> autocompleteUsers(String prefix);
    
    /**
     * Đồng bộ thông tin người dùng vào Elasticsearch.
     *
     * @param user Đối tượng người dùng
     */
    void indexUser(com.vibecart.api.modules.iam.entity.User user);
    
    /**
     * Xóa người dùng khỏi chỉ mục Elasticsearch.
     *
     * @param userId ID người dùng
     */
    void deleteUser(String userId);
}

