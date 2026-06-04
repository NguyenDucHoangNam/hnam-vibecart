# 🔌 Đặc tả API - Phân hệ 2: Cốt lõi Thương mại Điện tử (E-Commerce Core)

Tài liệu này đặc tả hợp đồng tích hợp API cho Giỏ hàng (Cart), luồng Tạo đơn hàng (Checkout), Quản lý đơn hàng (Order) và Webhook tích hợp với Cổng thanh toán **PayOS** của hệ thống **VibeCart** theo mô hình **Thu hộ Tập trung (Centralized Escrow Model)**.

---

## 🛍️ 1. Các Endpoints Giỏ hàng (Shopping Cart APIs)

Các hoạt động trên giỏ hàng được định tuyến qua Redis Hash lưu vết theo tài khoản người dùng hoặc lưu LocalStorage cho Khách vãng lai (Guest).

### 1.1 Xem giỏ hàng (Get Cart)
*   **Method:** `GET`
*   **Path:** `/api/v1/cart`
*   **Auth Level:** `Require Bearer Token` (Nếu là Guest, Client tự đọc từ LocalStorage)
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "result": {
        "items": [
          {
            "variantId": "variant_501",
            "spuId": "spu_201",
            "productName": "Tai nghe Sony WH-1000XM4",
            "variantName": "Màu Đen - Bản Quốc Tế",
            "thumbnailUrl": "https://vibecart.com/sony-black.jpg",
            "creatorId": "creator_1234",
            "creatorName": "Creator VibeCart",
            "quantity": 1,
            "originalPrice": 6490000.00,
            "discountPrice": 5990000.00, // Giá bán thực tế
            "availableStock": 15,
            "status": "AVAILABLE" // AVAILABLE, OUT_OF_STOCK, INSUFFICIENT_STOCK
          }
        ],
        "totalOriginalAmount": 6490000.00,
        "totalDiscountAmount": 5990000.00,
        "totalSavingAmount": 500000.00
      }
    }
    ```

---

### 1.2 Thêm sản phẩm vào giỏ hàng (Add to Cart)
*   **Method:** `POST`
*   **Path:** `/api/v1/cart/items`
*   **Auth Level:** `Require Bearer Token`
*   **Request Body:**
    ```json
    {
      "variantId": "variant_501",
      "quantity": 1
    }
    ```
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Đã thêm sản phẩm vào giỏ hàng thành công"
    }
    ```

---

### 1.3 Cập nhật số lượng sản phẩm (Update Item Quantity)
*   **Method:** `PUT`
*   **Path:** `/api/v1/cart/items/{variantId}`
*   **Auth Level:** `Require Bearer Token`
*   **Request Body:**
    ```json
    {
      "quantity": 3
    }
    ```
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Cập nhật số lượng sản phẩm thành công"
    }
    ```

---

### 1.4 Xóa sản phẩm khỏi giỏ hàng (Remove Item)
*   **Method:** `DELETE`
*   **Path:** `/api/v1/cart/items/{variantId}`
*   **Auth Level:** `Require Bearer Token`
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Đã xóa sản phẩm khỏi giỏ hàng"
    }
    ```

---

### 1.5 Gộp giỏ hàng Guest vào User (Merge Cart)
Gọi ngay sau khi đăng nhập thành công để đẩy sản phẩm từ LocalStorage của khách vãng lai lên Redis.
*   **Method:** `POST`
*   **Path:** `/api/v1/cart/merge`
*   **Auth Level:** `Require Bearer Token`
*   **Request Body:**
    ```json
    {
      "items": [
        {
          "variantId": "variant_501",
          "quantity": 2
        }
      ]
    }
    ```
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Gộp giỏ hàng thành công"
    }
    ```

---

### 1.6 Xóa sạch giỏ hàng (Clear Cart)
*   **Method:** `DELETE`
*   **Path:** `/api/v1/cart/clear`
*   **Auth Level:** `Require Bearer Token`
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Đã xóa sạch giỏ hàng"
    }
    ```

---

## 💳 2. Luồng Đặt hàng & Thanh toán (Checkout & Order APIs)

### 2.1 Tạo Đơn hàng & Lấy Link Thanh toán Gộp (Create Order & Pay)
Tiến hành gom đơn, khóa kho tạm giữ (15 phút), tự động chia tách đơn (Split Order) theo từng Creator. Toàn bộ các đơn hàng con được liên kết chung 1 phiên thanh toán số phẳng và xuất **1 Link thanh toán PayOS duy nhất** đại diện cho cả giỏ hàng (Single-click Checkout).
*   **Method:** `POST`
*   **Path:** `/api/v1/orders`
*   **Auth Level:** `Require Bearer Token`
*   **Request Headers:**
    *   `X-Idempotency-Key`: Khóa chống trùng lặp yêu cầu đặt hàng (Tùy chọn)
*   **Request Body:**
    ```json
    {
      "shippingAddress": "123 Đường ABC, Quận 1, TP. Hồ Chí Minh",
      "recipientName": "Nguyễn Văn A",
      "recipientPhone": "0987654321",
      "customerNote": "Giao giờ hành chính giúp em",
      "items": [
        {
          "variantId": "variant_501",
          "quantity": 2
        }
      ],
      "voucherCode": "GIAM50K" // Không bắt buộc
    }
    ```
*   **Response Success (201 Created):**
    ```json
    {
      "code": 1000,
      "message": "Đơn hàng đã được khởi tạo và phân tách thành công theo Creators",
      "result": {
        "checkoutSessionId": "178041638239065", // ID phiên checkout số phẳng
        "orders": [
          {
            "orderId": "order_sub_creator-A",
            "orderCode": "VBC51D1FF88-1", // Mã đẹp để lưu vết PostgreSQL
            "creatorId": "creator_1234",
            "finalAmount": 11980000.00,
            "status": "PENDING",
            "paymentUrl": "https://pay.payos.vn/web/a38bc12cfda4" // Cả 2 đơn con có chung paymentUrl gộp này
          },
          {
            "orderId": "order_sub_creator-B",
            "orderCode": "VBC51D1FF88-2",
            "creatorId": "creator_5678",
            "finalAmount": 5000000.00,
            "status": "PENDING",
            "paymentUrl": "https://pay.payos.vn/web/a38bc12cfda4"
          }
        ],
        "createdAt": "2026-05-27T17:22:00Z"
      }
    }
    ```
*   **Response Error (400 Bad Request - Hết hàng / Lỗi khóa):**
    ```json
    {
      "code": 1011,
      "message": "Sản phẩm [Tai nghe Sony] đã hết hàng hoặc không đủ tồn kho khả dụng để đặt đơn"
    }
    ```

---

### 2.2 Xem chi tiết đơn hàng (Get Order Details)
*   **Method:** `GET`
*   **Path:** `/api/v1/orders/{orderId}`
*   **Auth Level:** `Require Bearer Token`
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "result": {
        "orderId": "order_sub_creator-A",
        "orderCode": "VBC51D1FF88-1",
        "creatorId": "creator_1234",
        "creatorName": "Creator VibeCart",
        "recipientName": "Nguyễn Văn A",
        "recipientPhone": "0987654321",
        "shippingAddress": "123 Đường ABC, Quận 1, TP. Hồ Chí Minh",
        "items": [
          {
            "variantId": "variant_501",
            "productName": "Tai nghe Sony WH-1000XM4",
            "variantName": "Màu Đen - Bản Quốc Tế",
            "price": 5990000.00,
            "discountPrice": 0.00,
            "quantity": 1
          }
        ],
        "totalAmount": 5940000.00,
        "discountAmount": 50000.00,
        "finalAmount": 5890000.00,
        "status": "PAID", // PENDING, PAID, CANCELLED, SHIPPED, DELIVERED
        "paymentUrl": "https://pay.payos.vn/web/a38bc12cfda4",
        "createdAt": "2026-05-27T16:22:00Z",
        "trackingNumber": "TRK12345678"
      }
    }
    ```

---

### 2.3 Xem lịch sử đơn hàng của tôi (Get Order History)
*   **Method:** `GET`
*   **Path:** `/api/v1/orders`
*   **Auth Level:** `Require Bearer Token`
*   **Query Parameters:**
    *   `status`: Lọc trạng thái (Ví dụ: `status=PAID`)
    *   `page`: Số trang (Mặc định: `0`)
    *   `size`: Kích thước (Mặc định: `10`)
*   **Response Success (200 OK):**
    *   Trả về trang dữ liệu (`Page<OrderResponse>`) chứa danh sách đơn hàng rút gọn.

---

### 2.4 Xem lịch sử đơn hàng của Creator (Get Creator Orders)
Lấy danh sách các đơn hàng đặt mua sản phẩm của Creator hiện tại.
*   **Method:** `GET`
*   **Path:** `/api/v1/orders/creator`
*   **Auth Level:** `Require ROLE_CREATOR` hoặc `ROLE_ADMIN`
*   **Query Parameters:**
    *   `status`: Lọc trạng thái
    *   `page`: Số trang (Mặc định: `0`)
    *   `size`: Kích thước (Mặc định: `10`)
*   **Response Success (200 OK):**
    *   Trả về trang dữ liệu (`Page<OrderResponse>`) chứa danh sách đơn hàng của Creator.

---

### 2.5 Cập nhật trạng thái đơn hàng (Update Order Status)
*   **Method:** `PUT`
*   **Path:** `/api/v1/orders/{orderId}/status`
*   **Auth Level:** `Require ROLE_CREATOR` hoặc `ROLE_ADMIN`
*   **Request Body:**
    ```json
    {
      "newStatus": "SHIPPED", // PAID, CANCELLED, SHIPPED, DELIVERED
      "trackingNumber": "TRK987654321" // Bắt buộc khi chuyển sang SHIPPED
    }
    ```
*   **Response Success (200 OK):**
    *   Trả về thông tin đơn hàng sau khi cập nhật trạng thái.

---

### 2.6 Hủy đơn hàng (Cancel Order)
Shopper hủy đơn hàng của chính mình (chỉ khả dụng khi đơn hàng ở trạng thái `PENDING` hoặc `PAID` trước khi giao).
*   **Method:** `POST`
*   **Path:** `/api/v1/orders/{orderId}/cancel`
*   **Auth Level:** `Require Bearer Token`
*   **Response Success (200 OK):**
    *   Trả về thông tin đơn hàng sau khi hủy thành công.

---

## 📦 3. Các Endpoints Quản lý Tồn kho (Inventory APIs)

### 3.1 Điều chỉnh số lượng tồn kho (Adjust Stock)
*   **Method:** `POST`
*   **Path:** `/api/v1/products/variants/{variantId}/inventory/adjust`
*   **Auth Level:** `Require ROLE_CREATOR` hoặc `ROLE_ADMIN`
*   **Request Body:**
    ```json
    {
      "adjustment": 50, // Số dương là nhập thêm, số âm là xuất kho/giảm kho
      "reason": "Nhập thêm hàng đợt mới"
    }
    ```
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Điều chỉnh tồn kho thành công"
    }
    ```

---

### 3.2 Xem lịch sử biến động tồn kho (Get Inventory History)
*   **Method:** `GET`
*   **Path:** `/api/v1/products/variants/{variantId}/inventory/history`
*   **Auth Level:** `Require ROLE_CREATOR` hoặc `ROLE_ADMIN`
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Lấy lịch sử tồn kho thành công",
      "result": [
        {
          "historyId": "hist_123",
          "inventoryId": "inv_456",
          "transactionType": "IMPORT", // IMPORT, EXPORT, RESERVE, COMMIT, RELEASE, REFUND, ADJUST
          "quantityChanged": 50,
          "reason": "Nhập thêm hàng đợt mới",
          "createdBy": "creator_01",
          "createdAt": "2026-05-27T10:00:00Z"
        }
      ]
    }
    ```

---

## 🔌 4. API Tích hợp Cổng thanh toán (PayOS Webhook Gateway)

Webhook nhận thông báo biến động thanh toán từ hệ thống PayOS gửi về Backend VibeCart. Yêu cầu xử lý **Chữ ký số (HMAC-SHA256)** để đảm bảo an ninh và cơ chế **Idempotency** chống xử lý trùng lặp.

*   **Method:** `POST`
*   **Path:** `/api/v1/payments/payos/webhook`
*   **Auth Level:** `PermitAll` (Xác thực chữ ký qua trường signature trong body)
*   **Request Body (Cấu trúc chuẩn của PayOS):**
    ```json
    {
      "success": true,
      "data": {
        "orderCode": 178041638239065, // checkoutSessionId gộp
        "amount": 16980000, // Tổng tiền gộp thanh toán
        "description": "Thanh toan don hang VBC Session 178041638239065",
        "accountNumber": "123456789",
        "reference": "FT2611029582",
        "transactionDateTime": "2026-05-27 16:23:00",
        "paymentLinkId": "a38bc12cfda4",
        "currency": "VND"
      },
      "signature": "8a3cd0491823ebc45f8e02d8f921ea023b49c0d18d098e9102efbca873"
    }
    ```
*   **Response Success (200 OK):**
    ```json
    {
      "success": true,
      "message": "Webhook processed successfully"
    }
    ```
*   **Response Error (400 Bad Request - Sai chữ ký / Lỗi):**
    ```json
    {
      "success": false,
      "message": "Invalid signature verification failed"
    }
    ```
