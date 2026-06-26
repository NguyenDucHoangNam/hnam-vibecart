"use client";

import React, { useEffect } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { 
  ShieldAlert, 
  ShoppingBag, 
  FolderTree, 
  Users, 
  Activity, 
  ArrowRight,
  Loader2,
  Clock,
  LayoutDashboard
} from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useToast } from "@/context/ToastContext";
import { Forbidden } from "@/components/common/Forbidden";
import { ROUTES } from "@/constants/routes";

export default function AdminDashboardHub() {
  const { user, isAuthenticated, isLoading: isAuthLoading } = useAuth();
  const toast = useToast();
  const router = useRouter();
  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      toast.warning("Yêu cầu đăng nhập", "Vui lòng đăng nhập để truy cập trang quản trị.");
      router.push(ROUTES.LOGIN);
    }
  }, [isAuthenticated, isAuthLoading, router, toast]);
  if (isAuthLoading) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center min-h-[70vh] bg-zinc-50 transition-colors duration-300">
        <Loader2 className="h-10 w-10 text-brand-500 animate-spin mb-4" />
        <p className="text-sm text-zinc-500 font-light">Đang xác thực quyền truy cập...</p>
      </div>
    );
  }
  if (!isAuthenticated || !user?.roles?.includes("ROLE_ADMIN")) {
    return <Forbidden />;
  }

  const adminModules = [
    {
      title: "Quản lý Sản phẩm",
      desc: "Quản lý danh mục SPU & SKU sản phẩm, điều chỉnh giá bán, giá khuyến mãi và cập nhật tồn kho an toàn.",
      icon: ShoppingBag,
      href: ROUTES.ADMIN_PRODUCTS,
      color: "text-blue-600 bg-blue-50 border-blue-100 ",
      hoverColor: "hover:border-blue-300 hover:shadow-blue-500/5 "
    },
    {
      title: "Quản trị Cây danh mục",
      desc: "Tổ chức cấu trúc phân cấp danh mục ba cấp cho sản phẩm. Quản lý liên kết cha-con và auto-slug tiếng Việt.",
      icon: FolderTree,
      href: ROUTES.ADMIN_CATEGORIES,
      color: "text-brand-600 bg-brand-50 border-brand-100/65 ",
      hoverColor: "hover:border-brand-300 hover:shadow-brand-500/5 "
    },
    {
      title: "Phân quyền Người dùng",
      desc: "Quản lý thông tin thành viên, phân quyền hạn vai trò (Admin, Creator, User) và khóa/mở khóa tài khoản.",
      icon: Users,
      href: ROUTES.ADMIN_USERS,
      color: "text-purple-600 bg-purple-50 border-purple-100 ",
      hoverColor: "hover:border-purple-300 hover:shadow-purple-500/5 "
    }
  ];

  return (
    <div className="flex-1 flex flex-col bg-zinc-50 px-6 py-10 transition-colors duration-300 relative min-h-screen">
      <div className="absolute top-[8%] right-[8%] w-80 h-80 bg-brand-100/10 rounded-full blur-[100px] pointer-events-none" />
      <div className="absolute bottom-[10%] left-[8%] w-96 h-96 bg-brand-200/10 rounded-full blur-[120px] pointer-events-none" />

      <div className="max-w-5xl w-full mx-auto relative z-10 flex-1 flex flex-col space-y-8">
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-zinc-200/60 pb-6">
          <div>
            <div className="flex items-center gap-3">
              <div className="p-2 bg-rose-50 rounded-2xl border border-rose-100 shadow-2xs">
                <ShieldAlert className="h-6 w-6 text-rose-600 " />
              </div>
              <h1 className="text-3xl font-extrabold text-zinc-955 tracking-tight">Trung tâm Quản trị (Admin Portal)</h1>
            </div>
            <p className="text-zinc-555 text-sm mt-1.5 leading-relaxed">
              Hệ thống quản lý cốt lõi của VibeCart E-Commerce. Lựa chọn một phân hệ bên dưới để bắt đầu thực thi nghiệp vụ.
            </p>
          </div>

          <div className="inline-flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-xs font-bold bg-emerald-50 text-emerald-700 border border-emerald-200 shadow-2xs self-start md:self-center">
            <Activity className="h-3.5 w-3.5 text-emerald-500 animate-pulse" />
            Hệ thống Hoạt động Ổn định
          </div>
        </div>
        <div className="bg-white border border-zinc-200/60 rounded-3xl p-5 shadow-sm grid grid-cols-1 sm:grid-cols-3 gap-5">
          <div className="flex items-center gap-3.5">
            <div className="p-3 bg-zinc-50 border border-zinc-100 rounded-2xl">
              <ShieldAlert className="h-5.5 w-5.5 text-zinc-500 " />
            </div>
            <div>
              <span className="text-[10px] font-bold text-zinc-400 uppercase tracking-wider block">Tài khoản quản trị</span>
              <span className="text-sm font-bold text-zinc-800 truncate block max-w-[200px]">{user?.fullName || user?.username}</span>
            </div>
          </div>

          <div className="flex items-center gap-3.5">
            <div className="p-3 bg-zinc-50 border border-zinc-100 rounded-2xl">
              <Clock className="h-5.5 w-5.5 text-zinc-500 " />
            </div>
            <div>
              <span className="text-[10px] font-bold text-zinc-400 uppercase tracking-wider block">Thời gian phiên</span>
              <span className="text-sm font-bold text-zinc-800 ">Hoạt động bình thường</span>
            </div>
          </div>

          <div className="flex items-center gap-3.5">
            <div className="p-3 bg-zinc-50 border border-zinc-100 rounded-2xl">
              <LayoutDashboard className="h-5.5 w-5.5 text-zinc-500 " />
            </div>
            <div>
              <span className="text-[10px] font-bold text-zinc-400 uppercase tracking-wider block">Phiên bản Backend</span>
              <span className="text-sm font-bold text-zinc-800 ">v1.0.0 Stable (Spring Boot)</span>
            </div>
          </div>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {adminModules.map((module) => {
            const IconComponent = module.icon;
            return (
              <Link 
                key={module.title}
                href={module.href}
                className={`bg-white border border-zinc-200/70 rounded-[2rem] p-6.5 shadow-sm hover:shadow-xl hover:-translate-y-1 transition-all duration-300 group ${module.hoverColor} flex flex-col justify-between min-h-[250px]`}
              >
                <div className="space-y-4">
                  <div className={`p-3.5 rounded-2xl border w-fit ${module.color}`}>
                    <IconComponent className="h-6 w-6" />
                  </div>
                  <div className="space-y-1.5">
                    <h3 className="text-lg font-extrabold text-zinc-900 group-hover:text-brand-600 transition-colors">{module.title}</h3>
                    <p className="text-xs text-zinc-400 font-light leading-relaxed">{module.desc}</p>
                  </div>
                </div>
                <div className="flex items-center gap-1 text-xs font-bold text-zinc-700 mt-4 group-hover:text-brand-600 transition-colors">
                  <span>Truy cập quản trị</span>
                  <ArrowRight className="h-3.5 w-3.5 group-hover:translate-x-1 transition-transform" />
                </div>
              </Link>
            );
          })}
        </div>

      </div>
    </div>
  );
}
