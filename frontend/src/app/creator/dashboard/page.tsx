"use client";

import React, { useState, useEffect, useTransition } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { 
  Plus, 
  Search, 
  Edit, 
  Trash2, 
  Package, 
  ChevronLeft, 
  ChevronRight, 
  Loader2, 
  X, 
  AlertTriangle,
  RotateCw,
  ShoppingBag,
  Info,
  Calendar,
  Layers,
  FileSpreadsheet,
  Truck,
  Store,
  DollarSign,
  Barcode,
  Image as ImageIcon,
  UploadCloud
} from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useToast } from "@/context/ToastContext";
import { Forbidden } from "@/components/common/Forbidden";
import { api } from "@/lib/api-client";
import { uploadFilePresigned, uploadFilesPresigned } from "@/services/media.service";
import { productService } from "@/services/product.service";
import { categoryService } from "@/services/category.service";
import { orderService } from "@/services/order.service";
import { ROUTES } from "@/constants/routes";
import { Product, ProductVariant, Category, Order, InventoryHistoryResponse } from "@/types";

const formatNumberInputString = (val: string) => {
  const clean = val.replace(/\D/g, "");
  if (!clean) return "";
  return Number(clean).toLocaleString("vi-VN");
};

const isVideoUrl = (url: string): boolean => {
  if (!url) return false;
  const cleanUrl = url.toLowerCase().split('?')[0];
  return cleanUrl.endsWith('.mp4') || 
         cleanUrl.endsWith('.webm') || 
         cleanUrl.endsWith('.ogg') || 
         cleanUrl.endsWith('.mov') || 
         cleanUrl.endsWith('.mkv') ||
         url.includes('video/');
};

export default function CreatorDashboard() {
  const { user, isAuthenticated, isLoading: isAuthLoading } = useAuth();
  const toast = useToast();
  const router = useRouter();
  const [isPending, startTransition] = useTransition();
  const [activeTab, setActiveTab] = useState<"products" | "inventory" | "fulfillment">("products");
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [totalProducts, setTotalProducts] = useState(0);
  const [prodPage, setProdPage] = useState(0);
  const [isProdLoading, setIsProdLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null);
  const [productForm, setProductForm] = useState({
    name: "",
    description: "",
    categoryId: "",
    imageUrl: "",
  });
  const [isUploadingImage, setIsUploadingImage] = useState(false);
  const spuImageInputRef = React.useRef<HTMLInputElement>(null);

  const [galleryImages, setGalleryImages] = useState<string[]>([]);
  const [isUploadingGallery, setIsUploadingGallery] = useState(false);
  const galleryImagesInputRef = React.useRef<HTMLInputElement>(null);

  const [variantForms, setVariantForms] = useState<Array<{
    skuCode: string;
    variantName: string;
    price: string;
    discountPrice: string;
    initialQuantity: string;
    isNew?: boolean;
  }>>([{ skuCode: "", variantName: "", price: "", discountPrice: "0", initialQuantity: "0", isNew: true }]);
  const [selectedVariant, setSelectedVariant] = useState<ProductVariant | null>(null);
  const [inventoryHistory, setInventoryHistory] = useState<InventoryHistoryResponse[]>([]);
  const [isHistoryLoading, setIsHistoryLoading] = useState(false);
  const [adjustQty, setAdjustQty] = useState("");
  const [adjustReason, setAdjustReason] = useState("Nhập kho bổ sung định kỳ");
  const [orders, setOrders] = useState<Order[]>([]);
  const [totalOrders, setTotalOrders] = useState(0);
  const [orderPage, setOrderPage] = useState(0);
  const [activeOrderStatus, setActiveOrderStatus] = useState("");
  const [isOrdersLoading, setIsOrdersLoading] = useState(false);
  const [trackingNumbers, setTrackingNumbers] = useState<Record<string, string>>({});
  const [isFulfilling, setIsFulfilling] = useState<Record<string, boolean>>({});
  useEffect(() => {
    if (!isAuthLoading) {
      if (!isAuthenticated) {
        toast.warning("Yêu cầu đăng nhập", "Vui lòng đăng nhập để truy cập không gian Creator.");
        router.push(ROUTES.LOGIN);
      } else if (!user?.roles?.includes("ROLE_CREATOR")) {
        toast.error("Không có quyền truy cập", "Tài khoản của bạn chưa kích hoạt vai trò Creator.");
        router.push(ROUTES.HOME);
      }
    }
  }, [isAuthenticated, isAuthLoading, router, user]);
  useEffect(() => {
    async function fetchCats() {
      try {
        const data = await categoryService.getCategoriesTree();
        const leaves: Category[] = [];
        function traverse(cats: Category[]) {
          cats.forEach((c) => {
            if (!c.children || c.children.length === 0) {
              leaves.push(c);
            } else {
              traverse(c.children);
            }
          });
        }
        traverse(data || []);
        setCategories(leaves);
      } catch (err) {
        console.error("Failed to fetch category dropdown:", err);
      }
    }
    if (isAuthenticated) fetchCats();
  }, [isAuthenticated]);
  const fetchProducts = React.useCallback(async () => {
    if (!user) return;
    setIsProdLoading(true);
    try {
      const response = await productService.getProductsByCreator(user.id, {
        page: prodPage,
        size: 10,
      });
      setProducts(response.content || []);
      setTotalProducts(response.totalElements || 0);
    } catch (error) {
      console.error("Failed to load creator products:", error);
    } finally {
      setIsProdLoading(false);
    }
  }, [prodPage, user]);

  useEffect(() => {
    if (isAuthenticated && activeTab === "products") {
      fetchProducts();
    }
  }, [prodPage, searchQuery, isAuthenticated, activeTab, fetchProducts]);
  const handleVariantFormChange = (index: number, field: string, val: string) => {
    const updated = [...variantForms];
    if (field === "price" || field === "discountPrice" || field === "initialQuantity") {
      (updated[index] as any)[field] = formatNumberInputString(val);
    } else {
      (updated[index] as any)[field] = val;
    }
    setVariantForms(updated);
  };

  const handleAddVariantRow = () => {
    setVariantForms([...variantForms, { skuCode: "", variantName: "", price: "", discountPrice: "0", initialQuantity: "0", isNew: true }]);
  };

  const handleRemoveVariantRow = (index: number) => {
    if (variantForms.length === 1) return;
    setVariantForms(variantForms.filter((_, idx) => idx !== index));
  };

  const handleSpuImageClick = () => {
    if (spuImageInputRef.current) {
      spuImageInputRef.current.click();
    }
  };

  const handleSpuImageChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const isVideo = file.type.startsWith("video/");
    const maxSize = isVideo ? 50 * 1024 * 1024 : 5 * 1024 * 1024;
    const maxSizeStr = isVideo ? "50MB" : "5MB";

    if (file.size > maxSize) {
      toast.error("Tải tệp thất bại", `Kích thước ${isVideo ? "video" : "ảnh"} vượt quá giới hạn cho phép (${maxSizeStr}).`);
      return;
    }

    setIsUploadingImage(true);
    const formData = new FormData();
    formData.append("file", file);
    formData.append("folder", "products");

    try {
      const result = await uploadFilePresigned({ file, folder: "products" });
      setProductForm(prev => ({ ...prev, imageUrl: result.url }));
      toast.success("Tải tệp thành công", `${isVideo ? "Video" : "Ảnh"} đại diện sản phẩm đã được cập nhật.`);
    } catch (err: any) {
      console.error("SPU image upload error:", err);
      toast.error("Tải tệp thất bại", err.data?.message || err.message || "Không thể tải tệp lên máy chủ.");
    } finally {
      setIsUploadingImage(false);
    }
  };

  const handleGalleryImagesClick = () => {
    if (galleryImagesInputRef.current) {
      galleryImagesInputRef.current.click();
    }
  };

  const handleGalleryImagesChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;

    const fileList = Array.from(files);
    for (const file of fileList) {
      const isVideo = file.type.startsWith("video/");
      const maxSize = isVideo ? 50 * 1024 * 1024 : 5 * 1024 * 1024;
      const maxSizeStr = isVideo ? "50MB" : "5MB";
      if (file.size > maxSize) {
        toast.error("Tải tệp thất bại", `Tệp "${file.name}" (${isVideo ? "video" : "ảnh"}) vượt quá kích thước cho phép (${maxSizeStr}).`);
        return;
      }
    }

    setIsUploadingGallery(true);

    try {
      const results = await uploadFilesPresigned(fileList, "products");
      const newUrls = results.map(item => item.url);
      setGalleryImages(prev => [...prev, ...newUrls]);
      toast.success("Tải tệp thành công", `Đã tải lên thêm ${newUrls.length} tệp chi tiết.`);
    } catch (err: any) {
      console.error("Batch image upload error:", err);
      toast.error("Tải ảnh thất bại", err.data?.message || err.message || "Không thể tải các ảnh chi tiết lên máy chủ.");
    } finally {
      setIsUploadingGallery(false);
      if (galleryImagesInputRef.current) {
        galleryImagesInputRef.current.value = "";
      }
    }
  };

  const handleRemoveGalleryImage = (index: number) => {
    setGalleryImages(prev => prev.filter((_, idx) => idx !== index));
  };
  const openCreateModal = () => {
    setSelectedProduct(null);
    setProductForm({ name: "", description: "", categoryId: "", imageUrl: "" });
    setGalleryImages([]);
    setVariantForms([{ skuCode: "", variantName: "", price: "", discountPrice: "0", initialQuantity: "0" }]);
    setIsCreateOpen(true);
  };
  const handleSaveProductSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!productForm.name.trim() || !productForm.categoryId) {
      toast.warning("Thiếu thông tin", "Vui lòng điền tên SPU và chọn danh mục lá.");
      return;
    }
    const invalidVar = variantForms.some((v) => !v.skuCode.trim() || !v.variantName.trim() || !v.price);
    if (invalidVar) {
      toast.warning("Biến thể thiếu thông tin", "Vui lòng nhập đầy đủ SKU code, tên biến thể và giá bán.");
      return;
    }

    startTransition(async () => {
      try {
        const payload = {
          name: productForm.name.trim(),
          description: productForm.description.trim(),
          categoryId: productForm.categoryId,
          images: [
            ...(productForm.imageUrl.trim() 
              ? [{ imageUrl: productForm.imageUrl.trim(), isThumbnail: true, sortOrder: 0 }] 
              : []),
            ...galleryImages.map((url, idx) => ({
              imageUrl: url,
              isThumbnail: false,
              sortOrder: idx + 1
            }))
          ],
          variants: variantForms.map((v) => ({
            skuCode: v.skuCode.trim(),
            variantName: v.variantName.trim(),
            price: Number(v.price.replace(/\D/g, "")),
            discountPrice: Number(v.discountPrice ? v.discountPrice.replace(/\D/g, "") : 0),
            initialQuantity: (selectedProduct && !v.isNew) ? 0 : Number(v.initialQuantity ? v.initialQuantity.replace(/\D/g, "") : 0)
          }))
        };

        if (selectedProduct) {
          await productService.updateProduct(selectedProduct.id, payload as any);
          toast.success("Cập nhật thành công", `Đã lưu cập nhật cho sản phẩm "${productForm.name}".`);
        } else {
          await productService.createProduct(payload as any);
          toast.success("Tạo thành công", `Sản phẩm "${productForm.name}" đã được tạo với các biến thể.`);
        }
        
        setIsCreateOpen(false);
        setProdPage(0);
        fetchProducts();
      } catch (err: any) {
        console.error("Save product failed:", err);
        const errMsg = err?.data?.message || err?.message || "Không thể lưu trữ thông tin sản phẩm.";
        toast.error("Lỗi lưu trữ", errMsg);
      }
    });
  };
  const handleDeleteProduct = async (product: Product) => {
    if (!window.confirm(`Bạn có chắc chắn muốn xóa sản phẩm SPU "${product.name}"? Tồn kho tương ứng sẽ bị ẩn.`)) {
      return;
    }

    try {
      await productService.deleteProduct(product.id);
      toast.success("Đã xóa mềm", `Sản phẩm "${product.name}" đã được đánh dấu ẩn khỏi Store.`);
      fetchProducts();
    } catch (err: any) {
      console.error("Delete product failed:", err);
      toast.error("Lỗi xóa", "Không thể xóa sản phẩm này khỏi hệ thống.");
    }
  };
  const fetchInventoryHistory = React.useCallback(async (varId: string) => {
    setIsHistoryLoading(true);
    try {
      const response = await api.get<InventoryHistoryResponse[]>(
        `/products/variants/${varId}/inventory/history`
      );
      setInventoryHistory(response || []);
    } catch (err) {
      console.error("Failed to load inventory history ledger:", err);
      setInventoryHistory([]);
    } finally {
      setIsHistoryLoading(false);
    }
  }, []);

  useEffect(() => {
    if (selectedVariant && activeTab === "inventory") {
      fetchInventoryHistory(selectedVariant.id);
    }
  }, [selectedVariant, activeTab, fetchInventoryHistory]);
  useEffect(() => {
    if (selectedVariant && products.length > 0) {
      for (const p of products) {
        const found = p.variants?.find(v => v.id === selectedVariant.id);
        if (found) {
          setSelectedVariant(found);
          break;
        }
      }
    }
  }, [products]);
  const handleAdjustInventorySubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedVariant) return;
    const adjustment = parseInt(adjustQty);
    if (isNaN(adjustment) || adjustment === 0) {
      toast.warning("Nhập số lượng", "Vui lòng nhập lượng điều chỉnh tăng (+) hoặc giảm (-).");
      return;
    }

    if (selectedVariant.quantity + adjustment < 0) {
      toast.warning("Hao hụt âm kho", "Số lượng điều chỉnh giảm vượt quá tồn kho thực tế hiện có.");
      return;
    }

    startTransition(async () => {
      try {
        await productService.adjustInventory(selectedVariant.id, adjustment, adjustReason.trim());
        toast.success("Điều chỉnh kho thành công", `Tồn kho vật lý đã được ghi nhận cập nhật (${adjustment > 0 ? "+" : ""}${adjustment}).`);
        setSelectedVariant(prev => prev ? {
          ...prev,
          quantity: prev.quantity + adjustment,
          availableStock: prev.availableStock + adjustment
        } : null);
        setAdjustQty("");
        fetchProducts();
        fetchInventoryHistory(selectedVariant.id);
      } catch (err: any) {
        console.error("Failed to adjust inventory:", err);
        toast.error("Lỗi điều chỉnh", err?.message || "Không thể điều chỉnh tồn kho.");
      }
    });
  };
  const fetchCreatorOrders = React.useCallback(async () => {
    setIsOrdersLoading(true);
    try {
      const response = await orderService.getCreatorOrders(activeOrderStatus || undefined, orderPage, 10);
      setOrders(response.content || []);
      setTotalOrders(response.totalElements || 0);
    } catch (err) {
      console.error("Failed to load creator orders:", err);
    } finally {
      setIsOrdersLoading(false);
    }
  }, [activeOrderStatus, orderPage]);

  useEffect(() => {
    if (isAuthenticated && activeTab === "fulfillment") {
      fetchCreatorOrders();
    }
  }, [orderPage, activeOrderStatus, isAuthenticated, activeTab, fetchCreatorOrders]);
  const handleShipOrder = async (orderId: string, orderCode: string) => {
    const tracking = trackingNumbers[orderId] || "";
    if (!tracking.trim()) {
      toast.warning("Nhập mã vận đơn", `Vui lòng nhập mã vận đơn (Tracking number) để gửi hàng đơn "${orderCode}".`);
      return;
    }

    setIsFulfilling(prev => ({ ...prev, [orderId]: true }));
    try {
      await orderService.updateOrderStatus(orderId, "SHIPPED", tracking.trim());
      toast.success("Gửi hàng thành công", `Đơn hàng con "${orderCode}" đã được chuyển sang trạng thái SHIPPED.`);
      fetchCreatorOrders();
    } catch (err: any) {
      console.error("Fulfillment: Fail to ship order", err);
      toast.error("Lỗi gửi hàng", err?.message || "Không thể chuyển trạng thái đơn hàng.");
    } finally {
      setIsFulfilling(prev => ({ ...prev, [orderId]: false }));
    }
  };
  const handleDeliverOrder = async (orderId: string, orderCode: string) => {
    if (!window.confirm(`Xác nhận hoàn tất giao thành công đơn hàng con "${orderCode}"?`)) {
      return;
    }

    setIsFulfilling(prev => ({ ...prev, [orderId]: true }));
    try {
      await orderService.updateOrderStatus(orderId, "DELIVERED");
      toast.success("Hoàn tất giao hàng", `Đơn hàng con "${orderCode}" đã khép lại chu trình giao vận (DELIVERED).`);
      fetchCreatorOrders();
    } catch (err: any) {
      console.error("Fulfillment: Fail to deliver order", err);
      toast.error("Lỗi cập nhật", err?.message || "Không thể hoàn tất đơn hàng.");
    } finally {
      setIsFulfilling(prev => ({ ...prev, [orderId]: false }));
    }
  };

  if (isAuthLoading) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center min-h-[70vh] bg-zinc-50 dark:bg-zinc-950">
        <Loader2 className="h-10 w-10 text-brand-500 animate-spin mb-4" />
        <p className="text-sm text-zinc-500 dark:text-zinc-400 font-light">Đang xác thực quyền Creator...</p>
      </div>
    );
  }

  if (!isAuthenticated || !user?.roles?.includes("ROLE_CREATOR")) {
    return <Forbidden />;
  }

  return (
    <div className="flex-1 flex flex-col bg-zinc-50 px-4 sm:px-6 lg:px-8 py-10 transition-colors duration-300 relative">
      <div className="absolute top-[5%] right-[10%] w-72 h-72 bg-brand-100/10 rounded-full blur-[100px] pointer-events-none" />
      <div className="absolute bottom-[10%] left-[5%] w-96 h-96 bg-brand-200/10 rounded-full blur-[120px] pointer-events-none" />

      <div className="max-w-6xl w-full mx-auto relative z-10 flex-1 flex flex-col">
        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-8">
          <div>
            <h1 className="text-3xl font-extrabold tracking-tight text-zinc-900 flex items-center gap-2.5">
              <Store className="h-8 w-8 text-brand-500" />
              Kênh Nhà sáng tạo (Creator)
            </h1>
            <p className="text-sm text-zinc-500 mt-1 font-light">
              Quản lý sản phẩm, theo dõi tồn kho phiên bản, và xử lý hoàn tất đơn hàng nhanh chóng.
            </p>
          </div>
          
          {activeTab === "products" && (
            <button
              onClick={openCreateModal}
              className="flex h-11 items-center gap-2 rounded-full bg-brand-500 px-6 text-sm font-semibold text-white shadow-lg shadow-brand-500/20 hover:bg-brand-600 active:scale-[0.98] transition-all duration-200"
            >
              <Plus className="h-4 w-4" />
              Đăng sản phẩm mới
            </button>
          )}
        </div>
        <div className="bg-white border border-zinc-200/60 rounded-2xl p-2 mb-6 flex overflow-x-auto gap-1 shadow-sm shrink-0">
          <button
            onClick={() => setActiveTab("products")}
            className={`px-5 py-2.5 rounded-xl text-xs font-bold whitespace-nowrap transition-all duration-200 flex items-center gap-2 ${
              activeTab === "products"
                ? "bg-brand-500 text-white shadow-sm"
                : "text-zinc-500 hover:bg-zinc-50"
            }`}
          >
            <Layers className="h-4 w-4" />
            Quản lý Sản phẩm & Phiên bản
          </button>
          
          <button
            onClick={() => setActiveTab("inventory")}
            className={`px-5 py-2.5 rounded-xl text-xs font-bold whitespace-nowrap transition-all duration-200 flex items-center gap-2 ${
              activeTab === "inventory"
                ? "bg-brand-500 text-white shadow-sm"
                : "text-zinc-500 hover:bg-zinc-50"
            }`}
          >
            <FileSpreadsheet className="h-4 w-4" />
            Nhật ký & Tồn kho sản phẩm
          </button>

          <button
            onClick={() => setActiveTab("fulfillment")}
            className={`px-5 py-2.5 rounded-xl text-xs font-bold whitespace-nowrap transition-all duration-200 flex items-center gap-2 ${
              activeTab === "fulfillment"
                ? "bg-brand-500 text-white shadow-sm"
                : "text-zinc-500 hover:bg-zinc-50"
            }`}
          >
            <Truck className="h-4 w-4" />
            Xử lý Đơn hàng & Giao nhận
          </button>
        </div>
        {activeTab === "products" && (
          <div className="bg-white border border-zinc-200/60 rounded-3xl p-5 shadow-sm space-y-4">
            <div className="flex justify-between items-center pb-4 border-b border-zinc-100">
              <h3 className="text-sm font-bold text-zinc-900">Danh sách Sản phẩm</h3>
              <div className="flex h-9 border rounded-xl overflow-hidden px-3 bg-zinc-50 border-zinc-200 items-center gap-2 max-w-xs w-full">
                <Search className="h-4 w-4 text-zinc-400" />
                <input
                  type="text"
                  placeholder="Tìm kiếm sản phẩm..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="w-full text-xs bg-transparent border-0 focus:outline-none focus:ring-0"
                />
              </div>
            </div>

            {isProdLoading ? (
              <div className="flex flex-col items-center justify-center py-20">
                <Loader2 className="h-8 w-8 text-brand-500 animate-spin mb-3" />
                <p className="text-xs text-zinc-400">Đang tải danh sách sản phẩm...</p>
              </div>
            ) : products.length === 0 ? (
              <div className="text-center py-20">
                <div className="h-14 w-14 rounded-2xl bg-zinc-50 text-zinc-400 flex items-center justify-center mb-4 border mx-auto">
                  <Package className="h-7 w-7" />
                </div>
                <h4 className="text-sm font-bold text-zinc-800">Chưa có sản phẩm nào</h4>
                <p className="text-xs text-zinc-400 mt-1 max-w-xs mx-auto leading-relaxed">
                  Hãy bấm nút **Đăng sản phẩm mới** để bắt đầu đăng bán và phân chia các phiên bản tương ứng.
                </p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-left text-xs">
                  <thead>
                    <tr className="border-b border-zinc-200 bg-zinc-50/50 text-[10px] uppercase font-bold text-zinc-400 tracking-wider">
                      <th className="px-5 py-3.5">Sản phẩm</th>
                      <th className="px-5 py-3.5">Danh mục</th>
                      <th className="px-5 py-3.5">Số phân loại (Phiên bản)</th>
                      <th className="px-5 py-3.5">Trạng thái</th>
                      <th className="px-5 py-3.5 text-right">Thao tác</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-zinc-100 text-zinc-700">
                    {products.map((p) => {
                      const thumbnail = p.images?.find((img) => img.isThumbnail)?.imageUrl || p.images?.[0]?.imageUrl || "";
                      return (
                        <tr key={p.id} className="hover:bg-zinc-50/30 transition-colors">
                          <td className="px-5 py-3.5 flex items-center gap-3">
                            <div 
                              onMouseEnter={(e) => {
                                const video = e.currentTarget.querySelector("video");
                                if (video) video.play().catch(() => {});
                              }}
                              onMouseLeave={(e) => {
                                const video = e.currentTarget.querySelector("video");
                                if (video) {
                                  video.pause();
                                  video.currentTime = 0;
                                }
                              }}
                              className="w-10 h-10 bg-zinc-50 rounded-lg overflow-hidden border shrink-0 flex items-center justify-center"
                            >
                              {thumbnail ? (
                                isVideoUrl(thumbnail) ? (
                                  <video 
                                    src={thumbnail} 
                                    muted 
                                    loop 
                                    playsInline 
                                    preload="metadata"
                                    className="w-full h-full object-cover"
                                  />
                                ) : (
                                  <img src={thumbnail} alt="" className="w-full h-full object-cover" />
                                )
                              ) : (
                                <Package className="h-5 w-5 text-zinc-300" />
                              )}
                            </div>
                            <span className="font-bold text-zinc-900 truncate max-w-xs">{p.name}</span>
                          </td>
                          <td className="px-5 py-3.5 font-medium text-zinc-500">{p.categoryName}</td>
                          <td className="px-5 py-3.5 font-bold text-brand-600">{p.variants?.length || 0} phiên bản</td>
                          <td className="px-5 py-3.5">
                            <span className={`inline-flex rounded-full px-2.5 py-0.5 font-semibold border text-[9px] ${
                              p.status === "ACTIVE" ? "bg-emerald-50 text-emerald-700 border-emerald-100" : "bg-zinc-100 text-zinc-400 border-zinc-200"
                            }`}>
                              {p.status === "ACTIVE" ? "Đang bán" : "Tạm ẩn"}
                            </span>
                          </td>
                          <td className="px-5 py-3.5 text-right space-x-1 shrink-0">
                            <button
                              onClick={() => {
                                setSelectedProduct(p);
                                setProductForm({
                                  name: p.name,
                                  description: p.description || "",
                                  categoryId: p.categoryId,
                                  imageUrl: thumbnail
                                });
                                const gallery = p.images
                                  ? p.images.filter(img => !img.isThumbnail).map(img => img.imageUrl)
                                  : [];
                                setGalleryImages(gallery);
                                setVariantForms(p.variants.map((v) => ({
                                  skuCode: v.skuCode,
                                  variantName: v.variantName,
                                  price: formatNumberInputString(v.price.toString()),
                                  discountPrice: formatNumberInputString(v.discountPrice.toString()),
                                  initialQuantity: formatNumberInputString(v.quantity.toString()),
                                  isNew: false,
                                })));
                                setIsCreateOpen(true);
                              }}
                              className="p-1.5 border border-zinc-200 rounded-lg hover:bg-zinc-100"
                            >
                              <Edit className="h-3.5 w-3.5" />
                            </button>
                            <button
                              onClick={() => handleDeleteProduct(p)}
                              className="p-1.5 border border-zinc-200 rounded-lg text-red-500 hover:bg-red-50"
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
        {activeTab === "inventory" && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            <aside className="lg:col-span-4 bg-white border border-zinc-200/60 rounded-3xl p-5 shadow-sm space-y-4">
              <h3 className="text-sm font-bold text-zinc-900 flex items-center gap-1.5">
                <Barcode className="h-4.5 w-4.5 text-brand-500" />
                Chọn phiên bản sản phẩm
              </h3>

              <div className="space-y-2 max-h-96 overflow-y-auto pr-1 custom-scrollbar">
                {products.length === 0 ? (
                  <p className="text-xs text-zinc-400 font-light">Không có sản phẩm nào để chọn phiên bản.</p>
                ) : (
                  products.map((p) => (
                    <div key={p.id} className="space-y-1">
                      <div className="text-[10px] uppercase font-bold text-zinc-400 tracking-wider mb-1.5 mt-2.5 truncate">{p.name}</div>
                      {p.variants?.map((v) => {
                        const isSelected = selectedVariant?.id === v.id;
                        return (
                          <button
                            key={v.id}
                            onClick={() => setSelectedVariant(v)}
                            className={`w-full text-left p-3 rounded-xl border text-xs flex justify-between items-center transition-all ${
                              isSelected
                                ? "border-brand-500 bg-brand-50/20 font-semibold"
                                : "border-zinc-200 hover:border-zinc-400"
                            }`}
                          >
                            <span className="truncate pr-1">{v.variantName}</span>
                            <span className="text-[10px] font-bold text-zinc-400">{v.skuCode}</span>
                          </button>
                        );
                      })}
                    </div>
                  ))
                )}
              </div>
            </aside>
            <main className="lg:col-span-8 bg-white border border-zinc-200/60 rounded-3xl p-6 shadow-sm space-y-8">
              
              {!selectedVariant ? (
                <div className="text-center py-20">
                  <div className="h-14 w-14 rounded-2xl bg-zinc-50 text-zinc-400 flex items-center justify-center mb-4 border mx-auto">
                    <FileSpreadsheet className="h-7 w-7" />
                  </div>
                  <h4 className="text-sm font-bold text-zinc-800">Chọn một phiên bản sản phẩm</h4>
                  <p className="text-xs text-zinc-400 mt-1 max-w-xs mx-auto leading-relaxed">
                    Vui lòng nhấp chọn một phiên bản sản phẩm ở danh sách bên trái để theo dõi lịch sử biến động kho vật lý và cập nhật tồn kho.
                  </p>
                </div>
              ) : (
                <>
                  <div>
                    <h3 className="text-base font-bold text-zinc-900 dark:text-white pb-3 border-b mb-6">
                      Quản lý Tồn kho Phiên bản: <span className="text-brand-600 font-extrabold">{selectedVariant.variantName}</span>
                      <span className="ml-2 text-xs font-mono bg-zinc-100 text-zinc-600 px-2 py-0.5 rounded">Mã SKU: {selectedVariant.skuCode}</span>
                    </h3>

                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
                      <div className="bg-zinc-50 p-4 rounded-2xl border text-center relative group">
                        <span className="text-[9px] uppercase font-bold text-zinc-400 tracking-wider flex items-center justify-center gap-1">
                          Tồn kho vật lý
                          <Info className="h-3.5 w-3.5 text-zinc-400 cursor-help" />
                        </span>
                        <div className="text-xl font-black text-zinc-900 mt-1">{selectedVariant.quantity.toLocaleString("vi-VN")} cái</div>
                        <div className="absolute z-10 bottom-full left-1/2 transform -translate-x-1/2 mb-2 w-56 bg-zinc-900 text-white text-[10px] p-3.5 rounded-xl opacity-0 group-hover:opacity-100 transition-opacity duration-200 pointer-events-none shadow-xl leading-relaxed">
                          Tổng số lượng sản phẩm thực tế đang nằm trong kho hàng của bạn.
                        </div>
                      </div>
                      <div className="bg-zinc-50 p-4 rounded-2xl border text-center relative group">
                        <span className="text-[9px] uppercase font-bold text-zinc-400 tracking-wider flex items-center justify-center gap-1">
                          Tồn kho giữ chỗ
                          <Info className="h-3.5 w-3.5 text-zinc-400 cursor-help" />
                        </span>
                        <div className="text-xl font-black text-amber-500 mt-1">{selectedVariant.reservedQuantity.toLocaleString("vi-VN")} cái</div>
                        <div className="absolute z-10 bottom-full left-1/2 transform -translate-x-1/2 mb-2 w-56 bg-zinc-900 text-white text-[10px] p-3.5 rounded-xl opacity-0 group-hover:opacity-100 transition-opacity duration-200 pointer-events-none shadow-xl leading-relaxed">
                          Số lượng hàng khách đã đặt mua nhưng chưa gửi đi (đang chờ xử lý giao nhận).
                        </div>
                      </div>
                      <div className="bg-zinc-50 p-4 rounded-2xl border text-center relative group">
                        <span className="text-[9px] uppercase font-bold text-zinc-400 tracking-wider flex items-center justify-center gap-1">
                          Tồn kho khả dụng
                          <Info className="h-3.5 w-3.5 text-zinc-400 cursor-help" />
                        </span>
                        <div className="text-xl font-black text-emerald-600 mt-1">{selectedVariant.availableStock.toLocaleString("vi-VN")} cái</div>
                        <div className="absolute z-10 bottom-full left-1/2 transform -translate-x-1/2 mb-2 w-56 bg-zinc-900 text-white text-[10px] p-3.5 rounded-xl opacity-0 group-hover:opacity-100 transition-opacity duration-200 pointer-events-none shadow-xl leading-relaxed">
                          Số lượng hàng sẵn sàng để bán trên website (Bằng Tồn kho vật lý trừ Hàng giữ chỗ).
                        </div>
                      </div>
                    </div>
                    <form onSubmit={handleAdjustInventorySubmit} className="bg-zinc-50/50 p-5 rounded-2xl border border-dashed flex flex-col sm:flex-row items-end gap-4">
                      <div className="flex-1 w-full space-y-1.5">
                        <label className="block text-[10px] font-bold text-zinc-700 uppercase tracking-wider">Số lượng điều chỉnh (+ / -)</label>
                        <input
                          type="number"
                          required
                          placeholder="Nhập 10 để tăng kho, -5 để giảm kho..."
                          value={adjustQty}
                          onChange={(e) => setAdjustQty(e.target.value)}
                          className="w-full h-10 px-3 bg-white rounded-xl border border-zinc-200 text-xs focus:outline-none focus:border-brand-500"
                        />
                      </div>
                      <div className="flex-1 w-full space-y-1.5">
                        <label className="block text-[10px] font-bold text-zinc-700 uppercase tracking-wider">Lý do điều chỉnh</label>
                        <input
                          type="text"
                          required
                          placeholder="Ví dụ: Nhập thêm hàng mới, Phát hiện hàng lỗi..."
                          value={adjustReason}
                          onChange={(e) => setAdjustReason(e.target.value)}
                          className="w-full h-10 px-3 bg-white rounded-xl border border-zinc-200 text-xs focus:outline-none focus:border-brand-500"
                        />
                      </div>
                      <button
                        type="submit"
                        disabled={isPending}
                        className="w-full sm:w-auto h-10 rounded-xl bg-zinc-900 hover:bg-black text-white font-bold text-xs px-5 shadow shrink-0 active:scale-[0.98] transition-all duration-150"
                      >
                        {isPending ? "Đang xử lý..." : "Cập nhật Kho"}
                      </button>
                    </form>
                  </div>
                  <div className="space-y-4">
                    <h4 className="text-xs font-bold text-zinc-400 uppercase tracking-wider flex items-center gap-1.5 border-b pb-2">
                      <FileSpreadsheet className="h-4.5 w-4.5 text-brand-500" />
                      Lịch sử biến động kho chi tiết
                    </h4>

                    {isHistoryLoading ? (
                      <div className="flex flex-col items-center justify-center py-10">
                        <Loader2 className="h-6 w-6 text-brand-500 animate-spin mb-2" />
                        <span className="text-[10px] text-zinc-400">Đang quét lịch sử kho...</span>
                      </div>
                    ) : inventoryHistory.length === 0 ? (
                      <p className="text-xs text-zinc-400 font-light py-6 text-center">Phiên bản này chưa phát sinh giao dịch tồn kho nào.</p>
                    ) : (
                      <div className="overflow-x-auto max-h-80 overflow-y-auto custom-scrollbar">
                        <table className="w-full text-left text-[11px]">
                          <thead>
                            <tr className="border-b text-zinc-400 font-bold bg-zinc-50/50">
                              <th className="px-4 py-2.5">Thời gian</th>
                              <th className="px-4 py-2.5">Hoạt động</th>
                              <th className="px-4 py-2.5 text-center">Lượng thay đổi</th>
                              <th className="px-4 py-2.5">Lý do chi tiết</th>
                              <th className="px-4 py-2.5">Người thực hiện</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y text-zinc-600">
                            {inventoryHistory.map((h) => {
                              let txLabel = h.transactionType;
                              if (h.transactionType === "IMPORT") txLabel = "Nhập kho";
                              if (h.transactionType === "COMMIT") txLabel = "Xuất bán";
                              if (h.transactionType === "RESERVE") txLabel = "Giữ chỗ";
                              if (h.transactionType === "RELEASE") txLabel = "Hủy giữ chỗ";
                              if (h.transactionType === "ADJUST") txLabel = "Điều chỉnh";

                              return (
                                <tr key={h.id} className="hover:bg-zinc-50/30">
                                  <td className="px-4 py-2.5 whitespace-nowrap">
                                    {new Date(h.createdAt).toLocaleDateString("vi-VN", {
                                      month: "2-digit",
                                      day: "2-digit",
                                      hour: "2-digit",
                                      minute: "2-digit"
                                    })}
                                  </td>
                                  <td className="px-4 py-2.5">
                                    <span className={`px-2 py-0.5 rounded text-[9px] font-extrabold uppercase ${
                                      h.transactionType === "IMPORT" || h.transactionType === "RELEASE"
                                        ? "bg-emerald-50 text-emerald-600 border border-emerald-100"
                                        : h.transactionType === "COMMIT" || h.transactionType === "RESERVE"
                                          ? "bg-red-50 text-red-500 border border-red-100"
                                          : h.transactionType === "ADJUST"
                                            ? "bg-indigo-50 text-indigo-650 border border-indigo-100"
                                            : "bg-zinc-100 text-zinc-500"
                                    }`}>
                                      {txLabel}
                                    </span>
                                  </td>
                                  <td className={`px-4 py-2.5 text-center font-bold ${
                                    h.quantityChanged > 0 ? "text-emerald-600" : "text-red-500"
                                  }`}>
                                    {h.quantityChanged > 0 ? "+" : ""}{h.quantityChanged.toLocaleString("vi-VN")}
                                  </td>
                                  <td className="px-4 py-2.5 max-w-xs truncate" title={h.reason === "Initial stock import on product creation" ? "Nhập tồn kho ban đầu khi tạo sản phẩm" : h.reason === "Initial stock import for new variant added during product update" ? "Nhập tồn kho ban đầu cho phiên bản mới" : h.reason}>
                                    {h.reason === "Initial stock import on product creation" 
                                      ? "Nhập tồn kho ban đầu khi tạo sản phẩm" 
                                      : h.reason === "Initial stock import for new variant added during product update" 
                                        ? "Nhập tồn kho ban đầu cho phiên bản mới" 
                                        : h.reason}
                                  </td>
                                  <td className="px-4 py-2.5 text-zinc-500 font-semibold text-[9px]">
                                    {h.createdBy === "system" ? "Hệ thống tự động" : "Nhà sáng tạo"}
                                  </td>
                                </tr>
                              );
                            })}
                          </tbody>
                        </table>
                      </div>
                    )}
                  </div>
                </>
              )}
            </main>
          </div>
        )}
        {activeTab === "fulfillment" && (
          <div className="bg-white border border-zinc-200/60 rounded-3xl p-5 shadow-sm space-y-5">
            <div className="flex justify-between items-center pb-4 border-b border-zinc-100">
              <h3 className="text-sm font-bold text-zinc-900">Danh sách Đơn hàng cần xử lý</h3>
              <select
                value={activeOrderStatus}
                onChange={(e) => { setActiveOrderStatus(e.target.value); setOrderPage(0); }}
                className="h-9 px-3 border rounded-xl bg-zinc-50 border-zinc-200 text-xs text-zinc-650 focus:outline-none"
              >
                <option value="">Tất cả trạng thái</option>
                <option value="PAID">Chờ giao hàng (Đã thanh toán)</option>
                <option value="SHIPPED">Đang vận chuyển</option>
                <option value="DELIVERED">Đã giao hàng thành công</option>
                <option value="CANCELLED">Đã hủy đơn</option>
              </select>
            </div>

            {isOrdersLoading ? (
              <div className="flex flex-col items-center justify-center py-20">
                <Loader2 className="h-8 w-8 text-brand-500 animate-spin mb-3" />
                <p className="text-xs text-zinc-400">Đang tìm kiếm đơn hàng...</p>
              </div>
            ) : orders.length === 0 ? (
              <div className="text-center py-20">
                <div className="h-14 w-14 rounded-2xl bg-zinc-50 text-zinc-400 flex items-center justify-center mb-4 border mx-auto">
                  <Truck className="h-7 w-7" />
                </div>
                <h4 className="text-sm font-bold text-zinc-800">Không tìm thấy đơn hàng nào</h4>
                <p className="text-xs text-zinc-400 mt-1 max-w-xs mx-auto leading-relaxed">
                  Hiện tại không có đơn hàng con nào thuộc nhóm này cần bạn xử lý đóng gói và giao vận.
                </p>
              </div>
            ) : (
              <div className="space-y-5">
                {orders.map((order) => {
                  const isPaid = order.status === "PAID";
                  const isShipped = order.status === "SHIPPED";
                  const isProcessing = !!isFulfilling[order.orderId];

                  let orderStatusLabel: string = order.status;
                  if (order.status === "PAID") orderStatusLabel = "Chờ giao hàng";
                  if (order.status === "SHIPPED") orderStatusLabel = "Đang vận chuyển";
                  if (order.status === "DELIVERED") orderStatusLabel = "Đã giao thành công";
                  if (order.status === "CANCELLED") orderStatusLabel = "Đã hủy";

                  return (
                    <div 
                      key={order.orderId}
                      className="bg-zinc-50 rounded-2xl border p-5 flex flex-col gap-4 text-xs"
                    >
                      <div className="flex flex-wrap items-center justify-between border-b pb-3.5 gap-2.5">
                        <div className="flex items-center gap-3">
                          <span className="font-black text-brand-600 tracking-wider">Đơn hàng: {order.orderCode}</span>
                          <span className="text-[10px] text-zinc-400 font-light flex items-center gap-1">
                            <Calendar className="h-3.5 w-3.5" />
                            Ngày đặt: {new Date(order.createdAt).toLocaleDateString("vi-VN")}
                          </span>
                        </div>
                        <span className={`px-2.5 py-0.5 rounded-full text-[9px] font-extrabold uppercase border ${
                          isPaid ? "bg-emerald-50 text-emerald-700 border-emerald-100" 
                          : isShipped ? "bg-amber-50 text-amber-700 border-amber-100"
                          : order.status === "DELIVERED" ? "bg-zinc-100 text-zinc-700 border-zinc-200"
                          : "bg-red-50 text-red-700 border-red-100"
                        }`}>
                          {orderStatusLabel}
                        </span>
                      </div>
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-5 items-start">
                        <div className="space-y-2">
                          <div className="font-bold text-zinc-800">Sản phẩm khách mua:</div>
                          {order.items?.map((item, idx) => (
                            <div key={idx} className="flex justify-between items-center text-zinc-500 font-light">
                              <span>• {item.productName} ({item.variantName})</span>
                              <span className="font-semibold text-zinc-900">x{item.quantity}</span>
                            </div>
                          ))}
                          <div className="pt-2 border-t font-bold flex justify-between items-center text-zinc-900 text-sm">
                            <span>Tổng doanh thu:</span>
                            <span className="text-brand-600">{new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(order.finalAmount)}</span>
                          </div>
                        </div>
                        <div className="bg-white p-4 rounded-xl border space-y-2 text-zinc-500 font-light">
                          <div className="font-bold text-zinc-800">Địa chỉ giao nhận hàng:</div>
                          <div>Người nhận: <strong className="font-bold text-zinc-800">{order.recipientName}</strong></div>
                          <div>Điện thoại: <strong className="font-bold text-zinc-800">{order.recipientPhone}</strong></div>
                          <div>Địa chỉ giao hàng: <strong className="font-bold text-zinc-800">{order.shippingAddress}</strong></div>
                          {order.customerNote && <div className="text-[10px] text-zinc-400 italic">Khách ghi chú: {order.customerNote}</div>}
                        </div>
                      </div>
                      {(isPaid || isShipped) && (
                        <div className="bg-white p-4.5 rounded-xl border border-dashed flex flex-col sm:flex-row items-end gap-3.5 justify-end">
                          {isPaid && (
                            <>
                              <div className="flex-1 w-full space-y-1.5">
                                <label className="block text-[10px] font-bold text-zinc-700 uppercase tracking-wider">Mã vận đơn / Số hiệu chuyến hàng *</label>
                                <input
                                  type="text"
                                  placeholder="Ví dụ: SPX9081273, VNPOST..."
                                  value={trackingNumbers[order.orderId] || ""}
                                  onChange={(e) => setTrackingNumbers({
                                    ...trackingNumbers,
                                    [order.orderId]: e.target.value
                                  })}
                                  className="w-full h-10 px-3 bg-zinc-50 rounded-xl border border-zinc-200 text-xs focus:outline-none focus:border-brand-500"
                                />
                              </div>
                              <button
                                disabled={isProcessing}
                                onClick={() => handleShipOrder(order.orderId, order.orderCode)}
                                className="w-full sm:w-auto h-10 px-6 rounded-xl bg-brand-500 hover:bg-brand-600 text-white font-bold text-xs shadow-md shadow-brand-500/10 active:scale-[0.98] transition-colors shrink-0"
                              >
                                {isProcessing ? "Đang xử lý..." : "Xác nhận gửi hàng"}
                              </button>
                            </>
                          )}

                          {isShipped && (
                            <button
                              disabled={isProcessing}
                              onClick={() => handleDeliverOrder(order.orderId, order.orderCode)}
                              className="w-full sm:w-auto h-10 px-6 rounded-xl bg-zinc-900 hover:bg-black text-white font-bold text-xs shadow active:scale-[0.98] transition-colors shrink-0"
                            >
                              {isProcessing ? "Đang xử lý..." : "Xác nhận giao thành công"}
                            </button>
                          )}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

      </div>
      {isCreateOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={() => setIsCreateOpen(false)} />
          
          <div className="relative w-full max-w-2xl bg-white rounded-[2.5rem] p-6 sm:p-8 max-h-[90vh] overflow-y-auto shadow-2xl animate-toast-in custom-scrollbar">
            <button 
              onClick={() => setIsCreateOpen(false)}
              className="absolute right-6 top-6 p-1 rounded-lg hover:bg-zinc-100"
            >
              <X className="h-5 w-5 text-zinc-400" />
            </button>

            <h3 className="text-lg font-black text-zinc-900 dark:text-white mb-6 flex items-center gap-2">
              <Plus className="h-5 w-5 text-brand-500" />
              {selectedProduct ? "Cập nhật chi tiết sản phẩm" : "Đăng sản phẩm & Phiên bản mới"}
            </h3>

            <form onSubmit={handleSaveProductSubmit} className="space-y-6">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="block text-[10px] font-bold text-zinc-700 dark:text-zinc-300 uppercase tracking-wider">Tên sản phẩm chính *</label>
                  <input
                    type="text"
                    required
                    placeholder="Ví dụ: Áo khoác hoodie VibeStreet"
                    value={productForm.name}
                    onChange={(e) => setProductForm({ ...productForm, name: e.target.value })}
                    className="w-full h-11 px-4 rounded-xl bg-zinc-50 border text-xs focus:outline-none focus:border-brand-500"
                  />
                </div>

                <div className="space-y-1.5">
                  <label className="block text-[10px] font-bold text-zinc-700 dark:text-zinc-300 uppercase tracking-wider">Danh mục sản phẩm *</label>
                  <select
                    required
                    value={productForm.categoryId}
                    onChange={(e) => setProductForm({ ...productForm, categoryId: e.target.value })}
                    className="w-full h-11 px-3 rounded-xl bg-zinc-50 border text-xs focus:outline-none focus:border-brand-500"
                  >
                    <option value="">Chọn danh mục phù hợp...</option>
                    {categories.map((c) => (
                      <option key={c.id} value={c.id}>{c.name}</option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="space-y-1.5">
                <label className="block text-[10px] font-bold text-zinc-700 dark:text-zinc-300 uppercase tracking-wider">Mô tả chi tiết sản phẩm</label>
                <textarea
                  placeholder="Mô tả thông tin chi tiết về sản phẩm (Ví dụ: chất liệu, hướng dẫn chọn size, phom dáng...)"
                  value={productForm.description}
                  onChange={(e) => setProductForm({ ...productForm, description: e.target.value })}
                  rows={2.5}
                  className="w-full p-4 rounded-xl bg-zinc-50 border text-xs focus:outline-none focus:border-brand-500 resize-none"
                />
              </div>
              <div className="space-y-2">
                <label className="block text-[10px] font-bold text-zinc-700 dark:text-zinc-350 uppercase tracking-wider">Ảnh chính đại diện của sản phẩm (Thumbnail) *</label>
                
                <input
                  type="file"
                  ref={spuImageInputRef}
                  onChange={handleSpuImageChange}
                  accept="image/*,video/*"
                  className="hidden"
                />

                {isUploadingImage ? (
                  <div className="w-full h-32 rounded-2xl border-2 border-dashed border-zinc-200 bg-zinc-50 flex flex-col items-center justify-center">
                    <Loader2 className="h-6 w-6 text-brand-500 animate-spin mb-2" />
                    <span className="text-xs text-zinc-500">Đang tải tệp lên hệ thống...</span>
                  </div>
                ) : productForm.imageUrl ? (
                  <div className="relative group w-full h-32 rounded-2xl overflow-hidden border border-zinc-200 shadow-sm bg-zinc-50">
                    {isVideoUrl(productForm.imageUrl) ? (
                      <video 
                        src={productForm.imageUrl} 
                        className="w-full h-full object-cover"
                        controls
                      />
                    ) : (
                      <img 
                        src={productForm.imageUrl} 
                        alt="Thumbnail Preview" 
                        className="w-full h-full object-cover"
                      />
                    )}
                    <div className="absolute inset-0 bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity duration-200 gap-3">
                      <button
                        type="button"
                        onClick={handleSpuImageClick}
                        className="px-4 py-2 bg-white/95 hover:bg-white text-zinc-800 text-[10px] font-bold rounded-xl shadow active:scale-95 transition-all cursor-pointer"
                      >
                        Thay tệp khác
                      </button>
                      <button
                        type="button"
                        onClick={() => setProductForm(prev => ({ ...prev, imageUrl: "" }))}
                        className="px-4 py-2 bg-red-500 hover:bg-red-600 text-white text-[10px] font-bold rounded-xl shadow active:scale-95 transition-all cursor-pointer"
                      >
                        Xóa tệp
                      </button>
                    </div>
                  </div>
                ) : (
                  <div 
                    onClick={handleSpuImageClick}
                    className="w-full h-32 rounded-2xl border-2 border-dashed border-zinc-200 hover:border-brand-300 bg-zinc-50/50 hover:bg-brand-50/10 flex flex-col items-center justify-center cursor-pointer transition-all duration-200 group"
                  >
                    <UploadCloud className="h-7 w-7 text-zinc-400 group-hover:text-brand-500 mb-2 transition-colors" />
                    <span className="text-xs font-bold text-zinc-700 group-hover:text-brand-600 transition-colors">Tải ảnh/video đại diện lên</span>
                    <span className="text-[10px] text-zinc-400 mt-1 font-light">Kéo thả hoặc nhấp để chọn tệp (Ảnh dưới 5MB, Video dưới 50MB)</span>
                  </div>
                )}
              </div>
              <div className="space-y-2">
                <div className="flex justify-between items-center">
                  <label className="block text-[10px] font-bold text-zinc-700 uppercase tracking-wider">
                    Bộ sưu tập ảnh/video chi tiết sản phẩm {galleryImages.length > 0 && `(${galleryImages.length} tệp)`}
                  </label>
                  <span className="text-[9px] text-zinc-400 font-light">Không bắt buộc</span>
                </div>

                <input
                  type="file"
                  ref={galleryImagesInputRef}
                  onChange={handleGalleryImagesChange}
                  accept="image/*,video/*"
                  multiple
                  className="hidden"
                />

                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                  {galleryImages.map((url, idx) => (
                    <div key={idx} className="relative group aspect-square rounded-xl overflow-hidden border border-zinc-200 shadow-sm bg-zinc-50">
                      {isVideoUrl(url) ? (
                        <video 
                          src={url} 
                          className="w-full h-full object-cover"
                          controls
                        />
                      ) : (
                        <img 
                          src={url} 
                          alt={`Gallery Preview ${idx + 1}`} 
                          className="w-full h-full object-cover"
                        />
                      )}
                      <div className="absolute inset-0 bg-black/45 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity duration-200">
                        <button
                          type="button"
                          onClick={() => handleRemoveGalleryImage(idx)}
                          className="p-2 bg-red-500 hover:bg-red-600 text-white rounded-xl shadow active:scale-90 transition-all cursor-pointer"
                          title="Xóa tệp"
                        >
                          <X className="h-4 w-4" />
                        </button>
                      </div>
                    </div>
                  ))}

                  {isUploadingGallery ? (
                    <div className="aspect-square rounded-xl border-2 border-dashed border-zinc-200 bg-zinc-50 flex flex-col items-center justify-center p-2 text-center">
                      <Loader2 className="h-5 w-5 text-brand-500 animate-spin mb-1" />
                      <span className="text-[10px] text-zinc-400">Đang tải tệp...</span>
                    </div>
                  ) : (
                    <div 
                      onClick={handleGalleryImagesClick}
                      className="aspect-square rounded-xl border-2 border-dashed border-zinc-200 hover:border-brand-300 bg-zinc-50/50 hover:bg-brand-50/10 flex flex-col items-center justify-center cursor-pointer transition-all duration-200 group text-center p-2"
                    >
                      <Plus className="h-5 w-5 text-zinc-400 group-hover:text-brand-500 mb-1 transition-colors" />
                      <span className="text-[10px] font-bold text-zinc-700 group-hover:text-brand-600 transition-colors">Thêm ảnh/video chi tiết</span>
                      <span className="text-[8px] text-zinc-400 mt-0.5 leading-none">Nhiều tệp</span>
                    </div>
                  )}
                </div>
              </div>
              <div className="border-t pt-5 space-y-4">
                <div className="flex justify-between items-center">
                  <div>
                    <h4 className="text-xs font-bold text-zinc-950 uppercase tracking-wider flex items-center gap-1">
                      Các phiên bản bán hàng (Phân loại) *
                    </h4>
                    <p className="text-[10px] text-zinc-450 font-light mt-0.5">Tạo các phiên bản khác nhau dựa trên màu sắc, size...</p>
                  </div>
                  <button
                    type="button"
                    onClick={handleAddVariantRow}
                    className="h-8 inline-flex items-center gap-1 rounded-lg border border-brand-500 text-brand-600 hover:bg-brand-50 px-3 text-[10px] font-bold uppercase transition-colors"
                  >
                    <Plus className="h-3 w-3" /> Thêm phiên bản
                  </button>
                </div>

                <div className="space-y-3.5">
                  {variantForms.map((v, idx) => (
                    <div 
                      key={idx} 
                      className="bg-zinc-50 p-4.5 rounded-2xl border border-zinc-200/60 relative grid grid-cols-1 sm:grid-cols-5 gap-3"
                    >
                      <div className="space-y-1">
                        <label className="text-[9px] font-bold text-zinc-500 uppercase tracking-wider h-8 flex items-end pb-1">Mã phiên bản (SKU) *</label>
                        <input
                          type="text"
                          required
                          placeholder="Ví dụ: SP-AO-DEN-L"
                          value={v.skuCode}
                          onChange={(e) => handleVariantFormChange(idx, "skuCode", e.target.value)}
                          className="w-full h-9 px-2 bg-white rounded-lg border text-xs focus:outline-none"
                        />
                      </div>
                      <div className="space-y-1">
                        <label className="text-[9px] font-bold text-zinc-500 uppercase tracking-wider h-8 flex items-end pb-1">Tên phiên bản *</label>
                        <input
                          type="text"
                          required
                          placeholder="Ví dụ: Đen - Size L"
                          value={v.variantName}
                          onChange={(e) => handleVariantFormChange(idx, "variantName", e.target.value)}
                          className="w-full h-9 px-2 bg-white rounded-lg border text-xs focus:outline-none"
                        />
                      </div>
                      <div className="space-y-1">
                        <label className="text-[9px] font-bold text-zinc-500 uppercase tracking-wider h-8 flex items-end pb-1">Giá bán lẻ (VND) *</label>
                        <input
                          type="text"
                          required
                          placeholder="Ví dụ: 180.000"
                          value={v.price}
                          onChange={(e) => handleVariantFormChange(idx, "price", e.target.value)}
                          className="w-full h-9 px-2 bg-white rounded-lg border text-xs focus:outline-none"
                        />
                      </div>
                      <div className="space-y-1">
                        <label className="text-[9px] font-bold text-zinc-500 uppercase tracking-wider h-8 flex items-end pb-1">Giá khuyến mãi (VND)</label>
                        <input
                          type="text"
                          placeholder="Nhập 0 nếu không giảm giá"
                          value={v.discountPrice}
                          onChange={(e) => handleVariantFormChange(idx, "discountPrice", e.target.value)}
                          className="w-full h-9 px-2 bg-white rounded-lg border text-xs focus:outline-none"
                        />
                      </div>
                      <div className="space-y-1">
                        <label className="text-[9px] font-bold text-zinc-500 uppercase tracking-wider h-8 flex items-end pb-1">
                          {selectedProduct && !v.isNew ? "Tồn kho hiện tại" : "Số lượng ban đầu *"}
                        </label>
                        <input
                          type="text"
                          placeholder="0"
                          disabled={!!selectedProduct && !v.isNew}
                          value={v.initialQuantity}
                          onChange={(e) => handleVariantFormChange(idx, "initialQuantity", e.target.value)}
                          className="w-full h-9 px-2 bg-white rounded-lg border text-xs focus:outline-none disabled:bg-zinc-100 disabled:opacity-60"
                        />
                      </div>
                      {variantForms.length > 1 && (
                        <button
                          type="button"
                          onClick={() => handleRemoveVariantRow(idx)}
                          className="absolute -top-2 -right-2 bg-white hover:bg-red-50 text-red-500 rounded-full h-6.5 w-6.5 flex items-center justify-center border shadow"
                        >
                          <X className="h-3.5 w-3.5" />
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              </div>
              <div className="flex gap-4 pt-4 border-t">
                <button
                  type="button"
                  onClick={() => setIsCreateOpen(false)}
                  className="flex-1 h-12 rounded-full border border-zinc-200 text-zinc-700 font-semibold text-xs transition-colors"
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  disabled={isPending}
                  className="flex-1 h-12 rounded-full bg-brand-500 hover:bg-brand-600 disabled:opacity-50 text-white font-bold text-xs shadow-lg shadow-brand-500/10 flex items-center justify-center gap-2 transition-all duration-200"
                >
                  {isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                  Lưu sản phẩm & Phiên bản
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
