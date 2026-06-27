package com.vibecart.api.modules.notification.service.impl;

import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.modules.notification.dto.event.InAppNotificationEvent;
import com.vibecart.api.modules.notification.dto.request.UpdatePreferencesRequest;
import com.vibecart.api.modules.notification.dto.response.NotificationResponse;
import com.vibecart.api.modules.notification.dto.response.PreferencesResponse;
import com.vibecart.api.modules.notification.entity.Notification;
import com.vibecart.api.modules.notification.entity.NotificationPreference;
import com.vibecart.api.modules.notification.entity.NotificationType;
import com.vibecart.api.modules.notification.mapper.NotificationMapper;
import com.vibecart.api.modules.notification.repository.NotificationPreferenceRepository;
import com.vibecart.api.modules.notification.repository.NotificationRepository;
import com.vibecart.api.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationMapper notificationMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;
    private final MongoTemplate mongoTemplate;

    private static final String UNREAD_COUNT_KEY_PREFIX = "notification:unread:";

    @Override
    public PageResponse<NotificationResponse> getNotifications(String userId, int page, int size, String readStatus) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));

        Page<Notification> notificationPage;
        if ("UNREAD".equalsIgnoreCase(readStatus)) {
            notificationPage = notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable);
        } else {
            notificationPage = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId, pageable);
        }

        List<NotificationResponse> content = notificationPage.getContent().stream()
                .map(notificationMapper::toResponse)
                .toList();

        return PageResponse.<NotificationResponse>builder()
                .content(content)
                .page(notificationPage.getNumber())
                .size(notificationPage.getSize())
                .totalElements(notificationPage.getTotalElements())
                .totalPages(notificationPage.getTotalPages())
                .last(notificationPage.isLast())
                .build();
    }

    @Override
    public long getUnreadCount(String userId) {
        String key = UNREAD_COUNT_KEY_PREFIX + userId;

        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return Long.parseLong(cached);
            }
        } catch (Exception e) {
            log.warn("Failed to read unread count from Redis for user {}", userId, e);
        }

        long count = notificationRepository.countByRecipientIdAndIsReadFalse(userId);
        try {
            redisTemplate.opsForValue().set(key, String.valueOf(count), 1, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Failed to cache unread count in Redis for user {}", userId, e);
        }
        return count;
    }

    @Override
    public void markAsRead(String notificationId, String userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getRecipientId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (!notification.isRead()) {
            notification.setRead(true);
            notificationRepository.save(notification);
            decrementUnreadCount(userId);
        }
    }

    @Override
    public void markAllAsRead(String userId) {
        Query query = new Query(Criteria.where("recipient_id").is(userId).and("is_read").is(false));
        Update update = new Update().set("is_read", true);
        mongoTemplate.updateMulti(query, update, Notification.class);

        syncUnreadCountFromDb(userId);
    }

    @Override
    public void deleteNotification(String notificationId, String userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getRecipientId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (!notification.isRead()) {
            decrementUnreadCount(userId);
        }

        notificationRepository.delete(notification);
    }

    @Override
    public void deleteAllNotifications(String userId) {
        notificationRepository.deleteByRecipientId(userId);
        syncUnreadCountFromDb(userId);
    }

    @Override
    public void processAndBroadcast(InAppNotificationEvent event) {
        if (!validateEvent(event)) {
            return;
        }

        if (isSelfNotification(event)) {
            log.debug("Skipping self-notification: actor={}, recipient={}, type={}",
                    event.getActorId(), event.getRecipientId(), event.getType());
            return;
        }

        NotificationType notificationType = parseNotificationType(event.getType());
        if (notificationType == null) {
            log.warn("Unknown notification type: {}. Skipping.", event.getType());
            return;
        }

        NotificationPreference preference = preferenceRepository.findByUserId(event.getRecipientId()).orElse(null);
        if (isNotificationDisabled(preference, event.getType())) {
            log.info("Notification disabled by user preference: type={}, recipient={}",
                    event.getType(), event.getRecipientId());
            return;
        }

        if (isDuplicate(event)) {
            log.info("Skipping duplicate notification: actor={}, recipient={}, type={}",
                    event.getActorId(), event.getRecipientId(), event.getType());
            return;
        }

        Notification notification = buildOrAggregateNotification(event, notificationType);
        boolean isNew = notification.getId() == null;

        notificationRepository.save(notification);

        if (isNew) {
            incrementUnreadCount(event.getRecipientId());
        }

        broadcastToUser(event, notification, preference);
    }

    @Override
    public PreferencesResponse getPreferences(String userId) {
        NotificationPreference preference = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> buildDefaultPreference(userId));

        return mapToPreferencesResponse(preference);
    }

    @Override
    public PreferencesResponse updatePreferences(String userId, UpdatePreferencesRequest request) {
        NotificationPreference preference = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> buildDefaultPreference(userId));

        Map<String, NotificationPreference.ChannelPreference> updatedPrefs = new HashMap<>();
        request.getPreferences().forEach((type, dto) -> {
            updatedPrefs.put(type, NotificationPreference.ChannelPreference.builder()
                    .inApp(dto.isInApp())
                    .sound(dto.isSound())
                    .push(dto.isPush())
                    .build());
        });

        preference.setPreferences(updatedPrefs);
        preferenceRepository.save(preference);

        return mapToPreferencesResponse(preference);
    }

    private boolean validateEvent(InAppNotificationEvent event) {
        if (event.getRecipientId() == null || event.getRecipientId().isBlank()) {
            log.error("Notification event has null/blank recipientId. EventId={}, type={}",
                    event.getEventId(), event.getType());
            return false;
        }
        if (event.getActorId() == null || event.getActorId().isBlank()) {
            log.error("Notification event has null/blank actorId. EventId={}", event.getEventId());
            return false;
        }
        return true;
    }

    private boolean isSelfNotification(InAppNotificationEvent event) {
        return event.getActorId().equals(event.getRecipientId());
    }

    private NotificationType parseNotificationType(String type) {
        if (type == null) return null;
        try {
            return NotificationType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isNotificationDisabled(NotificationPreference preference, String type) {
        if (preference == null) return false;
        NotificationPreference.ChannelPreference channelPref = preference.getPreferences().get(type);
        return channelPref != null && !channelPref.isInApp();
    }

    private boolean isDuplicate(InAppNotificationEvent event) {
        return notificationRepository.existsByActorIdAndRecipientIdAndTypeAndCreatedAtAfter(
                event.getActorId(),
                event.getRecipientId(),
                event.getType(),
                Instant.now().minus(5, ChronoUnit.MINUTES)
        );
    }

    private Notification buildOrAggregateNotification(InAppNotificationEvent event, NotificationType notificationType) {
        String groupKey = resolveGroupKey(event, notificationType);

        if (groupKey != null) {
            Optional<Notification> existingOpt = notificationRepository
                    .findFirstByRecipientIdAndGroupKeyAndIsReadFalseAndCreatedAtAfterOrderByCreatedAtDesc(
                            event.getRecipientId(), groupKey, Instant.now().minus(24, ChronoUnit.HOURS));

            if (existingOpt.isPresent()) {
                return aggregateIntoExisting(existingOpt.get(), event, notificationType);
            }
        }

        Notification notification = notificationMapper.fromEvent(event);
        notification.setGroupKey(groupKey);
        notification.setAggregatedActorIds(new ArrayList<>(List.of(event.getActorId())));
        notification.setAggregatedActorNames(new ArrayList<>(List.of(event.getActorFullName())));
        notification.setAggregatedCount(1);
        return notification;
    }

    private String resolveGroupKey(InAppNotificationEvent event, NotificationType type) {
        return switch (type) {
            case FOLLOW -> "FOLLOW:" + event.getRecipientId();
            case LIKE -> event.getReferenceId() != null ? "LIKE:" + event.getReferenceId() : null;
            case COMMENT -> event.getReferenceId() != null ? "COMMENT:" + event.getReferenceId() : null;
            default -> null;
        };
    }

    private Notification aggregateIntoExisting(Notification notification, InAppNotificationEvent event, NotificationType type) {
        if (notification.getAggregatedActorIds() == null) {
            notification.setAggregatedActorIds(new ArrayList<>(List.of(notification.getActorId())));
        }
        if (notification.getAggregatedActorNames() == null) {
            notification.setAggregatedActorNames(new ArrayList<>(List.of(notification.getActorFullName())));
        }

        if (!notification.getAggregatedActorIds().contains(event.getActorId())) {
            notification.getAggregatedActorIds().add(event.getActorId());
            notification.getAggregatedActorNames().add(event.getActorFullName());
        }

        notification.setActorId(event.getActorId());
        notification.setActorUsername(event.getActorUsername());
        notification.setActorFullName(event.getActorFullName());
        notification.setActorAvatarUrl(event.getActorAvatarUrl());
        notification.setCreatedAt(Instant.now());

        int count = notification.getAggregatedActorIds().size();
        notification.setAggregatedCount(count);
        notification.setContent(generateContent(type, notification.getAggregatedActorNames(), count));

        return notification;
    }

    private String generateContent(NotificationType type, List<String> actorNames, int count) {
        if (actorNames == null || actorNames.isEmpty()) return "";

        String action = switch (type) {
            case FOLLOW -> "đã bắt đầu theo dõi bạn";
            case LIKE -> "đã thích bài viết của bạn";
            case COMMENT -> "đã bình luận bài viết của bạn";
            case ORDER_PAID -> "đã đặt hàng sản phẩm của bạn";
            case ORDER_DELIVERED -> "đã nhận đơn hàng thành công";
            case PRODUCT_NEW -> "đã đăng sản phẩm mới";
        };

        if (count == 1) {
            return actorNames.get(0) + " " + action;
        } else if (count == 2) {
            return actorNames.get(0) + " và " + actorNames.get(1) + " " + action;
        } else {
            return actorNames.get(count - 1) + ", " + actorNames.get(count - 2)
                    + " và " + (count - 2) + " người khác " + action;
        }
    }

    private void broadcastToUser(InAppNotificationEvent event, Notification notification,
                                  NotificationPreference preference) {
        NotificationResponse response = notificationMapper.toResponse(notification);

        boolean sendSound = true;
        if (preference != null) {
            NotificationPreference.ChannelPreference channelPref = preference.getPreferences().get(event.getType());
            if (channelPref != null) {
                sendSound = channelPref.isSound();
            }
        }
        response.setSendSound(sendSound);

        messagingTemplate.convertAndSendToUser(
                event.getRecipientUsername(), "/queue/notifications", response);

        log.info("Notification processed: type={}, aggregatedCount={}, isNew={}, recipient={}",
                event.getType(), notification.getAggregatedCount(),
                notification.getId() == null, event.getRecipientUsername());
    }

    private NotificationPreference buildDefaultPreference(String userId) {
        Map<String, NotificationPreference.ChannelPreference> defaults = new HashMap<>();
        for (NotificationType type : NotificationType.values()) {
            boolean isSoundDefault = type != NotificationType.LIKE;
            boolean isPushDefault = type != NotificationType.LIKE;
            defaults.put(type.name(), NotificationPreference.ChannelPreference.builder()
                    .inApp(true).sound(isSoundDefault).push(isPushDefault).build());
        }

        return NotificationPreference.builder()
                .userId(userId)
                .preferences(defaults)
                .build();
    }

    private PreferencesResponse mapToPreferencesResponse(NotificationPreference preference) {
        Map<String, PreferencesResponse.ChannelPreferenceDto> dtoMap = preference.getPreferences()
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> PreferencesResponse.ChannelPreferenceDto.builder()
                                .inApp(e.getValue().isInApp())
                                .sound(e.getValue().isSound())
                                .push(e.getValue().isPush())
                                .build()
                ));

        return PreferencesResponse.builder().preferences(dtoMap).build();
    }

    private void incrementUnreadCount(String userId) {
        try {
            String key = UNREAD_COUNT_KEY_PREFIX + userId;
            redisTemplate.opsForValue().increment(key);
        } catch (Exception e) {
            log.warn("Failed to increment unread count in Redis for user {}", userId, e);
        }
    }

    private void decrementUnreadCount(String userId) {
        try {
            String key = UNREAD_COUNT_KEY_PREFIX + userId;
            Long current = redisTemplate.opsForValue().decrement(key);
            if (current != null && current < 0) {
                redisTemplate.opsForValue().set(key, "0");
            }
        } catch (Exception e) {
            log.warn("Failed to decrement unread count in Redis for user {}", userId, e);
        }
    }

    private void syncUnreadCountFromDb(String userId) {
        try {
            long count = notificationRepository.countByRecipientIdAndIsReadFalse(userId);
            String key = UNREAD_COUNT_KEY_PREFIX + userId;
            redisTemplate.opsForValue().set(key, String.valueOf(count), 1, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Failed to sync unread count from DB to Redis for user {}", userId, e);
        }
    }
}
