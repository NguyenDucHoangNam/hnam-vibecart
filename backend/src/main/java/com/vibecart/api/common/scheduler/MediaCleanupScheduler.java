package com.vibecart.api.common.scheduler;

import com.vibecart.api.common.entity.MediaMetadata;
import com.vibecart.api.common.repository.MediaMetadataRepository;
import com.vibecart.api.common.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MediaCleanupScheduler {

    private final MediaMetadataRepository mediaMetadataRepository;
    private final StorageService storageService;

    /**
     * Tác vụ quét dọn định kỳ chạy mỗi 15 phút.
     * Tự động xóa các file có trạng thái PENDING quá 15 phút trên S3 và xóa bản ghi
     * DB tương ứng.
     */
    @Scheduled(cron = "0 */15 * * * *")
    public void cleanupOrphanedPendingUploads() {
        ZonedDateTime cutoffTime = ZonedDateTime.now().minusMinutes(15);
        log.info("Bắt đầu tác vụ dọn dẹp tệp tin rác trên S3. Thời gian giới hạn: {}", cutoffTime);

        try {
            List<MediaMetadata> expiredPendingFiles = mediaMetadataRepository
                    .findByStatusAndCreatedAtBefore("PENDING", cutoffTime);

            if (expiredPendingFiles.isEmpty()) {
                log.info("Không tìm thấy tệp tin PENDING quá hạn nào cần dọn dẹp.");
                return;
            }

            log.info("Tìm thấy {} tệp tin PENDING quá hạn. Bắt đầu tiến trình xóa...", expiredPendingFiles.size());

            for (MediaMetadata metadata : expiredPendingFiles) {
                String key = metadata.getS3Key();

                try {
                    storageService.deleteFile(key);
                    log.info("Đã xóa thành công tệp rác quá hạn trên S3: {}", key);
                } catch (Exception e) {
                    log.error("Lỗi khi xóa tệp rác quá hạn '{}' trên S3: {}", key, e.getMessage());
                }

                try {
                    mediaMetadataRepository.delete(metadata);
                    log.info("Đã xóa bản ghi metadata tương ứng trong DB cho key: {}", key);
                } catch (Exception e) {
                    log.error("Lỗi khi xóa bản ghi metadata trong DB cho key '{}': {}", key, e.getMessage());
                }
            }

            log.info("Hoàn tất tác vụ dọn dẹp định kỳ tệp tin rác S3.");
        } catch (Exception e) {
            log.error("Đã xảy ra lỗi nghiêm trọng trong tác vụ dọn dẹp định kỳ tệp tin S3", e);
        }
    }
}
