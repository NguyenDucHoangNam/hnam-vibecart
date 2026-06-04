"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import { 
  ShoppingBag, 
  Trash2, 
  ChevronRight, 
  Clock, 
  CreditCard, 
  CheckCircle2, 
  ExternalLink,
  Store,
  Filter,
  Info,
  Calendar,
  Loader2,
  XCircle
} from "lucide-react";
import { orderService } from "@/services/order.service";
import { Order } from "@/types";
import { useToast } from "@/context/ToastContext";
import { ROUTES } from "@/constants/routes";

const STATUS_TABS = [
  { value: "", label: "Tất cả" },
  { value: "PENDING", label: "Chờ thanh toán" },
  { value: "PAID", label: "Đã thanh toán" },
  { value: "SHIPPED", label: "Đang giao hàng" },
  { value: "DELIVERED", label: "Đã nhận hàng" },
  { value: "CANCELLED", label: "Đã hủy" }
];

export default function MyOrdersPage() {
  const toast = useToast();

  // States
  const [orders, setOrders] = useState<Order[]>([]);
  const [activeTab, setActiveTab] = useState("");
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [isProcessingAction, setIsProcessingAction] = useState<Record<string, boolean>>({});

  // Load orders
  const loadOrders = React.useCallback(async () => {
    setIsLoading(true);
    try {
      const response = await orderService.getMyOrders(activeTab || undefined, page, 10);
      setOrders(response.content || []);
      setTotalElements(response.totalElements || 0);
    } catch (err) {
      console.error("Failed to load orders:", err);
      toast.error("Lỗi tải dữ liệu", "Không thể hiển thị lịch sử đơn hàng của bạn.");
    } finally {
      setIsLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTab, page]);

  useEffect(() => {
    loadOrders();
  }, [loadOrders]);

  // Cancel order shopper side
  const handleCancelOrder = async (orderId: string, orderCode: string) => {
    if (!window.confirm(`Bạn có chắc chắn muốn hủy đơn hàng "${orderCode}" không?`)) {
      return;
    }

    setIsProcessingAction((prev) => ({ ...prev, [orderId]: true }));
    try {
      await orderService.cancelOrder(orderId);
      toast.success("Hủy đơn thành công", `Đơn hàng "${orderCode}" đã được hủy và hoàn tồn kho khả dụng.`);
      loadOrders();
    } catch (err: any) {
      console.error("Failed to cancel order:", err);
      toast.error("Lỗi hủy đơn", err?.message || "Không thể hủy đơn hàng vào lúc này.");
    } finally {
      setIsProcessingAction((prev) => ({ ...prev, [orderId]: false }));
    }
  };

  const totalPages = Math.ceil(totalElements / 10);

  // Status mapping to color
  const getStatusBadge = (status: string) => {
    switch (status) {
      case "PENDING":
        return "bg-amber-50 text-amber-700 border-amber-200/50 dark:bg-amber-950/20 dark:text-amber-400 dark:border-amber-900/30";
      case "PAID":
        return "bg-emerald-50 text-emerald-700 border-emerald-200/50 dark:bg-emerald-950/20 dark:text-emerald-400 dark:border-emerald-900/30";
      case "SHIPPED":
        return "bg-blue-50 text-blue-700 border-blue-200/50 dark:bg-blue-950/20 dark:text-blue-400 dark:border-blue-900/30";
      case "DELIVERED":
        return "bg-zinc-100 text-zinc-800 border-zinc-200 dark:bg-zinc-850 dark:text-zinc-300 dark:border-zinc-700";
      case "CANCELLED":
        return "bg-red-50 text-red-700 border-red-200/50 dark:bg-red-950/20 dark:text-red-400 dark:border-red-900/30";
      default:
        return "bg-zinc-50 text-zinc-600 border-zinc-200";
    }
  };

  const getStatusLabel = (status: string) => {
    switch (status) {
      case "PENDING": return "Chờ thanh toán";
      case "PAID": return "Đã thanh toán";
      case "SHIPPED": return "Đang giao hàng";
      case "DELIVERED": return "Đã nhận hàng";
      case "CANCELLED": return "Đã hủy đơn";
      default: return status;
    }
  };

  return (
    <div className="flex-1 bg-zinc-50 dark:bg-zinc-950 px-4 sm:px-6 lg:px-8 py-10 transition-colors duration-300 relative">
      <div className="absolute top-[10%] left-[5%] w-80 h-80 bg-brand-100/10 rounded-full blur-[100px] pointer-events-none" />
      <div className="absolute bottom-[20%] right-[5%] w-96 h-96 bg-brand-200/10 rounded-full blur-[120px] pointer-events-none" />

      <div className="max-w-4xl w-full mx-auto relative z-10">
        
        {/* HEADER */}
        <div className="mb-8">
          <h1 className="text-3xl font-extrabold tracking-tight text-zinc-900 dark:text-white flex items-center gap-3">
            <ShoppingBag className="h-8 w-8 text-brand-500" />
            Đơn hàng của tôi
          </h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400 mt-1 font-light">
            Theo dõi trạng thái giao vận và thanh toán các đơn hàng con được phân tách tự động của bạn.
          </p>
        </div>

        {/* STATUS TABS PANEL */}
        <div className="bg-white dark:bg-zinc-900 border border-zinc-200/60 dark:border-zinc-800/60 rounded-2xl p-2 mb-6 flex overflow-x-auto gap-1 shadow-sm no-scrollbar">
          {STATUS_TABS.map((tab) => (
            <button
              key={tab.value}
              onClick={() => {
                setActiveTab(tab.value);
                setPage(0);
              }}
              className={`px-4.5 py-2 rounded-xl text-xs font-bold whitespace-nowrap transition-all duration-200 shrink-0 ${
                activeTab === tab.value
                  ? "bg-brand-500 text-white shadow-md shadow-brand-500/10"
                  : "text-zinc-500 hover:bg-zinc-50 hover:text-zinc-800 dark:hover:bg-zinc-950"
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* ORDERS LIST */}
        {isLoading ? (
          <div className="space-y-6">
            {Array.from({ length: 3 }).map((_, idx) => (
              <div 
                key={idx} 
                className="bg-white dark:bg-zinc-900 rounded-3xl border border-zinc-200/40 p-6 shadow-sm animate-pulse h-48"
              />
            ))}
          </div>
        ) : orders.length === 0 ? (
          <div className="bg-white dark:bg-zinc-900 rounded-[2rem] border border-zinc-200/50 p-12 sm:p-20 text-center shadow-sm">
            <div className="h-16 w-16 bg-zinc-50 dark:bg-zinc-950 text-zinc-400 rounded-2xl flex items-center justify-center mb-5 mx-auto border border-zinc-200/60">
              <ShoppingBag className="h-8 w-8" />
            </div>
            <h3 className="text-base font-bold text-zinc-850">Không tìm thấy đơn hàng</h3>
            <p className="text-xs text-zinc-400 mt-1 max-w-xs mx-auto leading-relaxed font-light">
              Bạn chưa có đơn hàng nào thuộc trạng thái này. Hãy bắt đầu chọn sản phẩm để thanh toán!
            </p>
          </div>
        ) : (
          <div className="space-y-6">
            {orders.map((order) => {
              const isPending = order.status === "PENDING";
              const isProcessing = !!isProcessingAction[order.orderId];

              return (
                <div
                  key={order.orderId}
                  className="bg-white dark:bg-zinc-900 rounded-[2rem] border border-zinc-200/60 dark:border-zinc-800/60 shadow-sm overflow-hidden flex flex-col hover:border-zinc-300 dark:hover:border-zinc-700 transition-all duration-200"
                >
                  {/* Card Top Details */}
                  <div className="bg-zinc-50/50 dark:bg-zinc-950/20 px-6 py-4 border-b border-zinc-200/60 dark:border-zinc-800/50 flex flex-wrap items-center justify-between gap-3">
                    <div className="flex items-center gap-3">
                      <span className="text-xs font-black text-brand-600 tracking-wider">
                        {order.orderCode}
                      </span>
                      <span className="text-[10px] text-zinc-400 dark:text-zinc-500 font-light flex items-center gap-1">
                        <Calendar className="h-3.5 w-3.5" />
                        {new Date(order.createdAt).toLocaleDateString("vi-VN", {
                          year: "numeric",
                          month: "long",
                          day: "numeric",
                          hour: "2-digit",
                          minute: "2-digit"
                        })}
                      </span>
                    </div>

                    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-[10px] font-bold border uppercase tracking-wider ${getStatusBadge(order.status)}`}>
                      {getStatusLabel(order.status)}
                    </span>
                  </div>

                  {/* Creator and Product Items summaries */}
                  <div className="p-6 flex flex-col gap-4 border-b border-zinc-100 dark:border-zinc-800/60">
                    <div className="flex items-center gap-2 text-xs font-bold text-zinc-850 dark:text-zinc-200">
                      <Store className="h-4.5 w-4.5 text-brand-500" />
                      Nhà sáng tạo: {order.creatorName || "Creator"}
                    </div>

                    <div className="space-y-3">
                      {order.items?.map((item, idx) => (
                        <div key={idx} className="flex justify-between items-center text-xs font-light text-zinc-500 dark:text-zinc-400">
                          <span>
                            • <strong className="font-bold text-zinc-700 dark:text-zinc-300">{item.productName}</strong> ({item.variantName})
                          </span>
                          <span className="font-semibold text-zinc-900 dark:text-zinc-200">
                            x{item.quantity} - {new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(
                              (item.discountPrice > 0 ? item.discountPrice : item.price) * item.quantity
                            )}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>

                  {/* Bottom details and CTAs */}
                  <div className="px-6 py-4 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 bg-zinc-50/20 dark:bg-zinc-950/10">
                    <div className="flex flex-col">
                      <span className="text-[10px] text-zinc-400 uppercase tracking-widest font-semibold">Tổng tiền thanh toán</span>
                      <span className="text-base font-black text-zinc-900 dark:text-white mt-0.5">
                        {new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(order.finalAmount)}
                      </span>
                    </div>

                    <div className="flex flex-wrap items-center gap-3 w-full sm:w-auto justify-end">
                      {/* Cancel pending order button */}
                      {isPending && (
                        <button
                          disabled={isProcessing}
                          onClick={() => handleCancelOrder(order.orderId, order.orderCode)}
                          className="h-9.5 inline-flex items-center gap-1 px-4 border border-zinc-200 hover:bg-zinc-50 dark:border-zinc-800 dark:hover:bg-zinc-900 text-zinc-600 dark:text-zinc-400 text-xs font-semibold rounded-full disabled:opacity-40 transition-colors"
                        >
                          Hủy đơn
                        </button>
                      )}

                      {/* PayOS checkout button */}
                      {isPending && order.paymentUrl && (
                        <a
                          href={order.paymentUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="h-9.5 inline-flex items-center gap-1.5 px-4 bg-brand-500 hover:bg-brand-600 text-white text-xs font-bold rounded-full shadow-md shadow-brand-500/10 transition-all"
                        >
                          Thanh toán QR (PayOS)
                          <ExternalLink className="h-3.5 w-3.5" />
                        </a>
                      )}

                      <Link
                        href={ROUTES.ORDER_DETAILS(order.orderId)}
                        className="h-9.5 inline-flex items-center gap-1 px-4 border border-zinc-200 dark:border-zinc-800 hover:bg-zinc-50 dark:hover:bg-zinc-900 text-zinc-700 dark:text-zinc-300 text-xs font-semibold rounded-full transition-colors"
                      >
                        Chi tiết đơn
                        <ChevronRight className="h-3.5 w-3.5" />
                      </Link>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-2.5 mt-10">
            <button
              onClick={() => setPage((prev) => Math.max(0, prev - 1))}
              disabled={page === 0 || isLoading}
              className="px-4 py-2 border border-zinc-200 dark:border-zinc-800 hover:bg-zinc-100 disabled:opacity-40 disabled:pointer-events-none rounded-xl text-xs font-medium text-zinc-700 dark:text-zinc-300 transition-colors"
            >
              Trước
            </button>
            <span className="text-xs text-zinc-400">Trang {page + 1} / {totalPages}</span>
            <button
              onClick={() => setPage((prev) => Math.min(totalPages - 1, prev + 1))}
              disabled={page === totalPages - 1 || isLoading}
              className="px-4 py-2 border border-zinc-200 dark:border-zinc-800 hover:bg-zinc-100 disabled:opacity-40 disabled:pointer-events-none rounded-xl text-xs font-medium text-zinc-700 dark:text-zinc-300 transition-colors"
            >
              Sau
            </button>
          </div>
        )}

      </div>
    </div>
  );
}
