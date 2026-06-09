package com.vibecart.api.modules.social.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Bộ lọc từ ngữ thô tục, nhạy cảm (Profanity Filter).
 * Kiểm tra nội dung bình luận trước khi lưu vào database.
 * Dựa trên danh sách từ cấm có sẵn (tiếng Việt + tiếng Anh).
 */
@Component
public class ProfanityFilter {

    private static final List<String> BANNED_WORDS = List.of(
            "đụ", "địt", "lồn", "cặc", "buồi", "đéo", "đĩ", "cave",
            "chó má", "thằng chó", "con chó", "đồ chó", "ngu", "đần",
            "khốn nạn", "mặt lồn", "đồ điếm", "thằng ngu", "con điếm",
            "vãi", "vl", "vcl", "clgt", "dmm", "đmm", "vkl",
            "fuck", "shit", "bitch", "asshole", "dick", "pussy",
            "motherfucker", "cunt", "bastard", "whore", "slut"
    );

    private static final List<Pattern> BANNED_PATTERNS;

    static {
        BANNED_PATTERNS = BANNED_WORDS.stream()
                .map(word -> Pattern.compile(
                        "(?i)" + Pattern.quote(word), // Case-insensitive
                        Pattern.UNICODE_CASE
                ))
                .toList();
    }

    /**
     * Kiểm tra nội dung có chứa từ ngữ thô tục hay không.
     *
     * @param content Nội dung cần kiểm tra
     * @return true nếu nội dung vi phạm (chứa từ cấm)
     */
    public boolean containsProfanity(String content) {
        if (content == null || content.isBlank()) return false;

        String lowerContent = content.toLowerCase();
        return BANNED_PATTERNS.stream()
                .anyMatch(pattern -> pattern.matcher(lowerContent).find());
    }
}
