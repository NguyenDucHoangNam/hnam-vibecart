package com.vibecart.api.modules.iam.repository;

import com.vibecart.api.modules.iam.document.UserDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserSearchRepository extends ElasticsearchRepository<UserDocument, String> {
}
