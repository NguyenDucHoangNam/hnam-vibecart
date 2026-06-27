"use client";

import React, { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  ShoppingBag,
  Trash2,
  ChevronRight,
  ArrowRight,
  Store,
  AlertTriangle,
  Zap,
  Info,
  Gift,
  ArrowLeft,
  Loader2
} from "lucide-react";
import { useCart } from "@/hooks/useCart";
import { useAuth } from "@/hooks/useAuth";
import { CartItem } from "@/types";
import { useToast } from "@/context/ToastContext";
import { ROUTES } from "@/constants/routes";
import { CartSkeleton } from "@/components/skeletons/LoadingSkeletons";


export default function CartPage() {
  const router = useRouter();
  const toast = useToast();
  const { isAuthenticated } = useAuth();
  const {
    items,
    isLoading,
    updateQuantity,
    removeFromCart,
    clearCart
  } = useCart();
  const [selectedItems, setSelectedItems] = useState<Record<string, boolean>>({});
  useEffect(() => {
    if (items.length > 0) {
      const initialSelected: Record<string, boolean> = {};
      items.forEach((item) => {
        const isAvailable = (item.status === "AVAILABLE" || item.status === "INSUFFICIENT_STOCK") && item.availableStock > 0;
        if (isAvailable) {
          initialSelected[item.variantId] = true;
        }
      });
      setSelectedItems(initialSelected);
    }
  }, [items]);
  const handleToggleSelect = (variantId: string) => {
    setSelectedItems((prev) => ({
      ...prev,
      [variantId]: !prev[variantId],
    }));
  };
  const groupedItems = items.reduce<Record<string, { creatorName: string; items: CartItem[] }>>(
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
  const handleToggleGroup = (cId: string, itemGroup: CartItem[]) => {
    const activeGroupItems = itemGroup.filter((item) => (item.status === "AVAILABLE" || item.status === "INSUFFICIENT_STOCK") && item.availableStock > 0);
    if (activeGroupItems.length === 0) return;

    const allGroupSelected = activeGroupItems.every((item) => selectedItems[item.variantId]);

    setSelectedItems((prev) => {
      const next = { ...prev };
      activeGroupItems.forEach((item) => {
        next[item.variantId] = !allGroupSelected;
      });
      return next;
    });
  };
  const selectedCartItems = items.filter((item) => selectedItems[item.variantId]);

  const grossSubtotal = selectedCartItems.reduce((acc, item) => acc + item.originalPrice * item.quantity, 0);
  const discountSavings = selectedCartItems.reduce(
    (acc, item) => acc + (item.discountPrice > 0 ? (item.originalPrice - item.discountPrice) * item.quantity : 0),
    0
  );
  const finalAmount = grossSubtotal - discountSavings;
  const totalSelectedQuantity = selectedCartItems.reduce((acc, item) => acc + item.quantity, 0);
  const handleCheckout = () => {
    if (selectedCartItems.length === 0) {
      toast.warning("Chọn sản phẩm", "Vui lòng chọn ít nhất một sản phẩm khả dụng để thanh toán.");
      return;
    }

    if (!isAuthenticated) {
      toast.warning("Yêu cầu đăng nhập", "Bạn vui lòng đăng nhập để tiến hành đặt hàng.");
      router.push(ROUTES.LOGIN + `?redirect=${encodeURIComponent(ROUTES.CART)}`);
      return;
    }
    sessionStorage.setItem("checkout_items", JSON.stringify(selectedCartItems));
    router.push(ROUTES.CHECKOUT);
  };

  if (isLoading) {
    return <CartSkeleton />;
  }

  return (
    <div className="flex-1 bg-zinc-50 dark:bg-zinc-950 px-4 sm:px-6 lg:px-8 py-10 transition-colors duration-300 relative">
      <div className="absolute top-[10%] left-[5%] w-80 h-80 bg-brand-100/10 rounded-full blur-[100px] pointer-events-none" />
      <div className="absolute bottom-[20%] right-[5%] w-96 h-96 bg-brand-200/10 rounded-full blur-[120px] pointer-events-none" />

      <div className="max-w-6xl w-full mx-auto relative z-10">
        <div className="flex justify-between items-end mb-8 border-b border-zinc-200/50 dark:border-zinc-800/60 pb-5">
          <div>
            <h1 className="text-3xl font-extrabold tracking-tight text-zinc-900 dark:text-white flex items-center gap-3">
              <ShoppingBag className="h-8 w-8 text-brand-500" />
              Giỏ hàng của tôi
            </h1>
            <p className="text-sm text-zinc-500 dark:text-zinc-400 mt-1 font-light">
              Quản lý các mặt hàng được chọn mua từ Creators trước khi tạo lập hóa đơn.
            </p>
          </div>
          {items.length > 0 && (
            <button
              onClick={clearCart}
              className="text-xs text-zinc-400 hover:text-red-500 font-semibold transition-colors flex items-center gap-1.5"
            >
              <Trash2 className="h-4 w-4" />
              Xóa sạch giỏ
            </button>
          )}
        </div>

        {items.length === 0 ? (
          <div className="bg-white dark:bg-zinc-900 rounded-[2.5rem] border border-zinc-200/50 p-12 sm:p-20 text-center shadow-sm">
            <div className="h-16 w-16 bg-zinc-50 dark:bg-zinc-950 text-zinc-400 dark:text-zinc-500 rounded-2xl flex items-center justify-center mb-5 mx-auto border border-zinc-200/60">
              <ShoppingBag className="h-8 w-8" />
            </div>
            <h3 className="text-lg font-bold text-zinc-850 dark:text-zinc-100">Giỏ hàng của bạn đang trống</h3>
            <p className="text-xs text-zinc-400 dark:text-zinc-500 mt-1 max-w-xs mx-auto leading-relaxed font-light">
              Hãy quay lại cửa hàng để lựa chọn các sản phẩm ưng ý từ các nhà sáng tạo yêu thích của bạn!
            </p>
            <Link
              href={ROUTES.PRODUCTS}
              className="mt-8 inline-flex h-11 items-center gap-2 rounded-full bg-brand-500 px-6 text-xs font-semibold text-white shadow-md shadow-brand-500/10 hover:bg-brand-600 active:scale-[0.98] transition-all duration-200"
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              Tiếp tục mua sắm
            </Link>
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 items-start">
            <div className="lg:col-span-2 space-y-6">
              {Object.entries(groupedItems).map(([cId, group]) => {
                const activeGroup = group.items.filter((i) => (i.status === "AVAILABLE" || i.status === "INSUFFICIENT_STOCK") && i.availableStock > 0);
                const allSelected = activeGroup.length > 0 && activeGroup.every((i) => selectedItems[i.variantId]);

                return (
                  <div
                    key={cId}
                    className="bg-white dark:bg-zinc-900 rounded-[2rem] border border-zinc-200/60 dark:border-zinc-800/60 shadow-sm overflow-hidden"
                  >
                    <div className="bg-zinc-50/50 dark:bg-zinc-950/20 px-6 py-4.5 border-b border-zinc-200/60 dark:border-zinc-800/50 flex items-center justify-between">
                      <div className="flex items-center gap-2.5">
                        <input
                          type="checkbox"
                          disabled={activeGroup.length === 0}
                          checked={allSelected}
                          onChange={() => handleToggleGroup(cId, group.items)}
                          className="h-4.5 w-4.5 rounded border-zinc-300 dark:border-zinc-700 text-brand-500 focus:ring-brand-500 cursor-pointer disabled:opacity-40"
                        />
                        <div className="flex items-center gap-2 font-bold text-zinc-900 dark:text-white text-sm">
                          <Store className="h-4.5 w-4.5 text-brand-500" />
                          {group.creatorName}
                        </div>
                      </div>
                      <span className="text-[10px] bg-brand-50 dark:bg-brand-950/20 text-brand-700 dark:text-brand-400 font-semibold px-2.5 py-1 rounded-full border border-brand-100 dark:border-brand-900/30">
                        {group.items.length} mặt hàng
                      </span>
                    </div>
                    <div className="divide-y divide-zinc-100 dark:divide-zinc-800/60">
                      {group.items.map((item) => {
                        const isAvailable = (item.status === "AVAILABLE" || item.status === "INSUFFICIENT_STOCK") && item.availableStock > 0;
                        const isOutOfStock = item.status === "OUT_OF_STOCK" || item.availableStock === 0;
                        const isInactive = item.status !== "AVAILABLE" && item.status !== "OUT_OF_STOCK" && item.status !== "INSUFFICIENT_STOCK";

                        const hasItemDiscount = item.discountPrice > 0 && item.discountPrice < item.originalPrice;
                        const activePrice = hasItemDiscount ? item.discountPrice : item.originalPrice;

                        return (
                          <div
                            key={item.variantId}
                            className={`p-6 flex flex-col sm:flex-row gap-4 sm:items-center relative ${!isAvailable ? "bg-zinc-50/40 dark:bg-zinc-950/5" : ""
                              }`}
                          >
                            <div className="flex items-center gap-3">
                              <input
                                type="checkbox"
                                disabled={!isAvailable}
                                checked={!!selectedItems[item.variantId]}
                                onChange={() => handleToggleSelect(item.variantId)}
                                className="h-4.5 w-4.5 rounded border-zinc-300 dark:border-zinc-700 text-brand-500 focus:ring-brand-500 cursor-pointer disabled:opacity-40"
                              />
                              <div className="relative w-20 h-20 bg-zinc-50 dark:bg-zinc-950 border border-zinc-200/50 rounded-2xl overflow-hidden shrink-0">
                                {item.thumbnailUrl ? (
                                  <img
                                    src={item.thumbnailUrl}
                                    alt={item.productName}
                                    className="w-full h-full object-cover"
                                  />
                                ) : (
                                  <div className="w-full h-full flex items-center justify-center text-zinc-300">
                                    <ShoppingBag className="h-6 w-6" />
                                  </div>
                                )}
                              </div>
                            </div>
                            <div className="flex-1 flex flex-col min-w-0 sm:pl-2">
                              <h4 className="text-sm font-bold text-zinc-900 dark:text-white truncate">
                                {item.productName}
                              </h4>
                              <span className="text-[10px] text-zinc-400 dark:text-zinc-500 font-light mt-1">
                                Phân loại: {item.variantName}
                              </span>
                              {item.status === "INSUFFICIENT_STOCK" && (
                                <div className="mt-2.5 inline-flex items-center gap-1.5 text-[10px] px-2.5 py-1.5 rounded-lg font-bold w-fit bg-amber-50 dark:bg-amber-950/20 text-amber-600 dark:text-amber-400 border border-amber-100 dark:border-amber-900/30">
                                  <AlertTriangle className="h-3 w-3" />
                                  Số lượng vượt quá tồn kho khả dụng ({item.availableStock.toLocaleString("vi-VN")})
                                </div>
                              )}
                              {isOutOfStock && (
                                <div className="mt-2.5 inline-flex items-center gap-1.5 text-[10px] px-2.5 py-1.5 rounded-lg font-bold w-fit bg-red-50 dark:bg-red-950/20 text-red-600 dark:text-red-400 border border-red-100 dark:border-red-900/30">
                                  <AlertTriangle className="h-3 w-3" />
                                  Tạm thời hết hàng vật lý
                                </div>
                              )}
                              {isInactive && (
                                <div className="mt-2.5 inline-flex items-center gap-1.5 text-[10px] px-2.5 py-1.5 rounded-lg font-bold w-fit bg-red-50 dark:bg-red-950/20 text-red-600 dark:text-red-400 border border-red-100 dark:border-red-900/30">
                                  <AlertTriangle className="h-3 w-3" />
                                  Sản phẩm đã bị xóa hoặc ngừng bán
                                </div>
                              )}
                            </div>
                            <div className="flex sm:flex-col items-center sm:items-end justify-between sm:justify-center gap-4.5 sm:text-right ml-auto shrink-0 w-full sm:w-auto border-t sm:border-t-0 pt-4 sm:pt-0">
                              <div className="flex h-8.5 border border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-950 rounded-lg overflow-hidden items-center">
                                <button
                                  disabled={!isAvailable || item.quantity <= 1}
                                  onClick={() => updateQuantity(item.variantId, item.quantity - 1)}
                                  className="w-8.5 h-full hover:bg-zinc-100 disabled:opacity-40 text-xs font-semibold"
                                >
                                  -
                                </button>
                                <span className="w-10 text-center text-xs font-bold text-zinc-900 dark:text-white">
                                  {item.quantity}
                                </span>
                                <button
                                  disabled={!isAvailable || item.quantity >= item.availableStock}
                                  onClick={() => updateQuantity(item.variantId, item.quantity + 1)}
                                  className="w-8.5 h-full hover:bg-zinc-100 disabled:opacity-40 text-xs font-semibold"
                                >
                                  +
                                </button>
                              </div>
                              <div className="flex flex-col sm:items-end">
                                <span className="text-sm font-extrabold text-zinc-950 dark:text-white">
                                  {new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(
                                    activePrice * item.quantity
                                  )}
                                </span>
                                {hasItemDiscount && (
                                  <span className="text-[10px] text-zinc-400 line-through mt-0.5">
                                    {new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(
                                      item.originalPrice * item.quantity
                                    )}
                                  </span>
                                )}
                              </div>
                              <button
                                onClick={() => removeFromCart(item.variantId)}
                                className="hidden sm:block text-zinc-300 hover:text-zinc-600 dark:text-zinc-700 dark:hover:text-zinc-400 transition-colors p-1"
                              >
                                <Trash2 className="h-4.5 w-4.5" />
                              </button>
                            </div>
                            <button
                              onClick={() => removeFromCart(item.variantId)}
                              className="sm:hidden absolute top-4 right-4 text-zinc-300 hover:text-red-500 p-1"
                            >
                              <Trash2 className="h-4.5 w-4.5" />
                            </button>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                );
              })}
            </div>
            <div className="bg-white dark:bg-zinc-900 rounded-[2rem] border border-zinc-200/60 dark:border-zinc-800/60 p-6 shadow-sm sticky top-24">
              <h3 className="text-base font-bold text-zinc-900 dark:text-white pb-3 border-b border-zinc-100 dark:border-zinc-800/80 mb-5 flex items-center gap-2">
                <Gift className="h-5 w-5 text-brand-500" />
                Hóa đơn tạm tính
              </h3>

              <div className="space-y-4">
                <div className="flex justify-between items-center text-xs text-zinc-500 dark:text-zinc-400 font-light">
                  <span>Tổng tiền hàng gốc:</span>
                  <span className="font-semibold text-zinc-900 dark:text-zinc-100">
                    {new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(grossSubtotal)}
                  </span>
                </div>
                {discountSavings > 0 && (
                  <div className="flex justify-between items-center text-xs text-red-500 font-medium">
                    <span>Giảm giá sản phẩm:</span>
                    <span>
                      -{new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(discountSavings)}
                    </span>
                  </div>
                )}
                <div className="flex justify-between items-center text-xs text-zinc-500 dark:text-zinc-400 font-light">
                  <span>Mã Voucher giảm:</span>
                  <span className="font-semibold text-zinc-400 dark:text-zinc-500">
                    Chưa áp dụng
                  </span>
                </div>
                <div className="flex justify-between items-center pt-4 border-t border-zinc-100 dark:border-zinc-800/80">
                  <span className="text-sm font-extrabold text-zinc-900 dark:text-white">Thành tiền tạm tính:</span>
                  <span className="text-lg font-black text-brand-600">
                    {new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(finalAmount)}
                  </span>
                </div>
              </div>
              <div className="mt-6 text-[10px] text-zinc-400 dark:text-zinc-500 bg-zinc-50 dark:bg-zinc-950/30 border border-zinc-100 dark:border-zinc-800/40 p-3 rounded-xl leading-relaxed font-light">
                <div className="flex gap-2">
                  <Info className="h-4.5 w-4.5 text-brand-500 shrink-0" />
                  <span>
                    Chính sách **Bảo mật nhất quán giá** bảo lưu mức giá mới nhất được cập nhật tự động từ PostgreSQL trước khi tiến hành Checkout.
                  </span>
                </div>
              </div>
              <button
                onClick={handleCheckout}
                disabled={totalSelectedQuantity === 0}
                className="w-full h-12 rounded-full bg-brand-500 hover:bg-brand-600 disabled:opacity-50 text-white font-bold text-xs shadow-xl shadow-brand-500/10 flex items-center justify-center gap-2 active:scale-[0.98] mt-6 transition-all duration-300"
              >
                Tiến hành Đặt hàng ({totalSelectedQuantity})
                <ArrowRight className="h-4 w-4" />
              </button>
            </div>

          </div>
        )}

      </div>
    </div>
  );
}
