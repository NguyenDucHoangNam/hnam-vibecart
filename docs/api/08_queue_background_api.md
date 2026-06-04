# 🔌 Đặc tả API - Phân hệ 8: Hàng đợi & Xử lý Nền (Queue & Background Processing)

Tài liệu này đặc tả hợp đồng tích hợp API (Contracts) giữa Frontend-Backend cho luồng kết xuất Báo cáo dữ liệu lớn bất đồng bộ (Async Report Generation) và cổng điều phối quản trị kích hoạt Spring Batch Job thủ công của hệ thống **VibeCart**.

---

## 📈 1. Các Endpoints Kết xuất Báo cáo Bất đồng bộ (Async Report APIs)

Các tác vụ sinh tệp tin Excel dung lượng lớn được xử lý bất đồng bộ thông qua Executor Thread Pool. Client nhận ngay Task ID để theo dõi tiến trình bằng cách polling.

### 1.1 Yêu cầu xuất báo cáo doanh thu (Request Export Report)

Yêu cầu hệ thống kết xuất tệp tin Excel (.xlsx) báo cáo doanh thu trong khoảng thời gian xác định. Phản hồi HTTP 202 Accepted được trả về ngay lập tức kèm mã Task ID.

*   **Method:** `POST`
*   **Path:** `/api/v1/reports/export`
*   **Auth Level:** `@PreAuthorize("hasRole('CREATOR')")`
*   **Request Body (`ExportReportRequest`):**
    ```json
    {
      "startDate": "2025-01-01T00:00:00+07:00",
      "endDate": "2025-12-31T23:59:59+07:00"
    }
    ```
    | Trường | Kiểu | Bắt buộc | Mô tả |
    | :--- | :--- | :--- | :--- |
    | `startDate` | `ZonedDateTime` (ISO 8601) | Không | Ngày bắt đầu khoảng thời gian lọc. Nếu `null`, lấy toàn bộ. |
    | `endDate` | `ZonedDateTime` (ISO 8601) | Không | Ngày kết thúc khoảng thời gian lọc. Nếu `null`, lấy toàn bộ. |

*   **Response Success (HTTP 202 Accepted):**
    ```json
    {
      "code": 1000,
      "message": "Tiếp nhận yêu cầu xuất báo cáo thành công",
      "result": {
        "taskId": "7f805a3b-2401-4475-8123-92f7e719c8f0",
        "status": "PENDING",
        "message": "Yêu cầu kết xuất báo cáo đã được tiếp nhận thành công. Vui lòng theo dõi trạng thái xử lý."
      }
    }
    ```

*   **Error Responses:**

    | HTTP Status | Error Code | Trường hợp |
    | :--- | :--- | :--- |
    | 401 Unauthorized | `UNAUTHENTICATED` | Không có Bearer Token hoặc Token hết hạn |
    | 403 Forbidden | `UNAUTHORIZED` | Tài khoản không có `ROLE_CREATOR` |

---

### 1.2 Kiểm tra trạng thái Task xuất báo cáo (Get Task Status)

Client định kỳ gửi yêu cầu (Polling với khoảng cách 5s - 10s) để kiểm tra xem tệp tin đã được sinh thành công và có liên kết tải về chưa.

*   **Method:** `GET`
*   **Path:** `/api/v1/reports/tasks/{taskId}`
*   **Auth Level:** `@PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")`

> **Lưu ý bảo mật (Ownership Check):** Mặc dù endpoint cho phép nhiều role truy cập, hệ thống kiểm tra quyền sở hữu: chỉ **User tạo Task** hoặc **ADMIN** mới được phép xem trạng thái. Truy cập bởi user khác sẽ bị từ chối với lỗi `UNAUTHORIZED`.

*   **Path Parameters:**

    | Tham số | Kiểu | Mô tả |
    | :--- | :--- | :--- |
    | `taskId` | `String` (UUID) | Mã định danh Task duy nhất nhận từ API xuất báo cáo |

*   **Response Success - Đang xử lý (HTTP 200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Truy vấn trạng thái tiến trình thành công",
      "result": {
        "taskId": "7f805a3b-2401-4475-8123-92f7e719c8f0",
        "taskType": "EXCEL_EXPORT",
        "status": "RUNNING",
        "resultUrl": null,
        "errorMessage": null,
        "createdAt": "2026-05-27T15:40:00+07:00"
      }
    }
    ```

*   **Response Success - Hoàn thành (HTTP 200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Truy vấn trạng thái tiến trình thành công",
      "result": {
        "taskId": "7f805a3b-2401-4475-8123-92f7e719c8f0",
        "taskType": "EXCEL_EXPORT",
        "status": "COMPLETED",
        "resultUrl": "https://vibecart-bucket.s3.amazonaws.com/reports/1234/a1b2c3d4.xlsx",
        "errorMessage": null,
        "createdAt": "2026-05-27T15:40:00+07:00"
      }
    }
    ```

*   **Response Success - Thất bại (HTTP 200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Truy vấn trạng thái tiến trình thành công",
      "result": {
        "taskId": "7f805a3b-2401-4475-8123-92f7e719c8f0",
        "taskType": "EXCEL_EXPORT",
        "status": "FAILED",
        "resultUrl": null,
        "errorMessage": "Lỗi cạn kiệt bộ nhớ Excel Generator hoặc truy vấn DB quá tải Timeout",
        "createdAt": "2026-05-27T15:40:00+07:00"
      }
    }
    ```

*   **Response Payload (`TaskStatusResponse`):**

    | Trường | Kiểu | Nullable | Mô tả |
    | :--- | :--- | :--- | :--- |
    | `taskId` | `String` | Không | UUID định danh Task |
    | `taskType` | `String` | Không | Loại tác vụ (hiện tại: `EXCEL_EXPORT`) |
    | `status` | `String` | Không | `PENDING` / `RUNNING` / `COMPLETED` / `FAILED` |
    | `resultUrl` | `String` | Có | URL tải tệp trên S3 (chỉ có khi `COMPLETED`) |
    | `errorMessage` | `String` | Có | Mô tả lỗi chi tiết (chỉ có khi `FAILED`) |
    | `createdAt` | `ZonedDateTime` | Không | Thời điểm Task được tạo (ISO 8601) |

*   **Error Responses:**

    | HTTP Status | Error Code | Trường hợp |
    | :--- | :--- | :--- |
    | 401 Unauthorized | `UNAUTHENTICATED` | Không có Bearer Token hoặc Token hết hạn |
    | 403 Forbidden | `UNAUTHORIZED` | User không phải chủ sở hữu Task và không phải ADMIN |
    | 404 Not Found | `INVALID_INPUT` | Task ID không tồn tại trong hệ thống |

---

## ⚙️ 2. API Quản trị Kích hoạt Batch Job Thủ công (Admin Manual Trigger API)

API khẩn cấp dành riêng cho quản trị viên (Admin) để kích hoạt cưỡng bức chạy các Batch Job ngay lập tức, không cần chờ đến lịch hẹn `@Scheduled` tự động.

*   **Method:** `POST`
*   **Path:** `/api/v1/admin/jobs/trigger`
*   **Auth Level:** `@PreAuthorize("hasRole('ADMIN')")`
*   **Query Parameters:**

    | Tham số | Kiểu | Bắt buộc | Mô tả |
    | :--- | :--- | :--- | :--- |
    | `jobName` | `String` | Có | Tên định danh Job cần kích hoạt |

    **Các Job hợp lệ hiện tại:**

    | Job Name | Mô tả |
    | :--- | :--- |
    | `commissionSettlementJob` | Đối soát hoa hồng tiếp thị liên kết |

*   **Response Success (HTTP 200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Batch Job [commissionSettlementJob] đã được kích hoạt chạy thủ công cưỡng bức thành công"
    }
    ```

*   **Job Parameters tự động đính kèm:**

    | Parameter | Giá trị | Mô tả |
    | :--- | :--- | :--- |
    | `time` | `System.currentTimeMillis()` | Đảm bảo mỗi lần chạy có tham số duy nhất |
    | `triggeredBy` | `"ADMIN_REST_API"` | Đánh dấu nguồn kích hoạt là API thủ công |

*   **Error Responses:**

    | HTTP Status | Error Code | Trường hợp |
    | :--- | :--- | :--- |
    | 400 Bad Request | `INVALID_INPUT` | `jobName` không được nhận dạng (không nằm trong danh sách Job hợp lệ) |
    | 401 Unauthorized | `UNAUTHENTICATED` | Không có Bearer Token hoặc Token hết hạn |
    | 403 Forbidden | `UNAUTHORIZED` | Tài khoản không có `ROLE_ADMIN` |
    | 500 Internal Server Error | — | Batch Job gặp lỗi trong quá trình thực thi |
