package com.vibecart.api.modules.search.repository;

import com.vibecart.api.modules.search.entity.SearchHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SearchHistoryRepository extends MongoRepository<SearchHistory, String> {
}
