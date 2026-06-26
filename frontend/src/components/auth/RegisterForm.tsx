"use client";

import React, { useState } from "react";
import { useAuth } from "@/context/AuthContext";
import { useToast } from "@/context/ToastContext";
import { useRouter } from "next/navigation";
import { ROUTES } from "@/constants/routes";
import { Mail, Lock, User, ArrowRight, Eye, EyeOff, Loader2, Info } from "lucide-react";
import Link from "next/link";

interface ApiErrorType {
  message?: string;
  status?: number;
  data?: {
    code?: number;
    message?: string;
  };
}

export default function RegisterForm() {
  const { register } = useAuth();
  const toast = useToast();
  const router = useRouter();

  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fullName, setFullName] = useState("");
  const [role, setRole] = useState("SHOPPER");
  
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const validateForm = (): boolean => {
    const errors: Record<string, string> = {};
    const trimmedFullName = fullName.trim();
    if (!trimmedFullName) {
      errors.fullName = "Vui lòng nhập họ và tên";
    } else if (trimmedFullName.length < 2) {
      errors.fullName = "Họ và tên phải có ít nhất 2 ký tự";
    } else if (trimmedFullName.length > 100) {
      errors.fullName = "Họ và tên không được quá 100 ký tự";
    } else if (!/^[\p{L}\s]+$/u.test(trimmedFullName)) {
      errors.fullName = "Họ tên chỉ được phép chứa chữ cái và khoảng trắng";
    }
    const trimmedUsername = username.trim();
    const normalizedUsername = trimmedUsername.toLowerCase();
    const systemKeywords = ["admin", "support", "system", "root", "vibecart"];
    const containsSystemKeyword = systemKeywords.some(keyword => normalizedUsername.includes(keyword));

    if (!trimmedUsername) {
      errors.username = "Vui lòng nhập tên đăng nhập";
    } else if (trimmedUsername.length < 5) {
      errors.username = "Tên đăng nhập phải từ 5 đến 30 ký tự";
    } else if (trimmedUsername.length > 30) {
      errors.username = "Tên đăng nhập phải từ 5 đến 30 ký tự";
    } else if (!/^[a-zA-Z0-9._-]+$/.test(trimmedUsername)) {
      errors.username = "Tên đăng nhập chỉ chứa chữ cái, số, dấu chấm, dấu gạch dưới hoặc gạch ngang";
    } else if (containsSystemKeyword) {
      errors.username = "Tên đăng nhập không được phép chứa các từ khóa hệ thống (admin, support, system, root, vibecart)";
    }
    const trimmedEmail = email.trim();
    const normalizedEmail = trimmedEmail.toLowerCase();
    const disposableDomains = ["tempmail.com", "10minutemail.com", "yopmail.com", "mailinator.com", "guerrillamail.com", "throwaway.email"];
    const emailDomain = normalizedEmail.split("@")[1];

    if (!trimmedEmail) {
      errors.email = "Vui lòng nhập email";
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
      errors.email = "Email không đúng định dạng chuẩn";
    } else if (trimmedEmail.length > 100) {
      errors.email = "Email không được vượt quá 100 ký tự";
    } else if (emailDomain && disposableDomains.includes(emailDomain)) {
      errors.email = "Email rác/email tạm thời không được phép sử dụng đăng ký";
    }
    if (!password) {
      errors.password = "Vui lòng nhập mật khẩu";
    } else if (password.length < 8) {
      errors.password = "Mật khẩu phải từ 8 đến 100 ký tự";
    } else if (password.length > 100) {
      errors.password = "Mật khẩu không được vượt quá 100 ký tự";
    } else if (!/^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).*$/.test(password)) {
      errors.password = "Mật khẩu phải chứa ít nhất 1 chữ hoa, 1 chữ thường, 1 số và 1 ký tự đặc biệt (@#$%^&+=!)";
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!validateForm()) return;

    setIsLoading(true);

    try {
      await register({ username, email, password, fullName, role });
      toast.success("Đăng ký thành công", "Vui lòng kiểm tra email để lấy mã OTP xác thực.");
      router.push(`${ROUTES.VERIFY_OTP}?email=${encodeURIComponent(email)}`);
    } catch (err: unknown) {
      console.error("Registration error:", err);
      const apiErr = err as ApiErrorType;
      const errorMsg = apiErr.data?.message || apiErr.message || "Đăng ký thất bại. Tên đăng nhập hoặc Email có thể đã tồn tại.";
      setError(errorMsg);
      toast.error("Đăng ký thất bại", errorMsg);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="w-full max-w-sm">
      <div className="bg-white/80 backdrop-blur-xl border border-brand-100/40 rounded-2xl p-6 shadow-xl shadow-brand-500/5 transition-all duration-300">
        <div className="text-center mb-6">
          <h2 className="text-2xl font-extrabold tracking-tight text-brand-800">
            Đăng ký
          </h2>
          <p className="text-zinc-400 mt-1.5 text-sm">
            Tạo tài khoản mới để tiếp tục
          </p>
        </div>
        {error && (
          <div className="mb-5 p-3 bg-rose-50 border border-rose-200/60 rounded-xl flex items-start space-x-3 text-rose-600 text-sm leading-relaxed">
            <Info className="w-5 h-5 shrink-0 mt-0.5" />
            <span>{error}</span>
          </div>
        )}
        <form onSubmit={handleSubmit} className="space-y-3.5">
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-zinc-500 uppercase tracking-wider block">
              Bạn là?
            </label>
            <div className="grid grid-cols-2 gap-2 bg-brand-50/60 p-1 rounded-xl border border-brand-100/50">
              <button
                type="button"
                onClick={() => setRole("SHOPPER")}
                className={`py-2 text-xs font-semibold rounded-lg transition-all duration-200 cursor-pointer ${
                  role === "SHOPPER"
                    ? "bg-white text-brand-700 shadow-sm border border-brand-100/20"
                    : "text-zinc-500 hover:text-zinc-700"
                }`}
              >
                Shopper (Người mua)
              </button>
              <button
                type="button"
                onClick={() => setRole("CREATOR")}
                className={`py-2 text-xs font-semibold rounded-lg transition-all duration-200 cursor-pointer ${
                  role === "CREATOR"
                    ? "bg-white text-brand-700 shadow-sm border border-brand-100/20"
                    : "text-zinc-500 hover:text-zinc-700"
                }`}
              >
                Creator (Creator)
              </button>
            </div>
          </div>
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-zinc-500 uppercase tracking-wider block">
              Họ và tên
            </label>
            <div className="relative group">
              <span className="absolute inset-y-0 left-0 flex items-center pl-4 text-brand-300 group-focus-within:text-brand-500 transition-colors duration-200">
                <User className="w-5 h-5" />
              </span>
              <input
                type="text"
                required
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                placeholder="Nhập họ và tên của bạn"
                disabled={isLoading}
                className="w-full pl-11 pr-4 py-3 bg-brand-50/40 border border-brand-100/60 rounded-xl text-sm text-zinc-800 placeholder-zinc-400 focus:outline-none focus:ring-2 focus:ring-brand-200/50 focus:border-brand-300 focus:bg-white transition-all duration-200"
              />
            </div>
            {fieldErrors.fullName && (
              <p className="text-xs text-rose-500 mt-1 pl-1">{fieldErrors.fullName}</p>
            )}
          </div>
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-zinc-500 uppercase tracking-wider block">
              Tên đăng nhập
            </label>
            <div className="relative group">
              <span className="absolute inset-y-0 left-0 flex items-center pl-4 text-brand-300 group-focus-within:text-brand-500 transition-colors duration-200">
                <User className="w-5 h-5" />
              </span>
              <input
                type="text"
                required
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="Tên đăng nhập (tối thiểu 5 ký tự)"
                disabled={isLoading}
                className="w-full pl-11 pr-4 py-3 bg-brand-50/40 border border-brand-100/60 rounded-xl text-sm text-zinc-800 placeholder-zinc-400 focus:outline-none focus:ring-2 focus:ring-brand-200/50 focus:border-brand-300 focus:bg-white transition-all duration-200"
              />
            </div>
            {fieldErrors.username && (
              <p className="text-xs text-rose-500 mt-1 pl-1">{fieldErrors.username}</p>
            )}
          </div>
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
                placeholder="Nhập địa chỉ email"
                disabled={isLoading}
                className="w-full pl-11 pr-4 py-3 bg-brand-50/40 border border-brand-100/60 rounded-xl text-sm text-zinc-800 placeholder-zinc-400 focus:outline-none focus:ring-2 focus:ring-brand-200/50 focus:border-brand-300 focus:bg-white transition-all duration-200"
              />
            </div>
            {fieldErrors.email && (
              <p className="text-xs text-rose-500 mt-1 pl-1">{fieldErrors.email}</p>
            )}
          </div>
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-zinc-500 uppercase tracking-wider block">
              Mật khẩu
            </label>
            <div className="relative group">
              <span className="absolute inset-y-0 left-0 flex items-center pl-4 text-brand-300 group-focus-within:text-brand-500 transition-colors duration-200">
                <Lock className="w-5 h-5" />
              </span>
              <input
                type={showPassword ? "text" : "password"}
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Tối thiểu 8 ký tự (chữ hoa, số, ký tự đặc biệt)"
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
            {fieldErrors.password && (
              <p className="text-xs text-rose-500 mt-1 pl-1">{fieldErrors.password}</p>
            )}
          </div>
          <button
            type="submit"
            disabled={isLoading}
            className="w-full mt-1 py-3 bg-gradient-to-r from-brand-500 to-brand-400 hover:from-brand-600 hover:to-brand-500 text-white text-sm font-semibold rounded-xl flex items-center justify-center space-x-2 shadow-lg shadow-brand-500/20 hover:shadow-brand-500/30 active:scale-[0.98] transition-all duration-200 disabled:opacity-50 disabled:pointer-events-none cursor-pointer"
          >
            {isLoading ? (
              <Loader2 className="w-5 h-5 animate-spin" />
            ) : (
              <>
                <span>Đăng ký</span>
                <ArrowRight className="w-5 h-5" />
              </>
            )}
          </button>
        </form>
        <p className="mt-6 text-center text-sm text-zinc-400">
          Đã có tài khoản?{" "}
          <Link href={ROUTES.LOGIN} className="font-semibold text-brand-500 hover:text-brand-600 transition-colors">
            Đăng nhập ngay
          </Link>
        </p>
      </div>
    </div>
  );
}
