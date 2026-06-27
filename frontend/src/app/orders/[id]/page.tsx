"use client";

import React, { useState, useEffect } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { 
  ShoppingBag, 
  ChevronLeft, 
  Clock, 
  CreditCard, 
  CheckCircle2, 
  Store,
  Calendar,
  MapPin,
  User,
  Phone,
  FileText,
  Truck,
  PackageCheck,
  XCircle,
  Info,
  ExternalLink,
  Loader2,
  Timer
} from "lucide-react";
import { orderService } from "@/services/order.service";
import { Order } from "@/types";
import { useToast } from "@/context/ToastContext";
import { ROUTES } from "@/constants/routes";
import { OrderDetailSkeleton } from "@/components/skeletons/LoadingSkeletons";


export default function OrderDetailsPage() {
  const params = useParams();
  const router = useRouter();
  const toast = useToast();
  const orderId = params.id as string;
  const [order, setOrder] = useState<Order | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isCancelling, setIsCancelling] = useState(false);
  const loadOrderDetails = React.useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await orderService.getOrderDetails(orderId);
      setOrder(data);
    } catch (err) {
      console.error("Failed to load order details:", err);
      toast.error("Lỗi lấy thông tin", "Không thể lấy thông tin chi tiết của đơn hàng này.");
    } finally {
      setIsLoading(false);
    }
  }, [orderId]);

  useEffect(() => {
    loadOrderDetails();
  }, [loadOrderDetails]);
  const handleCancelOrder = async () => {
    if (!order) return;
    if (!window.confirm(`Bạn có chắc chắn muốn hủy đơn hàng "${order.orderCode}" không?`)) {
      return;
    }

    setIsCancelling(true);
    try {
      await orderService.cancelOrder(order.orderId);
      toast.success("Hủy thành công", "Đơn hàng đã được hủy.");
      loadOrderDetails();
    } catch (err: any) {
      console.error("Failed to cancel order:", err);
      toast.error("Lỗi hủy đơn", err?.message || "Không thể hủy đơn hàng vào lúc này.");
    } finally {
      setIsCancelling(false);
    }
  };

  if (isLoading) {
    return <OrderDetailSkeleton />;
  }

  if (!order) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center py-20 bg-zinc-50 text-center">
        <div className="h-14 w-14 rounded-2xl bg-zinc-100 flex items-center justify-center text-zinc-400 border border-zinc-200/50 mb-4">
          <Info className="h-7 w-7" />
        </div>
        <h3 className="text-base font-bold text-zinc-800">Không tìm thấy đơn hàng</h3>
        <p className="text-xs text-zinc-400 mt-1 max-w-xs leading-relaxed font-light">
          Đơn hàng này không tồn tại hoặc đã bị xóa khỏi hệ thống.
        </p>
        <Link
          href={ROUTES.ORDERS}
          className="mt-6 px-5 py-2.5 rounded-full bg-brand-500 text-white text-xs font-semibold"
        >
          Quay lại danh sách đơn
        </Link>
      </div>
    );
  }

  const isPending = order.status === "PENDING";
  const isPaid = order.status === "PAID";
  const isShipped = order.status === "SHIPPED";
  const isDelivered = order.status === "DELIVERED";
  const isCancelled = order.status === "CANCELLED";
  const [timeRemaining, setTimeRemaining] = useState<number | null>(null);

  useEffect(() => {
    if (!order || order.status !== "PENDING") return;

    const orderCreatedAt = new Date(order.createdAt).getTime();
    const timeoutMs = 15 * 60 * 1000;
    const expiresAt = orderCreatedAt + timeoutMs;

    const updateTimer = () => {
      const now = Date.now();
      const remaining = Math.max(0, Math.floor((expiresAt - now) / 1000));
      setTimeRemaining(remaining);
    };

    updateTimer();
    const interval = setInterval(updateTimer, 1000);
    return () => clearInterval(interval);
  }, [order]);

  const formatCountdown = (seconds: number) => {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m.toString().padStart(2, "0")}:${s.toString().padStart(2, "0")}`;
  };
  const timelineSteps = [
    { label: "Đặt đơn thành công", active: true, desc: "Đơn hàng đã được tách và giữ chỗ tồn kho khả dụng", icon: Clock },
    { label: "Đã thanh toán", active: isPaid || isShipped || isDelivered, desc: "Thanh toán QR PayOS thành công, kho vật lý đã bị trừ", icon: CreditCard },
    { label: "Đang giao hàng", active: isShipped || isDelivered, desc: "Creator đã đóng gói và bàn giao cho đối tác vận chuyển", icon: Truck },
    { label: "Đã nhận hàng", active: isDelivered, desc: "Khách hàng nhận hàng thành công, khép lại chu kỳ đơn hàng", icon: PackageCheck }
  ];

  return (
    <div className="flex-1 bg-zinc-50 dark:bg-zinc-950 px-4 sm:px-6 lg:px-8 py-10 transition-colors duration-300 relative">
      <div className="absolute top-[10%] left-[5%] w-80 h-80 bg-brand-100/10 rounded-full blur-[100px] pointer-events-none" />
      <div className="absolute bottom-[20%] right-[5%] w-96 h-96 bg-brand-200/10 rounded-full blur-[120px] pointer-events-none" />

      <div className="max-w-5xl w-full mx-auto relative z-10">
        <Link 
          href={ROUTES.ORDERS}
          className="inline-flex items-center gap-1.5 text-zinc-500 hover:text-zinc-900 dark:hover:text-white text-xs font-semibold mb-8 group"
        >
          <ChevronLeft className="h-4 w-4 group-hover:-translate-x-0.5 transition-transform" />
          Lịch sử Đơn hàng
        </Link>
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          <div className="lg:col-span-8 space-y-6">
            {!isCancelled ? (
              <div className="bg-white dark:bg-zinc-900 rounded-[2.5rem] border border-zinc-200/60 dark:border-zinc-800/60 p-6 sm:p-8 shadow-sm">
                <h3 className="text-base font-bold text-zinc-900 dark:text-white pb-3 border-b border-zinc-100 dark:border-zinc-800/80 mb-6 flex items-center gap-2">
                  <Truck className="h-5 w-5 text-brand-500" />
                  Hành trình Đơn hàng
                </h3>

                <div className="space-y-6 relative pl-3.5 border-l border-zinc-100 dark:border-zinc-800">
                  {timelineSteps.map((step, idx) => {
                    const Icon = step.icon;
                    return (
                      <div key={idx} className="relative pl-6">
                        <div className={`absolute -left-[24.5px] top-0.5 flex h-5 w-5 items-center justify-center rounded-full border transition-all duration-300 ${
                          step.active
                            ? "bg-brand-500 border-brand-500 text-white shadow-sm shadow-brand-500/20"
                            : "bg-white border-zinc-200 text-zinc-300 dark:bg-zinc-900 dark:border-zinc-800"
                        }`}>
                          {step.active ? (
                            <CheckCircle2 className="h-3 w-3" />
                          ) : (
                            <span className="h-1.5 w-1.5 rounded-full bg-zinc-300" />
                          )}
                        </div>

                        <div className="flex flex-col">
                          <span className={`text-xs font-bold ${step.active ? "text-zinc-900 dark:text-white" : "text-zinc-400 dark:text-zinc-500"}`}>
                            {step.label}
                          </span>
                          <span className="text-[10px] text-zinc-400 dark:text-zinc-500 font-light mt-0.5 leading-relaxed">
                            {step.desc}
                          </span>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            ) : (
              <div className="bg-red-50 dark:bg-red-950/20 rounded-[2rem] border border-red-100 dark:border-red-900/30 p-6 flex gap-4 items-center shadow-sm">
                <XCircle className="h-12 w-12 text-red-500 shrink-0" />
                <div className="flex flex-col">
                  <h4 className="text-sm font-bold text-red-800 dark:text-red-400">Đơn hàng này đã bị hủy bỏ (CANCELLED)</h4>
                  <p className="text-xs text-red-600 dark:text-red-500 font-light leading-relaxed mt-1">
                    Đơn hàng đã được giải phóng số lượng giữ chỗ và hoàn lại tồn kho khả dụng cho SPU/SKU tương ứng, hoặc đã hoàn tiền nếu bị hủy sau khi đã thanh toán.
                  </p>
                </div>
              </div>
            )}
            {isPending && timeRemaining !== null && (
              <div className={`rounded-[2rem] border p-5 flex gap-4 items-center shadow-sm ${
                timeRemaining <= 120
                  ? "bg-red-50 dark:bg-red-950/20 border-red-100 dark:border-red-900/30"
                  : "bg-amber-50 dark:bg-amber-950/20 border-amber-100 dark:border-amber-900/30"
              }`}>
                <Timer className={`h-10 w-10 shrink-0 ${
                  timeRemaining <= 120 ? "text-red-500 animate-pulse" : "text-amber-500"
                }`} />
                <div className="flex flex-col">
                  <h4 className={`text-sm font-bold ${
                    timeRemaining <= 120 ? "text-red-800 dark:text-red-400" : "text-amber-800 dark:text-amber-400"
                  }`}>
                    {timeRemaining === 0 ? "Đơn hàng đã hết hạn thanh toán" : "Th\u1eddi gian thanh to\u00e1n c\u00f2n l\u1ea1i"}
                  </h4>
                  {timeRemaining > 0 && (
                    <p className={`text-lg font-black mt-0.5 tracking-wider font-mono ${
                      timeRemaining <= 120 ? "text-red-600" : "text-amber-600"
                    }`}>
                      {formatCountdown(timeRemaining)}
                    </p>
                  )}
                  <p className="text-[10px] text-zinc-400 mt-1 font-light">
                    Đơn hàng sẽ tự động bị hủy nếu chưa thanh toán trong vòng 15 phút.
                  </p>
                </div>
              </div>
            )}
            <div className="bg-white dark:bg-zinc-900 rounded-[2.5rem] border border-zinc-200/60 dark:border-zinc-800/60 p-6 sm:p-8 shadow-sm space-y-4">
              <h3 className="text-base font-bold text-zinc-900 dark:text-white pb-3 border-b border-zinc-100 dark:border-zinc-800/80 flex items-center gap-2">
                <MapPin className="h-5 w-5 text-brand-500" />
                Thông tin Giao nhận hàng
              </h3>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs font-light text-zinc-500 dark:text-zinc-400">
                <div className="flex items-center gap-2">
                  <User className="h-4.5 w-4.5 text-zinc-400 shrink-0" />
                  <span>Người nhận: <strong className="font-bold text-zinc-800 dark:text-zinc-200">{order.recipientName}</strong></span>
                </div>
                <div className="flex items-center gap-2">
                  <Phone className="h-4.5 w-4.5 text-zinc-400 shrink-0" />
                  <span>Số điện thoại: <strong className="font-bold text-zinc-800 dark:text-zinc-200">{order.recipientPhone}</strong></span>
                </div>
                <div className="col-span-1 sm:col-span-2 flex items-center gap-2">
                  <MapPin className="h-4.5 w-4.5 text-zinc-400 shrink-0" />
                  <span>Địa chỉ giao: <strong className="font-bold text-zinc-800 dark:text-zinc-200">{order.shippingAddress}</strong></span>
                </div>
                {order.customerNote && (
                  <div className="col-span-1 sm:col-span-2 flex items-start gap-2 bg-zinc-50 p-3 rounded-xl dark:bg-zinc-950/30">
                    <FileText className="h-4.5 w-4.5 text-zinc-400 shrink-0" />
                    <span>Ghi chú từ khách: <strong className="font-bold text-zinc-850 dark:text-zinc-300">{order.customerNote}</strong></span>
                  </div>
                )}
                {(isShipped || isDelivered) && order.trackingNumber && (
                  <div className="col-span-1 sm:col-span-2 flex items-center gap-2 bg-blue-50 dark:bg-blue-950/20 p-3 rounded-xl border border-blue-100 dark:border-blue-900/30">
                    <Truck className="h-4.5 w-4.5 text-blue-500 shrink-0" />
                    <span className="text-xs">Mã vận đơn: <strong className="font-bold text-blue-800 dark:text-blue-300 tracking-wider">{order.trackingNumber}</strong></span>
                  </div>
                )}
              </div>
            </div>
            <div className="bg-white dark:bg-zinc-900 rounded-[2.5rem] border border-zinc-200/60 dark:border-zinc-800/60 p-6 sm:p-8 shadow-sm">
              <h3 className="text-base font-bold text-zinc-900 dark:text-white pb-3 border-b border-zinc-100 dark:border-zinc-800/80 mb-5 flex items-center gap-2">
                <Store className="h-5 w-5 text-brand-500" />
                Cửa hàng Creator: {order.creatorName || "Creator"}
              </h3>

              <div className="space-y-4">
                {order.items?.map((item, idx) => {
                  const hasDiscount = item.discountPrice > 0 && item.discountPrice < item.price;
                  const activePrice = hasDiscount ? item.discountPrice : item.price;

                  return (
                    <div 
                      key={idx}
                      className="flex items-center justify-between gap-4 py-3 border-b border-zinc-100 last:border-b-0 dark:border-zinc-800/40"
                    >
                      <div className="flex flex-col min-w-0">
                        <h4 className="text-xs font-bold text-zinc-900 dark:text-white truncate">
                          {item.productName}
                        </h4>
                        <span className="text-[10px] text-zinc-400 dark:text-zinc-500 font-light mt-0.5">
                          Phiên bản: {item.variantName}
                        </span>
                      </div>

                      <div className="flex items-center gap-6 shrink-0">
                        <span className="text-xs text-zinc-400 dark:text-zinc-500">x{item.quantity}</span>
                        <div className="text-right flex flex-col justify-center">
                          <span className="text-xs font-extrabold text-zinc-850 dark:text-zinc-100">
                            {new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(
                              activePrice * item.quantity
                            )}
                          </span>
                          {hasDiscount && (
                            <span className="text-[9px] text-zinc-400 line-through">
                              {new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(
                                item.price * item.quantity
                              )}
                            </span>
                          )}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

          </div>
          <div className="lg:col-span-4 bg-white dark:bg-zinc-900 rounded-[2.5rem] border border-zinc-200/60 dark:border-zinc-800/60 p-6 shadow-sm sticky top-24 space-y-6">
            <div>
              <h3 className="text-base font-bold text-zinc-900 dark:text-white pb-3 border-b border-zinc-100 dark:border-zinc-800/80 mb-4 flex items-center gap-2">
                <CreditCard className="h-5 w-5 text-brand-500" />
                Hóa đơn chi tiết
              </h3>

              <div className="space-y-4">
                <div className="flex justify-between items-center text-xs text-zinc-500 dark:text-zinc-400 font-light">
                  <span>Mã đơn gốc:</span>
                  <span className="font-semibold text-zinc-900 dark:text-zinc-100">{order.orderCode}</span>
                </div>
                <div className="flex justify-between items-center text-xs text-zinc-500 dark:text-zinc-400 font-light">
                  <span>Giá gốc ban đầu:</span>
                  <span className="font-semibold text-zinc-900 dark:text-zinc-100">
                    {new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(order.totalAmount)}
                  </span>
                </div>
                {order.discountAmount > 0 && (
                  <div className="flex justify-between items-center text-xs text-red-500 font-medium">
                    <span>Đã tiết kiệm:</span>
                    <span>
                      -{new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(order.discountAmount)}
                    </span>
                  </div>
                )}
                <div className="flex justify-between items-center text-xs text-zinc-500 dark:text-zinc-400 font-light">
                  <span>Phí ship:</span>
                  <span className="font-extrabold text-emerald-600 dark:text-emerald-400">Miễn phí</span>
                </div>
                
                <div className="flex justify-between items-center pt-4 border-t border-zinc-100 dark:border-zinc-800/80">
                  <span className="text-xs font-bold text-zinc-900 dark:text-white uppercase tracking-wider">Tổng cộng:</span>
                  <span className="text-base font-black text-brand-600">
                    {new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(order.finalAmount)}
                  </span>
                </div>
              </div>
            </div>
            {isPending && (
              <div className="flex flex-col gap-3 pt-4 border-t border-zinc-100 dark:border-zinc-800/80">
                {order.paymentUrl && (
                  <a
                    href={order.paymentUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="w-full h-11 inline-flex items-center justify-center gap-1.5 rounded-full bg-brand-500 hover:bg-brand-600 text-white text-xs font-bold shadow-md shadow-brand-500/10 transition-colors"
                  >
                    Thanh toán QR (PayOS)
                    <ExternalLink className="h-4 w-4" />
                  </a>
                )}
                
                <button
                  disabled={isCancelling}
                  onClick={handleCancelOrder}
                  className="w-full h-11 border border-zinc-200 hover:bg-zinc-50 dark:border-zinc-800 dark:hover:bg-zinc-950 text-zinc-600 dark:text-zinc-400 text-xs font-semibold rounded-full disabled:opacity-40 transition-colors"
                >
                  {isCancelling ? <Loader2 className="h-4 w-4 animate-spin" /> : "Hủy đơn hàng này"}
                </button>
              </div>
            )}
          </div>

        </div>
      </div>
    </div>
  );
}
