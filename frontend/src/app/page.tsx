"use client";

import Link from "next/link";
import { 
  Leaf, 
  MessageSquare, 
  TrendingUp, 
  Zap, 
  ShieldCheck, 
  ArrowRight,
  Compass
} from "lucide-react";
import { ROUTES } from "@/constants/routes";

export default function Home() {
  return (
    <div className="flex flex-col w-full overflow-x-hidden">
      <section
        className="relative w-full min-h-[85vh] flex items-center justify-center overflow-hidden"
        style={{
          background: `
            radial-gradient(
              ellipse 70% 60% at 50% 50%,
              #a7f3d0 0%,
              #d1fae5 25%,
              #ecfdf5 45%,
              #ffffff 70%
            )
          `,
        }}
      >
        <div className="absolute top-[15%] left-[10%] w-72 h-72 bg-brand-200/40 rounded-full blur-[100px] pointer-events-none animate-pulse" />
        <div className="absolute bottom-[10%] right-[12%] w-96 h-96 bg-brand-100/50 rounded-full blur-[120px] pointer-events-none" />
        <div className="absolute top-[40%] right-[25%] w-48 h-48 bg-brand-300/20 rounded-full blur-[80px] pointer-events-none" />

        <div className="relative z-10 mx-auto max-w-3xl px-6 text-center flex flex-col items-center">
          <div className="inline-flex items-center gap-1.5 rounded-full bg-white/70 backdrop-blur-sm border border-brand-200/60 px-4 py-1.5 text-xs font-medium text-brand-700 mb-8 shadow-sm">
            <Leaf className="h-3.5 w-3.5 text-brand-500" />
            Nền tảng mua sắm xã hội thế hệ mới
          </div>
          <h1 className="text-5xl sm:text-6xl lg:text-7xl font-extrabold tracking-tight text-zinc-900 leading-[1.1] mb-6">
            <span className="bg-gradient-to-r from-brand-700 via-brand-500 to-brand-400 bg-clip-text text-transparent">
              VibeCart
            </span>
          </h1>

          <p className="text-lg sm:text-xl text-zinc-600 leading-relaxed max-w-xl mb-10 font-light">
            Khám phá sản phẩm. Kết nối Creator.
            <br className="hidden sm:block" />
            Mua sắm thông minh cùng cộng đồng.
          </p>
          <div className="flex flex-col sm:flex-row gap-4 w-full sm:w-auto justify-center">
            <Link 
              href={ROUTES.PRODUCTS}
              className="flex h-13 items-center justify-center gap-2.5 rounded-full bg-brand-500 px-9 text-sm font-semibold text-white shadow-xl shadow-brand-500/20 hover:bg-brand-600 hover:shadow-brand-600/25 hover:scale-[1.03] active:scale-[0.97] transition-all duration-200"
            >
              Khám phá ngay
              <ArrowRight className="h-4 w-4" />
            </Link>
            <Link 
              href={ROUTES.REGISTER}
              className="flex h-13 items-center justify-center gap-2 rounded-full bg-white/80 backdrop-blur-sm border border-brand-200/80 px-9 text-sm font-semibold text-zinc-700 hover:bg-white hover:border-brand-300 hover:scale-[1.03] active:scale-[0.97] shadow-lg shadow-brand-500/5 transition-all duration-300"
            >
              Tạo tài khoản
            </Link>
          </div>
        </div>
      </section>
      <section className="w-full bg-white py-16">
        <div className="mx-auto max-w-5xl px-6">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
            {[
              { value: "100k+", label: "Giao dịch" },
              { value: "12k+", label: "Creator" },
              { value: "<20ms", label: "Phản hồi" },
              { value: "100%", label: "An toàn" },
            ].map((stat) => (
              <div
                key={stat.label}
                className="flex flex-col items-center py-6 px-4 rounded-2xl bg-brand-50/60 border border-brand-100/50 hover:bg-brand-50 hover:border-brand-200/60 transition-all duration-300"
              >
                <span className="text-2xl sm:text-3xl font-extrabold text-brand-600 tracking-tight">
                  {stat.value}
                </span>
                <span className="text-xs text-zinc-500 uppercase tracking-widest mt-1.5 font-medium">
                  {stat.label}
                </span>
              </div>
            ))}
          </div>
        </div>
      </section>
      <section
        className="relative w-full py-24"
        style={{
          background: `
            radial-gradient(
              ellipse 80% 50% at 50% 100%,
              #ecfdf5 0%,
              #ffffff 60%
            )
          `,
        }}
      >
        <div className="mx-auto max-w-6xl px-6">
          <div className="text-center max-w-2xl mx-auto mb-16">
            <p className="text-xs font-semibold text-brand-500 uppercase tracking-[0.2em] mb-3">
              Tính năng
            </p>
            <h2 className="text-3xl sm:text-4xl font-extrabold text-zinc-900 leading-tight">
              Mọi thứ bạn cần,
              <br />
              <span className="text-brand-600">trong một nền tảng</span>
            </h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {[
              {
                icon: Compass,
                title: "Bảng tin xã hội",
                desc: "Theo dõi bài viết từ Creator, thích, bình luận và mua trực tiếp sản phẩm được gắn thẻ.",
              },
              {
                icon: Zap,
                title: "Flash Sale tốc độ",
                desc: "Giỏ hàng Redis + khóa phân tán ngăn lỗi bán âm kho khi hàng ngàn người mua cùng lúc.",
              },
              {
                icon: TrendingUp,
                title: "Tiếp thị liên kết",
                desc: "Link rút gọn Base62, theo dõi lượt click và tổng hợp báo cáo hoa hồng chuẩn xác.",
              },
              {
                icon: MessageSquare,
                title: "Chat thời gian thực",
                desc: "Chat 1-1 qua WebSockets, lưu trữ MongoDB, đồng bộ đa máy chủ tức thì.",
              },
              {
                icon: Leaf,
                title: "Tìm kiếm thông minh",
                desc: "Toàn văn + Fuzzy Search tự sửa lỗi chính tả bằng Elasticsearch.",
              },
              {
                icon: ShieldCheck,
                title: "Bảo mật & Hàng đợi",
                desc: "Rate Limiting (Bucket4j/Redis) + xử lý nền gửi email, hóa đơn qua Kafka.",
              },
            ].map((feature) => {
              const Icon = feature.icon;
              return (
                <div
                  key={feature.title}
                  className="group relative rounded-2xl bg-white/80 backdrop-blur-sm border border-brand-100/70 p-7 hover:bg-white hover:border-brand-200 hover:shadow-lg hover:shadow-brand-500/5 hover:-translate-y-0.5 transition-all duration-300"
                >
                  <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-brand-50 text-brand-500 mb-5 group-hover:bg-brand-100 group-hover:scale-110 transition-all duration-300">
                    <Icon className="h-5 w-5" />
                  </div>
                  <h4 className="text-base font-bold text-zinc-900 mb-2">
                    {feature.title}
                  </h4>
                  <p className="text-sm text-zinc-500 leading-relaxed">
                    {feature.desc}
                  </p>
                </div>
              );
            })}
          </div>
        </div>
      </section>
      <section className="w-full bg-white py-20">
        <div className="mx-auto max-w-3xl px-6">
          <div
            className="relative rounded-[2rem] p-10 sm:p-14 text-center overflow-hidden"
            style={{
              background: `
                radial-gradient(
                  ellipse 60% 80% at 50% 50%,
                  #a7f3d0 0%,
                  #d1fae5 30%,
                  #ecfdf5 60%,
                  #ffffff 100%
                )
              `,
            }}
          >
            <h3 className="text-2xl sm:text-3xl font-extrabold text-zinc-900 mb-4 relative z-10">
              Bắt đầu hành trình
              <br />
              <span className="text-brand-600">cùng VibeCart</span>
            </h3>
            <p className="text-sm sm:text-base text-zinc-600 leading-relaxed mb-8 max-w-md mx-auto relative z-10">
              Tạo tài khoản miễn phí để mua sắm hoặc chia sẻ liên kết sản phẩm và nhận hoa hồng.
            </p>
            <Link 
              href={ROUTES.REGISTER}
              className="relative z-10 inline-flex h-12 items-center justify-center rounded-full bg-brand-500 px-8 text-sm font-semibold text-white shadow-xl shadow-brand-500/20 hover:bg-brand-600 hover:scale-[1.03] active:scale-[0.97] transition-all duration-200"
            >
              Đăng ký miễn phí
              <ArrowRight className="h-4 w-4 ml-2" />
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
}
