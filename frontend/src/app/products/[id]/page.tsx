"use client";

import React, { useState, useEffect } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { 
  ShoppingBag, 
  ChevronLeft, 
  Tag, 
  Package, 
  Sparkles, 
  Info,
  User,
  ArrowRight,
  Loader2
} from "lucide-react";
import { productService } from "@/services/product.service";
import { Product, ProductVariant } from "@/types";
import { useCart } from "@/hooks/useCart";
import { useToast } from "@/context/ToastContext";
import { ROUTES } from "@/constants/routes";

export default function ProductDetailsPage() {
  const params = useParams();
  const router = useRouter();
  const toast = useToast();
  const { addToCart } = useCart();
  const productId = params.id as string;

  // States
  const [product, setProduct] = useState<Product | null>(null);
  const [selectedVariant, setSelectedVariant] = useState<ProductVariant | null>(null);
  const [selectedImage, setSelectedImage] = useState<string>("");
  const [quantity, setQuantity] = useState(1);
  const [isLoading, setIsLoading] = useState(true);

  // Load Product details
  useEffect(() => {
    async function loadProduct() {
      setIsLoading(true);
      try {
        const data = await productService.getProductById(productId);
        setProduct(data);
        
        // Auto-select first active variant if available
        if (data.variants && data.variants.length > 0) {
          const activeVar = data.variants.find((v) => v.status === "ACTIVE") || data.variants[0];
          setSelectedVariant(activeVar);
        }

        // Auto-select thumbnail or first image
        const thumb = data.images?.find((img) => img.isThumbnail)?.imageUrl || data.images?.[0]?.imageUrl || "";
        setSelectedImage(thumb);

      } catch (err) {
        console.error("Failed to load product:", err);
        toast.error("Lỗi lấy dữ liệu", "Không thể hiển thị thông tin sản phẩm này.");
      } finally {
        setIsLoading(false);
      }
    }
    loadProduct();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [productId]);

  // Adjust local quantity selection
  const handleQuantityChange = (val: number) => {
    if (!selectedVariant) return;
    const nextVal = quantity + val;
    if (nextVal < 1) return;
    if (nextVal > 100) {
      toast.warning("Giới hạn số lượng", "Bạn chỉ được chọn mua tối đa 100 sản phẩm một lần.");
      return;
    }
    if (nextVal > selectedVariant.availableStock) {
      toast.warning("Tồn kho không đủ", `Tồn kho khả dụng của biến thể này chỉ còn ${selectedVariant.availableStock} cái.`);
      return;
    }
    setQuantity(nextVal);
  };

  // Add Item to Cart Context
  const handleAddToCart = async () => {
    if (!product || !selectedVariant) return;
    await addToCart(selectedVariant, product, quantity);
  };

  // Buy Now
  const handleBuyNow = async () => {
    if (!product || !selectedVariant) return;
    await addToCart(selectedVariant, product, quantity);
    router.push(ROUTES.CART);
  };

  if (isLoading) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center min-h-[70vh] bg-zinc-50">
        <Loader2 className="h-10 w-10 text-brand-500 animate-spin mb-4" />
        <p className="text-sm text-zinc-500 font-light">Đang tải thông tin sản phẩm...</p>
      </div>
    );
  }

  if (!product) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center py-20 bg-zinc-50 text-center">
        <div className="h-14 w-14 rounded-2xl bg-zinc-100 flex items-center justify-center text-zinc-400 border border-zinc-200/50 mb-4">
          <Info className="h-7 w-7" />
        </div>
        <h3 className="text-base font-bold text-zinc-800">Sản phẩm không khả dụng</h3>
        <p className="text-xs text-zinc-400 mt-1 max-w-xs leading-relaxed font-light">
          Sản phẩm này đã ngừng bán hoặc bị xóa khỏi hệ thống bởi Creator/Admin.
        </p>
        <Link
          href={ROUTES.PRODUCTS}
          className="mt-6 px-5 py-2.5 rounded-full bg-brand-500 text-white text-xs font-semibold"
        >
          Quay lại cửa hàng
        </Link>
      </div>
    );
  }

  // Active price parameters
  const originalPrice = selectedVariant ? selectedVariant.price : 0;
  const discountPrice = selectedVariant ? selectedVariant.discountPrice : 0;
  const availableStock = selectedVariant ? selectedVariant.availableStock : 0;
  const isOutOfStock = availableStock === 0;

  const hasDiscount = discountPrice > 0 && discountPrice < originalPrice;

  return (
    <div className="flex-1 bg-zinc-50 px-4 sm:px-6 lg:px-8 py-10 transition-colors duration-300 relative">
      {/* Floating orbs */}
      <div className="absolute top-[15%] right-[5%] w-80 h-80 bg-brand-100/10 rounded-full blur-[100px] pointer-events-none" />
      <div className="absolute bottom-[10%] left-[5%] w-96 h-96 bg-brand-200/10 rounded-full blur-[120px] pointer-events-none" />

      <div className="max-w-6xl w-full mx-auto relative z-10">
        
        {/* Back Link */}
        <Link 
          href={ROUTES.PRODUCTS}
          className="inline-flex items-center gap-1.5 text-zinc-500 hover:text-zinc-900 text-xs font-semibold mb-8 group"
        >
          <ChevronLeft className="h-4 w-4 group-hover:-translate-x-0.5 transition-transform" />
          Quay lại cửa hàng
        </Link>

        {/* DETAILS GRID */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-10 lg:gap-14 bg-white rounded-[2.5rem] border border-zinc-200/60 p-6 sm:p-8 lg:p-10 shadow-sm">
          
          {/* 1. LEFT SIDE: IMAGE CAROUSEL */}
          <div className="flex flex-col gap-4.5">
            {/* Main Showcase Image */}
            <div className="relative w-full aspect-[4/3] rounded-[2rem] overflow-hidden bg-zinc-50 border border-zinc-200/50">
              {selectedImage ? (
                <img
                  src={selectedImage}
                  alt={product.name}
                  className="w-full h-full object-cover"
                />
              ) : (
                <div className="w-full h-full flex items-center justify-center text-zinc-300">
                  <Package className="h-16 w-16" />
                </div>
              )}

              {/* Discount Tag Overlay */}
              {hasDiscount && (
                <div className="absolute top-4 left-4 bg-red-500 text-white text-[10px] font-extrabold px-3 py-1.5 rounded-full uppercase tracking-wider flex items-center gap-1 shadow-md">
                  <Tag className="h-3.5 w-3.5" />
                  Đang giảm giá
                </div>
              )}
            </div>

            {/* List Album Images */}
            {product.images && product.images.length > 1 && (
              <div className="flex flex-wrap gap-3">
                {product.images
                  .sort((a, b) => a.sortOrder - b.sortOrder)
                  .map((img) => (
                    <button
                      key={img.id}
                      onClick={() => setSelectedImage(img.imageUrl)}
                      className={`relative w-16 h-16 rounded-xl overflow-hidden bg-zinc-50 border transition-all duration-200 ${
                        selectedImage === img.imageUrl
                          ? "border-brand-500 ring-2 ring-brand-500/20 scale-102"
                          : "border-zinc-200/70 hover:border-zinc-400"
                      }`}
                    >
                      <img src={img.imageUrl} alt="" className="w-full h-full object-cover" />
                    </button>
                  ))}
              </div>
            )}
          </div>

          {/* 2. RIGHT SIDE: SPECIFICATIONS */}
          <div className="flex flex-col">
            {/* Category Name */}
            <div className="inline-flex items-center gap-1.5 text-[11px] font-semibold text-brand-600 uppercase tracking-widest mb-2.5">
              <Sparkles className="h-3.5 w-3.5" />
              {product.categoryName || "Danh mục"}
            </div>

            {/* Name */}
            <h1 className="text-2xl sm:text-3xl font-extrabold text-zinc-900 leading-tight">
              {product.name}
            </h1>

            {/* Creator profile reference */}
            <div className="mt-3.5 flex items-center gap-2.5 py-3 border-y border-zinc-100">
              <div className="h-8.5 w-8.5 rounded-full bg-brand-50 flex items-center justify-center text-brand-600 border border-brand-200">
                <User className="h-4.5 w-4.5" />
              </div>
              <div className="flex flex-col">
                <span className="text-[10px] text-zinc-400">Được đăng bán bởi</span>
                <Link
                  href={ROUTES.CREATOR_PROFILE(product.creatorId)}
                  className="text-xs font-bold text-zinc-800 hover:text-brand-500 transition-colors flex items-center gap-1"
                >
                  {product.creatorName || "Creator"}
                  <ArrowRight className="h-3.5 w-3.5 text-zinc-400" />
                </Link>
              </div>
            </div>

            {/* Product description */}
            <p className="text-sm text-zinc-500 leading-relaxed font-light mt-5">
              {product.description || "Sản phẩm này hiện tại chưa được cập nhật thông tin mô tả chi tiết từ Creator..."}
            </p>

            {/* ═══════════════════════════════════════════════════════════════ */}
            {/* VARIANT SELECTOR GRID */}
            {/* ═══════════════════════════════════════════════════════════════ */}
            <div className="mt-8">
              <h3 className="text-xs font-bold text-zinc-700 uppercase tracking-wider mb-3.5">Chọn phân loại sản phẩm *</h3>
              <div className="grid grid-cols-2 gap-3 max-h-48 overflow-y-auto pr-1 custom-scrollbar">
                {product.variants?.map((v) => {
                  const active = selectedVariant?.id === v.id;
                  const variantOutOfStock = v.availableStock === 0;

                  return (
                    <button
                      key={v.id}
                      disabled={v.status !== "ACTIVE"}
                      onClick={() => {
                        setSelectedVariant(v);
                        setQuantity(1);
                      }}
                      className={`text-left p-3.5 rounded-2xl border transition-all duration-200 relative ${
                        v.status !== "ACTIVE"
                          ? "opacity-40 pointer-events-none bg-zinc-50"
                          : active
                            ? "border-brand-500 bg-brand-50/20 ring-1 ring-brand-500"
                            : "border-zinc-200/80 hover:border-zinc-400"
                      }`}
                    >
                      <div className="text-xs font-bold text-zinc-900 flex items-center justify-between">
                        <span className="truncate pr-1.5">{v.variantName}</span>
                        {variantOutOfStock && (
                          <span className="text-[9px] bg-red-100 text-red-600 px-1.5 py-0.5 rounded font-extrabold tracking-wider uppercase shrink-0">Hết</span>
                        )}
                      </div>
                      <div className="text-[10px] text-zinc-400 font-light mt-1.5">Mã: {v.skuCode}</div>
                      <div className="text-xs font-extrabold text-brand-600 mt-2 flex items-center justify-between">
                        <span>
                          {new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(
                            v.discountPrice > 0 ? v.discountPrice : v.price
                          )}
                        </span>
                        <span className="text-[10px] font-normal text-zinc-400">Tồn: {v.availableStock}</span>
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Price display panel */}
            <div className="mt-8 bg-zinc-50 rounded-2xl p-4.5 border border-zinc-200/50 flex items-center justify-between">
              <div className="flex flex-col">
                <span className="text-[10px] text-zinc-400 font-semibold uppercase tracking-wider mb-1">Giá bán thời gian thực</span>
                {hasDiscount ? (
                  <div className="flex items-baseline gap-2">
                    <span className="text-xl sm:text-2xl font-extrabold text-red-500">
                      {new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(discountPrice)}
                    </span>
                    <span className="text-xs text-zinc-400 line-through font-light">
                      {new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(originalPrice)}
                    </span>
                  </div>
                ) : (
                  <span className="text-xl sm:text-2xl font-extrabold text-zinc-900">
                    {new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(originalPrice || 0)}
                  </span>
                )}
              </div>
            </div>

            {/* Quantity selector & Available Stock check */}
            <div className="mt-8 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 pt-6 border-t border-zinc-100">
              <div className="flex flex-col">
                <span className="text-xs font-bold text-zinc-700 uppercase tracking-wider mb-1.5">Số lượng mua</span>
                <div className="flex items-center gap-3">
                  <div className="flex h-10 border border-zinc-200 rounded-xl bg-zinc-50 overflow-hidden items-center">
                    <button
                      disabled={isOutOfStock || quantity <= 1}
                      onClick={() => handleQuantityChange(-1)}
                      className="w-10 h-full hover:bg-zinc-100 disabled:opacity-40 text-sm font-semibold transition-colors"
                    >
                      -
                    </button>
                    <span className="w-12 text-center text-sm font-bold text-zinc-900">
                      {isOutOfStock ? 0 : quantity}
                    </span>
                    <button
                      disabled={isOutOfStock || quantity >= availableStock}
                      onClick={() => handleQuantityChange(1)}
                      className="w-10 h-full hover:bg-zinc-100 disabled:opacity-40 text-sm font-semibold transition-colors"
                    >
                      +
                    </button>
                  </div>
                  
                  <span className="text-xs text-zinc-400 font-light">
                    ({availableStock} cái có sẵn trong kho)
                  </span>
                </div>
              </div>

              {/* Status information badge */}
              {isOutOfStock && (
                <div className="inline-flex items-center gap-1.5 text-xs text-red-600 bg-red-50 border border-red-100 px-4 py-2 rounded-xl font-bold">
                  <Info className="h-4 w-4" />
                  Tạm thời cháy hàng
                </div>
              )}
            </div>

            {/* ACTION BUTTONS */}
            <div className="mt-8 flex flex-col sm:flex-row gap-4">
              <button
                disabled={isOutOfStock || !selectedVariant}
                onClick={handleAddToCart}
                className="flex-1 h-13 rounded-full border border-brand-500 text-brand-600 hover:bg-brand-500 hover:text-white font-bold text-sm shadow-md shadow-brand-500/5 active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none transition-all duration-300"
              >
                Thêm vào giỏ hàng
              </button>
              
              <button
                disabled={isOutOfStock || !selectedVariant}
                onClick={handleBuyNow}
                className="flex-1 h-13 rounded-full bg-brand-500 hover:bg-brand-600 text-white font-bold text-sm shadow-xl shadow-brand-500/20 active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none transition-all duration-300"
              >
                Mua ngay
              </button>
            </div>

          </div>

        </div>
      </div>
    </div>
  );
}
