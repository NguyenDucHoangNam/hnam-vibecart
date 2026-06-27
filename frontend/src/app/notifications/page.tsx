"use client";

import React, { useEffect, useRef, useCallback } from "react";
import { useRouter } from "next/navigation";
import {
  Bell,
  MoreHorizontal,
  Check,
  CheckCheck,
  Trash2,
  ArrowLeft,
} from "lucide-react";
import { useNotification } from "@/context/NotificationContext";
import { useAuth } from "@/hooks/useAuth";
import { ROUTES } from "@/constants/routes";
import { NotificationResponse } from "@/types/notification";

export default function NotificationsPage() {
  const { isAuthenticated } = useAuth();
  const router = useRouter();
  const {
    notifications,
    unreadCount,
    recentNotifications,
    earlierNotifications,
    activeTab,
    hasMore,
    isLoading,
    setActiveTab,
    loadMore,
    markAsRead,
    markAllAsRead,
    deleteNotification,
    deleteAllNotifications,
  } = useNotification();

  const [menuOpen, setMenuOpen] = React.useState(false);
  const [itemMenuId, setItemMenuId] = React.useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!isAuthenticated) {
      router.push(ROUTES.LOGIN);
    }
  }, [isAuthenticated, router]);

  const handleScroll = useCallback(() => {
    const el = scrollRef.current;
    if (!el || isLoading || !hasMore) return;
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 60) {
      loadMore();
    }
  }, [isLoading, hasMore, loadMore]);

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
      className={`group flex items-start gap-4 px-5 py-3.5 rounded-xl cursor-pointer transition-all duration-200 relative ${
        !n.read ? "bg-brand-50/60 hover:bg-brand-50" : "hover:bg-zinc-50"
      }`}
      onClick={() => {
        if (!n.read) markAsRead(n.id);
        if (n.type === "FOLLOW" && n.actor?.id) {
          router.push(ROUTES.CREATOR_PROFILE(n.actor.id));
        } else if (n.type === "PRODUCT_NEW" && n.referenceId) {
          router.push(ROUTES.PRODUCT_DETAILS(n.referenceId));
        } else if ((n.type === "LIKE" || n.type === "COMMENT") && n.referenceId) {
          router.push(ROUTES.POST_DETAILS(n.referenceId));
        }
      }}
    >
      <div className="relative h-12 w-12 rounded-full bg-zinc-100 shrink-0 overflow-hidden">
        {n.actor?.avatarUrl ? (
          <img src={n.actor.avatarUrl} alt="" className="h-full w-full object-cover" />
        ) : (
          <div className="h-full w-full flex items-center justify-center bg-gradient-to-br from-brand-400 to-brand-500 text-white font-bold text-sm">
            {n.actor?.fullName?.[0]?.toUpperCase() || "?"}
          </div>
        )}
      </div>
      <div className="flex-1 min-w-0">
        <p className={`text-sm leading-relaxed ${!n.read ? "font-semibold text-zinc-800" : "text-zinc-600"}`}>
          {n.content}
        </p>
        <p className={`text-xs mt-1 ${!n.read ? "text-brand-500 font-semibold" : "text-zinc-400"}`}>
          {formatTimeAgo(n.createdAt)}
        </p>
      </div>
      <div className="flex items-center gap-2 shrink-0">
        <div className="relative">
          <button
            onClick={(e) => {
              e.stopPropagation();
              setItemMenuId(itemMenuId === n.id ? null : n.id);
            }}
            className="h-8 w-8 rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 hover:bg-zinc-200 transition-all"
          >
            <MoreHorizontal className="h-4 w-4 text-zinc-500" />
          </button>
          {itemMenuId === n.id && (
            <div className="absolute right-0 top-9 z-50 w-52 bg-white rounded-xl border border-zinc-100 shadow-lg py-1.5 animate-in fade-in duration-150">
              {!n.read && (
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    markAsRead(n.id);
                    setItemMenuId(null);
                  }}
                  className="flex items-center gap-2 w-full px-4 py-2.5 text-sm text-zinc-700 hover:bg-zinc-50"
                >
                  <Check className="h-4 w-4" /> Đánh dấu đã đọc
                </button>
              )}
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  deleteNotification(n.id);
                  setItemMenuId(null);
                }}
                className="flex items-center gap-2 w-full px-4 py-2.5 text-sm text-red-600 hover:bg-red-50"
              >
                <Trash2 className="h-4 w-4" /> Xóa thông báo
              </button>
            </div>
          )}
        </div>
        {!n.read && (
          <div className="h-3 w-3 rounded-full bg-brand-500 shrink-0" />
        )}
      </div>
    </div>
  );

  if (!isAuthenticated) return null;

  return (
    <div className="min-h-screen bg-zinc-50/50">
      <div className="max-w-2xl mx-auto px-4 py-8">
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-3">
            <button
              onClick={() => router.back()}
              className="h-10 w-10 rounded-full flex items-center justify-center hover:bg-zinc-100 transition-colors"
            >
              <ArrowLeft className="h-5 w-5 text-zinc-600" />
            </button>
            <div>
              <h1 className="text-2xl font-bold text-zinc-800">Thông báo</h1>
              {unreadCount > 0 && (
                <p className="text-sm text-zinc-500">{unreadCount} chưa đọc</p>
              )}
            </div>
          </div>
          <div className="relative">
            <button
              onClick={() => setMenuOpen(!menuOpen)}
              className="h-10 w-10 rounded-full flex items-center justify-center hover:bg-zinc-100 transition-colors"
            >
              <MoreHorizontal className="h-5 w-5 text-zinc-500" />
            </button>
            {menuOpen && (
              <div className="absolute right-0 top-11 w-60 bg-white rounded-xl border border-zinc-100 shadow-lg py-1.5 z-50 animate-in fade-in duration-150">
                <button
                  onClick={() => { markAllAsRead(); setMenuOpen(false); }}
                  className="flex items-center gap-2.5 w-full px-4 py-3 text-sm font-medium text-zinc-700 hover:bg-zinc-50"
                >
                  <CheckCheck className="h-4 w-4" /> Đánh dấu tất cả đã đọc
                </button>
                <button
                  onClick={() => { deleteAllNotifications(); setMenuOpen(false); }}
                  className="flex items-center gap-2.5 w-full px-4 py-3 text-sm font-medium text-red-600 hover:bg-red-50"
                >
                  <Trash2 className="h-4 w-4" /> Xóa tất cả thông báo
                </button>
              </div>
            )}
          </div>
        </div>

        <div className="flex gap-2 mb-5">
          <button
            onClick={() => setActiveTab("ALL")}
            className={`px-5 py-2 rounded-full text-sm font-bold transition-all ${
              activeTab === "ALL"
                ? "bg-brand-500 text-white shadow-sm"
                : "bg-white text-zinc-600 hover:bg-zinc-100 border border-zinc-200"
            }`}
          >
            Tất cả
          </button>
          <button
            onClick={() => setActiveTab("UNREAD")}
            className={`px-5 py-2 rounded-full text-sm font-bold transition-all ${
              activeTab === "UNREAD"
                ? "bg-brand-500 text-white shadow-sm"
                : "bg-white text-zinc-600 hover:bg-zinc-100 border border-zinc-200"
            }`}
          >
            Chưa đọc
          </button>
        </div>

        <div
          ref={scrollRef}
          onScroll={handleScroll}
          className="bg-white rounded-2xl border border-zinc-100 shadow-sm overflow-hidden"
          style={{ maxHeight: "calc(100vh - 220px)", overflowY: "auto" }}
        >
          {notifications.length === 0 && !isLoading && (
            <div className="py-20 text-center">
              <Bell className="h-14 w-14 text-zinc-200 mx-auto mb-4" />
              <p className="text-zinc-400 text-base">Chưa có thông báo nào</p>
              <p className="text-zinc-300 text-sm mt-1">Các thông báo mới sẽ xuất hiện ở đây</p>
            </div>
          )}

          {recentNotifications.length > 0 && (
            <>
              <div className="px-5 pt-4 pb-2">
                <span className="text-sm font-bold text-zinc-700">Mới</span>
              </div>
              {recentNotifications.map(renderNotificationItem)}
            </>
          )}

          {earlierNotifications.length > 0 && (
            <>
              <div className="px-5 pt-5 pb-2 border-t border-zinc-50">
                <span className="text-sm font-bold text-zinc-700">Trước đó</span>
              </div>
              {earlierNotifications.map(renderNotificationItem)}
            </>
          )}

          {recentNotifications.length === 0 && earlierNotifications.length === 0 && notifications.length > 0 && (
            <div className="p-2">{notifications.map(renderNotificationItem)}</div>
          )}

          {isLoading && (
            <div className="py-6 text-center">
              <div className="h-6 w-6 border-2 border-brand-500 border-t-transparent rounded-full animate-spin mx-auto" />
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
