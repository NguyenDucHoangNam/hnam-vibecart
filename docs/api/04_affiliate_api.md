# 🔌 Đặc tả API - Phân hệ 4: Tiếp thị & Rút gọn Link (Shortlink & Affiliate)

Tài liệu này đặc tả hợp đồng tích hợp API cho chức năng Rút gọn Link tiếp thị liên kết (Shortlink), Điều hướng công cộng, Quản lý Báo cáo hoa hồng (Affiliate Dashboard) và Yêu cầu Rút tiền của hệ thống **VibeCart**.

---

## 🔗 1. Các Endpoints Rút gọn Link (Shortlink APIs)

### 1.1 Tạo mã rút gọn link tiếp thị (Create Shortlink)
Tạo tệp liên kết rút gọn mã hóa Base62 tích hợp sẵn mã tiếp thị liên kết (`affiliateId`).
*   **Method:** `POST`
*   **Path:** `/api/v1/shortlinks`
*   **Auth Level:** `Require ROLE_CREATOR` hoặc `ROLE_AFFILIATE` (Token Bearer)
*   **Request Body:**
    ```json
    {
      "originalUrl": "https://vibecart.com/products/tai-nghe-sony-wh-1000xm4",
      "campaignName": "Chiến dịch Hè 2026", // Không bắt buộc
      "note": "Link đặt dưới video Youtube" // Không bắt buộc
    }
    ```
*   **Response Success (201 Created):**
    ```json
    {
      "code": 1000,
      "result": {
        "id": 800101,
        "shortcode": "Ab5c8z", // Mã Base62 độc nhất sinh ra từ Snowflake ID
        "shortlinkUrl": "https://vibe.link/s/Ab5c8z", // URL rút gọn trả về cho Client
        "originalUrl": "https://vibecart.com/products/tai-nghe-sony-wh-1000xm4",
        "campaignName": "Chiến dịch Hè 2026",
        "createdAt": "2026-05-27T16:22:00Z"
      }
    }
    ```

---

### 1.2 Xem danh sách link đã rút gọn (List Shortlinks)
*   **Method:** `GET`
*   **Path:** `/api/v1/shortlinks`
*   **Auth Level:** `Require Bearer Token`
*   **Query Parameters:**
    *   `page`: Số trang (Ví dụ: `page=0`)
    *   `size`: Số lượng (Ví dụ: `size=20`)
*   **Response Success (200 OK):**
    *   *Trả về mảng Page danh sách các Shortlinks đã tạo.*

---

## 🚀 2. API Điều hướng Rút gọn (Public Redirect Link)

API công khai giải mã mã rút gọn, lưu log click bất đồng bộ và chuyển hướng người dùng bằng HTTP Status 302.

*   **Method:** `GET`
*   **Path:** `/s/{shortcode}` (Sử dụng tên miền phụ điều hướng siêu tốc: `vibe.link`)
*   **Auth Level:** `PermitAll`
*   **Response Success (302 Found):**
    *   *Headers:* `Location: https://vibecart.com/products/tai-nghe-sony-wh-1000xm4`
    *   *Set-Cookie:* `affiliate_id=1234; Max-Age=2592000; Path=/; HttpOnly; Secure` (Thiết lập Cookie Last-Click thời hạn 30 ngày để ghi nhận đơn hàng sau này).

---

## 📈 3. Dashboard Tiếp thị & Hoa hồng (Affiliate APIs)

### 3.1 Xem báo cáo Dashboard (Get Affiliate Dashboard)
Tổng hợp số liệu thống kê lượt Click, Đơn hàng thành công, Tỷ lệ chuyển đổi và doanh số hoa hồng.
*   **Method:** `GET`
*   **Path:** `/api/v1/affiliate/dashboard`
*   **Auth Level:** `Require Bearer Token`
*   **Query Parameters:**
    *   `startDate`: Định dạng ISO (Ví dụ: `startDate=2026-05-01T00:00:00Z`)
    *   `endDate`: Định dạng ISO (Ví dụ: `endDate=2026-05-31T23:59:59Z`)
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "result": {
        "totalClicks": 1250,
        "totalOrders": 35,
        "conversionRate": 2.80, // Tỷ lệ chuyển đổi (%)
        "pendingCommission": 1250000.00, // Hoa hồng chờ đối soát (VND)
        "approvedCommission": 3400000.00, // Hoa hồng được rút (VND)
        "withdrawnCommission": 2000000.00  // Hoa hồng đã rút thành công (VND)
      }
    }
    ```

---

### 3.2 Xem danh sách lịch sử hoa hồng (Get Commission History)
*   **Method:** `GET`
*   **Path:** `/api/v1/affiliate/commissions`
*   **Auth Level:** `Require Bearer Token`
*   **Query Parameters:**
    *   `status`: Lọc trạng thái (PENDING, APPROVED, FAILED)
    *   `page`: Số trang (Ví dụ: `page=0`)
    *   `size`: Số lượng (Ví dụ: `size=20`)
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "result": {
        "content": [
          {
            "id": 8801,
            "orderId": 900101,
            "shortcode": "Ab5c8z",
            "spuName": "Tai nghe Sony WH-1000XM4",
            "commissionAmount": 120000.00,
            "status": "APPROVED",
            "createdAt": "2026-05-27T16:22:00Z"
          }
        ],
        "pageNumber": 0,
        "pageSize": 20,
        "totalPages": 1,
        "totalElements": 1
      }
    }
    ```

---

### 3.3 Yêu cầu Rút tiền hoa hồng (Request Withdrawal)
Cho phép Creator gửi yêu cầu rút hoa hồng tích lũy về tài khoản ngân hàng liên kết.
*   **Method:** `POST`
*   **Path:** `/api/v1/affiliate/withdraw`
*   **Auth Level:** `Require Bearer Token`
*   **Request Body:**
    ```json
    {
      "amount": 500000.00, // Số tiền rút (VND, tối thiểu 50,000 VND)
      "bankCode": "MB",
      "bankAccountNo": "098765432100",
      "bankAccountName": "NGUYEN VAN A"
    }
    ```
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "result": {
        "withdrawalId": "wtd_990102",
        "status": "PROCESSING",
        "amount": 500000.00,
        "message": "Yêu cầu rút tiền đã được tiếp nhận và xử lý trong vòng 24h làm việc"
      }
    }
    ```
*   **Response Error (400 Bad Request - Không đủ tiền / Dưới hạn mức):**
    ```json
    {
      "code": 1012,
      "message": "Yêu cầu rút tiền thất bại: Số tiền rút tối thiểu là 50,000 VND và không vượt quá số dư APPROVED khả dụng"
    }
    ```
