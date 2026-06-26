import React from "react";
import Link from "next/link";
import { Leaf } from "lucide-react";
import { ROUTES } from "@/constants/routes";

const InstagramIcon = () => (
  <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect width="20" height="20" x="2" y="2" rx="5" ry="5"/><path d="M16 11.37A4 4 0 1 1 12.63 8 4 4 0 0 1 16 11.37z"/><line x1="17.5" x2="17.51" y1="6.5" y2="6.5"/></svg>
);

const TwitterIcon = () => (
  <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M22 4s-.7 2.1-2 3.4c1.6 10-9.4 17.3-18 11.6 2.2.1 4.4-.6 6-2C3 15.5.5 9.6 3 5c2.2 2.6 5.6 4.1 9 4-.9-4.2 4-6.6 7-3.8 1.1 0 3-1.2 3-1.2z"/></svg>
);

const GithubIcon = () => (
  <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4"/><path d="M9 18c-4.51 2-5-2-7-2"/></svg>
);

export function Footer() {
  return (
    <footer
      className="relative w-full text-zinc-500 py-16 px-4 sm:px-6 lg:px-8 overflow-hidden"
      style={{
        background: `radial-gradient(ellipse 80% 70% at 50% 100%, #d1fae5 0%, #ecfdf5 25%, #ffffff 55%)`,
      }}
    >
      <div className="mx-auto max-w-6xl relative z-10">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-10">
          <div className="flex flex-col gap-4">
            <Link href={ROUTES.HOME} className="flex items-center gap-2.5 group">
              <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-brand-400 to-brand-600 shadow-md shadow-brand-500/20">
                <Leaf className="h-4 w-4 text-white" />
              </div>
              <span className="text-lg font-extrabold text-zinc-800 tracking-tight">
                VibeCart
              </span>
            </Link>
            <p className="text-sm text-zinc-500 leading-relaxed max-w-xs">
              Nền tảng mua sắm kết hợp mạng xã hội thế hệ mới. Khám phá, kết nối và mua sắm sản phẩm bạn yêu thích.
            </p>
            <div className="flex gap-2 mt-1">
              {[InstagramIcon, TwitterIcon, GithubIcon].map((Icon, i) => (
                <a
                  key={i}
                  href="#"
                  className="p-2 rounded-xl bg-white/60 border border-brand-100/50 text-brand-500 hover:bg-brand-50 hover:text-brand-600 hover:border-brand-200/60 transition-all duration-300"
                >
                  <Icon />
                </a>
              ))}
            </div>
          </div>
          <div>
            <h3 className="text-xs font-bold uppercase tracking-[0.15em] text-zinc-800 mb-4">Cửa hàng</h3>
            <ul className="flex flex-col gap-2.5 text-sm">
              <li><Link href={ROUTES.PRODUCTS} className="hover:text-brand-600 transition-colors">Sản phẩm nổi bật</Link></li>
              <li><Link href={ROUTES.PRODUCTS} className="hover:text-brand-600 transition-colors">Khuyến mãi Flash Sale</Link></li>
              <li><Link href={ROUTES.PRODUCTS} className="hover:text-brand-600 transition-colors">Hàng mới về</Link></li>
              <li><Link href={ROUTES.PRODUCTS} className="hover:text-brand-600 transition-colors">Danh mục sản phẩm</Link></li>
            </ul>
          </div>
          <div>
            <h3 className="text-xs font-bold uppercase tracking-[0.15em] text-zinc-800 mb-4">Creators</h3>
            <ul className="flex flex-col gap-2.5 text-sm">
              <li><Link href={ROUTES.FEED} className="hover:text-brand-600 transition-colors">Khám phá Creator</Link></li>
              <li><Link href={ROUTES.CREATOR_DASHBOARD} className="hover:text-brand-600 transition-colors">Kênh Creator</Link></li>
              <li><Link href={ROUTES.AFFILIATE_LINKS} className="hover:text-brand-600 transition-colors">Báo cáo tiếp thị</Link></li>
              <li><Link href={ROUTES.REGISTER} className="hover:text-brand-600 transition-colors">Trở thành Creator</Link></li>
            </ul>
          </div>
          <div>
            <h3 className="text-xs font-bold uppercase tracking-[0.15em] text-zinc-700 mb-4">Công nghệ</h3>
            <ul className="flex flex-col gap-2.5 text-sm text-zinc-400">
              <li>Next.js + Spring Boot</li>
              <li>Docker & Kafka</li>
              <li>Elasticsearch v7</li>
              <li>WebSockets & Redis</li>
            </ul>
          </div>
        </div>

        <div className="border-t border-brand-100/50 mt-14 pt-6 flex flex-col sm:flex-row items-center justify-between text-xs text-zinc-400 gap-4">
          <p>© {new Date().getFullYear()} VibeCart. Tất cả các quyền được bảo lưu.</p>
          <div className="flex gap-6">
            <a href="#" className="hover:text-brand-600 transition-colors">Chính sách bảo mật</a>
            <a href="#" className="hover:text-brand-600 transition-colors">Điều khoản dịch vụ</a>
          </div>
        </div>
      </div>
    </footer>
  );
}
