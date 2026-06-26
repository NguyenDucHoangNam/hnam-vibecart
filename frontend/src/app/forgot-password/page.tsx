"use client";

import React, { useState } from "react";
import { api } from "@/lib/api-client";
import { ENDPOINTS } from "@/constants/api";
import { ROUTES } from "@/constants/routes";
import { useToast } from "@/context/ToastContext";
import { Mail, ArrowRight, Loader2, KeySquare, CheckCircle, Info } from "lucide-react";
import Link from "next/link";

interface ApiErrorType {
  message?: string;
  data?: {
    message?: string;
  };
}

export default function ForgotPasswordPage() {
  const toast = useToast();
  const [email, setEmail] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [isSent, setIsSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!email.trim()) {
      setError("Vui lòng nhập địa chỉ email.");
      return;
    }

    setIsLoading(true);

    try {
      await api.post(ENDPOINTS.AUTH.FORGOT_PASSWORD, { email: email.trim() });
      setIsSent(true);
      toast.success("Đã gửi yêu cầu", "Vui lòng kiểm tra hộp thư đến của bạn để nhận liên kết đặt lại mật khẩu.");
    } catch (err: unknown) {
      console.error("Forgot password error:", err);
      const apiErr = err as ApiErrorType;
      const errorMsg = apiErr.data?.message || apiErr.message || "Gửi yêu cầu thất bại. Vui lòng kiểm tra lại email hoặc thử lại sau.";
      setError(errorMsg);
      toast.error("Gửi yêu cầu thất bại", errorMsg);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div
      className="flex-1 flex items-center justify-center min-h-[calc(100vh-76px-80px)] px-4 py-12"
      style={{
        background: `radial-gradient(ellipse 60% 60% at 50% 40%, #d1fae5 0%, #ecfdf5 30%, #ffffff 65%)`,
      }}
    >
      <div className="w-full max-w-sm">
        <div className="bg-white/80 backdrop-blur-xl border border-brand-100/40 rounded-2xl p-6 shadow-xl shadow-brand-500/5 transition-all duration-300">
          
          {!isSent ? (
            <>
              <div className="text-center mb-6">
                <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-brand-50 text-brand-500 mb-4 shadow-sm border border-brand-100/40">
                  <KeySquare className="h-6 w-6" />
                </div>
                <h2 className="text-2xl font-extrabold tracking-tight text-brand-800">
                  Quên mật khẩu?
                </h2>
                <p className="text-zinc-400 mt-1.5 text-sm leading-relaxed">
                  Nhập email tài khoản của bạn để nhận liên kết đặt lại mật khẩu bảo mật
                </p>
              </div>
              {error && (
                <div className="mb-6 p-4 bg-rose-50 border border-rose-200/50 rounded-xl flex items-start space-x-3 text-rose-700 text-sm leading-relaxed animate-in fade-in slide-in-from-top-1 duration-200">
                  <Info className="w-5 h-5 shrink-0 mt-0.5" />
                  <span>{error}</span>
                </div>
              )}
              <form onSubmit={handleSubmit} className="space-y-4">
                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-zinc-500 uppercase tracking-wider block">
                    Địa chỉ Email
                  </label>
                  <div className="relative group">
                    <span className="absolute inset-y-0 left-0 flex items-center pl-4 text-brand-300 group-focus-within:text-brand-500 transition-colors duration-200">
                      <Mail className="w-5 h-5" />
                    </span>
                    <input
                      type="email"
                      required
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      placeholder="email_cua_ban@example.com"
                      disabled={isLoading}
                      className="w-full pl-11 pr-4 py-3 bg-brand-50/40 border border-brand-100/60 rounded-xl text-sm text-zinc-800 placeholder-zinc-400 focus:outline-none focus:ring-2 focus:ring-brand-200/50 focus:border-brand-300 focus:bg-white transition-all duration-200"
                    />
                  </div>
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
                      <span>Gửi yêu cầu khôi phục</span>
                      <ArrowRight className="w-5 h-5" />
                    </>
                  )}
                </button>
              </form>
            </>
          ) : (
            <div className="text-center py-4 animate-in fade-in zoom-in-95 duration-300">
              <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-emerald-50 text-emerald-500 mb-6 shadow-sm border border-emerald-100/50">
                <CheckCircle className="h-8 w-8" />
              </div>
              <h2 className="text-2xl font-extrabold tracking-tight text-brand-800 mb-3">
                Đã gửi yêu cầu thành công
              </h2>
              <p className="text-zinc-500 text-sm leading-relaxed mb-8">
                Nếu địa chỉ email <span className="font-semibold text-brand-700">{email}</span> tồn tại trong hệ thống, bạn sẽ sớm nhận được một email chứa đường dẫn bảo mật để tạo lại mật khẩu mới.
              </p>
              
              <div className="p-4 rounded-xl bg-brand-50/50 border border-brand-100/40 text-xs text-brand-600 leading-relaxed mb-6">
                💡 Bạn vui lòng kiểm tra cả hộp thư **Spam (Thư rác)** hoặc hộp thư quảng cáo nếu chưa nhận được sau vài phút.
              </div>
            </div>
          )}
          <div className="mt-6 text-center text-sm border-t border-brand-100/40 pt-4">
            <Link
              href={ROUTES.LOGIN}
              className="font-semibold text-brand-500 hover:text-brand-600 transition-colors"
            >
              Quay lại đăng nhập
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
