package com.vibecart.api.modules.notification.service.impl;

import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.repository.UserRepository;
import com.vibecart.api.modules.notification.dto.event.InAppNotificationEvent;
import com.vibecart.api.modules.notification.dto.request.UpdatePreferencesRequest;
import com.vibecart.api.modules.notification.dto.response.NotificationResponse;
import com.vibecart.api.modules.notification.dto.response.PreferencesResponse;
import com.vibecart.api.modules.notification.entity.Notification;
import com.vibecart.api.modules.notification.entity.NotificationPreference;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    private static final String UNREAD_COUNT_KEY_PREFIX = "notification:unread:";

    @Override
    public PageResponse<NotificationResponse> getNotifications(String username, int page, int size, String readStatus) {
        String userId = getUserIdByUsername(username);
        Pageable pageable = PageRequest.of(page, size);

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
    public long getUnreadCount(String username) {
        String userId = getUserIdByUsername(username);
        String key = UNREAD_COUNT_KEY_PREFIX + userId;

        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return Long.parseLong(cached);
        }

        long count = notificationRepository.countByRecipientIdAndIsReadFalse(userId);
        redisTemplate.opsForValue().set(key, String.valueOf(count), 1, TimeUnit.DAYS);
        return count;
    }

    @Override
    public void markAsRead(String notificationId, String username) {
        String userId = getUserIdByUsername(username);

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
    public void markAllAsRead(String username) {
        String userId = getUserIdByUsername(username);

        Query query = new Query(Criteria.where("recipient_id").is(userId).and("is_read").is(false));
        Update update = new Update().set("is_read", true);
        mongoTemplate.updateMulti(query, update, Notification.class);

        String key = UNREAD_COUNT_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, "0");
    }

    @Override
    public void deleteNotification(String notificationId, String username) {
        String userId = getUserIdByUsername(username);

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
    public void deleteAllNotifications(String username) {
        String userId = getUserIdByUsername(username);
        notificationRepository.deleteByRecipientId(userId);

        String key = UNREAD_COUNT_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, "0");
    }

    @Override
    public void processAndBroadcast(InAppNotificationEvent event) {
        NotificationPreference preference = preferenceRepository.findByUserId(event.getRecipientId()).orElse(null);
        if (preference != null) {
            NotificationPreference.ChannelPreference channelPref = preference.getPreferences().get(event.getType());
            if (channelPref != null && !channelPref.isInApp()) {
                log.info("Notification disabled by user preference: type={}, recipient={}",
                        event.getType(), event.getRecipientId());
                return;
            }
        }

        boolean isDuplicate = notificationRepository.existsByActorIdAndRecipientIdAndTypeAndCreatedAtAfter(
                event.getActorId(),
                event.getRecipientId(),
                event.getType(),
                Instant.now().minus(5, ChronoUnit.MINUTES)
        );

        if (isDuplicate) {
            log.info("Skipping duplicate notification: actor={}, recipient={}, type={}",
                    event.getActorId(), event.getRecipientId(), event.getType());
            return;
        }

        Notification notification = notificationMapper.fromEvent(event);
        notificationRepository.save(notification);

        incrementUnreadCount(event.getRecipientId());

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

        log.info("Notification sent: type={}, actor={}, recipient={}",
                event.getType(), event.getActorUsername(), event.getRecipientUsername());
    }

    @Override
    public PreferencesResponse getPreferences(String username) {
        String userId = getUserIdByUsername(username);
        NotificationPreference preference = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> buildDefaultPreference(userId));

        return mapToPreferencesResponse(preference);
    }

    @Override
    public PreferencesResponse updatePreferences(String username, UpdatePreferencesRequest request) {
        String userId = getUserIdByUsername(username);
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

    private NotificationPreference buildDefaultPreference(String userId) {
        Map<String, NotificationPreference.ChannelPreference> defaults = new HashMap<>();
        defaults.put("FOLLOW", NotificationPreference.ChannelPreference.builder().inApp(true).sound(true).push(true).build());
        defaults.put("LIKE", NotificationPreference.ChannelPreference.builder().inApp(true).sound(false).push(false).build());
        defaults.put("COMMENT", NotificationPreference.ChannelPreference.builder().inApp(true).sound(true).push(true).build());
        defaults.put("ORDER_PAID", NotificationPreference.ChannelPreference.builder().inApp(true).sound(true).push(true).build());
        defaults.put("ORDER_DELIVERED", NotificationPreference.ChannelPreference.builder().inApp(true).sound(true).push(true).build());

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
        String key = UNREAD_COUNT_KEY_PREFIX + userId;
        redisTemplate.opsForValue().increment(key);
    }

    private void decrementUnreadCount(String userId) {
        String key = UNREAD_COUNT_KEY_PREFIX + userId;
        Long current = redisTemplate.opsForValue().decrement(key);
        if (current != null && current < 0) {
            redisTemplate.opsForValue().set(key, "0");
        }
    }

    private String getUserIdByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        return user.getId();
    }
}
