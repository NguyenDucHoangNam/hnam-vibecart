"use client";

import React, { useState, useEffect, useRef, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/hooks/useAuth";
import { useToast } from "@/context/ToastContext";
import { ROUTES } from "@/constants/routes";
import { ShieldAlert, ArrowRight, Loader2, KeyRound } from "lucide-react";
import Link from "next/link";

interface ApiErrorType {
  message?: string;
  status?: number;
  data?: {
    code?: number;
    message?: string;
  };
}

function VerifyOtpContent() {
  const { verifyOtp, resendOtp } = useAuth();
  const toast = useToast();
  const router = useRouter();
  const searchParams = useSearchParams();
  const email = searchParams.get("email") || "";

  const [otp, setOtp] = useState<string[]>(new Array(6).fill(""));
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [countdown, setCountdown] = useState(60);

  const handleResendOtp = async () => {
    if (!email) {
      setError("Không tìm thấy địa chỉ email. Vui lòng quay lại trang đăng ký.");
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      await resendOtp(email);
      toast.success("Đã gửi lại mã!", "Mã OTP mới đã được gửi thành công đến hòm thư của bạn.");
      setOtp(new Array(6).fill(""));
      setCountdown(60);
    } catch (err: unknown) {
      console.error("Resend OTP Error:", err);
      const apiErr = err as ApiErrorType;
      const errorMsg = apiErr.data?.message || apiErr.message || "Gửi lại mã OTP thất bại.";
      setError(errorMsg);
      toast.error("Gửi lại thất bại", errorMsg);
    } finally {
      setIsLoading(false);
    }
  };
  const inputRefs = useRef<HTMLInputElement[]>([]);
  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [countdown]);
  useEffect(() => {
    if (inputRefs.current[0]) {
      inputRefs.current[0].focus();
    }
  }, []);
  const handleChange = (element: HTMLInputElement, index: number) => {
    const value = element.value.replace(/[^0-9]/g, "");
    
    const newOtp = [...otp];
    newOtp[index] = value.substring(value.length - 1);
    setOtp(newOtp);
    if (value && index < 5 && inputRefs.current[index + 1]) {
      inputRefs.current[index + 1].focus();
    }
  };
  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>, index: number) => {
    if (e.key === "Backspace") {
      if (!otp[index] && index > 0 && inputRefs.current[index - 1]) {
        inputRefs.current[index - 1].focus();
      }
    }
  };
  const handlePaste = (e: React.ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault();
    const pasteData = e.clipboardData.getData("text").replace(/[^0-9]/g, "").substring(0, 6);
    
    if (pasteData.length === 6) {
      const newOtp = pasteData.split("");
      setOtp(newOtp);
      if (inputRefs.current[5]) {
        inputRefs.current[5].focus();
      }
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const otpCode = otp.join("");
    if (otpCode.length < 6) {
      setError("Vui lòng nhập đầy đủ mã OTP gồm 6 chữ số.");
      return;
    }

    if (!email) {
      setError("Không tìm thấy địa chỉ email. Vui lòng quay lại trang đăng ký.");
      return;
    }

    setIsLoading(true);

    try {
      await verifyOtp(email, otpCode);
      toast.success("Kích hoạt thành công!", "Tài khoản của bạn đã được xác thực hoạt động.");
      router.push(ROUTES.HOME);
    } catch (err: unknown) {
      console.error("OTP Verification Error:", err);
      const apiErr = err as ApiErrorType;
      const errorCode = apiErr.data?.code;
      let errorMsg = apiErr.data?.message || apiErr.message || "Xác thực mã OTP thất bại.";
      
      if (errorCode === 1014 || errorMsg.includes("LOCK") || apiErr.status === 423) {
        errorMsg = "Mã OTP đã bị khóa do nhập sai quá nhiều lần. Vui lòng đăng ký lại để nhận mã mới.";
      }
      
      setError(errorMsg);
      toast.error("Xác thực thất bại", errorMsg);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="w-full max-w-md">
      <div className="bg-white/80 backdrop-blur-xl border border-brand-100/40 rounded-2xl p-6 sm:p-8 shadow-xl shadow-brand-500/5 transition-all duration-300">
        <div className="text-center mb-6">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-brand-50 text-brand-500 mb-4 shadow-sm border border-brand-100/40">
            <KeyRound className="h-6 w-6" />
          </div>
          <h2 className="text-2xl font-extrabold tracking-tight text-brand-800">
            Xác thực tài khoản
          </h2>
          <p className="text-zinc-500 mt-2 text-sm leading-relaxed max-w-sm mx-auto">
            Chúng tôi đã gửi mã xác thực gồm 6 chữ số đến hộp thư:
            <br />
            <span className="font-semibold text-brand-700 break-all">{email || "email_cua_ban@example.com"}</span>
          </p>
        </div>
        {error && (
          <div className="mb-6 p-4 bg-rose-50 border border-rose-200/50 rounded-xl flex items-start space-x-3 text-rose-700 text-sm leading-relaxed animate-in fade-in slide-in-from-top-1 duration-200">
            <ShieldAlert className="w-5 h-5 shrink-0 mt-0.5" />
            <span>{error}</span>
          </div>
        )}
        <form onSubmit={handleSubmit} className="space-y-6">
          <div className="flex justify-between gap-2 max-w-xs mx-auto">
            {otp.map((digit, idx) => (
              <input
                key={idx}
                type="text"
                inputMode="numeric"
                pattern="[0-9]*"
                maxLength={1}
                value={digit}
                disabled={isLoading}
                ref={(el) => {
                  if (el) inputRefs.current[idx] = el;
                }}
                onChange={(e) => handleChange(e.target, idx)}
                onKeyDown={(e) => handleKeyDown(e, idx)}
                onPaste={idx === 0 ? handlePaste : undefined}
                className="w-11 h-12 sm:w-12 sm:h-13 text-center text-xl font-extrabold bg-brand-50/40 border border-brand-100 rounded-xl focus:outline-none focus:ring-2 focus:ring-brand-200/50 focus:border-brand-300 focus:bg-white transition-all text-zinc-800"
              />
            ))}
          </div>
          <button
            type="submit"
            disabled={isLoading || otp.join("").length < 6}
            className="w-full py-3 bg-gradient-to-r from-brand-500 to-brand-400 hover:from-brand-600 hover:to-brand-500 text-white text-sm font-semibold rounded-xl flex items-center justify-center space-x-2 shadow-lg shadow-brand-500/20 hover:shadow-brand-500/30 active:scale-[0.98] transition-all duration-200 disabled:opacity-50 disabled:pointer-events-none cursor-pointer"
          >
            {isLoading ? (
              <Loader2 className="w-5 h-5 animate-spin" />
            ) : (
              <>
                <span>Xác thực kích hoạt</span>
                <ArrowRight className="w-5 h-5" />
              </>
            )}
          </button>
        </form>
        <div className="mt-8 text-center text-sm text-zinc-400">
          {countdown > 0 ? (
            <p>
              Gửi lại mã sau <span className="font-semibold text-brand-500">{countdown}s</span>
            </p>
          ) : (
            <p className="leading-relaxed">
              Không nhận được mã?{" "}
              <button
                type="button"
                onClick={handleResendOtp}
                disabled={isLoading}
                className="font-semibold text-brand-500 hover:text-brand-600 transition-colors cursor-pointer focus:outline-none bg-transparent border-none p-0 inline font-medium"
              >
                Gửi lại mã ngay
              </button>
            </p>
          )}
        </div>
      </div>
    </div>
  );
}

export default function VerifyOtpPage() {
  return (
    <div
      className="flex-1 flex items-center justify-center min-h-[calc(100vh-76px-80px)] px-4 py-12"
      style={{
        background: `radial-gradient(ellipse 60% 60% at 50% 40%, #d1fae5 0%, #ecfdf5 30%, #ffffff 65%)`,
      }}
    >
      <Suspense fallback={
        <div className="w-full max-w-md bg-white/80 backdrop-blur-xl border border-brand-100/40 rounded-2xl p-6 shadow-xl flex items-center justify-center py-20">
          <Loader2 className="h-8 w-8 text-brand-500 animate-spin" />
        </div>
      }>
        <VerifyOtpContent />
      </Suspense>
    </div>
  );
}
