"use client";

import React, { useState, useEffect, useRef, useCallback } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  Leaf,
  ShoppingBag,
  Compass,
  MessageSquare,
  Bell,
  ChevronDown,
  LogOut,
  User as UserIcon,
  ShieldAlert,
  LayoutDashboard,
  Menu,
  X,
  MoreHorizontal,
  Check,
  CheckCheck,
  Trash2,
  Settings
} from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useCart } from "@/hooks/useCart";
import { ROUTES } from "@/constants/routes";
import { useChat } from "@/context/ChatContext";
import { useNotification } from "@/context/NotificationContext";
import { NotificationResponse } from "@/types/notification";

export function Header() {
  const { user, isAuthenticated, logout } = useAuth();
  const { totalQuantity } = useCart();
  const { globalUnreadCount } = useChat();
  const {
    notifications,
    unreadCount: notifUnreadCount,
    recentNotifications,
    earlierNotifications,
    activeTab: notifActiveTab,
    hasMore: notifHasMore,
    isLoading: notifIsLoading,
    setActiveTab: setNotifActiveTab,
    loadMore: loadMoreNotifications,
    markAsRead: markNotifAsRead,
    markAllAsRead: markAllNotifsAsRead,
    deleteNotification: deleteNotif,
    deleteAllNotifications: deleteAllNotifs,
  } = useNotification();
  const pathname = usePathname();
  const router = useRouter();
  const [isProfileDropdownOpen, setIsProfileDropdownOpen] = useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [isNotifDropdownOpen, setIsNotifDropdownOpen] = useState(false);
  const [notifMenuOpen, setNotifMenuOpen] = useState(false);
  const [itemMenuId, setItemMenuId] = useState<string | null>(null);

  const dropdownRef = useRef<HTMLDivElement>(null);
  const mobileMenuRef = useRef<HTMLDivElement>(null);
  const notifDropdownRef = useRef<HTMLDivElement>(null);
  const notifScrollRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsProfileDropdownOpen(false);
      }
      if (notifDropdownRef.current && !notifDropdownRef.current.contains(event.target as Node)) {
        setIsNotifDropdownOpen(false);
        setNotifMenuOpen(false);
        setItemMenuId(null);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, []);
  useEffect(() => {
    setIsMobileMenuOpen(false);
    setIsNotifDropdownOpen(false);
  }, [pathname]);

  const handleNotifScroll = useCallback(() => {
    const el = notifScrollRef.current;
    if (!el || notifIsLoading || !notifHasMore) return;
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 40) {
      loadMoreNotifications();
    }
  }, [notifIsLoading, notifHasMore, loadMoreNotifications]);

  const formatTimeAgo = (dateStr: string) => {
    const diff = Date.now() - new Date(dateStr).getTime();
    const minutes = Math.floor(diff / 60000);
    if (minutes < 1) return "Vừa xong";
    if (minutes < 60) return `${minutes} phút trước`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours} giờ trước`;
    const days = Math.floor(hours / 24);
    if (days < 30) return `${days} ngày trước`;
    return `${Math.floor(days / 30)} tháng trước`;
  };

  const renderNotificationItem = (n: NotificationResponse) => (
    <div
      key={n.id}
      className={`group flex items-start gap-3 px-3 py-2.5 rounded-xl cursor-pointer transition-all duration-200 relative ${
        !n.read ? "bg-brand-50/60 hover:bg-brand-50" : "hover:bg-zinc-50"
      }`}
      onClick={() => {
        if (!n.read) markNotifAsRead(n.id);
        if (n.type === "FOLLOW" && n.actor?.id) {
          router.push(ROUTES.CREATOR_PROFILE(n.actor.id));
          setIsNotifDropdownOpen(false);
        }
      }}
    >
      <div className="relative h-10 w-10 rounded-full bg-zinc-100 shrink-0 overflow-hidden">
        {n.actor?.avatarUrl ? (
          <img src={n.actor.avatarUrl} alt="" className="h-full w-full object-cover" />
        ) : (
          <div className="h-full w-full flex items-center justify-center bg-gradient-to-br from-brand-400 to-brand-500 text-white font-bold text-xs">
            {n.actor?.fullName?.[0]?.toUpperCase() || "?"}
          </div>
        )}
      </div>
      <div className="flex-1 min-w-0">
        <p className={`text-xs leading-relaxed ${!n.read ? "font-semibold text-zinc-800" : "text-zinc-600"}`}>
          {n.content}
        </p>
        <p className={`text-[11px] mt-0.5 ${!n.read ? "text-brand-500 font-semibold" : "text-zinc-400"}`}>
          {formatTimeAgo(n.createdAt)}
        </p>
      </div>
      <div className="flex items-center gap-1 shrink-0">
        <div className="relative">
          <button
            onClick={(e) => {
              e.stopPropagation();
              setItemMenuId(itemMenuId === n.id ? null : n.id);
            }}
            className="h-7 w-7 rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 hover:bg-zinc-200 transition-all"
          >
            <MoreHorizontal className="h-4 w-4 text-zinc-500" />
          </button>
          {itemMenuId === n.id && (
            <div className="absolute right-0 top-8 z-50 w-48 bg-white rounded-xl border border-zinc-100 shadow-lg py-1.5 animate-in fade-in slide-in-from-top-2 duration-150">
              {!n.read && (
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    markNotifAsRead(n.id);
                    setItemMenuId(null);
                  }}
                  className="flex items-center gap-2 w-full px-3 py-2 text-xs text-zinc-700 hover:bg-zinc-50"
                >
                  <Check className="h-3.5 w-3.5" /> Đánh dấu đã đọc
                </button>
              )}
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  deleteNotif(n.id);
                  setItemMenuId(null);
                }}
                className="flex items-center gap-2 w-full px-3 py-2 text-xs text-red-600 hover:bg-red-50"
              >
                <Trash2 className="h-3.5 w-3.5" /> Xóa thông báo
              </button>
            </div>
          )}
        </div>
        {!n.read && (
          <div className="h-2.5 w-2.5 rounded-full bg-brand-500 shrink-0" />
        )}
      </div>
    </div>
  );

  const handleLogout = async () => {
    setIsProfileDropdownOpen(false);
    await logout();
  };

  const isCreator = user?.roles?.includes("ROLE_CREATOR");
  const isAdmin = user?.roles?.includes("ROLE_ADMIN");
  const getAvatarBorderClass = () => {
    if (isAdmin) {
      return "border-2 border-rose-500 ring-2 ring-rose-100 shadow-sm shadow-rose-500/20";
    }
    if (isCreator) {
      return "border-2 border-emerald-500 ring-2 ring-emerald-100 shadow-sm shadow-emerald-500/20";
    }
    return "border-2 border-blue-500 ring-2 ring-blue-100 shadow-sm shadow-blue-500/20";
  };
  const getFallbackGradientClass = () => {
    if (isAdmin) {
      return "from-rose-400 to-rose-500";
    }
    if (isCreator) {
      return "from-emerald-400 to-emerald-500";
    }
    return "from-blue-400 to-blue-500";
  };
  const getUserInitials = () => {
    if (!user?.fullName) return "U";
    return user.fullName
      .split(" ")
      .map((name) => name[0])
      .join("")
      .substring(0, 2)
      .toUpperCase();
  };
  const navLinks = [
    { label: "Cửa hàng", href: ROUTES.PRODUCTS, icon: ShoppingBag },
    { label: "Bảng tin", href: ROUTES.FEED, icon: Compass },
    ...(isAuthenticated && isCreator && user?.id
      ? [{ label: "Trang cá nhân", href: ROUTES.CREATOR_PROFILE(user.id), icon: UserIcon }]
      : []),
  ];

  return (
    <header className="sticky top-0 z-50 w-full border-b border-brand-100 bg-white h-20 transition-all duration-300 shadow-sm shadow-brand-50/20">
      <div className="mx-auto flex h-full max-w-6xl items-center justify-between px-4 sm:px-6 lg:px-8">
        <Link href={ROUTES.HOME} className="flex items-center gap-2.5 group shrink-0">
          <div className="flex h-11 w-11 items-center justify-center rounded-2xl border border-zinc-100 bg-white p-1.5 shadow-sm transition-all duration-300 group-hover:scale-105">
            <img src="/logo.png" alt="VibeCart Logo" className="h-full w-full object-contain" />
          </div>
          <span className="text-2xl font-extrabold text-brand-500 tracking-tight transition-colors duration-300">
            VibeCart
          </span>
        </Link>
        <nav className="hidden md:flex items-center gap-8">
          {navLinks.map((link) => {
            const LinkIcon = link.icon;
            const isActive = pathname === link.href || (link.href !== "/" && pathname.startsWith(link.href));
            return (
              <Link
                key={link.href}
                href={link.href}
                className={`flex items-center gap-2 py-2 text-[15px] font-semibold transition-all duration-200 ${isActive
                    ? "text-brand-600 font-bold"
                    : "text-zinc-700 hover:text-brand-600"
                  }`}
              >
                <LinkIcon className="h-5 w-5" />
                {link.label}
              </Link>
            );
          })}
        </nav>
        <div className="flex items-center gap-5">
          <Link
            href={ROUTES.CART}
            className="relative flex h-11 w-11 items-center justify-center rounded-full text-zinc-700 hover:bg-zinc-50 transition-all duration-200"
          >
            <ShoppingBag className="h-5.5 w-5.5" />
            {totalQuantity > 0 && (
              <span className="absolute top-1 right-1 min-w-[17px] h-[17px] px-1 rounded-full bg-brand-500 text-[9px] font-bold text-white flex items-center justify-center shadow-sm">
                {totalQuantity > 99 ? "99+" : totalQuantity}
              </span>
            )}
          </Link>
          {isAuthenticated && (
            <Link
              href={ROUTES.MESSAGES}
              className="relative flex h-11 w-11 items-center justify-center rounded-full text-zinc-700 hover:bg-zinc-50 transition-all duration-200"
            >
              <MessageSquare className="h-5.5 w-5.5" />
              {globalUnreadCount > 0 && (
                <span className="absolute top-1 right-1 min-w-[17px] h-[17px] px-1 rounded-full bg-brand-500 text-[9px] font-bold text-white flex items-center justify-center shadow-sm">
                  {globalUnreadCount > 99 ? "99+" : globalUnreadCount}
                </span>
              )}
            </Link>
          )}
          {isAuthenticated && (
            <div className="relative" ref={notifDropdownRef}>
              <button
                onClick={() => {
                  setIsNotifDropdownOpen(!isNotifDropdownOpen);
                  setIsProfileDropdownOpen(false);
                }}
                className="relative flex h-11 w-11 items-center justify-center rounded-full text-zinc-700 hover:bg-zinc-50 transition-all duration-200"
              >
                <Bell className="h-5.5 w-5.5" />
                {notifUnreadCount > 0 && (
                  <span className="absolute top-1 right-1 min-w-[17px] h-[17px] px-1 rounded-full bg-brand-500 text-[9px] font-bold text-white flex items-center justify-center shadow-sm">
                    {notifUnreadCount > 99 ? "99+" : notifUnreadCount}
                  </span>
                )}
              </button>
              {isNotifDropdownOpen && (
                <div className="absolute right-0 mt-2.5 w-[380px] rounded-2xl border border-zinc-100 bg-white shadow-xl shadow-zinc-200/50 animate-in fade-in slide-in-from-top-3 duration-250 z-50">
                  <div className="flex items-center justify-between px-4 pt-4 pb-2">
                    <h3 className="text-lg font-bold text-zinc-800">Thông báo</h3>
                    <div className="relative">
                      <button
                        onClick={() => setNotifMenuOpen(!notifMenuOpen)}
                        className="h-8 w-8 rounded-full flex items-center justify-center hover:bg-zinc-100 transition-colors"
                      >
                        <MoreHorizontal className="h-5 w-5 text-zinc-500" />
                      </button>
                      {notifMenuOpen && (
                        <div className="absolute right-0 top-9 w-56 bg-white rounded-xl border border-zinc-100 shadow-lg py-1.5 z-50 animate-in fade-in duration-150">
                          <button
                            onClick={() => { markAllNotifsAsRead(); setNotifMenuOpen(false); }}
                            className="flex items-center gap-2 w-full px-3 py-2 text-xs font-medium text-zinc-700 hover:bg-zinc-50"
                          >
                            <CheckCheck className="h-4 w-4" /> Đánh dấu tất cả đã đọc
                          </button>
                          <button
                            onClick={() => { deleteAllNotifs(); setNotifMenuOpen(false); }}
                            className="flex items-center gap-2 w-full px-3 py-2 text-xs font-medium text-red-600 hover:bg-red-50"
                          >
                            <Trash2 className="h-4 w-4" /> Xóa tất cả thông báo
                          </button>
                        </div>
                      )}
                    </div>
                  </div>
                  <div className="flex gap-2 px-4 pb-3">
                    <button
                      onClick={() => setNotifActiveTab("ALL")}
                      className={`px-4 py-1.5 rounded-full text-xs font-bold transition-all ${
                        notifActiveTab === "ALL"
                          ? "bg-brand-500 text-white shadow-sm"
                          : "bg-zinc-100 text-zinc-600 hover:bg-zinc-200"
                      }`}
                    >
                      Tất cả
                    </button>
                    <button
                      onClick={() => setNotifActiveTab("UNREAD")}
                      className={`px-4 py-1.5 rounded-full text-xs font-bold transition-all ${
                        notifActiveTab === "UNREAD"
                          ? "bg-brand-500 text-white shadow-sm"
                          : "bg-zinc-100 text-zinc-600 hover:bg-zinc-200"
                      }`}
                    >
                      Chưa đọc
                    </button>
                  </div>
                  <div
                    ref={notifScrollRef}
                    onScroll={handleNotifScroll}
                    className="max-h-[420px] overflow-y-auto px-2 pb-2"
                  >
                    {notifications.length === 0 && !notifIsLoading && (
                      <div className="py-10 text-center">
                        <Bell className="h-10 w-10 text-zinc-200 mx-auto mb-3" />
                        <p className="text-sm text-zinc-400">Chưa có thông báo nào</p>
                      </div>
                    )}
                    {recentNotifications.length > 0 && (
                      <>
                        <div className="flex items-center justify-between px-3 pt-1 pb-1.5">
                          <span className="text-xs font-bold text-zinc-700">Mới</span>
                          <Link
                            href={ROUTES.NOTIFICATIONS}
                            onClick={() => setIsNotifDropdownOpen(false)}
                            className="text-xs font-semibold text-brand-500 hover:text-brand-600"
                          >
                            Xem tất cả
                          </Link>
                        </div>
                        {recentNotifications.map(renderNotificationItem)}
                      </>
                    )}
                    {earlierNotifications.length > 0 && (
                      <>
                        <div className="px-3 pt-3 pb-1.5">
                          <span className="text-xs font-bold text-zinc-700">Trước đó</span>
                        </div>
                        {earlierNotifications.map(renderNotificationItem)}
                      </>
                    )}
                    {recentNotifications.length === 0 && earlierNotifications.length === 0 && notifications.length > 0 && (
                      <>{notifications.map(renderNotificationItem)}</>
                    )}
                    {notifIsLoading && (
                      <div className="py-4 text-center">
                        <div className="h-5 w-5 border-2 border-brand-500 border-t-transparent rounded-full animate-spin mx-auto" />
                      </div>
                    )}
                  </div>
                  <div className="border-t border-zinc-100">
                    <Link
                      href={ROUTES.NOTIFICATIONS}
                      onClick={() => setIsNotifDropdownOpen(false)}
                      className="block text-center py-3 text-xs font-bold text-brand-500 hover:bg-zinc-50 rounded-b-2xl transition-colors"
                    >
                      Xem tất cả thông báo
                    </Link>
                  </div>
                </div>
              )}
            </div>
          )}
          <div>
            {isAuthenticated && user ? (
              <div className="relative" ref={dropdownRef}>
                <button
                  onClick={() => setIsProfileDropdownOpen(!isProfileDropdownOpen)}
                  className="flex items-center gap-1.5 p-1 rounded-full hover:bg-zinc-50 transition-all duration-200"
                >
                  <div className={`relative h-9 w-9 rounded-full flex items-center justify-center bg-zinc-100 transition-all duration-200 ${getAvatarBorderClass()}`}>
                    {user.avatarUrl ? (
                      <img
                        src={user.avatarUrl}
                        alt={user.fullName || "User Avatar"}
                        className="h-full w-full object-cover rounded-full"
                        onError={(e) => {
                          (e.target as HTMLElement).style.display = "none";
                          const fallback = (e.target as HTMLElement).nextElementSibling as HTMLElement;
                          if (fallback) fallback.style.display = "flex";
                        }}
                      />
                    ) : null}
                    <div
                      className={`h-full w-full rounded-full bg-gradient-to-br ${getFallbackGradientClass()} text-white font-extrabold text-xs items-center justify-center ${user.avatarUrl ? "hidden" : "flex"
                        }`}
                    >
                      {getUserInitials()}
                    </div>
                  </div>
                  <ChevronDown className={`h-4.5 w-4.5 text-zinc-500 transition-transform duration-300 ${isProfileDropdownOpen ? "rotate-180" : ""}`} />
                </button>
                {isProfileDropdownOpen && (
                  <div className="absolute right-0 mt-2.5 w-60 rounded-2xl border border-zinc-100 bg-white p-2.5 shadow-xl shadow-zinc-200/50 animate-in fade-in slide-in-from-top-3 duration-250">
                    <div className="px-3.5 py-3 border-b border-zinc-100 mb-2 flex flex-col items-start gap-1.5">
                      <div>
                        <p className="text-sm font-bold text-zinc-800 truncate leading-none mb-1.5">{user.fullName}</p>
                        <p className="text-xs text-zinc-400 truncate font-light">{user.email}</p>
                      </div>
                      {isAdmin ? (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-50 text-rose-600 border border-rose-100">
                          <ShieldAlert className="h-3 w-3" />
                          Quản trị viên
                        </span>
                      ) : isCreator ? (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-50 text-emerald-600 border border-emerald-100">
                          <Leaf className="h-3 w-3" />
                          Creator
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-blue-50 text-blue-600 border border-blue-100">
                          <ShoppingBag className="h-3 w-3" />
                          Khách hàng
                        </span>
                      )}
                    </div>

                    <Link
                      href={ROUTES.CREATOR_PROFILE(user.id)}
                      onClick={() => setIsProfileDropdownOpen(false)}
                      className="flex items-center gap-2.5 rounded-xl px-3 py-2 text-xs font-semibold text-zinc-700 hover:bg-brand-50 hover:text-brand-600 transition-all duration-200"
                    >
                      <UserIcon className="h-4 w-4 text-zinc-400" />
                      Trang cá nhân
                    </Link>

                    <Link
                      href={ROUTES.PROFILE}
                      onClick={() => setIsProfileDropdownOpen(false)}
                      className="flex items-center gap-2.5 rounded-xl px-3 py-2 text-xs font-semibold text-zinc-700 hover:bg-brand-50 hover:text-brand-600 transition-all duration-200"
                    >
                      <Settings className="h-4 w-4 text-zinc-400" />
                      Cài đặt tài khoản
                    </Link>

                    <Link
                      href={ROUTES.ORDERS}
                      onClick={() => setIsProfileDropdownOpen(false)}
                      className="flex items-center gap-2.5 rounded-xl px-3 py-2 text-xs font-semibold text-zinc-700 hover:bg-brand-50 hover:text-brand-600 transition-all duration-200"
                    >
                      <ShoppingBag className="h-4 w-4 text-zinc-400" />
                      Đơn mua của bạn
                    </Link>

                    {isCreator && (
                      <Link
                        href={ROUTES.CREATOR_DASHBOARD}
                        onClick={() => setIsProfileDropdownOpen(false)}
                        className="flex items-center gap-2.5 rounded-xl px-3 py-2 text-xs font-semibold text-brand-600 bg-brand-50/50 hover:bg-brand-100 hover:text-brand-700 transition-all duration-200"
                      >
                        <LayoutDashboard className="h-4 w-4 text-brand-500" />
                        Kênh Creator
                      </Link>
                    )}

                    {isAdmin && (
                      <Link
                        href={ROUTES.ADMIN}
                        onClick={() => setIsProfileDropdownOpen(false)}
                        className="flex items-center gap-2.5 rounded-xl px-3 py-2 text-xs font-semibold text-rose-600 bg-rose-50/50 hover:bg-rose-100 hover:text-rose-700 transition-all duration-200"
                      >
                        <ShieldAlert className="h-4 w-4 text-rose-500" />
                        Quản trị viên
                      </Link>
                    )}

                    <div className="h-px bg-zinc-100 my-2" />

                    <button
                      onClick={handleLogout}
                      className="flex w-full items-center gap-2.5 rounded-xl px-3 py-2 text-xs font-semibold text-zinc-500 hover:bg-zinc-50 hover:text-zinc-800 transition-all duration-200"
                    >
                      <LogOut className="h-4 w-4 text-zinc-400" />
                      Đăng xuất
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <div className="flex items-center gap-5">
                <Link
                  href={ROUTES.LOGIN}
                  className="text-[15px] font-bold text-zinc-700 hover:text-brand-600 transition-colors"
                >
                  Đăng nhập
                </Link>
                <Link
                  href={ROUTES.REGISTER}
                  className="flex h-10 items-center justify-center rounded-full bg-brand-500 hover:bg-brand-600 px-6 text-[15px] font-bold text-white shadow-md shadow-brand-500/10 hover:shadow-brand-500/20 hover:scale-[1.03] active:scale-[0.97] transition-all duration-200"
                >
                  Đăng ký
                </Link>
              </div>
            )}
          </div>
          <button
            onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
            className="flex h-9 w-9 md:hidden items-center justify-center rounded-full border border-zinc-100 text-zinc-600 hover:bg-zinc-50 transition-colors"
          >
            {isMobileMenuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>

        </div>
      </div>
      {isMobileMenuOpen && (
        <div
          ref={mobileMenuRef}
          className="md:hidden w-full border-t border-brand-100 bg-white/95 backdrop-blur-md px-4 py-5 shadow-inner flex flex-col gap-4 animate-in slide-in-from-top duration-300"
        >
          <div className="flex flex-col gap-2">
            {navLinks.map((link) => {
              const LinkIcon = link.icon;
              const isActive = pathname === link.href || (link.href !== "/" && pathname.startsWith(link.href));
              return (
                <Link
                  key={link.href}
                  href={link.href}
                  className={`flex items-center gap-3 px-4 py-2.5 rounded-xl text-sm font-semibold transition-all duration-200 ${isActive
                      ? "text-brand-600 bg-brand-50/50"
                      : "text-zinc-600 hover:text-zinc-900 hover:bg-zinc-50"
                    }`}
                >
                  <LinkIcon className="h-4.5 w-4.5" />
                  {link.label}
                </Link>
              );
            })}
          </div>

          <div className="h-px bg-brand-100/60" />
          <div>
            {isAuthenticated && user ? (
              <div className="flex flex-col gap-3">
                <div className="flex items-center gap-3 px-4 py-2 bg-zinc-50/60 rounded-2xl">
                  <div className={`relative h-9 w-9 rounded-full flex items-center justify-center bg-zinc-100 transition-all duration-200 ${getAvatarBorderClass()}`}>
                    {user.avatarUrl ? (
                      <img
                        src={user.avatarUrl}
                        alt={user.fullName}
                        className="h-full w-full object-cover rounded-full"
                        onError={(e) => {
                          (e.target as HTMLElement).style.display = "none";
                          const fallback = (e.target as HTMLElement).nextElementSibling as HTMLElement;
                          if (fallback) fallback.style.display = "flex";
                        }}
                      />
                    ) : null}
                    <div
                      className={`h-full w-full rounded-full bg-gradient-to-br ${getFallbackGradientClass()} text-white font-extrabold text-xs items-center justify-center ${user.avatarUrl ? "hidden" : "flex"
                        }`}
                    >
                      {getUserInitials()}
                    </div>
                  </div>
                  <div className="truncate flex flex-col items-start gap-1">
                    <p className="text-xs font-bold text-zinc-800 truncate leading-none">{user.fullName}</p>
                    <p className="text-[10px] text-zinc-400 truncate leading-none">{user.email}</p>
                    {isAdmin ? (
                      <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full text-[9px] font-bold bg-rose-50 text-rose-600 border border-rose-100">
                        <ShieldAlert className="h-2.5 w-2.5" />
                        Quản trị viên
                      </span>
                    ) : isCreator ? (
                      <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full text-[9px] font-bold bg-emerald-50 text-emerald-600 border border-emerald-100">
                        <Leaf className="h-2.5 w-2.5" />
                        Creator
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full text-[9px] font-bold bg-blue-50 text-blue-600 border border-blue-100">
                        <ShoppingBag className="h-2.5 w-2.5" />
                        Khách hàng
                      </span>
                    )}
                  </div>
                </div>

                <div className="flex flex-col gap-1 px-1">
                  <Link
                    href={ROUTES.PROFILE}
                    className="flex items-center gap-3 px-3 py-2 rounded-lg text-xs font-bold text-zinc-600 hover:bg-zinc-50"
                  >
                    <UserIcon className="h-4 w-4 text-zinc-400" />
                    Trang cá nhân của bạn
                  </Link>

                  <Link
                    href={ROUTES.ORDERS}
                    className="flex items-center gap-3 px-3 py-2 rounded-lg text-xs font-bold text-zinc-600 hover:bg-zinc-50"
                  >
                    <ShoppingBag className="h-4 w-4 text-zinc-400" />
                    Quản lý đơn mua
                  </Link>

                  {isAdmin && (
                    <Link
                      href={ROUTES.ADMIN}
                      className="flex items-center gap-3 px-3 py-2 rounded-lg text-xs font-bold text-rose-600 bg-rose-50/30 hover:bg-rose-50"
                    >
                      <ShieldAlert className="h-4 w-4 text-rose-500" />
                      Quản trị viên (Admin)
                    </Link>
                  )}

                  <button
                    onClick={handleLogout}
                    className="flex items-center gap-3 px-3 py-2 rounded-lg text-xs font-bold text-zinc-400 hover:bg-zinc-50 hover:text-zinc-700"
                  >
                    <LogOut className="h-4 w-4 text-zinc-300" />
                    Đăng xuất tài khoản
                  </button>
                </div>
              </div>
            ) : (
              <div className="flex flex-col gap-2.5 px-2">
                <Link
                  href={ROUTES.LOGIN}
                  className="flex h-10 w-full items-center justify-center rounded-xl border border-zinc-200 text-sm font-bold text-zinc-700 hover:bg-zinc-50 transition-colors"
                >
                  Đăng nhập
                </Link>
                <Link
                  href={ROUTES.REGISTER}
                  className="flex h-10 w-full items-center justify-center rounded-xl bg-brand-500 hover:bg-brand-600 text-sm font-bold text-white shadow-md shadow-brand-500/10 transition-all duration-200"
                >
                  Đăng ký tài khoản
                </Link>
              </div>
            )}
          </div>
        </div>
      )}
    </header>
  );
}
