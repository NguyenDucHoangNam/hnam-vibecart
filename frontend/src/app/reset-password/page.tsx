"use client";

import React, { useState, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { api } from "@/lib/api-client";
import { ENDPOINTS } from "@/constants/api";
import { ROUTES } from "@/constants/routes";
import { useToast } from "@/context/ToastContext";
import { Lock, Eye, EyeOff, Loader2, RefreshCw, CheckCircle2, ShieldAlert, ArrowRight } from "lucide-react";
import Link from "next/link";

interface ApiErrorType {
  message?: string;
  data?: {
    message?: string;
  };
}

function ResetPasswordContent() {
  const toast = useToast();
  const searchParams = useSearchParams();
  const token = searchParams.get("token") || "";

  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isSuccess, setIsSuccess] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const validateForm = (): boolean => {
    const errors: Record<string, string> = {};

    if (!newPassword) {
      errors.newPassword = "Vui lòng nhập mật khẩu mới.";
    } else if (newPassword.length < 8) {
      errors.newPassword = "Mật khẩu phải có ít nhất 8 ký tự.";
    } else if (!/^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).*$/.test(newPassword)) {
      errors.newPassword = "Mật khẩu phải chứa ít nhất 1 chữ hoa, 1 chữ thường, 1 số và 1 ký tự đặc biệt.";
    }

    if (newPassword !== confirmPassword) {
      errors.confirmPassword = "Xác nhận mật khẩu không khớp với mật khẩu mới.";
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!token) {
      setError("Mã token khôi phục mật khẩu không tìm thấy hoặc đã hết hạn. Vui lòng gửi lại yêu cầu.");
      return;
    }

    if (!validateForm()) return;

    setIsLoading(true);

    try {
      await api.post(ENDPOINTS.AUTH.RESET_PASSWORD, {
        token: token,
        newPassword: newPassword,
      });

      setIsSuccess(true);
      toast.success("Đặt lại mật khẩu thành công", "Các phiên làm việc khác đã được đăng xuất an toàn.");
    } catch (err: unknown) {
      console.error("Reset password error:", err);
      const apiErr = err as ApiErrorType;
      const errorMsg = apiErr.data?.message || apiErr.message || "Đặt lại mật khẩu thất bại. Token có thể đã hết hạn.";
      setError(errorMsg);
      toast.error("Thất bại", errorMsg);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="w-full max-w-sm">
      <div className="bg-white/80 backdrop-blur-xl border border-brand-100/40 rounded-2xl p-6 shadow-xl shadow-brand-500/5 transition-all duration-300">
        
        {!token ? (
          <div className="text-center py-4">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-rose-50 text-rose-500 mb-4 border border-rose-100">
              <ShieldAlert className="h-6 w-6" />
            </div>
            <h2 className="text-xl font-extrabold text-zinc-800 mb-2">Liên kết không hợp lệ</h2>
            <p className="text-zinc-500 text-sm leading-relaxed mb-6">
              Đường dẫn khôi phục mật khẩu này bị thiếu mã token hoặc liên kết đã hết hạn hoạt động (10 phút). Vui lòng yêu cầu cấp lại liên kết mới.
            </p>
            <Link
              href={ROUTES.FORGOT_PASSWORD}
              className="inline-flex h-11 w-full items-center justify-center rounded-xl bg-gradient-to-r from-brand-500 to-brand-400 text-white text-sm font-semibold shadow-md shadow-brand-500/20 hover:scale-[1.02] active:scale-[0.98] transition-all cursor-pointer"
            >
              Gửi lại yêu cầu mới
            </Link>
          </div>
        ) : !isSuccess ? (
          <>
            <div className="text-center mb-6">
              <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-brand-50 text-brand-500 mb-4 shadow-sm border border-brand-100/40">
                <RefreshCw className="h-6 w-6" />
              </div>
              <h2 className="text-2xl font-extrabold tracking-tight text-brand-800">
                Đặt lại mật khẩu
              </h2>
              <p className="text-zinc-400 mt-1.5 text-sm leading-relaxed">
                Thiết lập mật khẩu mới cho tài khoản của bạn
              </p>
            </div>
            {error && (
              <div className="mb-5 p-3 bg-rose-50 border border-rose-200/50 rounded-xl flex items-start space-x-3 text-rose-700 text-sm leading-relaxed animate-in fade-in slide-in-from-top-1 duration-200">
                <ShieldAlert className="w-5 h-5 shrink-0 mt-0.5" />
                <span>{error}</span>
              </div>
            )}
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-zinc-500 uppercase tracking-wider block">
                  Mật khẩu mới
                </label>
                <div className="relative group">
                  <span className="absolute inset-y-0 left-0 flex items-center pl-4 text-brand-300 group-focus-within:text-brand-500 transition-colors duration-200">
                    <Lock className="w-5 h-5" />
                  </span>
                  <input
                    type={showPassword ? "text" : "password"}
                    required
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    placeholder="Mật khẩu mới bảo mật"
                    disabled={isLoading}
                    className="w-full pl-11 pr-12 py-3 bg-brand-50/40 border border-brand-100/60 rounded-xl text-sm text-zinc-800 placeholder-zinc-400 focus:outline-none focus:ring-2 focus:ring-brand-200/50 focus:border-brand-300 focus:bg-white transition-all duration-200"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    tabIndex={-1}
                    className="absolute inset-y-0 right-0 flex items-center pr-4 text-zinc-400 hover:text-brand-500 transition-colors"
                  >
                    {showPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
                  </button>
                </div>
                {fieldErrors.newPassword && (
                  <p className="text-[11px] text-rose-500 mt-1 pl-1 leading-normal">{fieldErrors.newPassword}</p>
                )}
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-zinc-500 uppercase tracking-wider block">
                  Xác nhận mật khẩu
                </label>
                <div className="relative group">
                  <span className="absolute inset-y-0 left-0 flex items-center pl-4 text-brand-300 group-focus-within:text-brand-500 transition-colors duration-200">
                    <Lock className="w-5 h-5" />
                  </span>
                  <input
                    type={showConfirmPassword ? "text" : "password"}
                    required
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    placeholder="Xác nhận lại mật khẩu"
                    disabled={isLoading}
                    className="w-full pl-11 pr-12 py-3 bg-brand-50/40 border border-brand-100/60 rounded-xl text-sm text-zinc-800 placeholder-zinc-400 focus:outline-none focus:ring-2 focus:ring-brand-200/50 focus:border-brand-300 focus:bg-white transition-all duration-200"
                  />
                  <button
                    type="button"
                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                    tabIndex={-1}
                    className="absolute inset-y-0 right-0 flex items-center pr-4 text-zinc-400 hover:text-brand-500 transition-colors"
                  >
                    {showConfirmPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
                  </button>
                </div>
                {fieldErrors.confirmPassword && (
                  <p className="text-[11px] text-rose-500 mt-1 pl-1 leading-normal">{fieldErrors.confirmPassword}</p>
                )}
              </div>
              <button
                type="submit"
                disabled={isLoading}
                className="w-full mt-2 py-3 bg-gradient-to-r from-brand-500 to-brand-400 hover:from-brand-600 hover:to-brand-500 text-white text-sm font-semibold rounded-xl flex items-center justify-center space-x-2 shadow-lg shadow-brand-500/20 hover:shadow-brand-500/30 active:scale-[0.98] transition-all duration-200 disabled:opacity-50 disabled:pointer-events-none cursor-pointer"
              >
                {isLoading ? (
                  <Loader2 className="w-5 h-5 animate-spin" />
                ) : (
                  <>
                    <span>Cập nhật mật khẩu</span>
                    <ArrowRight className="w-5 h-5" />
                  </>
                )}
              </button>
            </form>
          </>
        ) : (
          <div className="text-center py-4 animate-in fade-in zoom-in-95 duration-300">
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-emerald-50 text-emerald-500 mb-6 shadow-sm border border-emerald-100/50">
              <CheckCircle2 className="h-8 w-8" />
            </div>
            <h2 className="text-2xl font-extrabold tracking-tight text-brand-800 mb-3">
              Đặt lại thành công!
            </h2>
            <p className="text-zinc-500 text-sm leading-relaxed mb-8">
              Mật khẩu của bạn đã được thay đổi thành công. Đồng thời, toàn bộ phiên làm việc của tài khoản này trên các thiết bị khác đã được đăng xuất an toàn.
            </p>
            <Link
              href={ROUTES.LOGIN}
              className="inline-flex h-11 w-full items-center justify-center rounded-xl bg-gradient-to-r from-brand-500 to-brand-400 text-white text-sm font-semibold shadow-md shadow-brand-500/20 hover:scale-[1.02] active:scale-[0.98] transition-all cursor-pointer"
            >
              Đăng nhập ngay
              </Link>
          </div>
        )}
        {!isSuccess && (
          <div className="mt-6 text-center text-sm border-t border-brand-100/40 pt-4">
            <Link
              href={ROUTES.LOGIN}
              className="font-semibold text-zinc-400 hover:text-brand-500 transition-colors"
            >
              Quay lại đăng nhập
            </Link>
          </div>
        )}
      </div>
    </div>
  );
}

export default function ResetPasswordPage() {
  return (
    <div
      className="flex-1 flex items-center justify-center min-h-[calc(100vh-76px-80px)] px-4 py-12"
      style={{
        background: `radial-gradient(ellipse 60% 60% at 50% 40%, #d1fae5 0%, #ecfdf5 30%, #ffffff 65%)`,
      }}
    >
      <Suspense fallback={
        <div className="w-full max-w-sm bg-white/80 backdrop-blur-xl border border-brand-100/40 rounded-2xl p-6 shadow-xl flex items-center justify-center py-20">
          <Loader2 className="h-8 w-8 text-brand-500 animate-spin" />
        </div>
      }>
        <ResetPasswordContent />
      </Suspense>
    </div>
  );
}
