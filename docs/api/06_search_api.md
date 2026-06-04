# 🔌 Đặc tả API - Phân hệ 6: Công cụ Tìm kiếm & Gợi ý (Search Engine & Suggestions)

Tài liệu này đặc tả hợp đồng tích hợp API cho chức năng Tìm kiếm sản phẩm thông minh, Gợi ý từ khóa thời gian thực (Autocomplete), Lịch sử tìm kiếm (Search History), Từ khóa phổ biến (Trending Searches), Tìm kiếm & Gợi ý Nhà sáng tạo (Creator Search), và công cụ Quản trị Lập chỉ mục của hệ thống **VibeCart**.

**Base URL:** `/api/v1/search`

---

## 🔍 1. Các Endpoints Tìm kiếm chính (Product Search APIs)

### 1.1 Tìm kiếm & Lọc động đa chiều (Search & Faceted Filter)
Tìm kiếm sản phẩm hỗ trợ Tiếng Việt có dấu/không dấu, sửa lỗi gõ sai (Fuzzy) và lọc nâng cao.

*   **Method:** `GET`
*   **Path:** `/api/v1/search`
*   **Auth Level:** `PermitAll`
*   **Query Parameters:**

| Param | Kiểu | Bắt buộc | Mặc định | Mô tả |
| :--- | :--- | :---: | :--- | :--- |
| `q` | `String` | Không | — | Từ khóa tìm kiếm (Ví dụ: `q=sony`) |
| `categoryId` | `String (UUID)` | Không | — | Lọc theo mã danh mục |
| `minPrice` | `BigDecimal` | Không | — | Khoảng giá từ (Ví dụ: `minPrice=1000000`) |
| `maxPrice` | `BigDecimal` | Không | — | Khoảng giá đến (Ví dụ: `maxPrice=5000000`) |
| `sort` | `String` | Không | `relevance` | Sắp xếp: `price_asc`, `price_desc`, `newest`, `relevance` |
| `page` | `int` | Không | `0` | Số trang (bắt đầu từ 0) |
| `size` | `int` | Không | `20` | Số lượng trên trang (tối đa 50) |

*   **Response Success (200 OK - Khi tìm thấy sản phẩm):**
    ```json
    {
      "code": 1000,
      "message": "Tìm kiếm sản phẩm thành công",
      "result": {
        "content": [
          {
            "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            "name": "Tai nghe Sony WH-1000XM4",
            "description": "Tai nghe chống ồn cao cấp...",
            "categoryId": "f1e2d3c4-b5a6-7890-abcd-ef1234567890",
            "categoryName": "Tai nghe",
            "creatorId": "c1d2e3f4-a5b6-7890-abcd-ef1234567890",
            "thumbnailUrl": "https://vibecart.com/sony-black.jpg",
            "minPrice": 5990000.00,
            "maxPrice": 6490000.00,
            "status": "ACTIVE",
            "createdAt": "2026-05-27T17:22:00Z",
            "updatedAt": "2026-05-27T17:22:00Z"
          }
        ],
        "page": 0,
        "size": 20,
        "totalElements": 1,
        "totalPages": 1,
        "last": true,
        "suggestion": null
      }
    }
    ```
*   **Response Success (200 OK - Khi không tìm thấy và có gợi ý sửa lỗi Spell Check):**
    ```json
    {
      "code": 1000,
      "message": "Tìm kiếm sản phẩm thành công",
      "result": {
        "content": [],
        "page": 0,
        "size": 20,
        "totalElements": 0,
        "totalPages": 0,
        "last": true,
        "suggestion": "điện thoại sony"
      }
    }
    ```

> **Lưu ý:** `content` trả về trực tiếp `ProductDocument` từ Elasticsearch, bao gồm đầy đủ các trường: `id`, `name`, `description`, `categoryId`, `categoryName`, `creatorId`, `thumbnailUrl`, `minPrice`, `maxPrice`, `status`, `createdAt`, `updatedAt`.

---

### 1.2 Gợi ý từ khóa nhanh khi đang gõ (Autocomplete Typeahead)
Trả về danh sách các cụm từ đề xuất khớp với prefix của từ khóa đang nhập (Thường gọi qua cơ chế Debounce 200ms phía Client).
*   **Method:** `GET`
*   **Path:** `/api/v1/search/autocomplete`
*   **Auth Level:** `PermitAll`
*   **Query Parameters:**

| Param | Kiểu | Bắt buộc | Mô tả |
| :--- | :--- | :---: | :--- |
| `prefix` | `String` | Có | Cụm ký tự đang gõ (Ví dụ: `prefix=ta`) |

*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Gợi ý từ khóa thành công",
      "result": [
        "Tai nghe Sony WH-1000XM4",
        "Tai nghe JBL Tune 510BT",
        "Tạp dề Creator VibeCart"
      ]
    }
    ```

> **Giới hạn:** Trả về tối đa **5 gợi ý** duy nhất (distinct tên sản phẩm).

---

## 💾 2. Các Endpoints Lịch sử Tìm kiếm (Search History APIs)

Quản lý danh sách 10 từ khóa tìm kiếm gần nhất được lưu trữ tại MongoDB.

### 2.1 Lấy lịch sử tìm kiếm gần đây (Get History)
*   **Method:** `GET`
*   **Path:** `/api/v1/search/history`
*   **Auth Level:** `@PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")` (Với khách vãng lai Guest, Client tự đọc LocalStorage)
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Lấy lịch sử tìm kiếm thành công",
      "result": [
        {
          "keyword": "tai nghe sony",
          "searchedAt": "2026-05-27T17:22:00"
        },
        {
          "keyword": "iphone 15 pro max",
          "searchedAt": "2026-05-27T17:15:30"
        }
      ]
    }
    ```

> **Lưu ý:** Trường `searchedAt` trả về kiểu `LocalDateTime` (không có timezone suffix `Z`), vì MongoDB lưu trữ dưới dạng `LocalDateTime` trong Java Entity.

---

### 2.2 Xóa một từ khóa trong lịch sử (Delete Specific Keyword)
*   **Method:** `DELETE`
*   **Path:** `/api/v1/search/history`
*   **Auth Level:** `@PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")`
*   **Query Parameters:**

| Param | Kiểu | Bắt buộc | Mô tả |
| :--- | :--- | :---: | :--- |
| `keyword` | `String` | Có | Từ khóa cần xóa (Ví dụ: `keyword=tai nghe sony`) |

*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Xóa từ khóa lịch sử thành công"
    }
    ```

---

### 2.3 Xóa toàn bộ lịch sử tìm kiếm (Clear All History)
*   **Method:** `DELETE`
*   **Path:** `/api/v1/search/history/clear`
*   **Auth Level:** `@PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")`
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Xóa sạch lịch sử tìm kiếm thành công"
    }
    ```

---

### 2.4 Đồng bộ lịch sử tìm kiếm khi đăng nhập (Merge History)
Gộp lịch sử từ LocalStorage lên MongoDB ngay khi người dùng đăng nhập thành công.
*   **Method:** `POST`
*   **Path:** `/api/v1/search/history/merge`
*   **Auth Level:** `@PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")`
*   **Request Body:**
    ```json
    {
      "keywords": [
        "áo phông creator",
        "tai nghe sony"
      ]
    }
    ```
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Đồng bộ lịch sử tìm kiếm thành công"
    }
    ```

> **Logic merge:** Keywords được xử lý ngược thứ tự (reverse) rồi lần lượt `recordPersonalHistory()` để từ khóa mới nhất nằm trên đầu danh sách sau khi merge.

---

## 📈 3. Đặc tả Từ khóa Xu hướng (Trending Searches API)

Lấy danh sách Top 8 từ khóa phổ biến được tìm kiếm nhiều nhất toàn sàn trong 7 ngày qua (đọc trực tiếp từ Redis Cache).

*   **Method:** `GET`
*   **Path:** `/api/v1/search/trending`
*   **Auth Level:** `PermitAll`
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Lấy danh sách xu hướng thành công",
      "result": [
        "tai nghe sony",
        "áo hoodie creator",
        "iphone 15 pro max",
        "sách kỹ năng",
        "tạp dề",
        "ốp lưng"
      ]
    }
    ```

---

## ⚙️ 4. API Quản trị Lập chỉ mục (Search Admin Sync API)

API cho phép quản trị viên kích hoạt quy trình lập chỉ mục lại toàn sàn (Re-indexing) bất đồng bộ. Quy trình sẽ reindex đồng thời cả **Products** và **Users** vào Elasticsearch.

*   **Method:** `POST`
*   **Path:** `/api/v1/search/sync`
*   **Auth Level:** `@PreAuthorize("hasRole('ADMIN')")`
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Đồng bộ lại toàn bộ chỉ mục tìm kiếm thành công"
    }
    ```

---

## 👥 5. Các Endpoints Tìm kiếm & Gợi ý Creator (Creator Search & Suggestion APIs)

Các API tìm kiếm toàn văn và gợi ý tài khoản Nhà sáng tạo (Creator) trên nền tảng.

### 5.1 Tìm kiếm & Lọc Nhà sáng tạo (Creator Search & Filter)
*   **Method:** `GET`
*   **Path:** `/api/v1/search/users`
*   **Auth Level:** `PermitAll`
*   **Query Parameters:**

| Param | Kiểu | Bắt buộc | Mặc định | Mô tả |
| :--- | :--- | :---: | :--- | :--- |
| `q` | `String` | Không | — | Từ khóa tìm kiếm Creator (Ví dụ: `q=nam`) |
| `page` | `int` | Không | `0` | Số trang (bắt đầu từ 0) |
| `size` | `int` | Không | `20` | Số lượng trên trang (tối đa 50) |

*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Tìm kiếm thành viên thành công",
      "result": {
        "content": [
          {
            "id": "u1a2b3c4-d5e6-7890-abcd-ef1234567890",
            "username": "namcreator",
            "email": "nam@vibecart.com",
            "fullName": "Nguyen Duc Hoang Nam",
            "avatarUrl": "https://vibecart.com/nam-avatar.jpg",
            "status": "ACTIVE",
            "roles": [
              "ROLE_CREATOR"
            ],
            "createdAt": "2026-05-27T17:22:00Z",
            "isFollowing": true,
            "followerCount": 1520
          }
        ],
        "suggestion": null,
        "page": 0,
        "size": 20,
        "totalElements": 1,
        "totalPages": 1,
        "last": true
      }
    }
    ```

> **Enrichment:** Các trường `isFollowing` và `followerCount` được bổ sung từ `FollowService` (Social Module) trong quá trình xử lý, không lưu trữ trong Elasticsearch.

---

### 5.2 Gợi ý nhanh tài khoản Creator khi đang gõ (Creator Autocomplete Typeahead)
*   **Method:** `GET`
*   **Path:** `/api/v1/search/users/autocomplete`
*   **Auth Level:** `PermitAll`
*   **Query Parameters:**

| Param | Kiểu | Bắt buộc | Mô tả |
| :--- | :--- | :---: | :--- |
| `prefix` | `String` | Có | Cụm ký tự đang gõ (Ví dụ: `prefix=na`) |

*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Gợi ý từ khóa thành viên thành công",
      "result": [
        "namcreator",
        "natalie_shop",
        "nathan_art"
      ]
    }
    ```

> **Giới hạn:** Trả về tối đa **5 gợi ý** username duy nhất (distinct).

---

## 📋 6. Tổng hợp Bảng Endpoints (API Summary Table)

| # | Method | Path | Auth | Mô tả |
| :---: | :---: | :--- | :--- | :--- |
| 1 | `GET` | `/api/v1/search` | PermitAll | Tìm kiếm & lọc sản phẩm |
| 2 | `GET` | `/api/v1/search/autocomplete` | PermitAll | Gợi ý từ khóa sản phẩm |
| 3 | `GET` | `/api/v1/search/history` | Authenticated | Lịch sử tìm kiếm cá nhân |
| 4 | `DELETE` | `/api/v1/search/history` | Authenticated | Xóa một từ khóa lịch sử |
| 5 | `DELETE` | `/api/v1/search/history/clear` | Authenticated | Xóa toàn bộ lịch sử |
| 6 | `POST` | `/api/v1/search/history/merge` | Authenticated | Đồng bộ lịch sử từ LocalStorage |
| 7 | `GET` | `/api/v1/search/trending` | PermitAll | Top 8 từ khóa xu hướng |
| 8 | `POST` | `/api/v1/search/sync` | ADMIN only | Re-index toàn bộ sản phẩm & users |
| 9 | `GET` | `/api/v1/search/users` | PermitAll | Tìm kiếm Creator |
| 10 | `GET` | `/api/v1/search/users/autocomplete` | PermitAll | Gợi ý nhanh Creator |
