package com.vibecart.api.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public enum ErrorCode {
        SUCCESS(1000, "Thành công", HttpStatus.OK),
        UNCATEGORIZED_EXCEPTION(9999, "Đã xảy ra lỗi không xác định", HttpStatus.INTERNAL_SERVER_ERROR),
        INVALID_KEY(1001, "Khóa thông báo không hợp lệ", HttpStatus.BAD_REQUEST),
        USER_EXISTED(1002, "Tài khoản đã tồn tại", HttpStatus.BAD_REQUEST),
        USERNAME_EXISTED(1009, "Tên đăng nhập đã được sử dụng", HttpStatus.BAD_REQUEST),
        EMAIL_EXISTED(1010, "Email đã được sử dụng", HttpStatus.BAD_REQUEST),
        USERNAME_INVALID(1003, "Tên đăng nhập phải từ 5 đến 30 ký tự", HttpStatus.BAD_REQUEST),
        INVALID_PASSWORD(1004,
                        "Mật khẩu phải từ 8 đến 100 ký tự, bao gồm ít nhất 1 chữ hoa, 1 chữ thường, 1 chữ số và 1 ký tự đặc biệt (@#$%^&+=!)",
                        HttpStatus.BAD_REQUEST),
        USER_NOT_EXISTED(1005, "Tài khoản không tồn tại", HttpStatus.NOT_FOUND),
        UNAUTHENTICATED(1006, "Xác thực thất bại. Vui lòng kiểm tra lại tài khoản hoặc mật khẩu",
                        HttpStatus.UNAUTHORIZED),
        UNAUTHORIZED(1007, "Bạn không có quyền truy cập tài nguyên này", HttpStatus.FORBIDDEN),
        INVALID_INPUT(1008, "Dữ liệu đầu vào không hợp lệ", HttpStatus.BAD_REQUEST),

        OTP_EXPIRED(1011, "Mã OTP đã hết hạn. Vui lòng yêu cầu mã mới", HttpStatus.BAD_REQUEST),
        INVALID_OTP(1012, "Mã OTP không đúng", HttpStatus.BAD_REQUEST),
        OTP_COOLDOWN(1013, "Vui lòng chờ 60 giây trước khi yêu cầu mã OTP mới", HttpStatus.TOO_MANY_REQUESTS),
        OTP_ATTEMPTS_EXCEEDED(1014, "Nhập sai mã OTP quá nhiều lần. Mã đã bị vô hiệu hóa, vui lòng yêu cầu mã mới",
                        HttpStatus.LOCKED),
        ACCOUNT_TEMPORARILY_LOCKED(1015,
                        "Tài khoản tạm thời bị khóa do đăng nhập sai quá nhiều lần. Vui lòng thử lại sau 15 phút",
                        HttpStatus.LOCKED),
        DISPOSABLE_EMAIL_NOT_ALLOWED(1016, "Email tạm thời không được phép đăng ký", HttpStatus.BAD_REQUEST),
        PASSWORD_MISMATCH(1017, "Mật khẩu xác nhận không khớp", HttpStatus.BAD_REQUEST),
        SAME_PASSWORD(1018, "Mật khẩu mới không được trùng với mật khẩu cũ", HttpStatus.BAD_REQUEST),
        ACCOUNT_NOT_ACTIVE(1019, "Tài khoản không hoạt động", HttpStatus.BAD_REQUEST),
        ACCOUNT_PENDING_VERIFICATION(1020, "Tài khoản chưa được xác thực. Vui lòng kiểm tra email để lấy mã OTP",
                        HttpStatus.BAD_REQUEST),
        ACCOUNT_BANNED(1021, "Tài khoản đã bị cấm truy cập", HttpStatus.FORBIDDEN),
        INVALID_RESET_TOKEN(1022, "Token khôi phục mật khẩu không hợp lệ hoặc đã hết hạn", HttpStatus.BAD_REQUEST),
        OLD_PASSWORD_INCORRECT(1023, "Mật khẩu hiện tại không đúng", HttpStatus.BAD_REQUEST),
        CANNOT_CHANGE_OWN_STATUS(1024, "Không thể thay đổi trạng thái tài khoản của chính mình",
                        HttpStatus.BAD_REQUEST),
        INVALID_STATUS(1025, "Trạng thái tài khoản không hợp lệ", HttpStatus.BAD_REQUEST),
        CANNOT_CHANGE_OWN_ROLE(1026, "Không thể tự thay đổi vai trò (phân quyền) của chính mình",
                        HttpStatus.BAD_REQUEST),
        RATE_LIMIT_EXCEEDED(1030, "Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau", HttpStatus.TOO_MANY_REQUESTS),

        PRODUCT_NOT_FOUND(2001, "Sản phẩm không tồn tại hoặc đã bị xóa", HttpStatus.NOT_FOUND),
        PRODUCT_INACTIVE(2002, "Sản phẩm hiện không khả dụng", HttpStatus.BAD_REQUEST),
        OUT_OF_STOCK(2003, "Sản phẩm đã hết hàng", HttpStatus.BAD_REQUEST),
        INSUFFICIENT_STOCK(2004, "Số lượng tồn kho không đủ", HttpStatus.BAD_REQUEST),
        INVENTORY_ADJUST_FAILED(2005, "Điều chỉnh tồn kho thất bại (không đủ số lượng)", HttpStatus.BAD_REQUEST),
        CATEGORY_NOT_FOUND(2006, "Danh mục không tồn tại", HttpStatus.NOT_FOUND),
        CATEGORY_HAS_PRODUCTS(2007, "Không thể xóa danh mục đang chứa sản phẩm", HttpStatus.BAD_REQUEST),
        CATEGORY_NOT_LEAF(2008, "Sản phẩm chỉ được liên kết với danh mục lá (không có danh mục con)",
                        HttpStatus.BAD_REQUEST),
        VARIANT_NOT_FOUND(2009, "Biến thể sản phẩm không tồn tại", HttpStatus.NOT_FOUND),

        CART_EMPTY(2010, "Giỏ hàng trống", HttpStatus.BAD_REQUEST),
        CART_ITEM_NOT_FOUND(2011, "Sản phẩm không tồn tại trong giỏ hàng", HttpStatus.NOT_FOUND),
        CART_QUANTITY_EXCEEDED(2012, "Số lượng sản phẩm trong giỏ hàng vượt quá giới hạn tối đa (100)",
                        HttpStatus.BAD_REQUEST),
        VARIANT_INACTIVE(2013, "Biến thể sản phẩm không còn khả dụng", HttpStatus.BAD_REQUEST),
        CATEGORY_HAS_CHILDREN(2014, "Không thể xóa danh mục đang có danh mục con", HttpStatus.BAD_REQUEST),
        PRODUCT_ACCESS_DENIED(2015, "Bạn không có quyền thực hiện thao tác này trên sản phẩm", HttpStatus.FORBIDDEN),

        VOUCHER_NOT_FOUND(2020, "Mã giảm giá không tồn tại", HttpStatus.NOT_FOUND),
        VOUCHER_EXPIRED(2021, "Mã giảm giá đã hết hạn", HttpStatus.BAD_REQUEST),
        VOUCHER_USAGE_LIMIT_REACHED(2022, "Mã giảm giá đã hết lượt sử dụng", HttpStatus.BAD_REQUEST),
        VOUCHER_MIN_ORDER_NOT_MET(2023, "Giá trị đơn hàng chưa đạt mức tối thiểu để áp dụng mã giảm giá",
                        HttpStatus.BAD_REQUEST),
        VOUCHER_INACTIVE(2024, "Mã giảm giá không còn hiệu lực", HttpStatus.BAD_REQUEST),

        ORDER_NOT_FOUND(2030, "Đơn hàng không tồn tại", HttpStatus.NOT_FOUND),
        INVALID_ORDER_STATE_TRANSITION(2031, "Chuyển đổi trạng thái đơn hàng không hợp lệ", HttpStatus.BAD_REQUEST),
        ORDER_ALREADY_CANCELLED(2032, "Đơn hàng đã bị hủy trước đó", HttpStatus.BAD_REQUEST),
        DUPLICATE_ORDER_REQUEST(2040, "Yêu cầu đặt hàng trùng lặp, vui lòng chờ xử lý", HttpStatus.CONFLICT),
        TRACKING_NUMBER_REQUIRED(2041, "Mã vận đơn (Tracking Number) là bắt buộc khi chuyển sang trạng thái SHIPPED",
                        HttpStatus.BAD_REQUEST),
        ORDER_ACCESS_DENIED(2042, "Bạn không có quyền truy cập đơn hàng này", HttpStatus.FORBIDDEN),

        SYSTEM_BUSY(2050, "Hệ thống đang bận, vui lòng thử lại sau giây lát", HttpStatus.SERVICE_UNAVAILABLE),
        PAYMENT_GATEWAY_ERROR(2060, "Cổng thanh toán tạm thời gặp sự cố", HttpStatus.BAD_GATEWAY),
        INVALID_WEBHOOK_SIGNATURE(2061, "Chữ ký webhook không hợp lệ", HttpStatus.BAD_REQUEST),
        INVALID_PAYMENT_METHOD(2062, "Phương thức thanh toán không hợp lệ", HttpStatus.BAD_REQUEST),

        POST_NOT_FOUND(3001, "Bài viết không tồn tại hoặc đã bị xóa", HttpStatus.NOT_FOUND),
        POST_ACCESS_DENIED(3002, "Bạn không có quyền thực hiện thao tác này trên bài viết", HttpStatus.FORBIDDEN),
        COMMENT_NOT_FOUND(3003, "Bình luận không tồn tại hoặc đã bị xóa", HttpStatus.NOT_FOUND),
        COMMENT_ACCESS_DENIED(3004, "Bạn không có quyền xóa bình luận này", HttpStatus.FORBIDDEN),
        CANNOT_FOLLOW_SELF(3005, "Bạn không thể theo dõi chính mình", HttpStatus.BAD_REQUEST),
        MAX_MEDIA_EXCEEDED(3006, "Số lượng media vượt quá giới hạn tối đa (10)", HttpStatus.BAD_REQUEST),
        MAX_PRODUCTS_EXCEEDED(3007, "Số lượng sản phẩm gắn thẻ vượt quá giới hạn tối đa (5)", HttpStatus.BAD_REQUEST),
        MAX_COMMENT_DEPTH(3008, "Bình luận lồng nhau đã đạt độ sâu tối đa (3 cấp)", HttpStatus.BAD_REQUEST),
        CREATOR_ROLE_REQUIRED(3009, "Chỉ Creator mới có quyền đăng bài viết", HttpStatus.FORBIDDEN),
        PROFANITY_DETECTED(3010, "Nội dung bình luận chứa từ ngữ thô tục, nhạy cảm. Vui lòng chỉnh sửa lại",
                        HttpStatus.BAD_REQUEST),

        FILE_TYPE_NOT_SUPPORTED(4001, "Loại file không được hỗ trợ. Chấp nhận: JPEG, PNG, GIF, WEBP, MP4, WEBM",
                        HttpStatus.BAD_REQUEST),
        FILE_TOO_LARGE(4002, "Kích thước file vượt quá giới hạn cho phép", HttpStatus.BAD_REQUEST),
        FILE_UPLOAD_FAILED(4003, "Tải lên file thất bại, vui lòng thử lại", HttpStatus.INTERNAL_SERVER_ERROR),
        MEDIA_NOT_FOUND(4004, "File media không tồn tại trong hệ thống. Vui lòng tải lên trước khi đính kèm",
                        HttpStatus.BAD_REQUEST),
        MEDIA_NOT_VERIFIED(4005, "File media chưa được xác thực (đang chờ quét virus hoặc xử lý)",
                        HttpStatus.BAD_REQUEST),
        MEDIA_ACCESS_DENIED(4006, "Bạn không có quyền sử dụng file media này (không phải người tải lên)",
                        HttpStatus.FORBIDDEN),

        CONVERSATION_NOT_FOUND(5001, "Cuộc hội thoại không tồn tại hoặc đã bị xóa", HttpStatus.NOT_FOUND),
        CONVERSATION_ACCESS_DENIED(5002, "Bạn không có quyền truy cập cuộc hội thoại này", HttpStatus.FORBIDDEN),
        ;

        private final int code;
        private final String message;
        private final HttpStatusCode statusCode;

        ErrorCode(int code, String message, HttpStatusCode statusCode) {
                this.code = code;
                this.message = message;
                this.statusCode = statusCode;
        }

        public int getCode() {
                return code;
        }

        public String getMessage() {
                return message;
        }

        public HttpStatusCode getStatusCode() {
                return statusCode;
        }
}
