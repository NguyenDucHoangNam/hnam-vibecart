"use client";

import React, { useState, useEffect, useRef } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/hooks/useAuth";
import { useToast } from "@/context/ToastContext";
import { api } from "@/lib/api-client";
import { ENDPOINTS } from "@/constants/api";
import { ROUTES } from "@/constants/routes";
import { AuthResponse } from "@/types";
import {
  Lock,
  Trash2,
  UserSquare2,
  ShieldAlert,
  Check,
  Loader2,
  LogOut,
  Camera,
  AlertTriangle,
  Cloud
} from "lucide-react";

interface ApiErrorType {
  message?: string;
  data?: {
    message?: string;
  };
}

export default function ProfilePage() {
  const { user, isAuthenticated, isLoading, updateUser, logout } = useAuth();
  const toast = useToast();
  const router = useRouter();

  // Tab state: "info" | "security" | "danger"
  const [activeTab, setActiveTab] = useState<"info" | "security" | "danger">("info");

  // Info Tab form state
  const [username, setUsername] = useState("");
  const [fullName, setFullName] = useState("");
  const [avatarUrl, setAvatarUrl] = useState("");
  const [isUpdatingInfo, setIsUpdatingInfo] = useState(false);
  const [isUploadingAvatar, setIsUploadingAvatar] = useState(false);
  const [uploadedMeta, setUploadedMeta] = useState<{
    key?: string;
    size?: number;
    contentType?: string;
  } | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);

  // Password Change form state
  const [oldPassword, setOldPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [isChangingPassword, setIsChangingPassword] = useState(false);
  const [passwordErrors, setPasswordErrors] = useState<Record<string, string>>({});

  // Deactivation confirmation modal state
  const [showDeactivateModal, setShowDeactivateModal] = useState(false);
  const [isDeactivating, setIsDeactivating] = useState(false);

  const handleAvatarClick = () => {
    if (fileInputRef.current) {
      fileInputRef.current.click();
    }
  };

  const handleAvatarChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // Validate size (5MB maximum)
    if (file.size > 5 * 1024 * 1024) {
      toast.error("Tải ảnh thất bại", "Kích thước ảnh vượt quá giới hạn cho phép (5MB).");
      return;
    }

    setIsUploadingAvatar(true);
    const formData = new FormData();
    formData.append("file", file);
    formData.append("folder", "avatars");

    try {
      // POST /api/v1/media/upload
      const response = await api.post<{ url: string; key?: string; size?: number; contentType?: string }>("/media/upload", formData);
      setAvatarUrl(response.url);
      setUploadedMeta({
        key: response.key,
        size: response.size,
        contentType: response.contentType,
      });
      toast.success("Tải ảnh thành công", "Ảnh đại diện đã được tải lên thành công. Nhấp 'Lưu thông tin' để lưu thay đổi.");
    } catch (err: unknown) {
      console.error("Avatar upload error:", err);
      const apiErr = err as ApiErrorType;
      toast.error("Tải ảnh thất bại", apiErr.data?.message || apiErr.message || "Không thể tải ảnh lên máy chủ.");
    } finally {
      setIsUploadingAvatar(false);
    }
  };

  // Protect route
  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.replace(ROUTES.LOGIN);
    }
  }, [isAuthenticated, isLoading, router]);

  // Load user data into form fields once loaded
  useEffect(() => {
    if (user) {
      const timer = setTimeout(() => {
        setFullName(user.fullName || "");
        setAvatarUrl(user.avatarUrl || "");
        setUsername(user.username || "");
      }, 0);
      return () => clearTimeout(timer);
    }
  }, [user]);

  if (isLoading || !isAuthenticated || !user) {
    return (
      <div className="flex-1 flex items-center justify-center min-h-[50vh]">
        <Loader2 className="h-8 w-8 text-brand-500 animate-spin" />
      </div>
    );
  }

  // Determine highest role and dynamic styling
  const rolesList = user.roles || [];
  const isAdmin = rolesList.includes("ROLE_ADMIN");
  const isCreator = rolesList.includes("ROLE_CREATOR");

  let roleName = "THÀNH VIÊN";
  let ringClass = "border-emerald-500 ring-emerald-100 dark:ring-emerald-950 bg-emerald-50 shadow-[0_0_15px_rgba(16,185,129,0.15)]";
  let roleBadgeClass = "bg-gradient-to-r from-emerald-600 to-teal-600 text-white border-emerald-400 shadow-emerald-200/50";

  if (isAdmin) {
    roleName = "QUẢN TRỊ VIÊN";
    ringClass = "border-amber-400 ring-amber-100 dark:ring-amber-950 bg-amber-50 shadow-[0_0_15px_rgba(245,158,11,0.2)]";
    roleBadgeClass = "bg-gradient-to-r from-amber-500 to-yellow-500 text-white border-amber-300 shadow-amber-200/50";
  } else if (isCreator) {
    roleName = "NHÀ SÁNG TẠO";
    ringClass = "border-indigo-400 ring-indigo-100 dark:ring-indigo-950 bg-indigo-50 shadow-[0_0_15px_rgba(99,102,241,0.2)]";
    roleBadgeClass = "bg-gradient-to-r from-violet-600 to-indigo-600 text-white border-indigo-400 shadow-indigo-200/50";
  }

  // Handle Info Submit
  const handleInfoSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsUpdatingInfo(true);

    try {
      // PUT /api/v1/auth/profile
      const response = await api.put<AuthResponse>(
        ENDPOINTS.AUTH.UPDATE_PROFILE,
        {
          username: username.trim(),
          fullName: fullName.trim(),
          avatarUrl: avatarUrl.trim(),
        }
      );

      // If username changed, new tokens are returned. Update local storage.
      if (response.accessToken && response.refreshToken) {
        localStorage.setItem("access_token", response.accessToken);
        localStorage.setItem("refresh_token", response.refreshToken);
      }

      // Update local auth context user state
      updateUser(response.user);

      toast.success("Thành công", "Thông tin cá nhân đã được cập nhật thành công.");
    } catch (err: unknown) {
      console.error("Update profile error:", err);
      const apiErr = err as ApiErrorType;
      toast.error("Cập nhật thất bại", apiErr.data?.message || apiErr.message || "Đã xảy ra lỗi.");
    } finally {
      setIsUpdatingInfo(false);
    }
  };

  // Validate Change Password form
  const validatePasswordForm = (): boolean => {
    const errors: Record<string, string> = {};

    if (!oldPassword) {
      errors.oldPassword = "Vui lòng nhập mật khẩu hiện tại.";
    }

    if (!newPassword) {
      errors.newPassword = "Vui lòng nhập mật khẩu mới.";
    } else if (newPassword.length < 8) {
      errors.newPassword = "Mật khẩu mới phải có ít nhất 8 ký tự.";
    } else if (!/^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).*$/.test(newPassword)) {
      errors.newPassword = "Mật khẩu phải chứa ít nhất 1 chữ hoa, 1 chữ thường, 1 số và 1 ký tự đặc biệt.";
    }

    if (newPassword !== confirmPassword) {
      errors.confirmPassword = "Xác nhận mật khẩu không khớp với mật khẩu mới.";
    }

    setPasswordErrors(errors);
    return Object.keys(errors).length === 0;
  };

  // Handle Change Password Submit
  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validatePasswordForm()) return;

    setIsChangingPassword(true);

    try {
      // POST /api/v1/auth/change-password
      await api.post(ENDPOINTS.AUTH.CHANGE_PASSWORD, {
        oldPassword,
        newPassword,
        confirmPassword,
      });

      toast.success("Thay đổi thành công", "Mật khẩu đã được thay đổi. Hệ thống sẽ tự động đăng xuất các phiên hoạt động.");

      // Perform logout immediately as old tokens are invalidated
      setTimeout(() => {
        logout();
      }, 1500);

    } catch (err: unknown) {
      console.error("Change password error:", err);
      const apiErr = err as ApiErrorType;
      toast.error("Thất bại", apiErr.data?.message || apiErr.message || "Đổi mật khẩu thất bại. Vui lòng kiểm tra lại mật khẩu cũ.");
    } finally {
      setIsChangingPassword(false);
    }
  };

  // Handle Deactivate Account
  const handleDeactivateConfirm = async () => {
    setIsDeactivating(true);

    try {
      // DELETE /api/v1/auth/account
      await api.delete(ENDPOINTS.AUTH.DELETE_ACCOUNT);

      toast.success("Đóng băng tài khoản thành công", "Tài khoản của bạn đã được chuyển sang trạng thái chờ xóa. Hệ thống sẽ đăng xuất lập tức.");

      // Logout and redirect
      setTimeout(() => {
        setShowDeactivateModal(false);
        logout();
      }, 2000);

    } catch (err: unknown) {
      console.error("Deactivation error:", err);
      const apiErr = err as ApiErrorType;
      toast.error("Thao tác thất bại", apiErr.data?.message || apiErr.message || "Yêu cầu xóa tài khoản thất bại.");
      setIsDeactivating(false);
    }
  };

  return (
    <div className="flex-1 w-full bg-zinc-50/50 py-10 px-4 sm:px-6 lg:px-8">
      <div className="max-w-5xl mx-auto">


        {/* Layout Grid */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6 items-start">

          {/* Left Sidebar — Tabs navigation */}
          <div className="bg-white border border-brand-100/40 rounded-2xl p-2 shadow-sm flex flex-col gap-1 md:col-span-1">
            <button
              onClick={() => setActiveTab("info")}
              className={`flex items-center gap-3 w-full rounded-xl px-4 py-3 text-sm font-semibold transition-all duration-200 cursor-pointer ${activeTab === "info"
                ? "bg-brand-50 text-brand-700 shadow-sm"
                : "text-zinc-600 hover:bg-brand-50/30 hover:text-brand-600"
                }`}
            >
              <UserSquare2 className="h-4.5 w-4.5" />
              Hồ sơ của tôi
            </button>
            <button
              onClick={() => setActiveTab("security")}
              className={`flex items-center gap-3 w-full rounded-xl px-4 py-3 text-sm font-semibold transition-all duration-200 cursor-pointer ${activeTab === "security"
                ? "bg-brand-50 text-brand-700 shadow-sm"
                : "text-zinc-600 hover:bg-brand-50/30 hover:text-brand-600"
                }`}
            >
              <Lock className="h-4.5 w-4.5" />
              Bảo mật
            </button>
          </div>

          {/* Right Area — Tab Content */}
          <div className="md:col-span-3">

            {/* ══════════════════ TAB 1: PROFILE INFO ══════════════════ */}
            {activeTab === "info" && (
              <div className="bg-white border border-brand-100/40 rounded-2xl p-6 shadow-sm animate-in fade-in duration-200">
                <div className="border-b border-brand-100/30 pb-4 mb-6">
                  <h3 className="text-lg font-bold text-zinc-900">Thông tin cá nhân</h3>
                </div>

                <form onSubmit={handleInfoSubmit} className="space-y-6">
                  {/* Top Profile Zone: Avatar & Deactivation Side-by-Side */}
                  <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-center pb-6 border-b border-zinc-100">

                    {/* Left: Avatar with Role Rings and Upload Button */}
                    <div className="lg:col-span-2 flex flex-col sm:flex-row items-center gap-6 w-full">
                      {/* Avatar with Role Rings and Badge */}
                      <div className="relative flex flex-col items-center select-none pt-4 pb-2 shrink-0">
                        {/* Role Badge sitting perfectly centered on top/overlapping the avatar */}
                        <div className={`absolute -top-3.5 z-10 px-3 py-0.5 rounded-full text-[9px] sm:text-[10px] font-extrabold tracking-wider border shadow-md transition-all duration-300 ${roleBadgeClass}`}>
                          {roleName}
                        </div>

                        {/* Outer Glow Ring & Avatar Circle Container */}
                        <div
                          onClick={!isUploadingAvatar ? handleAvatarClick : undefined}
                          className={`relative group h-24 w-24 sm:h-28 sm:w-28 rounded-full flex items-center justify-center border-4 overflow-hidden shadow-lg transition-all duration-300 ${ringClass} ${!isUploadingAvatar ? 'cursor-pointer hover:scale-[1.03]' : 'cursor-wait'}`}
                        >
                          {isUploadingAvatar ? (
                            <div className="absolute inset-0 bg-white/85 flex items-center justify-center backdrop-blur-xs">
                              <Loader2 className="h-6 w-6 text-brand-500 animate-spin" />
                            </div>
                          ) : (
                            <>
                              {avatarUrl ? (
                                <img
                                  src={avatarUrl}
                                  alt={fullName}
                                  className="h-full w-full object-cover"
                                  onError={(e) => {
                                    e.currentTarget.src = `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(fullName)}`;
                                  }}
                                />
                              ) : (
                                <div className="flex h-full w-full items-center justify-center bg-gradient-to-br from-brand-400 to-brand-600 text-3xl font-extrabold text-white">
                                  {fullName ? fullName.charAt(0).toUpperCase() : (user.username || "U").charAt(0).toUpperCase()}
                                </div>
                              )}
                              <div className="absolute inset-0 bg-black/45 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity duration-200">
                                <Camera className="h-6 w-6 text-white" />
                              </div>
                            </>
                          )}
                        </div>
                      </div>

                      {/* Hidden Native File Input */}
                      <input
                        type="file"
                        ref={fileInputRef}
                        onChange={handleAvatarChange}
                        accept="image/*"
                        className="hidden"
                      />

                      <div className="flex-1 space-y-3 w-full">
                        <label className="text-xs font-bold text-zinc-500 uppercase tracking-wider block">Ảnh đại diện (Avatar)</label>

                        <div className="flex flex-wrap items-center gap-3">
                          <button
                            type="button"
                            onClick={handleAvatarClick}
                            disabled={isUploadingAvatar}
                            className="px-4 py-2 bg-brand-50 hover:bg-brand-100 text-brand-700 text-xs font-bold rounded-xl border border-brand-100 shadow-sm active:scale-95 transition-all cursor-pointer flex items-center gap-1.5 disabled:opacity-50"
                          >
                            {isUploadingAvatar ? (
                              <>
                                <Loader2 className="h-3.5 w-3.5 animate-spin text-brand-500" />
                                <span>Đang tải...</span>
                              </>
                            ) : (
                              <>
                                <Camera className="h-3.5 w-3.5 text-brand-500" />
                                <span>Tải ảnh mới</span>
                              </>
                            )}
                          </button>
                          {avatarUrl && (
                            <span className="text-xs text-zinc-500 font-medium">
                              Ảnh đại diện đã được cập nhật thành công.
                            </span>
                          )}
                        </div>
                      </div>
                    </div>

                    {/* Right: Deactivation Zone (Vô hiệu hóa) adjacent to Avatar */}
                    <div className="lg:col-span-1 w-full flex justify-center lg:justify-end">
                      <div className="relative group/deactivate flex flex-col items-center justify-center w-full max-w-[240px] h-24 sm:h-28 p-5 bg-rose-50/25 border border-rose-100/60 rounded-2xl select-none hover:bg-rose-50/50 hover:border-rose-200 transition-all duration-300">
                        
                        <button
                          type="button"
                          onClick={() => setShowDeactivateModal(true)}
                          className="w-full py-2.5 px-4 bg-white hover:bg-rose-50 border border-rose-200/80 text-rose-700 active:scale-95 transition-all text-xs font-bold rounded-xl shadow-xs cursor-pointer flex items-center justify-center gap-2"
                        >
                          <Trash2 className="h-4 w-4 text-rose-500 shrink-0" />
                          <span>Vô hiệu hóa tài khoản</span>
                        </button>

                        {/* Popover showing deactivation details on Hover */}
                        <div className="absolute right-1/2 lg:right-0 translate-x-1/2 lg:translate-x-0 top-full mt-3 w-72 p-4 bg-zinc-900 text-zinc-100 text-xs rounded-2xl shadow-2xl border border-zinc-800 opacity-0 pointer-events-none group-hover/deactivate:opacity-100 group-hover/deactivate:pointer-events-auto transition-all duration-250 z-30 space-y-2">
                          <div className="flex items-center gap-1.5 font-extrabold text-rose-400 mb-1">
                            <AlertTriangle className="h-4 w-4 text-rose-400 shrink-0" />
                            <span>Quy trình đóng băng 30 ngày</span>
                          </div>
                          <p className="text-[11px] leading-relaxed text-zinc-300 text-left">
                            1. Mọi dữ liệu tạm khóa. Đăng nhập lại trong vòng **30 ngày** để khôi phục.
                          </p>
                          <p className="text-[11px] leading-relaxed text-zinc-300 text-left">
                            2. Sau 30 ngày, hệ thống tự động ẩn danh hóa & xóa vĩnh viễn dữ liệu (Không thể hoàn tác).
                          </p>
                          <div className="absolute -top-1.5 right-1/2 lg:right-10 translate-x-1/2 lg:translate-x-0 w-3 h-3 bg-zinc-900 border-l border-t border-zinc-800 rotate-45"></div>
                        </div>
                      </div>
                    </div>

                  </div>

                  {/* Editable and Read-only Grid */}
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
                    {/* Full Name */}
                    <div className="space-y-1.5">
                      <label className="text-xs font-bold text-zinc-500 uppercase tracking-wider block">Họ và tên</label>
                      <input
                        type="text"
                        required
                        value={fullName}
                        onChange={(e) => setFullName(e.target.value)}
                        placeholder="Nhập họ và tên"
                        className="w-full px-4 py-2.5 bg-brand-50/30 border border-brand-100 rounded-xl text-sm text-zinc-800 focus:outline-none focus:ring-2 focus:ring-brand-200/50 focus:border-brand-300 focus:bg-white transition-all duration-200"
                      />
                    </div>

                    {/* Username */}
                    <div className="space-y-1.5">
                      <label className="text-xs font-bold text-zinc-500 uppercase tracking-wider block">Tên tài khoản</label>
                      <input
                        type="text"
                        required
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                        placeholder="Nhập tên tài khoản"
                        className="w-full px-4 py-2.5 bg-brand-50/30 border border-brand-100 rounded-xl text-sm text-zinc-800 focus:outline-none focus:ring-2 focus:ring-brand-200/50 focus:border-brand-300 focus:bg-white transition-all duration-200"
                      />
                    </div>

                    {/* Email (Read-only) */}
                    <div className="space-y-1.5">
                      <label className="text-xs font-bold text-zinc-500 uppercase tracking-wider block flex items-center gap-1">
                        <span>Địa chỉ Email</span>
                        <Lock className="h-3 w-3 text-zinc-400 shrink-0" />
                      </label>
                      <div className="relative">
                        <input
                          type="text"
                          disabled
                          value={user.email}
                          className="w-full pl-4 pr-10 py-2.5 bg-zinc-50/80 border border-zinc-200/60 rounded-xl text-sm text-zinc-400 select-all outline-none cursor-not-allowed font-medium"
                        />
                        <Lock className="absolute right-3.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-zinc-300" />
                      </div>
                    </div>

                    {/* Role / Type (Read-only) */}
                    <div className="space-y-1.5">
                      <label className="text-xs font-bold text-zinc-500 uppercase tracking-wider block flex items-center gap-1">
                        <span>Vai trò tài khoản</span>
                        <Lock className="h-3 w-3 text-zinc-400 shrink-0" />
                      </label>
                      <div className="relative">
                        <div className="flex h-10 items-center pl-4 pr-10 bg-zinc-50/80 border border-zinc-200/60 rounded-xl text-sm text-zinc-400 font-bold select-none cursor-not-allowed uppercase tracking-wider">
                          {roleName}
                        </div>
                        <Lock className="absolute right-3.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-zinc-300" />
                      </div>
                    </div>
                  </div>

                  {/* Save Button */}
                  <div className="flex justify-end pt-4 border-t border-zinc-100">
                    <button
                      type="submit"
                      disabled={isUpdatingInfo}
                      className="px-6 py-2.5 bg-gradient-to-r from-brand-500 to-brand-400 hover:from-brand-600 hover:to-brand-500 text-white text-xs font-bold rounded-xl flex items-center justify-center space-x-2 shadow-md shadow-brand-500/10 hover:shadow-brand-500/20 active:scale-[0.98] transition-all cursor-pointer disabled:opacity-50"
                    >
                      {isUpdatingInfo ? (
                        <Loader2 className="h-4 w-4 animate-spin" />
                      ) : (
                        <>
                          <Check className="h-4 w-4" />
                          <span>Lưu thông tin</span>
                        </>
                      )}
                    </button>
                  </div>
                </form>
              </div>
            )}

            {/* ══════════════════ TAB 2: SECURITY ══════════════════ */}
            {activeTab === "security" && (
              <div className="bg-white border border-brand-100/40 rounded-2xl p-6 shadow-sm animate-in fade-in duration-200">
                <div className="border-b border-brand-100/30 pb-4 mb-6">
                  <h3 className="text-lg font-bold text-zinc-900">Thay đổi mật khẩu</h3>
                  <p className="text-zinc-400 text-xs mt-0.5">Đặt lại mật khẩu tài khoản và quản lý bảo mật phiên</p>
                </div>

                {user.oauthProvider !== "LOCAL" && !user.hasPassword ? (
                  /* Social accounts warning */
                  <div className="p-4 bg-amber-50 border border-amber-200 rounded-xl flex items-start space-x-3 text-amber-700 text-sm leading-relaxed">
                    <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5" />
                    <span>
                      Tài khoản của bạn được liên kết trực tiếp qua cổng mạng xã hội **{user.oauthProvider}**. Do đó, bạn không thể thay đổi mật khẩu trực tiếp tại đây mà thực hiện quản lý mật khẩu bên nhà cung cấp liên kết.
                    </span>
                  </div>
                ) : (
                  /* Standard form */
                  <form onSubmit={handlePasswordSubmit} className="space-y-6">
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
                      {/* Old Password */}
                      <div className="space-y-1.5 sm:col-span-2">
                        <label className="text-xs font-semibold text-zinc-500 uppercase tracking-wider block">Mật khẩu hiện tại</label>
                        <input
                          type="password"
                          required
                          value={oldPassword}
                          onChange={(e) => setOldPassword(e.target.value)}
                          placeholder="Nhập mật khẩu cũ"
                          className="w-full px-4 py-2.5 bg-brand-50/30 border border-brand-100 rounded-xl text-sm text-zinc-800 focus:outline-none focus:ring-2 focus:ring-brand-200/50 focus:border-brand-300 focus:bg-white transition-all duration-200"
                        />
                        {passwordErrors.oldPassword && (
                          <p className="text-xs text-rose-500 mt-1">{passwordErrors.oldPassword}</p>
                        )}
                      </div>

                      {/* New Password */}
                      <div className="space-y-1.5">
                        <label className="text-xs font-semibold text-zinc-500 uppercase tracking-wider block">Mật khẩu mới</label>
                        <input
                          type="password"
                          required
                          value={newPassword}
                          onChange={(e) => setNewPassword(e.target.value)}
                          placeholder="Tối thiểu 8 ký tự, 1 hoa, 1 thường, 1 số, 1 đặc biệt"
                          className="w-full px-4 py-2.5 bg-brand-50/30 border border-brand-100 rounded-xl text-sm text-zinc-800 focus:outline-none focus:ring-2 focus:ring-brand-200/50 focus:border-brand-300 focus:bg-white transition-all duration-200"
                        />
                        {passwordErrors.newPassword && (
                          <p className="text-xs text-rose-500 mt-1">{passwordErrors.newPassword}</p>
                        )}
                      </div>

                      {/* Confirm Password */}
                      <div className="space-y-1.5">
                        <label className="text-xs font-semibold text-zinc-500 uppercase tracking-wider block">Xác nhận mật khẩu mới</label>
                        <input
                          type="password"
                          required
                          value={confirmPassword}
                          onChange={(e) => setConfirmPassword(e.target.value)}
                          placeholder="Xác nhận lại mật khẩu mới"
                          className="w-full px-4 py-2.5 bg-brand-50/30 border border-brand-100 rounded-xl text-sm text-zinc-800 focus:outline-none focus:ring-2 focus:ring-brand-200/50 focus:border-brand-300 focus:bg-white transition-all duration-200"
                        />
                        {passwordErrors.confirmPassword && (
                          <p className="text-xs text-rose-500 mt-1">{passwordErrors.confirmPassword}</p>
                        )}
                      </div>
                    </div>

                    {/* Submit Button */}
                    <div className="flex justify-end pt-4 border-t border-zinc-100">
                      <button
                        type="submit"
                        disabled={isChangingPassword}
                        className="px-6 py-2.5 bg-gradient-to-r from-brand-500 to-brand-400 hover:from-brand-600 hover:to-brand-500 text-white text-xs font-bold rounded-xl flex items-center justify-center space-x-2 shadow-md shadow-brand-500/10 hover:shadow-brand-500/20 active:scale-[0.98] transition-all cursor-pointer disabled:opacity-50"
                      >
                        {isChangingPassword ? (
                          <Loader2 className="h-4 w-4 animate-spin" />
                        ) : (
                          <>
                            <Check className="h-4 w-4" />
                            <span>Đổi mật khẩu</span>
                          </>
                        )}
                      </button>
                    </div>
                  </form>
                )}
              </div>
            )}


          </div>

        </div>

      </div>

      {/* ══════════════════ DEACTIVATION WARNING MODAL ══════════════════ */}
      {showDeactivateModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
          <div className="fixed inset-0 bg-zinc-950/40 backdrop-blur-sm" onClick={() => !isDeactivating && setShowDeactivateModal(false)} />

          <div className="relative bg-white border border-rose-100 rounded-3xl p-6 sm:p-8 max-w-md w-full shadow-2xl animate-in zoom-in-95 duration-200 z-10 text-center">
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-rose-50 text-rose-500 mb-6 border border-rose-100">
              <AlertTriangle className="h-8 w-8" />
            </div>

            <h3 className="text-xl font-extrabold text-zinc-900 mb-3">Xác nhận xóa tài khoản?</h3>
            <p className="text-zinc-500 text-sm leading-relaxed mb-6">
              Bạn có chắc chắn muốn xóa tài khoản **{user.username}**? Hành động này sẽ đăng xuất bạn lập tức và đóng băng dữ liệu của bạn trong 30 ngày trước khi bị ẩn danh hóa vĩnh viễn khỏi toàn bộ hệ thống VibeCart.
            </p>

            <div className="flex gap-3">
              <button
                type="button"
                disabled={isDeactivating}
                onClick={() => setShowDeactivateModal(false)}
                className="flex-1 py-3 bg-zinc-100 hover:bg-zinc-200 text-zinc-700 text-sm font-semibold rounded-xl transition-all cursor-pointer disabled:opacity-50"
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                disabled={isDeactivating}
                onClick={handleDeactivateConfirm}
                className="flex-1 py-3 bg-rose-600 hover:bg-rose-700 text-white text-sm font-semibold rounded-xl flex items-center justify-center gap-2 transition-all cursor-pointer shadow-lg shadow-rose-500/10"
              >
                {isDeactivating ? (
                  <Loader2 className="h-5 w-5 animate-spin" />
                ) : (
                  <>
                    <LogOut className="h-4 w-4" />
                    <span>Xác nhận xóa</span>
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}
