"use client";

import React, { useState, useEffect, useTransition } from "react";
import { useRouter } from "next/navigation";
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
  Info
} from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { useToast } from "@/context/ToastContext";
import { Forbidden } from "@/components/common/Forbidden";
import { productService } from "@/services/product.service";
import { ROUTES } from "@/constants/routes";
interface AdminProduct {
  id: string;
  name: string;
  description: string;
  price: number;
  discountPrice: number;
  status: "ACTIVE" | "INACTIVE";
  quantity: number;
  createdAt: string;
  updatedAt: string;
}

const formatNumberInputString = (val: string) => {
  const clean = val.replace(/\D/g, "");
  if (!clean) return "";
  return Number(clean).toLocaleString("vi-VN");
};

export default function AdminProductsPage() {
  const { user, isAuthenticated, isLoading: isAuthLoading } = useAuth();
  const toast = useToast();
  const router = useRouter();
  const [products, setProducts] = useState<AdminProduct[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [size] = useState(10);
  const [searchQuery, setSearchQuery] = useState("");
  const [isDataLoading, setIsDataLoading] = useState(false);
  const [isPending, startTransition] = useTransition();
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isAdjustOpen, setIsAdjustOpen] = useState(false);
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const [selectedProduct, setSelectedProduct] = useState<AdminProduct | null>(null);
  const [formData, setFormData] = useState({
    name: "",
    description: "",
    price: "",
    discountPrice: "0",
    status: "ACTIVE" as "ACTIVE" | "INACTIVE",
    initialStock: "0"
  });

  const [adjustData, setAdjustData] = useState({
    adjustment: "",
    reason: ""
  });
  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      toast.warning("Yêu cầu đăng nhập", "Vui lòng đăng nhập để truy cập trang quản trị.");
      router.push(ROUTES.LOGIN);
    }
  }, [isAuthenticated, isAuthLoading, router]);
  const fetchProducts = React.useCallback(async () => {
    setIsDataLoading(true);
    try {
      const response = await productService.getProducts({
        query: searchQuery || undefined,
        page,
        size
      });
      const mappedContent: AdminProduct[] = (response.content || []).map((p: any) => ({
        id: p.id,
        name: p.name,
        description: p.description || "",
        price: Number(p.price),
        discountPrice: Number(p.discountPrice || 0),
        status: (p.status || "ACTIVE") as "ACTIVE" | "INACTIVE",
        quantity: Number(p.quantity || 0),
        createdAt: p.createdAt,
        updatedAt: p.updatedAt
      }));

      setProducts(mappedContent);
      setTotalElements(response.totalElements || 0);
    } catch (error) {
      console.error("Lỗi khi tải danh sách sản phẩm:", error);
      toast.error("Lỗi tải dữ liệu", "Không thể lấy danh sách sản phẩm từ máy chủ.");
    } finally {
      setIsDataLoading(false);
    }
  }, [page, size, searchQuery]);

  useEffect(() => {
    if (isAuthenticated && user?.roles?.includes("ROLE_ADMIN")) {
      fetchProducts();
    }
  }, [page, searchQuery, isAuthenticated, user, fetchProducts]);
  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    fetchProducts();
  };
  const openCreateModal = () => {
    setFormData({
      name: "",
      description: "",
      price: "",
      discountPrice: "0",
      status: "ACTIVE",
      initialStock: "0"
    });
    setIsCreateOpen(true);
  };
  const openEditModal = (product: AdminProduct) => {
    setSelectedProduct(product);
    setFormData({
      name: product.name,
      description: product.description,
      price: formatNumberInputString(product.price.toString()),
      discountPrice: formatNumberInputString(product.discountPrice.toString()),
      status: product.status,
      initialStock: formatNumberInputString(product.quantity.toString())
    });
    setIsEditOpen(true);
  };
  const openAdjustModal = (product: AdminProduct) => {
    setSelectedProduct(product);
    setAdjustData({
      adjustment: "",
      reason: "Điều chỉnh tồn kho định kỳ"
    });
    setIsAdjustOpen(true);
  };
  const openDeleteModal = (product: AdminProduct) => {
    setSelectedProduct(product);
    setIsDeleteOpen(true);
  };
  const handleCreateProduct = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.name.trim() || !formData.price) {
      toast.warning("Thiếu thông tin", "Vui lòng nhập tên và giá sản phẩm.");
      return;
    }

    startTransition(async () => {
      try {
        const payload = {
          name: formData.name.trim(),
          description: formData.description.trim(),
          price: Number(formData.price.replace(/\D/g, "")),
          discountPrice: Number(formData.discountPrice ? formData.discountPrice.replace(/\D/g, "") : 0),
          status: formData.status,
          quantity: Number(formData.initialStock ? formData.initialStock.replace(/\D/g, "") : 0)
        };
        await productService.createProduct(payload as any);
        toast.success("Tạo thành công", `Sản phẩm "${formData.name}" đã được tạo.`);
        setIsCreateOpen(false);
        setPage(0);
        fetchProducts();
      } catch (error) {
        console.error("Lỗi tạo sản phẩm:", error);
        toast.error("Lỗi tạo sản phẩm", "Không thể lưu thông tin sản phẩm mới.");
      }
    });
  };
  const handleEditProduct = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedProduct) return;
    if (!formData.name.trim() || !formData.price) {
      toast.warning("Thiếu thông tin", "Vui lòng nhập tên và giá sản phẩm.");
      return;
    }

    startTransition(async () => {
      try {
        const payload = {
          name: formData.name.trim(),
          description: formData.description.trim(),
          price: Number(formData.price.replace(/\D/g, "")),
          discountPrice: Number(formData.discountPrice ? formData.discountPrice.replace(/\D/g, "") : 0),
          status: formData.status
        };
        await productService.updateProduct(selectedProduct.id, payload as any);
        toast.success("Cập nhật thành công", `Sản phẩm "${formData.name}" đã được cập nhật.`);
        setIsEditOpen(false);
        fetchProducts();
      } catch (error) {
        console.error("Lỗi cập nhật sản phẩm:", error);
        toast.error("Lỗi cập nhật", "Không thể lưu thông tin chỉnh sửa.");
      }
    });
  };
  const handleAdjustInventory = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedProduct) return;
    const adjustmentQty = parseInt(adjustData.adjustment);
    if (isNaN(adjustmentQty) || adjustmentQty === 0) {
      toast.warning("Nhập số lượng", "Vui lòng nhập số lượng tăng (+) hoặc giảm (-) hợp lệ.");
      return;
    }
    if (selectedProduct.quantity + adjustmentQty < 0) {
      toast.warning("Lỗi tồn kho", "Số lượng điều chỉnh giảm vượt quá tồn kho hiện có.");
      return;
    }

    startTransition(async () => {
      try {
        await productService.adjustInventory(
          selectedProduct.id, 
          adjustmentQty, 
          adjustData.reason || undefined
        );
        toast.success(
          "Điều chỉnh kho thành công", 
          `Sản phẩm "${selectedProduct.name}" đã được cập nhật kho hàng (${adjustmentQty > 0 ? "+" : ""}${adjustmentQty}).`
        );
        setIsAdjustOpen(false);
        fetchProducts();
      } catch (error) {
        console.error("Lỗi điều chỉnh tồn kho:", error);
        toast.error("Lỗi điều chỉnh kho", "Không thể thay đổi tồn kho của sản phẩm.");
      }
    });
  };
  const handleDeleteProduct = async () => {
    if (!selectedProduct) return;

    startTransition(async () => {
      try {
        await productService.deleteProduct(selectedProduct.id);
        toast.success("Xóa thành công", `Đã xóa sản phẩm "${selectedProduct.name}" khỏi hệ thống.`);
        setIsDeleteOpen(false);
        if (products.length === 1 && page > 0) {
          setPage(prev => prev - 1);
        } else {
          fetchProducts();
        }
      } catch (error) {
        console.error("Lỗi xóa sản phẩm:", error);
        toast.error("Lỗi xóa sản phẩm", "Không thể xóa sản phẩm này. Có thể sản phẩm đang nằm trong đơn hàng.");
      }
    });
  };
  if (isAuthLoading) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center min-h-[70vh] bg-zinc-50 transition-colors duration-300">
        <Loader2 className="h-10 w-10 text-brand-500 animate-spin mb-4" />
        <p className="text-sm text-zinc-500 font-light">Đang xác thực quyền truy cập...</p>
      </div>
    );
  }
  if (!isAuthenticated || !user?.roles?.includes("ROLE_ADMIN")) {
    return <Forbidden />;
  }

  const totalPages = Math.ceil(totalElements / size);

  return (
    <div className="flex-1 flex flex-col bg-zinc-50 px-6 py-10 transition-colors duration-300 relative">
      <div className="absolute top-[5%] right-[10%] w-72 h-72 bg-brand-100/10 rounded-full blur-[100px] pointer-events-none" />
      <div className="absolute bottom-[10%] left-[5%] w-96 h-96 bg-brand-200/10 rounded-full blur-[120px] pointer-events-none" />

      <div className="max-w-6xl w-full mx-auto relative z-10 flex-1 flex flex-col">
        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-8">
          <div>
            <h1 className="text-3xl font-extrabold tracking-tight text-zinc-900 flex items-center gap-2.5">
              <ShoppingBag className="h-8 w-8 text-brand-500" />
              Quản lý Sản phẩm
            </h1>
            <p className="text-sm text-zinc-500 mt-1 font-light">
              Tạo mới, chỉnh sửa thông tin, xóa sản phẩm và điều chỉnh tồn kho nguyên tử.
            </p>
          </div>
          
          <button
            onClick={openCreateModal}
            className="flex h-11 items-center gap-2 rounded-full bg-brand-500 px-6 text-sm font-semibold text-white shadow-lg shadow-brand-500/20 hover:bg-brand-600 hover:shadow-brand-600/25 active:scale-[0.98] transition-all duration-200"
          >
            <Plus className="h-4 w-4" />
            Thêm sản phẩm
          </button>
        </div>
        <div className="bg-white backdrop-blur-sm rounded-2xl border border-zinc-200/60 p-4 mb-6 shadow-sm flex flex-col sm:flex-row gap-4 items-center justify-between">
          <form onSubmit={handleSearchSubmit} className="relative w-full sm:max-w-sm">
            <input
              type="text"
              placeholder="Tìm kiếm sản phẩm theo tên..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full h-10 pl-10 pr-4 rounded-xl bg-zinc-50 border border-zinc-200 text-sm text-zinc-950 placeholder-zinc-400 focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 transition-all duration-200"
            />
            <Search className="absolute left-3.5 top-3 h-4.5 w-4.5 text-zinc-400 pointer-events-none" />
          </form>

          <button
            onClick={() => fetchProducts()}
            className="flex h-10 items-center justify-center gap-2 px-4 rounded-xl border border-zinc-200 hover:bg-zinc-50 text-sm font-medium text-zinc-700 hover:border-zinc-300 transition-colors"
          >
            <RotateCw className={`h-4 w-4 ${isDataLoading ? "animate-spin" : ""}`} />
            Làm mới
          </button>
        </div>
        <div className="bg-white backdrop-blur-sm rounded-2xl border border-zinc-200/60 shadow-sm overflow-hidden flex-1 flex flex-col min-h-[400px]">
          <div className="overflow-x-auto flex-1">
            <table className="w-full border-collapse text-left">
              <thead>
                <tr className="border-b border-zinc-200/80 bg-zinc-50/50 text-xs font-semibold text-zinc-400 uppercase tracking-wider">
                  <th className="px-6 py-4">Tên & Mô tả</th>
                  <th className="px-6 py-4">Giá gốc / Giảm</th>
                  <th className="px-6 py-4">Tồn kho</th>
                  <th className="px-6 py-4">Trạng thái</th>
                  <th className="px-6 py-4 text-right">Hành động</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-200/60 text-sm">
                {isDataLoading && products.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-6 py-20 text-center">
                      <div className="flex flex-col items-center justify-center">
                        <Loader2 className="h-8 w-8 text-brand-500 animate-spin mb-3" />
                        <p className="text-zinc-400 font-light">Đang tải danh sách sản phẩm...</p>
                      </div>
                    </td>
                  </tr>
                ) : products.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-6 py-20 text-center">
                      <div className="flex flex-col items-center justify-center max-w-sm mx-auto">
                        <div className="h-14 w-14 rounded-2xl bg-zinc-100 text-zinc-400 flex items-center justify-center mb-4 border border-zinc-200/50 ">
                          <Package className="h-7 w-7" />
                        </div>
                        <h3 className="text-base font-bold text-zinc-800 ">Không tìm thấy sản phẩm</h3>
                        <p className="text-xs text-zinc-400 mt-1 font-light leading-relaxed">
                          Hiện tại chưa có sản phẩm nào khớp hoặc không có sản phẩm nào được khai báo trong hệ thống.
                        </p>
                      </div>
                    </td>
                  </tr>
                ) : (
                  products.map((product) => (
                    <tr 
                      key={product.id}
                      className="hover:bg-zinc-50/50 transition-colors duration-150 group"
                    >
                      <td className="px-6 py-4.5 max-w-xs">
                        <div className="font-bold text-zinc-950 truncate" title={product.name}>
                          {product.name}
                        </div>
                        <div className="text-xs text-zinc-400 mt-1.5 truncate leading-relaxed" title={product.description}>
                          {product.description || "Chưa cập nhật mô tả..."}
                        </div>
                      </td>
                      <td className="px-6 py-4.5">
                        {product.discountPrice > 0 ? (
                          <div className="flex flex-col">
                            <span className="text-sm font-bold text-red-500 ">
                              {new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(product.discountPrice)}
                            </span>
                            <span className="text-xs text-zinc-400 line-through mt-0.5 font-light">
                              {new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(product.price)}
                            </span>
                          </div>
                        ) : (
                          <span className="font-bold text-zinc-950 ">
                            {new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(product.price)}
                          </span>
                        )}
                      </td>
                      <td className="px-6 py-4.5">
                        <div className="flex items-center gap-1.5">
                          <span className={`inline-block h-2 w-2 rounded-full ${
                            product.quantity === 0 
                              ? "bg-red-500" 
                              : product.quantity <= 10 
                                ? "bg-amber-500" 
                                : "bg-emerald-500"
                          }`} />
                          <span className="font-semibold text-zinc-950 ">{product.quantity.toLocaleString("vi-VN")}</span>
                          <span className="text-zinc-400 text-xs font-light">cái</span>
                        </div>
                      </td>
                      <td className="px-6 py-4.5">
                        <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold border ${
                          product.status === "ACTIVE"
                            ? "bg-emerald-50 text-emerald-700 border-emerald-200/50 "
                            : "bg-zinc-100 text-zinc-500 border-zinc-200/50 "
                        }`}>
                          {product.status === "ACTIVE" ? "Đang bán" : "Ngừng bán"}
                        </span>
                      </td>
                      <td className="px-6 py-4.5 text-right">
                        <div className="flex items-center justify-end gap-1 opacity-80 group-hover:opacity-100 transition-opacity">
                          <button
                            onClick={() => openAdjustModal(product)}
                            title="Điều chỉnh tồn kho"
                            className="p-1.5 rounded-lg border border-zinc-200 hover:bg-brand-50 text-zinc-600 hover:text-brand-500 hover:border-brand-200 transition-colors"
                          >
                            <Package className="h-4 w-4" />
                          </button>
                          <button
                            onClick={() => openEditModal(product)}
                            title="Chỉnh sửa thông tin"
                            className="p-1.5 rounded-lg border border-zinc-200 hover:bg-zinc-50 text-zinc-600 hover:text-zinc-950 transition-colors"
                          >
                            <Edit className="h-4 w-4" />
                          </button>
                          <button
                            onClick={() => openDeleteModal(product)}
                            title="Xóa sản phẩm"
                            className="p-1.5 rounded-lg border border-zinc-200 hover:bg-red-50 text-zinc-600 hover:text-red-500 hover:border-red-200 transition-colors"
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          {totalPages > 1 && (
            <div className="border-t border-zinc-200/80 px-6 py-4.5 flex items-center justify-between bg-zinc-50/30 ">
              <span className="text-xs text-zinc-400 font-light">
                Hiển thị trang {page + 1} / {totalPages} (Tổng cộng {totalElements} sản phẩm)
              </span>
              
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setPage((prev) => Math.max(0, prev - 1))}
                  disabled={page === 0 || isDataLoading}
                  className="flex h-8.5 w-8.5 items-center justify-center rounded-lg border border-zinc-200 hover:bg-zinc-50 disabled:opacity-40 disabled:pointer-events-none text-zinc-700 hover:border-zinc-300 transition-colors"
                >
                  <ChevronLeft className="h-4.5 w-4.5" />
                </button>
                <button
                  onClick={() => setPage((prev) => Math.min(totalPages - 1, prev + 1))}
                  disabled={page === totalPages - 1 || isDataLoading}
                  className="flex h-8.5 w-8.5 items-center justify-center rounded-lg border border-zinc-200 hover:bg-zinc-50 disabled:opacity-40 disabled:pointer-events-none text-zinc-700 hover:border-zinc-300 transition-colors"
                >
                  <ChevronRight className="h-4.5 w-4.5" />
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
      {isCreateOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-zinc-950/60 backdrop-blur-sm transition-opacity" onClick={() => setIsCreateOpen(false)} />
          
          <div className="relative w-full max-w-lg bg-white rounded-[2rem] border border-zinc-200/80 shadow-2xl p-7 sm:p-8 animate-toast-in">
            <button 
              onClick={() => setIsCreateOpen(false)}
              className="absolute right-6 top-6 p-1 rounded-lg text-zinc-400 hover:text-zinc-800 hover:bg-zinc-100 transition-colors"
            >
              <X className="h-5 w-5" />
            </button>

            <h3 className="text-xl font-extrabold text-zinc-950 mb-1.5 flex items-center gap-2">
              <Plus className="h-5 w-5 text-brand-500" />
              Thêm sản phẩm mới
            </h3>
            <p className="text-xs text-zinc-400 font-light mb-6">Nhập thông tin sản phẩm và số lượng kho khởi tạo ban đầu.</p>

            <form onSubmit={handleCreateProduct} className="space-y-4.5">
              <div>
                <label className="block text-xs font-bold text-zinc-700 mb-1.5 uppercase tracking-wider">Tên sản phẩm *</label>
                <input
                  type="text"
                  required
                  placeholder="Ví dụ: Tai nghe chụp tai VibeBass Pro"
                  value={formData.name}
                  onChange={(e) => setFormData({...formData, name: e.target.value})}
                  className="w-full h-11 px-4 rounded-xl bg-zinc-50 border border-zinc-200 text-sm text-zinc-950 focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 transition-colors"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-zinc-700 mb-1.5 uppercase tracking-wider">Mô tả sản phẩm</label>
                <textarea
                  placeholder="Nhập thông tin chi tiết về sản phẩm..."
                  value={formData.description}
                  onChange={(e) => setFormData({...formData, description: e.target.value})}
                  rows={3}
                  className="w-full p-4 rounded-xl bg-zinc-50 border border-zinc-200 text-sm text-zinc-950 focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 transition-colors resize-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-zinc-700 mb-1.5 uppercase tracking-wider">Giá gốc (VND) *</label>
                  <input
                    type="text"
                    required
                    min={0}
                    placeholder="Ví dụ: 150000"
                    value={formData.price}
                    onChange={(e) => setFormData({...formData, price: formatNumberInputString(e.target.value)})}
                    className="w-full h-11 px-4 rounded-xl bg-zinc-50 border border-zinc-200 text-sm text-zinc-950 focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 transition-colors"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-zinc-700 mb-1.5 uppercase tracking-wider">Giá giảm (VND)</label>
                  <input
                    type="text"
                    min={0}
                    placeholder="Ví dụ: 120000"
                    value={formData.discountPrice}
                    onChange={(e) => setFormData({...formData, discountPrice: formatNumberInputString(e.target.value)})}
                    className="w-full h-11 px-4 rounded-xl bg-zinc-50 border border-zinc-200 text-sm text-zinc-950 focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 transition-colors"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-zinc-700 mb-1.5 uppercase tracking-wider">Tồn kho ban đầu *</label>
                  <input
                    type="text"
                    required
                    min={0}
                    placeholder="Ví dụ: 100"
                    value={formData.initialStock}
                    onChange={(e) => setFormData({...formData, initialStock: formatNumberInputString(e.target.value)})}
                    className="w-full h-11 px-4 rounded-xl bg-zinc-50 border border-zinc-200 text-sm text-zinc-950 focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 transition-colors"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-zinc-700 mb-1.5 uppercase tracking-wider">Trạng thái bán</label>
                  <select
                    value={formData.status}
                    onChange={(e) => setFormData({...formData, status: e.target.value as "ACTIVE" | "INACTIVE"})}
                    className="w-full h-11 px-4 rounded-xl bg-zinc-50 border border-zinc-200 text-sm text-zinc-950 focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 transition-colors"
                  >
                    <option value="ACTIVE">Đang bán (ACTIVE)</option>
                    <option value="INACTIVE">Ngừng bán (INACTIVE)</option>
                  </select>
                </div>
              </div>

              <div className="flex gap-3.5 pt-4">
                <button
                  type="button"
                  onClick={() => setIsCreateOpen(false)}
                  className="flex-1 h-12 rounded-full border border-zinc-200 hover:bg-zinc-50 text-sm font-semibold text-zinc-700 transition-colors"
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  disabled={isPending}
                  className="flex-1 h-12 rounded-full bg-brand-500 hover:bg-brand-600 disabled:opacity-50 text-sm font-semibold text-white shadow-lg shadow-brand-500/10 flex items-center justify-center gap-2 active:scale-[0.98] transition-all duration-200"
                >
                  {isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                  Lưu sản phẩm
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
      {isEditOpen && selectedProduct && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-zinc-950/60 backdrop-blur-sm transition-opacity" onClick={() => setIsEditOpen(false)} />
          
          <div className="relative w-full max-w-lg bg-white rounded-[2rem] border border-zinc-200/80 shadow-2xl p-7 sm:p-8 animate-toast-in">
            <button 
              onClick={() => setIsEditOpen(false)}
              className="absolute right-6 top-6 p-1 rounded-lg text-zinc-400 hover:text-zinc-800 hover:bg-zinc-100 transition-colors"
            >
              <X className="h-5 w-5" />
            </button>

            <h3 className="text-xl font-extrabold text-zinc-950 mb-1.5 flex items-center gap-2">
              <Edit className="h-5 w-5 text-brand-500" />
              Chỉnh sửa thông tin
            </h3>
            <p className="text-xs text-zinc-400 font-light mb-6">Cập nhật chi tiết sản phẩm. (Lưu ý: Thay đổi tồn kho bằng tính năng Điều chỉnh kho riêng).</p>

            <form onSubmit={handleEditProduct} className="space-y-4.5">
              <div>
                <label className="block text-xs font-bold text-zinc-700 mb-1.5 uppercase tracking-wider">Tên sản phẩm *</label>
                <input
                  type="text"
                  required
                  placeholder="Ví dụ: Tai nghe chụp tai VibeBass Pro"
                  value={formData.name}
                  onChange={(e) => setFormData({...formData, name: e.target.value})}
                  className="w-full h-11 px-4 rounded-xl bg-zinc-50 border border-zinc-200 text-sm text-zinc-950 focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 transition-colors"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-zinc-700 mb-1.5 uppercase tracking-wider">Mô tả sản phẩm</label>
                <textarea
                  placeholder="Nhập thông tin chi tiết về sản phẩm..."
                  value={formData.description}
                  onChange={(e) => setFormData({...formData, description: e.target.value})}
                  rows={3}
                  className="w-full p-4 rounded-xl bg-zinc-50 border border-zinc-200 text-sm text-zinc-950 focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 transition-colors resize-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-zinc-700 mb-1.5 uppercase tracking-wider">Giá gốc (VND) *</label>
                  <input
                    type="text"
                    required
                    min={0}
                    placeholder="Ví dụ: 150000"
                    value={formData.price}
                    onChange={(e) => setFormData({...formData, price: formatNumberInputString(e.target.value)})}
                    className="w-full h-11 px-4 rounded-xl bg-zinc-50 border border-zinc-200 text-sm text-zinc-950 focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 transition-colors"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-zinc-700 mb-1.5 uppercase tracking-wider">Giá giảm (VND)</label>
                  <input
                    type="text"
                    min={0}
                    placeholder="Ví dụ: 120000"
                    value={formData.discountPrice}
                    onChange={(e) => setFormData({...formData, discountPrice: formatNumberInputString(e.target.value)})}
                    className="w-full h-11 px-4 rounded-xl bg-zinc-50 border border-zinc-200 text-sm text-zinc-950 focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 transition-colors"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-bold text-zinc-700 mb-1.5 uppercase tracking-wider">Trạng thái bán</label>
                <select
                  value={formData.status}
                  onChange={(e) => setFormData({...formData, status: e.target.value as "ACTIVE" | "INACTIVE"})}
                  className="w-full h-11 px-4 rounded-xl bg-zinc-50 border border-zinc-200 text-sm text-zinc-950 focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 transition-colors"
                >
                  <option value="ACTIVE">Đang bán (ACTIVE)</option>
                  <option value="INACTIVE">Ngừng bán (INACTIVE)</option>
                </select>
              </div>

              <div className="flex gap-3.5 pt-4">
                <button
                  type="button"
                  onClick={() => setIsEditOpen(false)}
                  className="flex-1 h-12 rounded-full border border-zinc-200 hover:bg-zinc-50 text-sm font-semibold text-zinc-700 transition-colors"
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  disabled={isPending}
                  className="flex-1 h-12 rounded-full bg-brand-500 hover:bg-brand-600 disabled:opacity-50 text-sm font-semibold text-white shadow-lg shadow-brand-500/10 flex items-center justify-center gap-2 active:scale-[0.98] transition-all duration-200"
                >
                  {isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                  Cập nhật
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
      {isAdjustOpen && selectedProduct && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-zinc-950/60 backdrop-blur-sm transition-opacity" onClick={() => setIsAdjustOpen(false)} />
          
          <div className="relative w-full max-w-md bg-white rounded-[2rem] border border-zinc-200/80 shadow-2xl p-7 sm:p-8 animate-toast-in">
            <button 
              onClick={() => setIsAdjustOpen(false)}
              className="absolute right-6 top-6 p-1 rounded-lg text-zinc-400 hover:text-zinc-800 hover:bg-zinc-100 transition-colors"
            >
              <X className="h-5 w-5" />
            </button>

            <h3 className="text-xl font-extrabold text-zinc-950 mb-1.5 flex items-center gap-2">
              <Package className="h-5 w-5 text-brand-500" />
              Điều chỉnh tồn kho
            </h3>
            <p className="text-xs text-zinc-400 font-light mb-6">
              Sản phẩm: <span className="font-bold text-zinc-850 ">{selectedProduct.name}</span>
              <br />
              Tồn kho hiện tại: <span className="font-bold text-brand-600">{selectedProduct.quantity.toLocaleString("vi-VN")} cái</span>
            </p>

            <form onSubmit={handleAdjustInventory} className="space-y-4.5">
              <div>
                <label className="block text-xs font-bold text-zinc-700 mb-1.5 uppercase tracking-wider">
                  Số lượng thay đổi * (Tăng/Giảm)
                </label>
                <input
                  type="number"
                  required
                  placeholder="Ví dụ: 15 để tăng 15 cái, hoặc -10 để giảm 10 cái"
                  value={adjustData.adjustment}
                  onChange={(e) => setAdjustData({...adjustData, adjustment: e.target.value})}
                  className="w-full h-11 px-4 rounded-xl bg-zinc-50 border border-zinc-200 text-sm text-zinc-950 focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 transition-colors"
                />
                <span className="inline-flex items-center gap-1 text-[11px] text-zinc-400 mt-1.5 font-light">
                  <Info className="h-3 w-3" />
                  Số âm (-) sẽ thực hiện giảm tồn kho, số dương (+) tăng tồn kho.
                </span>
              </div>

              <div>
                <label className="block text-xs font-bold text-zinc-700 mb-1.5 uppercase tracking-wider">Lý do điều chỉnh</label>
                <input
                  type="text"
                  placeholder="Ví dụ: Nhập thêm hàng mới, kiểm kê kho thừa..."
                  value={adjustData.reason}
                  onChange={(e) => setAdjustData({...adjustData, reason: e.target.value})}
                  className="w-full h-11 px-4 rounded-xl bg-zinc-50 border border-zinc-200 text-sm text-zinc-950 focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 transition-colors"
                />
              </div>

              <div className="flex gap-3.5 pt-4">
                <button
                  type="button"
                  onClick={() => setIsAdjustOpen(false)}
                  className="flex-1 h-12 rounded-full border border-zinc-200 hover:bg-zinc-50 text-sm font-semibold text-zinc-700 transition-colors"
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  disabled={isPending}
                  className="flex-1 h-12 rounded-full bg-brand-500 hover:bg-brand-600 disabled:opacity-50 text-sm font-semibold text-white shadow-lg shadow-brand-500/10 flex items-center justify-center gap-2 active:scale-[0.98] transition-all duration-200"
                >
                  {isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                  Cập nhật kho
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
      {isDeleteOpen && selectedProduct && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-zinc-950/60 backdrop-blur-sm transition-opacity" onClick={() => setIsDeleteOpen(false)} />
          
          <div className="relative w-full max-w-md bg-white rounded-[2rem] border border-zinc-200/80 shadow-2xl p-7 sm:p-8 animate-toast-in text-center">
            
            <div className="inline-flex h-14 w-14 items-center justify-center rounded-2xl bg-red-50 text-red-500 mb-5 border border-red-200/35">
              <AlertTriangle className="h-6 w-6" />
            </div>

            <h3 className="text-lg font-bold text-zinc-950 mb-2">Xác nhận xóa sản phẩm</h3>
            <p className="text-xs text-zinc-500 leading-relaxed max-w-xs mx-auto mb-6 font-light">
              Bạn có chắc chắn muốn xóa sản phẩm **"{selectedProduct.name}"**? Hành động này sẽ thực hiện **Soft Delete (Xóa mềm)** và không thể hoàn tác.
            </p>

            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => setIsDeleteOpen(false)}
                className="flex-1 h-11 rounded-full border border-zinc-200 hover:bg-zinc-50 text-xs font-semibold text-zinc-700 transition-colors"
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                onClick={handleDeleteProduct}
                disabled={isPending}
                className="flex-1 h-11 rounded-full bg-red-500 hover:bg-red-600 disabled:opacity-50 text-xs font-semibold text-white shadow-lg shadow-red-500/15 flex items-center justify-center gap-2 active:scale-[0.98] transition-all duration-200"
              >
                {isPending && <Loader2 className="h-4.5 w-4.5 animate-spin" />}
                Đồng ý xóa
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
