"use client";

import Link from "next/link";
import { ShieldAlert, ArrowLeft, LogIn } from "lucide-react";
import { ROUTES } from "@/constants/routes";
import { useAuth } from "@/context/AuthContext";

export function Forbidden() {
  const { logout } = useAuth();

  return (
    <div className="flex-1 flex items-center justify-center min-h-[70vh] px-6 py-12 bg-zinc-50 dark:bg-zinc-950 transition-colors duration-300">
      <div className="absolute top-[30%] left-[20%] w-64 h-64 bg-red-200/20 dark:bg-red-900/10 rounded-full blur-[100px] pointer-events-none" />
      <div className="absolute bottom-[20%] right-[20%] w-80 h-80 bg-brand-200/20 dark:bg-brand-900/10 rounded-full blur-[120px] pointer-events-none" />

      <div className="relative z-10 w-full max-w-md bg-white dark:bg-zinc-900/80 backdrop-blur-md rounded-[2rem] border border-zinc-200/80 dark:border-zinc-800/80 p-8 sm:p-10 shadow-2xl shadow-zinc-200/50 dark:shadow-none text-center">
        <div className="inline-flex h-20 w-20 items-center justify-center rounded-full bg-red-50 dark:bg-red-950/50 text-red-500 dark:text-red-400 mb-6 ring-8 ring-red-100/50 dark:ring-red-950/20 animate-bounce">
          <ShieldAlert className="h-10 w-10" />
        </div>
        <h1 className="text-3xl font-extrabold tracking-tight text-zinc-900 dark:text-white mb-3">
          403 Access Denied
        </h1>
        
        <h2 className="text-lg font-semibold text-zinc-700 dark:text-zinc-300 mb-4">
          Quyền truy cập bị từ chối
        </h2>
        <p className="text-sm text-zinc-500 dark:text-zinc-400 leading-relaxed mb-8 font-light">
          Tài khoản của bạn không có quyền hạn **Quản trị viên (Admin)** để truy cập trang này. Vui lòng quay lại trang chủ hoặc đăng nhập bằng tài khoản được cấp quyền.
        </p>
        <div className="flex flex-col gap-3">
          <Link
            href={ROUTES.HOME}
            className="flex h-12 w-full items-center justify-center gap-2 rounded-full bg-brand-500 hover:bg-brand-600 text-sm font-semibold text-white shadow-lg shadow-brand-500/20 hover:shadow-brand-600/25 active:scale-[0.98] transition-all duration-200"
          >
            <ArrowLeft className="h-4 w-4" />
            Quay lại Trang chủ
          </Link>
          
          <button
            onClick={() => logout()}
            className="flex h-12 w-full items-center justify-center gap-2 rounded-full bg-white dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 hover:bg-zinc-50 dark:hover:bg-zinc-700/80 text-sm font-semibold text-zinc-700 dark:text-zinc-200 hover:border-zinc-300 dark:hover:border-zinc-600 active:scale-[0.98] transition-all duration-200"
          >
            <LogIn className="h-4 w-4" />
            Đăng nhập Tài khoản khác
          </button>
        </div>
      </div>
    </div>
  );
}
