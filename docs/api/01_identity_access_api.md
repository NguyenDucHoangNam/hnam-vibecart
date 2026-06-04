# 🔌 Đặc tả API - Phân hệ 1: Hệ thống Tài khoản & Định danh (Identity & Access)

Tài liệu này đặc tả hợp đồng tích hợp API (API Contract) cho toàn bộ các hoạt động Đăng ký, Xác thực OTP, Đăng nhập (cục bộ & OAuth2), Quản lý phiên, Quản lý mật khẩu, Hồ sơ cá nhân và Quản trị tài khoản trên hệ thống **VibeCart**.

---

## 🌐 1. Cấu hình Chung (Global Configurations)

*   **Base URL:**
    *   *Môi trường Phát triển (Local Dev):* `http://localhost:8080`
    *   *Môi trường Production (Sản xuất):* `https://api.vibecart.com`
*   **Mặc định Headers:**
    *   `Content-Type: application/json`
    *   `Accept: application/json`
*   **Cơ chế Xác thực (Authentication):**
    *   Sử dụng **JSON Web Token (JWT)**.
    *   Các API yêu cầu đăng nhập bắt buộc gửi Header: `Authorization: Bearer <Access_Token>`
*   **Cơ chế Refresh Token:**
    *   Refresh Token là một chuỗi **UUID** (không phải JWT), được lưu trữ trên Redis với TTL 7 ngày.
    *   Mỗi lần gia hạn token, hệ thống áp dụng cơ chế **Token Rotation** (xoay vòng): xóa token cũ, sinh token mới.
    *   Tối đa **3 phiên đăng nhập đồng thời** trên các thiết bị khác nhau. Phiên cũ nhất sẽ bị đẩy ra khi vượt quá giới hạn.

---

## 📊 2. Bảng Mã lỗi Phân hệ IAM (Error Codes)

| Mã (Code) | Tên lỗi (Error Name) | Mô tả nghiệp vụ | HTTP Status |
| :--- | :--- | :--- | :--- |
| **1000** | `SUCCESS` | Yêu cầu thực thi thành công | 200 OK / 201 Created |
| **1002** | `USER_EXISTED` | Tài khoản đã tồn tại | 400 Bad Request |
| **1003** | `USERNAME_INVALID` | Tên đăng nhập phải từ 5 đến 30 ký tự | 400 Bad Request |
| **1004** | `INVALID_PASSWORD` | Mật khẩu không đáp ứng yêu cầu độ mạnh | 400 Bad Request |
| **1005** | `USER_NOT_EXISTED` | Tài khoản không tồn tại | 404 Not Found |
| **1006** | `UNAUTHENTICATED` | Xác thực thất bại (sai mật khẩu hoặc token hết hạn) | 401 Unauthorized |
| **1007** | `UNAUTHORIZED` | Không đủ quyền truy cập tài nguyên | 403 Forbidden |
| **1008** | `INVALID_INPUT` | Dữ liệu đầu vào không hợp lệ | 400 Bad Request |
| **1009** | `USERNAME_EXISTED` | Tên đăng nhập đã được sử dụng | 400 Bad Request |
| **1010** | `EMAIL_EXISTED` | Email đã được sử dụng | 400 Bad Request |
| **1011** | `OTP_EXPIRED` | Mã OTP đã hết hạn (5 phút) | 400 Bad Request |
| **1012** | `INVALID_OTP` | Mã OTP không đúng | 400 Bad Request |
| **1013** | `OTP_COOLDOWN` | Chờ 60 giây trước khi yêu cầu mã OTP mới | 429 Too Many Requests |
| **1014** | `OTP_ATTEMPTS_EXCEEDED` | Nhập sai OTP quá 5 lần, mã đã bị vô hiệu hóa | 423 Locked |
| **1015** | `ACCOUNT_TEMPORARILY_LOCKED` | Đăng nhập sai quá 5 lần, khóa 15 phút | 423 Locked |
| **1016** | `DISPOSABLE_EMAIL_NOT_ALLOWED` | Email tạm thời không được phép đăng ký | 400 Bad Request |
| **1017** | `PASSWORD_MISMATCH` | Mật khẩu xác nhận không khớp | 400 Bad Request |
| **1018** | `SAME_PASSWORD` | Mật khẩu mới trùng mật khẩu cũ | 400 Bad Request |
| **1019** | `ACCOUNT_NOT_ACTIVE` | Tài khoản không hoạt động | 400 Bad Request |
| **1020** | `ACCOUNT_PENDING_VERIFICATION` | Tài khoản chưa xác thực OTP | 400 Bad Request |
| **1021** | `ACCOUNT_BANNED` | Tài khoản đã bị cấm truy cập | 403 Forbidden |
| **1022** | `INVALID_RESET_TOKEN` | Token khôi phục mật khẩu không hợp lệ hoặc đã hết hạn | 400 Bad Request |
| **1023** | `OLD_PASSWORD_INCORRECT` | Mật khẩu hiện tại không đúng | 400 Bad Request |
| **1024** | `CANNOT_CHANGE_OWN_STATUS` | Admin không thể tự đổi trạng thái của mình | 400 Bad Request |
| **1025** | `INVALID_STATUS` | Trạng thái tài khoản không hợp lệ | 400 Bad Request |
| **1026** | `CANNOT_CHANGE_OWN_ROLE` | Admin không thể tự thay đổi vai trò của mình | 400 Bad Request |

---

## 🔌 3. Đặc tả các Endpoints

### 3.1 Đăng ký tài khoản (Register)
Tạo tài khoản mới cho Shopper hoặc Creator. Sau khi đăng ký, tài khoản ở trạng thái `PENDING_VERIFICATION` và hệ thống gửi mã OTP qua email để kích hoạt.
*   **Method:** `POST`
*   **Path:** `/api/v1/auth/register`
*   **Auth Level:** `PermitAll`
*   **Request Body (`RegisterRequest`):**
    ```json
    {
      "username": "hoangnam511",
      "email": "user@example.com",
      "password": "StrongPassword123!",
      "fullName": "Nguyễn Đức Hoàng Nam",
      "role": "SHOPPER"
    }
    ```
    > **Validation Rules:**
    > - `username`: Bắt buộc, 5–30 ký tự, chỉ chứa `[a-zA-Z0-9._-]`
    > - `email`: Bắt buộc, định dạng email hợp lệ, tối đa 100 ký tự, không chấp nhận email tạm thời
    > - `password`: Bắt buộc, 8–100 ký tự, chứa ít nhất 1 chữ hoa, 1 chữ thường, 1 chữ số, 1 ký tự đặc biệt
    > - `fullName`: Bắt buộc, 2–100 ký tự, chỉ chứa chữ cái và khoảng trắng
    > - `role`: Tùy chọn — `"SHOPPER"` (mặc định) hoặc `"CREATOR"`

*   **Response Success (201 Created):**
    ```json
    {
      "code": 1000,
      "message": "Đăng ký thành công, vui lòng kiểm tra email để lấy mã OTP xác thực",
      "result": {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "username": "hoangnam511",
        "email": "user@example.com",
        "fullName": "Nguyễn Đức Hoàng Nam",
        "avatarUrl": null,
        "status": "PENDING_VERIFICATION",
        "oauthProvider": "LOCAL",
        "roles": ["ROLE_USER"],
        "createdAt": "2026-06-01T16:20:00+07:00"
      }
    }
    ```

---

### 3.2 Xác thực mã OTP (Verify OTP)
Xác thực mã OTP 6 chữ số gửi qua email → kích hoạt tài khoản → tự động đăng nhập trả về bộ token.
*   **Method:** `POST`
*   **Path:** `/api/v1/auth/verify-otp`
*   **Auth Level:** `PermitAll`
*   **Request Body (`VerifyOtpRequest`):**
    ```json
    {
      "email": "user@example.com",
      "otpCode": "958614"
    }
    ```
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Xác thực kích hoạt tài khoản thành công",
      "result": {
        "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        "refreshToken": "4a0c8d1d-06de-4e3f-a681-9b1b50ef2d36",
        "tokenType": "Bearer",
        "expiresIn": 3600,
        "user": {
          "id": "550e8400-e29b-41d4-a716-446655440000",
          "username": "hoangnam511",
          "email": "user@example.com",
          "fullName": "Nguyễn Đức Hoàng Nam",
          "avatarUrl": null,
          "status": "ACTIVE",
          "oauthProvider": "LOCAL",
          "roles": ["ROLE_USER"],
          "createdAt": "2026-06-01T16:20:00+07:00"
        }
      }
    }
    ```
*   **Lưu ý bảo mật:** Nhập sai OTP quá **5 lần** → mã OTP bị vô hiệu hóa, trả về mã lỗi `1014` (HTTP 423 Locked).

---

### 3.3 Gửi lại mã OTP (Resend OTP)
Yêu cầu gửi lại mã OTP mới cho email đang ở trạng thái `PENDING_VERIFICATION`.
*   **Method:** `POST`
*   **Path:** `/api/v1/auth/resend-otp`
*   **Auth Level:** `PermitAll`
*   **Request Body (`ResendOtpRequest`):**
    ```json
    {
      "email": "user@example.com"
    }
    ```
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Mã OTP mới đã được gửi thành công, vui lòng kiểm tra hòm thư của bạn"
    }
    ```
*   **Lưu ý bảo mật:** Phải chờ tối thiểu **60 giây** giữa các lần gửi, nếu gọi sớm hơn → mã lỗi `1013` (HTTP 429).

---

### 3.4 Đăng nhập cục bộ (Login)
Đăng nhập bằng username hoặc email kết hợp mật khẩu.
*   **Method:** `POST`
*   **Path:** `/api/v1/auth/login`
*   **Auth Level:** `PermitAll`
*   **Request Body (`LoginRequest`):**
    ```json
    {
      "usernameOrEmail": "hoangnam511",
      "password": "StrongPassword123!"
    }
    ```
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Đăng nhập thành công",
      "result": {
        "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        "refreshToken": "4a0c8d1d-06de-4e3f-a681-9b1b50ef2d36",
        "tokenType": "Bearer",
        "expiresIn": 3600,
        "user": {
          "id": "550e8400-e29b-41d4-a716-446655440000",
          "username": "hoangnam511",
          "email": "user@example.com",
          "fullName": "Nguyễn Đức Hoàng Nam",
          "avatarUrl": null,
          "status": "ACTIVE",
          "oauthProvider": "LOCAL",
          "roles": ["ROLE_USER"],
          "createdAt": "2026-06-01T16:20:00+07:00"
        }
      }
    }
    ```
*   **Lưu ý bảo mật:** Đăng nhập sai mật khẩu quá **5 lần** → tài khoản bị khóa tạm thời **15 phút**, trả về mã lỗi `1015` (HTTP 423).
*   **Lưu ý trạng thái tài khoản:**
    *   `ACTIVE`: Đăng nhập bình thường.
    *   `PENDING_DELETION`: Đăng nhập thành công nhưng response trả về `status: "PENDING_DELETION"` — Frontend dựa vào giá trị này để điều hướng người dùng đến trang khôi phục tài khoản, không cho vào trang mua sắm thông thường.
    *   `PENDING_VERIFICATION`: Từ chối đăng nhập, trả về mã lỗi `1020` (HTTP 400).
    *   `BANNED`: Từ chối đăng nhập, trả về mã lỗi `1021` (HTTP 403).

---

### 3.5 Đăng nhập Google (OAuth2)
Đăng nhập hoặc tự động tạo tài khoản mới qua Google ID Token.
*   **Method:** `POST`
*   **Path:** `/api/v1/auth/oauth2/google`
*   **Auth Level:** `PermitAll`
*   **Request Body (`OAuth2Request`):**
    ```json
    {
      "token": "google-id-token-received-from-client-sdk"
    }
    ```
*   **Response Success (200 OK):**
    *(Cấu trúc `AuthResponse` tương tự mục 3.4, `oauthProvider` = `"GOOGLE"`)*
*   **Lưu ý kỹ thuật:** Email nhận được từ Google sẽ được chuẩn hóa về chữ thường (`toLowerCase()`) trước khi đối chiếu với Database.

---

### 3.6 Đăng nhập Facebook (OAuth2)
Đăng nhập hoặc tự động tạo tài khoản mới qua Facebook Access Token.
*   **Method:** `POST`
*   **Path:** `/api/v1/auth/oauth2/facebook`
*   **Auth Level:** `PermitAll`
*   **Request Body (`OAuth2Request`):**
    ```json
    {
      "token": "facebook-access-token-received-from-client-sdk"
    }
    ```
*   **Response Success (200 OK):**
    *(Cấu trúc `AuthResponse` tương tự mục 3.4, `oauthProvider` = `"FACEBOOK"`)*
*   **Lưu ý kỹ thuật:** Email nhận được từ Facebook sẽ được chuẩn hóa về chữ thường (`toLowerCase()`) trước khi đối chiếu với Database.

---

### 3.7 Gia hạn Token (Refresh Token Rotation)
Lấy cặp Access Token + Refresh Token mới khi Access Token hết hạn. Token cũ bị thu hồi ngay lập tức.
*   **Method:** `POST`
*   **Path:** `/api/v1/auth/refresh`
*   **Auth Level:** `PermitAll`
*   **Request Body (`RefreshRequest`):**
    ```json
    {
      "refreshToken": "4a0c8d1d-06de-4e3f-a681-9b1b50ef2d36"
    }
    ```
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Gia hạn token thành công",
      "result": {
        "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.new_access_token...",
        "refreshToken": "b7e1c9a2-3f8d-4a5b-9c2d-1e4f6a8b0c3e",
        "tokenType": "Bearer",
        "expiresIn": 3600,
        "user": { ... }
      }
    }
    ```
*   **Lưu ý bảo mật:** Nếu hệ thống phát hiện Refresh Token đã bị sử dụng trước đó (Token Reuse / Theft Detection) và **ngoài khoảng thời gian ân hạn 10 giây (Grace Period)** → **toàn bộ phiên đăng nhập** trên mọi thiết bị của người dùng đó sẽ bị thu hồi ngay lập tức. Trong vòng 10 giây kể từ lần xoay vòng gần nhất, request trùng lặp sẽ được chấp nhận và trả về cặp token mới đã sinh (chống false-positive do mạng chập chờn).

---

### 3.8 Đăng xuất (Logout)
Vô hiệu hóa phiên hiện tại: đưa Access Token vào danh sách đen (Blacklist) và xóa Refresh Token khỏi Redis.
*   **Method:** `POST`
*   **Path:** `/api/v1/auth/logout`
*   **Auth Level:** `PermitAll` *(nhận Access Token từ Header `Authorization` để Blacklist)*
*   **Request Body (`RefreshRequest`):**
    ```json
    {
      "refreshToken": "4a0c8d1d-06de-4e3f-a681-9b1b50ef2d36"
    }
    ```
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Đăng xuất thành công"
    }
    ```

---

### 3.9 Lấy thông tin cá nhân (Get My Profile)
*   **Method:** `GET`
*   **Path:** `/api/v1/auth/me`
*   **Auth Level:** `Authenticated` *(Yêu cầu Header `Authorization: Bearer <token>`)*
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Lấy thông tin cá nhân thành công",
      "result": {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "username": "hoangnam511",
        "email": "user@example.com",
        "fullName": "Nguyễn Đức Hoàng Nam",
        "avatarUrl": "https://vibecart.com/avatar.jpg",
        "status": "ACTIVE",
        "oauthProvider": "LOCAL",
        "roles": ["ROLE_USER"],
        "createdAt": "2026-06-01T16:20:00+07:00"
      }
    }
    ```

---

### 3.10 Cập nhật hồ sơ cá nhân (Update Profile)
Cập nhật username, họ tên hoặc avatar. Nếu đổi username → toàn bộ phiên cũ bị thu hồi và sinh token mới.
*   **Method:** `PUT`
*   **Path:** `/api/v1/auth/profile`
*   **Auth Level:** `Authenticated`
*   **Request Body (`UpdateProfileRequest`):**
    ```json
    {
      "username": "newusername",
      "fullName": "Nguyễn Đức Hoàng Nam (Updated)",
      "avatarUrl": "https://vibecart.com/uploads/avatars/new-avatar.jpg"
    }
    ```
    > Tất cả các trường đều tùy chọn (optional). Chỉ cập nhật trường được gửi lên.
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Cập nhật thông tin cá nhân thành công",
      "result": {
        "accessToken": "...",
        "refreshToken": "...",
        "tokenType": "Bearer",
        "expiresIn": 3600,
        "user": { ... }
      }
    }
    ```

---

### 3.11 Đổi mật khẩu (Change Password)
Đổi mật khẩu khi đang đăng nhập. Sau khi đổi thành công, **tất cả các phiên khác đều bị đăng xuất**.
*   **Method:** `POST`
*   **Path:** `/api/v1/auth/change-password`
*   **Auth Level:** `Authenticated`
*   **Request Body (`ChangePasswordRequest`):**
    ```json
    {
      "oldPassword": "SecurePasswordOld123!",
      "newPassword": "SecurePasswordNew456!",
      "confirmPassword": "SecurePasswordNew456!"
    }
    ```
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Thay đổi mật khẩu thành công. Các thiết bị khác đã được đăng xuất để bảo mật"
    }
    ```

---

### 3.12 Yêu cầu khôi phục mật khẩu (Forgot Password)
Gửi liên kết đặt lại mật khẩu qua email. Để chống dò email (Anti-Enumeration), API luôn trả về phản hồi giống nhau bất kể email có tồn tại hay không.
*   **Method:** `POST`
*   **Path:** `/api/v1/auth/forgot-password`
*   **Auth Level:** `PermitAll`
*   **Request Body (`ForgotPasswordRequest`):**
    ```json
    {
      "email": "user@example.com"
    }
    ```
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Nếu email tồn tại trên hệ thống, chúng tôi đã gửi liên kết khôi phục mật khẩu vào hòm thư của bạn"
    }
    ```

---

### 3.13 Đặt lại mật khẩu mới (Reset Password)
Đặt lại mật khẩu bằng UUID Reset Token nhận được qua email (hiệu lực 10 phút). Sau khi đặt lại, **tất cả phiên đăng nhập đều bị thu hồi**.
*   **Method:** `POST`
*   **Path:** `/api/v1/auth/reset-password`
*   **Auth Level:** `PermitAll`
*   **Request Body (`ResetPasswordRequest`):**
    ```json
    {
      "token": "4a0c8d1d-06de-4e3f-a681-9b1b50ef2d36",
      "newPassword": "MyNewSecurePassword999!"
    }
    ```
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Đặt lại mật khẩu thành công. Các phiên hoạt động khác đã được đăng xuất an toàn"
    }
    ```

---

### 3.14 Yêu cầu xóa tài khoản (Delete Account)
Chuyển trạng thái tài khoản sang `PENDING_DELETION`. Dữ liệu được đóng băng 30 ngày trước khi ẩn danh hóa vĩnh viễn.
*   **Method:** `DELETE`
*   **Path:** `/api/v1/auth/account`
*   **Auth Level:** `Authenticated`
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Yêu cầu xóa tài khoản thành công. Dữ liệu sẽ được đóng băng trong 30 ngày trước khi bị ẩn danh hóa vĩnh viễn."
    }
    ```

---

## 🛡️ 4. Đặc tả Endpoints Quản trị (Admin APIs)

Tất cả các endpoint dưới đây yêu cầu tài khoản có vai trò `ROLE_ADMIN`.

### 4.1 Tìm kiếm & Phân trang người dùng (Search Users)
*   **Method:** `GET`
*   **Path:** `/api/v1/admin/users`
*   **Auth Level:** `@PreAuthorize("hasRole('ADMIN')")`
*   **Query Parameters:**
    *   `search` *(optional)*: Tìm kiếm theo username, email hoặc họ tên
    *   `status` *(optional)*: Lọc theo trạng thái (`ACTIVE`, `BANNED`, `PENDING_VERIFICATION`, `PENDING_DELETION` hoặc `ALL`)
    *   `role` *(optional)*: Lọc theo vai trò (`ROLE_USER`, `ROLE_CREATOR`, `ROLE_ADMIN` hoặc `ALL`)
    *   `page` *(default: 0)*: Số trang
    *   `size` *(default: 10)*: Số bản ghi mỗi trang
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Lấy danh sách người dùng thành công",
      "result": {
        "content": [
          {
            "id": "...",
            "username": "hoangnam511",
            "email": "user@example.com",
            "fullName": "Nguyễn Đức Hoàng Nam",
            "avatarUrl": null,
            "status": "ACTIVE",
            "oauthProvider": "LOCAL",
            "roles": ["ROLE_USER"],
            "createdAt": "2026-06-01T16:20:00+07:00"
          }
        ],
        "totalElements": 150,
        "totalPages": 15,
        "number": 0,
        "size": 10
      }
    }
    ```

---

### 4.2 Cập nhật trạng thái tài khoản (Ban / Unban User)
*   **Method:** `PUT`
*   **Path:** `/api/v1/admin/users/{id}/status`
*   **Auth Level:** `@PreAuthorize("hasRole('ADMIN')")`
*   **Path Variable:** `id` — UUID của người dùng mục tiêu
*   **Request Body (`UpdateUserStatusRequest`):**
    ```json
    {
      "status": "BANNED"
    }
    ```
    > Các giá trị hợp lệ: `ACTIVE`, `BANNED`, `PENDING_VERIFICATION`, `PENDING_DELETION`
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Cập nhật trạng thái tài khoản thành công",
      "result": {
        "id": "...",
        "username": "violator_user",
        "email": "spam@domain.com",
        "fullName": "Spam Account",
        "avatarUrl": null,
        "status": "BANNED",
        "oauthProvider": "LOCAL",
        "roles": ["ROLE_USER"],
        "createdAt": "..."
      }
    }
    ```
*   **Lưu ý:** Khi chuyển trạng thái sang `BANNED`, hệ thống tự động thu hồi tất cả phiên đăng nhập của người dùng đó. Admin không thể thay đổi trạng thái tài khoản của chính mình.

---

### 4.3 Cập nhật vai trò người dùng (Update User Roles)
*   **Method:** `PUT`
*   **Path:** `/api/v1/admin/users/{id}/roles`
*   **Auth Level:** `@PreAuthorize("hasRole('ADMIN')")`
*   **Path Variable:** `id` — UUID của người dùng mục tiêu
*   **Request Body (`UpdateUserRolesRequest`):**
    ```json
    {
      "roles": ["ROLE_USER", "ROLE_CREATOR"]
    }
    ```
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Cập nhật vai trò thành công",
      "result": { ... }
    }
    ```
*   **Lưu ý:** Sau khi cập nhật vai trò, toàn bộ phiên đăng nhập của người dùng đó bị thu hồi để buộc làm mới token chứa danh sách quyền mới. Admin không thể tự thay đổi vai trò của chính mình.
