"use client";

import React, { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { 
  ShoppingBag, 
  MapPin, 
  User, 
  Phone, 
  FileText, 
  Lock, 
  ShieldCheck, 
  Sparkles,
  ArrowRight,
  Info,
  CreditCard,
  CheckCircle2,
  ExternalLink,
  ChevronRight,
  ChevronLeft,
  Store,
  Loader2,
  Gift
} from "lucide-react";
import { orderService, PlaceOrderPayload } from "@/services/order.service";
import { CartItem, CheckoutResponse } from "@/types";
import { useToast } from "@/context/ToastContext";
import { useCart } from "@/hooks/useCart";
import { ROUTES } from "@/constants/routes";

export default function CheckoutPage() {
  const router = useRouter();
  const toast = useToast();
  const { fetchCart } = useCart();

  // Load checkout snapshot
  const [checkoutItems, setCheckoutItems] = useState<CartItem[]>([]);
  const [isPlacing, setIsPlacing] = useState(false);
  const [checkoutSuccessData, setCheckoutSuccessData] = useState<CheckoutResponse | null>(null);

  // Form states
  const [recipientName, setRecipientName] = useState("");
  const [recipientPhone, setRecipientPhone] = useState("");
  const [shippingAddress, setShippingAddress] = useState("");
  const [customerNote, setCustomerNote] = useState("");
  const [voucherCode, setVoucherCode] = useState("");

  useEffect(() => {
    const raw = sessionStorage.getItem("checkout_items");
    if (!raw) {
      toast.warning("Giỏ hàng trống", "Không tìm thấy sản phẩm thanh toán. Vui lòng thử lại.");
      router.push(ROUTES.CART);
      return;
    }
    setCheckoutItems(JSON.parse(raw));
  }, [router, toast]);

  if (checkoutItems.length === 0 && !checkoutSuccessData) {
    return null;
  }

  // Group snapshot items by Creator for visualization
  const groupedCheckout = checkoutItems.reduce<Record<string, { creatorName: string; items: CartItem[] }>>(
    (acc, item) => {
      const cId = item.creatorId || "unknown";
      if (!acc[cId]) {
        acc[cId] = {
          creatorName: item.creatorName || "Creator",
          items: [],
        };
      }
      acc[cId].items.push(item);
      return acc;
    },
    {}
  );

  // Compute overall financial summaries
  const totalGrossAmount = checkoutItems.reduce((acc, item) => acc + item.originalPrice * item.quantity, 0);
  const totalDiscountAmount = checkoutItems.reduce(
    (acc, item) => acc + (item.discountPrice > 0 ? (item.originalPrice - item.discountPrice) * item.quantity : 0),
    0
  );
  const finalBillingAmount = totalGrossAmount - totalDiscountAmount;

  // Submit place order
  const handlePlaceOrderSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!recipientName.trim() || !recipientPhone.trim() || !shippingAddress.trim()) {
      toast.warning("Thiếu thông tin", "Vui lòng nhập đầy đủ thông tin nhận hàng.");
      return;
    }

    // Validate Vietnamese phone number (10 digits starting with 0)
    const phoneRegex = /^0\d{9}$/;
    if (!phoneRegex.test(recipientPhone.trim())) {
      toast.warning("Số điện thoại không hợp lệ", "Vui lòng nhập số điện thoại Việt Nam hợp lệ (10 số, bắt đầu bằng 0).");
      return;
    }

    setIsPlacing(true);
    try {
      // 1. Generate client-side Idempotency Key
      const randomId = Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15);
      const idempotencyKey = `chk-${Date.now()}-${randomId}`;

      // 2. Prepare payload
      const payload: PlaceOrderPayload = {
        items: checkoutItems.map((i) => ({
          variantId: i.variantId,
          quantity: i.quantity,
        })),
        shippingAddress: shippingAddress.trim(),
        recipientName: recipientName.trim(),
        recipientPhone: recipientPhone.trim(),
        customerNote: customerNote.trim() || undefined,
        voucherCode: voucherCode.trim() || undefined,
      };

      // 3. Post to API
      const result = await orderService.placeOrder(payload, idempotencyKey);
      
      // 4. Update states & clean snapshot
      setCheckoutSuccessData(result);
      sessionStorage.removeItem("checkout_items");
      
      // 5. Force reload Cart context to reflect items deletion
      await fetchCart();

      toast.success("Đặt hàng thành công", "Đơn hàng đã được tách và khởi tạo thành công.");
    } catch (err: any) {
      console.error("Checkout: Order placement failed", err);
      toast.error("Lỗi đặt hàng", err?.message || "Không thể tạo đơn hàng. Vui lòng kiểm tra lại tồn kho.");
    } finally {
      setIsPlacing(false);
    }
  };

  // =========================================================================
  // SUCCESS VIEW: SHOW CREATOR PAYMENT DASHBOARD
  // =========================================================================
  if (checkoutSuccessData) {
    return (
      <div className="flex-1 bg-zinc-50 dark:bg-zinc-950 px-4 sm:px-6 lg:px-8 py-10 transition-colors duration-300 relative flex items-center justify-center min-h-[85vh]">
        <div className="absolute top-[10%] left-[5%] w-80 h-80 bg-brand-100/10 rounded-full blur-[100px] pointer-events-none" />
        <div className="absolute bottom-[10%] right-[5%] w-96 h-96 bg-brand-200/10 rounded-full blur-[120px] pointer-events-none" />

        <div className="max-w-2xl w-full bg-white dark:bg-zinc-900 rounded-[2.5rem] border border-zinc-200/60 dark:border-zinc-800/60 p-6 sm:p-10 shadow-2xl relative z-10 text-center animate-toast-in">
          
          <div className="h-16 w-16 bg-emerald-50 dark:bg-emerald-950/20 text-emerald-500 rounded-full flex items-center justify-center mb-6 mx-auto border border-emerald-100 dark:border-emerald-900/30">
            <CheckCircle2 className="h-9 w-9" />
          </div>

          <h1 className="text-2xl sm:text-3xl font-extrabold text-zinc-900 dark:text-white leading-tight">
            Khởi tạo Đơn hàng Thành công!
          </h1>
          
          <p className="text-sm text-zinc-500 dark:text-zinc-400 mt-2.5 font-light leading-relaxed max-w-md mx-auto">
            Hệ thống **VibeCart** đã tự động **phân tách giỏ hàng** của bạn thành các đơn hàng con tương ứng với từng kho hàng vật lý của các Creators:
          </p>

          {/* Sub-orders listing with QR payment options */}
          <div className="mt-8 space-y-4 text-left">
            {checkoutSuccessData.subOrders?.map((order) => (
              <div 
                key={order.orderId}
                className="bg-zinc-50 dark:bg-zinc-950 rounded-2xl border border-zinc-200/50 dark:border-zinc-800/40 p-5 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4"
              >
                <div className="flex flex-col min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-black text-brand-600 tracking-wider">
                      {order.orderCode}
                    </span>
                    <span className="text-[9px] bg-amber-50 dark:bg-amber-950/20 text-amber-600 border border-amber-200 dark:border-amber-900/30 px-2 py-0.5 rounded-full font-bold uppercase tracking-wider">
                      {order.status}
                    </span>
                  </div>
                  <span className="text-[11px] font-bold text-zinc-800 dark:text-zinc-200 mt-1 truncate">
                    Cửa hàng: {order.creatorName || "Creator Store"}
                  </span>
                  <span className="text-[10px] text-zinc-400 dark:text-zinc-500 mt-0.5">
                    Số tiền: <strong className="text-zinc-900 dark:text-zinc-100 font-extrabold">{new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(order.finalAmount)}</strong>
                  </span>
                </div>

                {order.paymentUrl ? (
                  <a
                    href={order.paymentUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="w-full sm:w-auto h-10 inline-flex items-center justify-center gap-1.5 rounded-full bg-brand-500 hover:bg-brand-600 text-white font-bold text-xs shadow-md shadow-brand-500/10 px-5 transition-all duration-300"
                  >
                    Thanh toán QR (PayOS)
                    <ExternalLink className="h-3.5 w-3.5" />
                  </a>
                ) : (
                  <span className="text-xs text-zinc-400 font-light">Không có link thanh toán</span>
                )}
              </div>
            ))}
          </div>

          <div className="mt-8 text-[11px] text-zinc-400 dark:text-zinc-500 bg-zinc-50 dark:bg-zinc-950/30 border border-zinc-100 dark:border-zinc-800/40 p-4.5 rounded-xl leading-relaxed text-left font-light flex gap-2">
            <Info className="h-4.5 w-4.5 text-brand-500 shrink-0" />
            <span>
              Hệ thống áp dụng giới hạn thời gian thanh toán là **15 phút**. Nếu quá thời gian trên chưa quét mã QR, các đơn hàng con sẽ tự động bị hủy và giải phóng tồn kho vật lý. Bạn có thể thanh toán các đơn hàng con độc lập.
            </span>
          </div>

          {/* CTA Footer */}
          <div className="mt-8 flex flex-col sm:flex-row gap-3.5 justify-center">
            <Link
              href={ROUTES.ORDERS}
              className="h-11 inline-flex items-center justify-center gap-1.5 rounded-full bg-zinc-900 hover:bg-black text-white text-xs font-semibold px-6 transition-colors duration-200"
            >
              Xem Đơn hàng của tôi
            </Link>
            <Link
              href={ROUTES.PRODUCTS}
              className="h-11 inline-flex items-center justify-center gap-1.5 rounded-full border border-zinc-200 hover:bg-zinc-50 text-zinc-700 text-xs font-semibold px-6 transition-colors duration-200"
            >
              Quay lại mua sắm
            </Link>
          </div>

        </div>
      </div>
    );
  }

  // =========================================================================
  // STANDARD CHECKOUT FORM VIEW
  // =========================================================================
  return (
    <div className="flex-1 bg-zinc-50 dark:bg-zinc-950 px-4 sm:px-6 lg:px-8 py-10 transition-colors duration-300 relative">
      <div className="absolute top-[10%] left-[5%] w-80 h-80 bg-brand-100/10 rounded-full blur-[100px] pointer-events-none" />
      <div className="absolute bottom-[20%] right-[5%] w-96 h-96 bg-brand-200/10 rounded-full blur-[120px] pointer-events-none" />

      <div className="max-w-6xl w-full mx-auto relative z-10">
        
        {/* Back Link */}
        <Link 
          href={ROUTES.CART}
          className="inline-flex items-center gap-1.5 text-zinc-500 hover:text-zinc-900 dark:hover:text-white text-xs font-semibold mb-8 group"
        >
          <ChevronLeft className="h-4 w-4 group-hover:-translate-x-0.5 transition-transform" />
          Quay lại Giỏ hàng
        </Link>

        {/* HEADER */}
        <div className="mb-10 text-center max-w-xl mx-auto">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-brand-50 border border-brand-200 px-3 py-1 text-xs font-semibold text-brand-700 mb-4">
            <Lock className="h-3.5 w-3.5 text-brand-500" />
            Thanh toán an toàn bảo mật
          </span>
          <h1 className="text-3xl font-extrabold text-zinc-900 dark:text-white leading-tight">
            Xác nhận Thông tin Đặt hàng
          </h1>
          <p className="text-xs text-zinc-400 mt-1 font-light">
            Vui lòng cung cấp chính xác thông tin nhận hàng và kiểm tra cách phân tách các đơn hàng con.
          </p>
        </div>

        {/* CONTENT GRID */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          
          {/* LEFT PANEL: FORM & SPLIT DIAGRAM */}
          <div className="lg:col-span-7 space-y-6">
            
            {/* 1. SHIPPING ADDRESS FORM */}
            <form onSubmit={handlePlaceOrderSubmit} className="bg-white dark:bg-zinc-900 rounded-[2.5rem] border border-zinc-200/60 dark:border-zinc-800/60 p-6 sm:p-8 shadow-sm space-y-5">
              <h3 className="text-base font-bold text-zinc-900 dark:text-white pb-3 border-b border-zinc-100 dark:border-zinc-800/80 flex items-center gap-2">
                <MapPin className="h-5 w-5 text-brand-500" />
                Thông tin người nhận
              </h3>

              {/* Recipient Name */}
              <div>
                <label className="block text-xs font-bold text-zinc-700 dark:text-zinc-300 mb-1.5 uppercase tracking-wider">Họ và Tên người nhận *</label>
                <div className="relative">
                  <input
                    type="text"
                    required
                    placeholder="Nhập họ tên đầy đủ..."
                    value={recipientName}
                    onChange={(e) => setRecipientName(e.target.value)}
                    className="w-full h-11 pl-10 pr-4 rounded-xl bg-zinc-50 dark:bg-zinc-950 border border-zinc-200 dark:border-zinc-800 text-sm focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 transition-colors"
                  />
                  <User className="absolute left-3.5 top-3.5 h-4 w-4 text-zinc-400" />
                </div>
              </div>

              {/* Recipient Phone */}
              <div>
                <label className="block text-xs font-bold text-zinc-700 dark:text-zinc-300 mb-1.5 uppercase tracking-wider">Số điện thoại *</label>
                <div className="relative">
                  <input
                    type="tel"
                    required
                    placeholder="Nhập số điện thoại liên hệ..."
                    value={recipientPhone}
                    onChange={(e) => setRecipientPhone(e.target.value)}
                    className="w-full h-11 pl-10 pr-4 rounded-xl bg-zinc-50 dark:bg-zinc-950 border border-zinc-200 dark:border-zinc-800 text-sm focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 transition-colors"
                  />
                  <Phone className="absolute left-3.5 top-3.5 h-4 w-4 text-zinc-400" />
                </div>
              </div>

              {/* Shipping Address */}
              <div>
                <label className="block text-xs font-bold text-zinc-700 dark:text-zinc-300 mb-1.5 uppercase tracking-wider">Địa chỉ giao hàng *</label>
                <div className="relative">
                  <input
                    type="text"
                    required
                    placeholder="Số nhà, ngõ ngách, phường/xã, quận/huyện, tỉnh/thành phố..."
                    value={shippingAddress}
                    onChange={(e) => setShippingAddress(e.target.value)}
                    className="w-full h-11 pl-10 pr-4 rounded-xl bg-zinc-50 dark:bg-zinc-950 border border-zinc-200 dark:border-zinc-800 text-sm focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 transition-colors"
                  />
                  <MapPin className="absolute left-3.5 top-3.5 h-4 w-4 text-zinc-400" />
                </div>
              </div>

              {/* Customer Note */}
              <div>
                <label className="block text-xs font-bold text-zinc-700 dark:text-zinc-300 mb-1.5 uppercase tracking-wider">Ghi chú cho đơn hàng</label>
                <div className="relative">
                  <textarea
                    placeholder="Giao giờ hành chính, gọi trước khi giao..."
                    value={customerNote}
                    onChange={(e) => setCustomerNote(e.target.value)}
                    rows={2.5}
                    className="w-full p-4 rounded-xl bg-zinc-50 dark:bg-zinc-950 border border-zinc-200 dark:border-zinc-800 text-sm focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 transition-colors resize-none"
                  />
                </div>
              </div>

              {/* Form submit hidden or active on sidebar */}
              <button type="submit" className="hidden" id="checkout-form-submit" />
            </form>

            {/* 1.5. VOUCHER CODE INPUT */}
            <div className="bg-white dark:bg-zinc-900 rounded-[2.5rem] border border-zinc-200/60 dark:border-zinc-800/60 p-6 sm:p-8 shadow-sm">
              <h3 className="text-base font-bold text-zinc-900 dark:text-white pb-3 border-b border-zinc-100 dark:border-zinc-800/80 mb-5 flex items-center gap-2">
                <Gift className="h-5 w-5 text-brand-500" />
                Mã giảm giá (Voucher)
              </h3>
              <div className="flex gap-3">
                <input
                  type="text"
                  placeholder="Nhập mã voucher nếu có..."
                  value={voucherCode}
                  onChange={(e) => setVoucherCode(e.target.value.toUpperCase())}
                  className="flex-1 h-11 px-4 rounded-xl bg-zinc-50 dark:bg-zinc-950 border border-zinc-200 dark:border-zinc-800 text-sm focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 transition-colors uppercase tracking-wider font-semibold"
                />
                {voucherCode && (
                  <button
                    type="button"
                    onClick={() => setVoucherCode("")}
                    className="h-11 px-4 rounded-xl border border-zinc-200 hover:bg-zinc-50 text-zinc-500 text-xs font-semibold transition-colors"
                  >
                    Xóa
                  </button>
                )}
              </div>
              <p className="text-[10px] text-zinc-400 mt-2 font-light">
                Mã voucher sẽ được xác thực và áp dụng tự động khi bạn xác nhận đặt hàng.
              </p>
            </div>

            {/* 2. SPLIT-ORDER VISUALIZATION DIAGRAM */}
            <div className="bg-white dark:bg-zinc-900 rounded-[2.5rem] border border-zinc-200/60 dark:border-zinc-800/60 p-6 sm:p-8 shadow-sm">
              <h3 className="text-base font-bold text-zinc-900 dark:text-white pb-3 border-b border-zinc-100 dark:border-zinc-800/80 mb-5 flex items-center gap-2">
                <Sparkles className="h-5 w-5 text-brand-500" />
                Sơ đồ Phân tách Đơn hàng Tự động
              </h3>
              
              <p className="text-xs text-zinc-500 dark:text-zinc-400 leading-relaxed font-light mb-6">
                Vì mô hình VibeCart hỗ trợ nhiều nhà bán hàng (Creators) có kho vật lý riêng biệt, giỏ hàng của bạn sẽ được tự động gom nhóm theo từng Creator và tách thành các hóa đơn con tương ứng để tiện cho Creators xử lý:
              </p>

              {/* Flowchart nodes */}
              <div className="space-y-4">
                {Object.entries(groupedCheckout).map(([cId, group], index) => {
                  const grpOriginal = group.items.reduce((acc, item) => acc + item.originalPrice * item.quantity, 0);
                  const grpDiscount = group.items.reduce((acc, item) => acc + (item.discountPrice > 0 ? (item.originalPrice - item.discountPrice) * item.quantity : 0), 0);
                  const grpFinal = grpOriginal - grpDiscount;

                  return (
                    <div 
                      key={cId}
                      className="relative pl-6 border-l-2 border-dashed border-brand-200 dark:border-brand-900/60 pb-2"
                    >
                      {/* Floating node badge */}
                      <span className="absolute -left-[9.5px] top-0 flex h-4.5 w-4.5 items-center justify-center rounded-full bg-brand-500 text-[10px] font-bold text-white shadow-sm ring-2 ring-white">
                        {index + 1}
                      </span>

                      <div className="bg-zinc-50/50 dark:bg-zinc-950/20 rounded-2xl border border-zinc-200/50 dark:border-zinc-800/40 p-4">
                        <div className="flex justify-between items-center pb-2 border-b border-zinc-200/50 dark:border-zinc-800/20 mb-3">
                          <span className="text-xs font-extrabold text-zinc-900 dark:text-white flex items-center gap-1">
                            <Store className="h-3.5 w-3.5 text-brand-500" />
                            Đơn con: {group.creatorName}
                          </span>
                          <span className="text-xs font-black text-brand-600">
                            {new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(grpFinal)}
                          </span>
                        </div>

                        {/* List items in this sub-order */}
                        <div className="space-y-2">
                          {group.items.map((item) => (
                            <div key={item.variantId} className="flex justify-between items-center text-[10px] text-zinc-400 dark:text-zinc-500 font-light">
                              <span className="truncate pr-4">• {item.productName} ({item.variantName}) x {item.quantity}</span>
                              <span className="shrink-0 font-semibold text-zinc-700 dark:text-zinc-300">
                                {new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(
                                  (item.discountPrice > 0 ? item.discountPrice : item.originalPrice) * item.quantity
                                )}
                              </span>
                            </div>
                          ))}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

          </div>

          {/* RIGHT PANEL: BILLING SUMMARY & PAY BUTTON */}
          <div className="lg:col-span-5 bg-white dark:bg-zinc-900 rounded-[2.5rem] border border-zinc-200/60 dark:border-zinc-800/60 p-6 shadow-sm sticky top-24">
            <h3 className="text-base font-bold text-zinc-900 dark:text-white pb-3 border-b border-zinc-100 dark:border-zinc-800/80 mb-5 flex items-center gap-2">
              <CreditCard className="h-5 w-5 text-brand-500" />
              Tổng quát Hóa đơn đặt hàng
            </h3>

            {/* List all items to buy */}
            <div className="space-y-4 mb-6 max-h-48 overflow-y-auto pr-1 custom-scrollbar pb-3 border-b border-zinc-100 dark:border-zinc-800/80">
              {checkoutItems.map((item) => (
                <div key={item.variantId} className="flex gap-3 items-center">
                  <div className="w-10 h-10 rounded-xl bg-zinc-50 border border-zinc-100 overflow-hidden shrink-0">
                    <img src={item.thumbnailUrl} alt="" className="w-full h-full object-cover" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <h4 className="text-[11px] font-bold text-zinc-900 truncate">{item.productName}</h4>
                    <span className="text-[9px] text-zinc-400 font-light block mt-0.5">Phân loại: {item.variantName}</span>
                  </div>
                  <span className="text-[10px] text-zinc-400 shrink-0">x{item.quantity}</span>
                  <span className="text-xs font-bold text-zinc-800 shrink-0">
                    {new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(
                      (item.discountPrice > 0 ? item.discountPrice : item.originalPrice) * item.quantity
                    )}
                  </span>
                </div>
              ))}
            </div>

            {/* Price Calculations */}
            <div className="space-y-4 pb-5 border-b border-zinc-100 dark:border-zinc-800/80">
              <div className="flex justify-between items-center text-xs text-zinc-500 dark:text-zinc-400 font-light">
                <span>Tổng giá trị hàng gốc:</span>
                <span className="font-semibold text-zinc-900 dark:text-zinc-100">
                  {new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(totalGrossAmount)}
                </span>
              </div>

              {totalDiscountAmount > 0 && (
                <div className="flex justify-between items-center text-xs text-red-500 font-medium">
                  <span>Giảm giá từ Creator:</span>
                  <span>
                    -{new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(totalDiscountAmount)}
                  </span>
                </div>
              )}

              <div className="flex justify-between items-center text-xs text-zinc-500 dark:text-zinc-400 font-light">
                <span>Phí vận chuyển:</span>
                <span className="font-extrabold text-emerald-600 dark:text-emerald-400">
                  Miễn phí giao hàng
                </span>
              </div>

              <div className="flex justify-between items-center pt-4 border-t border-zinc-100 dark:border-zinc-800/80">
                <span className="text-sm font-extrabold text-zinc-900 dark:text-white">Tổng số tiền phải thanh toán:</span>
                <span className="text-lg font-black text-brand-600">
                  {new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(finalBillingAmount)}
                </span>
              </div>
            </div>

            {/* Idempotent Check Tag */}
            <div className="mt-6 text-[10px] text-zinc-400 dark:text-zinc-500 bg-zinc-50 dark:bg-zinc-950/30 border border-zinc-100 dark:border-zinc-800/40 p-3 rounded-xl leading-relaxed font-light">
              <div className="flex gap-2 items-center">
                <ShieldCheck className="h-4 w-4 text-brand-500 shrink-0" />
                <span>
                  Hệ thống tự động kích hoạt mã định danh **Idempotency Key** để ngăn chặn hoàn toàn giao dịch trùng lặp khi mạng chập chờn.
                </span>
              </div>
            </div>

            {/* Submit Button Triggering Form */}
            <button
              onClick={() => {
                const btn = document.getElementById("checkout-form-submit");
                if (btn) btn.click();
              }}
              disabled={isPlacing}
              className="w-full h-12 rounded-full bg-brand-500 hover:bg-brand-600 disabled:opacity-50 text-white font-bold text-xs shadow-xl shadow-brand-500/10 flex items-center justify-center gap-2 active:scale-[0.98] mt-6 transition-all duration-300"
            >
              {isPlacing ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Đang khởi tạo đơn hàng con...
                </>
              ) : (
                <>
                  Xác nhận đặt hàng & Tách hóa đơn con
                  <ArrowRight className="h-4 w-4" />
                </>
              )}
            </button>
          </div>

        </div>
      </div>
    </div>
  );
}
