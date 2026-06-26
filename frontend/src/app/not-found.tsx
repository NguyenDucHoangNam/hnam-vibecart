"use client";

import Link from "next/link";
import { FileQuestion, ArrowLeft, Home } from "lucide-react";
import { ROUTES } from "@/constants/routes";

export default function NotFound() {
  return (
    <div className="flex-1 flex items-center justify-center min-h-[75vh] px-6 py-12 bg-zinc-50 dark:bg-zinc-950 transition-colors duration-300 relative overflow-hidden">

      <div className="absolute top-[25%] left-[15%] w-72 h-72 bg-brand-200/20 dark:bg-brand-900/10 rounded-full blur-[100px] pointer-events-none" />
      <div className="absolute bottom-[20%] right-[15%] w-80 h-80 bg-zinc-200/30 dark:bg-zinc-800/10 rounded-full blur-[120px] pointer-events-none" />

      <div className="relative z-10 w-full max-w-md bg-white dark:bg-zinc-900/80 backdrop-blur-md rounded-[2.5rem] border border-zinc-200/80 dark:border-zinc-800/80 p-8 sm:p-10 shadow-2xl shadow-zinc-200/50 dark:shadow-none text-center">


        <div className="inline-flex h-24 w-24 items-center justify-center rounded-full bg-brand-50 dark:bg-brand-950/50 text-brand-500 dark:text-brand-400 mb-6 ring-8 ring-brand-100/50 dark:ring-brand-950/20 animate-pulse">
          <FileQuestion className="h-12 w-12" />
        </div>


        <h1 className="text-4xl font-black tracking-tight text-zinc-900 dark:text-white mb-2">
          404
        </h1>

        <h2 className="text-xl font-bold text-zinc-800 dark:text-zinc-200 mb-4">
          Không tìm thấy trang
        </h2>


        <p className="text-sm text-zinc-500 dark:text-zinc-400 leading-relaxed mb-8 font-light max-w-xs mx-auto">
          Đường dẫn bạn truy cập không tồn tại, đã bị xóa hoặc đã chuyển sang một địa chỉ khác.
        </p>


        <div className="flex flex-col gap-3">
          <Link
            href={ROUTES.HOME}
            className="flex h-12 w-full items-center justify-center gap-2 rounded-full bg-brand-500 hover:bg-brand-600 text-sm font-semibold text-white shadow-lg shadow-brand-500/20 hover:shadow-brand-600/25 active:scale-[0.98] transition-all duration-200"
          >
            <Home className="h-4 w-4" />
            Về Trang chủ
          </Link>

          <button
            onClick={() => window.history.back()}
            className="flex h-12 w-full items-center justify-center gap-2 rounded-full bg-white dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 hover:bg-zinc-50 dark:hover:bg-zinc-700/80 text-sm font-semibold text-zinc-700 dark:text-zinc-200 hover:border-zinc-300 dark:hover:border-zinc-600 active:scale-[0.98] transition-all duration-200"
          >
            <ArrowLeft className="h-4 w-4" />
            Quay lại trang trước
          </button>
        </div>
      </div>
    </div>
  );
}
