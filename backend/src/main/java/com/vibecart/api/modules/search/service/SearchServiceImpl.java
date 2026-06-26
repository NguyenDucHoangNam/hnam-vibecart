package com.vibecart.api.modules.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.search.PhraseSuggester;
import co.elastic.clients.elasticsearch.core.search.Suggester;
import com.vibecart.api.modules.ecommerce.document.ProductDocument;
import com.vibecart.api.modules.ecommerce.entity.Product;
import com.vibecart.api.modules.ecommerce.entity.ProductImage;
import com.vibecart.api.modules.ecommerce.repository.ProductRepository;
import com.vibecart.api.modules.ecommerce.repository.ProductSearchRepository;
import com.vibecart.api.modules.search.dto.request.SearchMergeRequest;
import com.vibecart.api.modules.search.dto.response.SearchHistoryResponse;
import com.vibecart.api.modules.search.dto.response.SearchResultResponse;
import com.vibecart.api.modules.search.dto.response.UserSearchResponse;
import com.vibecart.api.modules.search.dto.response.UserSearchResultResponse;
import com.vibecart.api.modules.search.entity.SearchHistory;
import com.vibecart.api.modules.search.entity.SearchItem;
import com.vibecart.api.modules.iam.document.UserDocument;
import com.vibecart.api.modules.iam.repository.UserSearchRepository;
import com.vibecart.api.modules.iam.repository.UserRepository;
import com.vibecart.api.modules.social.service.FollowService;
import com.vibecart.api.modules.iam.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchServiceImpl implements SearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient elasticsearchClient;
    private final MongoTemplate mongoTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final ProductSearchRepository productSearchRepository;
    private final ProductRepository productRepository;
    private final UserSearchRepository userSearchRepository;
    private final UserRepository userRepository;
    private final FollowService followService;

    @Override
    public SearchResultResponse search(String query, String categoryId, BigDecimal minPrice, BigDecimal maxPrice,
            String sort, int page, int size, String userId) {
        log.info(
                "Searching products with query='{}', categoryId={}, minPrice={}, maxPrice={}, sort={}, page={}, size={}",
                query, categoryId, minPrice, maxPrice, sort, page, size);

        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();

        if (query != null && !query.isBlank()) {
            boolQueryBuilder.must(m -> m
                    .multiMatch(mm -> mm
                            .query(query)
                            .fields("name^3", "description")
                            .fuzziness("AUTO")
                            .prefixLength(2)
                            .maxExpansions(50)));
        } else {
            boolQueryBuilder.must(m -> m.matchAll(ma -> ma));
        }

        if (categoryId != null && !categoryId.isBlank()) {
            boolQueryBuilder.filter(f -> f.term(t -> t.field("categoryId").value(categoryId)));
        }

        if (minPrice != null) {
            boolQueryBuilder.filter(f -> f.range(r -> r
                    .number(n -> n.field("maxPrice").gte(minPrice.doubleValue()))));
        }
        if (maxPrice != null) {
            boolQueryBuilder.filter(f -> f.range(r -> r
                    .number(n -> n.field("minPrice").lte(maxPrice.doubleValue()))));
        }

        boolQueryBuilder.filter(f -> f.term(t -> t.field("status").value("ACTIVE")));

        Sort sortSpec = switch (sort != null ? sort : "relevance") {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "minPrice");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "maxPrice");
            case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt");
            default -> Sort.unsorted();
        };

        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(boolQueryBuilder.build()))
                .withPageable(PageRequest.of(page, Math.min(size, 50)))
                .withSort(sortSpec)
                .build();

        SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(searchQuery, ProductDocument.class);
        List<ProductDocument> items = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .toList();

        long totalElements = searchHits.getTotalHits();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        boolean last = (page + 1) >= totalPages;

        String suggestion = null;

        if (totalElements == 0 && query != null && !query.isBlank()) {
            suggestion = getSpellcheckSuggestion(query);
        }

        if (totalElements > 0 && query != null && !query.isBlank()) {
            recordSearchKeyword(query, userId);
            if (userId != null && !userId.isBlank()) {
                recordPersonalHistory(userId, query.trim());
            }
        }

        return SearchResultResponse.builder()
                .content(items)
                .suggestion(suggestion)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last(last)
                .build();
    }

    private String getSpellcheckSuggestion(String query) {
        try {
            var response = elasticsearchClient.search(s -> s
                    .index("products")
                    .size(0)
                    .suggest(su -> su
                            .suggesters("spelling-suggestion", sg -> sg
                                    .text(query)
                                    .phrase(ph -> ph
                                            .field("name")
                                            .confidence(1.0)
                                            .size(1)
                                            .directGenerator(dg -> dg
                                                    .field("name")
                                                    .suggestMode(
                                                            co.elastic.clients.elasticsearch._types.SuggestMode.Popular)
                                                    .minWordLength(2))))),
                    ProductDocument.class);

            if (response.suggest() != null && response.suggest().containsKey("spelling-suggestion")) {
                var suggestList = response.suggest().get("spelling-suggestion");
                if (!suggestList.isEmpty()) {
                    var firstSuggest = suggestList.get(0);
                    if (firstSuggest.isPhrase() && firstSuggest.phrase().options() != null
                            && !firstSuggest.phrase().options().isEmpty()) {
                        return firstSuggest.phrase().options().get(0).text();
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch spelling suggestions from Elasticsearch", e);
        }
        return null;
    }

    private void recordSearchKeyword(String keyword, String userIdentifier) {
        try {
            String cleanKeyword = keyword.trim().toLowerCase();
            if (cleanKeyword.length() < 2) {
                return;
            }

            String todayStr = java.time.LocalDate.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));

            String identifier = userIdentifier;
            if (identifier == null || identifier.isBlank() || identifier.equals("anonymousUser")) {
                identifier = getClientIp();
            }

            String rateKey = "search:rate:" + todayStr + ":" + identifier + ":" + cleanKeyword;

            Long currentRate = redisTemplate.opsForValue().increment(rateKey, 1);
            if (currentRate != null) {
                if (currentRate == 1) {
                    redisTemplate.expire(rateKey, Duration.ofHours(24));
                }
                if (currentRate > 3) {
                    log.debug("Keyword anti-spam limit exceeded for user/IP: {}, term: {}", identifier, cleanKeyword);
                    return;
                }
            }

            String dailyKey = "search:trending:" + todayStr;
            redisTemplate.opsForZSet().incrementScore(dailyKey, cleanKeyword, 1.0);
            redisTemplate.expire(dailyKey, Duration.ofDays(10));
        } catch (Exception e) {
            log.error("Failed to increment keyword trend in Redis", e);
        }
    }

    private String getClientIp() {
        try {
            var attributes = (org.springframework.web.context.request.ServletRequestAttributes) org.springframework.web.context.request.RequestContextHolder
                    .getRequestAttributes();
            if (attributes != null) {
                var request = attributes.getRequest();
                String xfHeader = request.getHeader("X-Forwarded-For");
                if (xfHeader == null || xfHeader.isBlank()) {
                    return request.getRemoteAddr();
                } else {
                    return xfHeader.split(",")[0].trim();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve client IP from request attributes", e);
        }
        return "guest";
    }

    private void recordPersonalHistory(String userId, String keyword) {
        try {
            Query pullQuery = Query.query(Criteria.where("id").is(userId));
            Update pullUpdate = new Update().pull("items", Query.query(Criteria.where("keyword").is(keyword)));
            mongoTemplate.updateFirst(pullQuery, pullUpdate, SearchHistory.class);

            SearchItem newItem = new SearchItem(keyword, LocalDateTime.now());
            Update pushUpdate = new Update()
                    .push("items")
                    .atPosition(Update.Position.FIRST)
                    .slice(10)
                    .value(newItem)
                    .set("updatedAt", LocalDateTime.now());

            mongoTemplate.upsert(pullQuery, pushUpdate, SearchHistory.class);
        } catch (Exception e) {
            log.error("Failed to append search history to MongoDB for user {}", userId, e);
        }
    }

    @Override
    public List<String> autocomplete(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return Collections.emptyList();
        }

        try {
            NativeQuery autocompleteQuery = NativeQuery.builder()
                    .withQuery(q -> q.match(m -> m.field("name.suggest").query(prefix)))
                    .withPageable(PageRequest.of(0, 20))
                    .build();

            SearchHits<ProductDocument> hits = elasticsearchOperations.search(autocompleteQuery, ProductDocument.class);
            return hits.getSearchHits().stream()
                    .map(h -> h.getContent().getName())
                    .distinct()
                    .limit(5)
                    .toList();
        } catch (Exception e) {
            log.error("Autocomplete failure for prefix: {}", prefix, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> getTrendingKeywords() {
        try {
            String weeklyKey = "search:trending:weekly";
            Set<String> trending = redisTemplate.opsForZSet().reverseRange(weeklyKey, 0, 7);
            if (trending == null || trending.isEmpty()) {
                aggregateWeeklyTrending();
                trending = redisTemplate.opsForZSet().reverseRange(weeklyKey, 0, 7);
            }
            return trending != null ? new ArrayList<>(trending) : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch trending search ranks from Redis", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<SearchHistoryResponse> getPersonalHistory(String userId) {
        try {
            SearchHistory history = mongoTemplate.findById(userId, SearchHistory.class);
            if (history == null || history.getItems() == null) {
                return Collections.emptyList();
            }
            return history.getItems().stream()
                    .map(item -> new SearchHistoryResponse(item.getKeyword(), item.getSearchedAt()))
                    .toList();
        } catch (Exception e) {
            log.error("Failed to query MongoDB personal history for user {}", userId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public void deleteHistoryKeyword(String userId, String keyword) {
        try {
            Query query = Query.query(Criteria.where("id").is(userId));
            Update update = new Update().pull("items", Query.query(Criteria.where("keyword").is(keyword)));
            mongoTemplate.updateFirst(query, update, SearchHistory.class);
            log.info("Deleted keyword '{}' from personal history of user {}", keyword, userId);
        } catch (Exception e) {
            log.error("Failed to delete personal history keyword", e);
        }
    }

    @Override
    public void clearHistory(String userId) {
        try {
            Query query = Query.query(Criteria.where("id").is(userId));
            Update update = new Update().set("items", Collections.emptyList()).set("updatedAt", LocalDateTime.now());
            mongoTemplate.updateFirst(query, update, SearchHistory.class);
            log.info("Cleared personal search history for user {}", userId);
        } catch (Exception e) {
            log.error("Failed to clear personal history", e);
        }
    }

    @Override
    public void mergeHistory(String userId, SearchMergeRequest request) {
        if (request == null || request.getKeywords() == null || request.getKeywords().isEmpty()) {
            return;
        }
        log.info("Merging search history from localStorage for user {}: {} terms", userId,
                request.getKeywords().size());
        List<String> keywords = new ArrayList<>(request.getKeywords());
        Collections.reverse(keywords);
        for (String keyword : keywords) {
            recordPersonalHistory(userId, keyword);
        }
    }

    @Override
    public void reindexAll() {
        log.info("Reindexing all PostgreSQL product records into Elasticsearch...");
        try {
            List<Product> products = productRepository.findAll();
            List<ProductDocument> documents = products.stream().map(product -> {
                String thumbnailUrl = null;
                if (product.getImages() != null) {
                    thumbnailUrl = product.getImages().stream()
                            .filter(ProductImage::isThumbnail)
                            .map(ProductImage::getImageUrl)
                            .findFirst()
                            .orElse(product.getImages().isEmpty() ? null : product.getImages().get(0).getImageUrl());
                }

                BigDecimal minPrice = BigDecimal.ZERO;
                BigDecimal maxPrice = BigDecimal.ZERO;
                if (product.getVariants() != null && !product.getVariants().isEmpty()) {
                    minPrice = product.getVariants().stream()
                            .map(v -> v.getDiscountPrice() != null
                                    && v.getDiscountPrice().compareTo(BigDecimal.ZERO) > 0
                                            ? v.getDiscountPrice()
                                            : v.getPrice())
                            .min(BigDecimal::compareTo)
                            .orElse(BigDecimal.ZERO);
                    maxPrice = product.getVariants().stream()
                            .map(v -> v.getDiscountPrice() != null
                                    && v.getDiscountPrice().compareTo(BigDecimal.ZERO) > 0
                                            ? v.getDiscountPrice()
                                            : v.getPrice())
                            .max(BigDecimal::compareTo)
                            .orElse(BigDecimal.ZERO);
                }

                return ProductDocument.builder()
                        .id(product.getId())
                        .name(product.getName())
                        .description(product.getDescription())
                        .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                        .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                        .creatorId(product.getCreatorId())
                        .thumbnailUrl(thumbnailUrl)
                        .minPrice(minPrice)
                        .maxPrice(maxPrice)
                        .status(product.getStatus().name())
                        .build();
            }).toList();

            productSearchRepository.saveAll(documents);
            log.info("Successfully reindexed {} product documents in Elasticsearch", documents.size());

            log.info("Reindexing all PostgreSQL user records into Elasticsearch...");
            List<com.vibecart.api.modules.iam.entity.User> users = userRepository.findAll();
            List<UserDocument> userDocs = users.stream().map(user -> {
                Set<String> roles = user.getRole() == null ? Collections.emptySet()
                        : Set.of(user.getRole().getName());

                return UserDocument.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .fullName(user.getFullName())
                        .email(user.getEmail())
                        .avatarUrl(user.getAvatarUrl())
                        .status(user.getStatus())
                        .roles(roles)
                        .createdAt(user.getCreatedAt())
                        .updatedAt(user.getUpdatedAt())
                        .build();
            }).toList();
            userSearchRepository.saveAll(userDocs);
            log.info("Successfully reindexed {} user documents in Elasticsearch", userDocs.size());

        } catch (Exception e) {
            log.error("Failed during bulk records reindex", e);
            throw new RuntimeException("Reindex failed", e);
        }
    }

    @Override
    @Scheduled(cron = "0 0 * * * *")
    public void aggregateWeeklyTrending() {
        log.info("Aggregating weekly trending terms...");
        try {
            LocalDateTime now = LocalDateTime.now();
            List<String> keys = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                LocalDateTime day = now.minusDays(i);
                String dateStr = day.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                keys.add("search:trending:" + dateStr);
            }

            String weeklyKey = "search:trending:weekly";
            if (!keys.isEmpty()) {
                redisTemplate.opsForZSet().unionAndStore(keys.get(0), keys.subList(1, keys.size()), weeklyKey);
                redisTemplate.expire(weeklyKey, Duration.ofDays(8));
                log.info("Weekly search trends synchronized in ZSET: {}", weeklyKey);
            }
        } catch (Exception e) {
            log.error("Failed to run weekly trending aggregations", e);
        }
    }

    @Override
    public UserSearchResultResponse searchUsers(String query, int page, int size, String currentUsername) {
        log.info("Searching users with query='{}', page={}, size={}, currentUsername={}",
                query, page, size, currentUsername);

        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();

        if (query != null && !query.isBlank()) {
            boolQueryBuilder.must(m -> m
                    .multiMatch(mm -> mm
                            .query(query)
                            .fields("username^3", "fullName^2")
                            .fuzziness("AUTO")
                            .prefixLength(2)
                            .maxExpansions(50)));
        } else {
            boolQueryBuilder.must(m -> m.matchAll(ma -> ma));
        }

        boolQueryBuilder.filter(f -> f.term(t -> t.field("status").value("ACTIVE")));

        boolQueryBuilder.filter(f -> f.term(t -> t.field("roles").value("ROLE_CREATOR")));

        if (currentUsername != null && !currentUsername.isBlank() && !currentUsername.equals("anonymousUser")) {
            boolQueryBuilder.mustNot(mn -> mn.term(t -> t.field("username.keyword").value(currentUsername)));
        }

        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(boolQueryBuilder.build()))
                .withPageable(PageRequest.of(page, Math.min(size, 50)))
                .build();

        SearchHits<UserDocument> searchHits = elasticsearchOperations.search(searchQuery, UserDocument.class);

        List<UserSearchResponse> items = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(doc -> {
                    boolean isFollowing = false;
                    long followerCount = 0;
                    try {
                        if (currentUsername != null && !currentUsername.equals("anonymousUser")
                                && !currentUsername.isBlank()) {
                            isFollowing = followService.isFollowing(doc.getId(), currentUsername);
                        }
                        followerCount = followService.getFollowerCount(doc.getId());
                    } catch (Exception e) {
                        log.error("Failed to query follow relationships for user: {}", doc.getId(), e);
                    }

                    return UserSearchResponse.builder()
                            .id(doc.getId())
                            .username(doc.getUsername())
                            .fullName(doc.getFullName())
                            .email(doc.getEmail())
                            .avatarUrl(doc.getAvatarUrl())
                            .roles(doc.getRoles())
                            .status(doc.getStatus())
                            .createdAt(doc.getCreatedAt())
                            .isFollowing(isFollowing)
                            .followerCount(followerCount)
                            .build();
                })
                .toList();

        long totalElements = searchHits.getTotalHits();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        boolean last = (page + 1) >= totalPages;

        String suggestion = null;
        if (totalElements == 0 && query != null && !query.isBlank()) {
            suggestion = getSpellcheckUserSuggestion(query);
        }

        if (totalElements > 0 && query != null && !query.isBlank()) {
            recordSearchKeyword(query, currentUsername);
            if (currentUsername != null && !currentUsername.equals("anonymousUser") && !currentUsername.isBlank()) {
                userRepository.findByUsername(currentUsername).ifPresent(u -> {
                    recordPersonalHistory(u.getId(), query.trim());
                });
            }
        }

        return UserSearchResultResponse.builder()
                .content(items)
                .suggestion(suggestion)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last(last)
                .build();
    }

    private String getSpellcheckUserSuggestion(String query) {
        try {
            var response = elasticsearchClient.search(s -> s
                    .index("users")
                    .size(0)
                    .suggest(su -> su
                            .suggesters("user-spelling-suggestion", sg -> sg
                                    .text(query)
                                    .phrase(ph -> ph
                                            .field("fullName")
                                            .confidence(1.0)
                                            .size(1)
                                            .directGenerator(dg -> dg
                                                    .field("fullName")
                                                    .suggestMode(
                                                            co.elastic.clients.elasticsearch._types.SuggestMode.Popular)
                                                    .minWordLength(2))))),
                    UserDocument.class);

            if (response.suggest() != null && response.suggest().containsKey("user-spelling-suggestion")) {
                var suggestList = response.suggest().get("user-spelling-suggestion");
                if (!suggestList.isEmpty()) {
                    var firstSuggest = suggestList.get(0);
                    if (firstSuggest.isPhrase() && firstSuggest.phrase().options() != null
                            && !firstSuggest.phrase().options().isEmpty()) {
                        return firstSuggest.phrase().options().get(0).text();
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch spelling suggestions from Elasticsearch for users", e);
        }
        return null;
    }

    @Override
    public List<String> autocompleteUsers(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return Collections.emptyList();
        }

        try {
            NativeQuery autocompleteQuery = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> b
                            .should(s -> s.match(m -> m.field("username.suggest").query(prefix)))
                            .should(s -> s.match(m -> m.field("fullName.suggest").query(prefix)))))
                    .withPageable(PageRequest.of(0, 20))
                    .build();

            SearchHits<UserDocument> hits = elasticsearchOperations.search(autocompleteQuery, UserDocument.class);
            return hits.getSearchHits().stream()
                    .map(h -> h.getContent().getUsername())
                    .distinct()
                    .limit(5)
                    .toList();
        } catch (Exception e) {
            log.error("Autocomplete failure for prefix on users: {}", prefix, e);
            return Collections.emptyList();
        }
    }

    @Override
    public void indexUser(com.vibecart.api.modules.iam.entity.User user) {
        if (user == null)
            return;
        log.info("Indexing user to Elasticsearch: {}", user.getUsername());
        try {
            Set<String> roles = user.getRole() == null ? Collections.emptySet()
                    : Set.of(user.getRole().getName());

            UserDocument document = UserDocument.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .fullName(user.getFullName())
                    .email(user.getEmail())
                    .avatarUrl(user.getAvatarUrl())
                    .status(user.getStatus())
                    .roles(roles)
                    .createdAt(user.getCreatedAt())
                    .updatedAt(user.getUpdatedAt())
                    .build();

            userSearchRepository.save(document);
            log.info("Successfully indexed user in ES: {}", user.getUsername());
        } catch (Exception e) {
            log.error("Failed to index user in ES: {}", user.getUsername(), e);
        }
    }

    @Override
    public void deleteUser(String userId) {
        if (userId == null)
            return;
        log.info("Deleting user from Elasticsearch: {}", userId);
        try {
            userSearchRepository.deleteById(userId);
            log.info("Successfully deleted user from ES: {}", userId);
        } catch (Exception e) {
            log.error("Failed to delete user from ES: {}", userId, e);
        }
    }
}
