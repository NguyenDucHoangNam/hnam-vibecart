package com.vibecart.api.modules.search.service;

import com.vibecart.api.modules.search.dto.request.ProductSearchRequest;
import com.vibecart.api.modules.search.dto.request.SearchMergeRequest;
import com.vibecart.api.modules.search.dto.response.SearchHistoryResponse;
import com.vibecart.api.modules.search.dto.response.SearchResultResponse;
import com.vibecart.api.modules.search.dto.response.UserSearchResultResponse;

import java.math.BigDecimal;
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
    UserSearchResultResponse searchUsers(String query, int page, int size, String currentUsername);
    List<String> autocompleteUsers(String prefix);
    void indexUser(com.vibecart.api.modules.iam.entity.User user);
    void deleteUser(String userId);
}

