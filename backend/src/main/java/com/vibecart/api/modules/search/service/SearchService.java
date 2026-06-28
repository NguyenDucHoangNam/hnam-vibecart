package com.vibecart.api.modules.search.service;

import com.vibecart.api.modules.search.dto.request.ProductSearchRequest;
import com.vibecart.api.modules.search.dto.request.SearchMergeRequest;
import com.vibecart.api.modules.search.dto.response.SearchHistoryResponse;
import com.vibecart.api.modules.search.dto.response.SearchResultResponse;
import com.vibecart.api.modules.search.dto.response.UserSearchResultResponse;
import com.vibecart.api.modules.iam.dto.response.UserResponse;
import com.vibecart.api.modules.iam.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SearchService {
    SearchResultResponse search(ProductSearchRequest request, String userId);
    List<String> autocomplete(String prefix);
    List<String> getTrendingKeywords();
    List<SearchHistoryResponse> getPersonalHistory(String userId);
    void deleteHistoryKeyword(String userId, String keyword);
    void clearHistory(String userId);
    void mergeHistory(String userId, SearchMergeRequest request);
    void reindexAll();
    void aggregateWeeklyTrending();
    UserSearchResultResponse searchUsers(String query, int page, int size, String currentUserId);
    List<String> autocompleteUsers(String prefix);
    void indexUser(User user);
    void deleteUser(String userId);
    Page<UserResponse> adminSearchUsers(String search, String status, String role, Pageable pageable);
}


