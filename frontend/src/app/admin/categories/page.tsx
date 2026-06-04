"use client";

import React, { useState, useEffect, useTransition, useCallback, useMemo } from "react";
import { useRouter } from "next/navigation";
import { 
  FolderTree, 
  Folder, 
  FolderOpen,
  ChevronRight, 
  ChevronDown, 
  Plus, 
  Edit, 
  Trash2, 
  Loader2, 
  Shield, 
  Activity, 
  AlertTriangle, 
  CheckCircle, 
  X, 
  Info,
  Layers,
  ArrowRight,
  RefreshCw,
  FolderPlus
} from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useToast } from "@/context/ToastContext";
import { Forbidden } from "@/components/common/Forbidden";
import { categoryService } from "@/services/category.service";
import { Category } from "@/types";
import { ROUTES } from "@/constants/routes";

// Real-time Vietnamese-to-English Auto-Slug Generator
const convertToSlug = (text: string): string => {
  if (!text) return "";
  let slug = text.toLowerCase();
  
  // Replace Vietnamese accents
  slug = slug.replace(/[áàảãạăắằẳẵặâấầẩẫậ]/g, "a");
  slug = slug.replace(/[éèẻẽẹêếềểễệ]/g, "e");
  slug = slug.replace(/[íìỉĩị]/g, "i");
  slug = slug.replace(/[óòỏõọôốồổỗộơớờởỡợ]/g, "o");
  slug = slug.replace(/[úùủũụưứừửữự]/g, "u");
  slug = slug.replace(/[ýỳỷỹỵ]/g, "y");
  slug = slug.replace(/đ/g, "d");
  
  // Remove special characters, strip spaces and replace with dashes
  slug = slug.replace(/[^a-z0-9\s-]/g, "");
  slug = slug.replace(/\s+/g, "-");
  slug = slug.replace(/-+/g, "-");
  slug = slug.trim().replace(/^-+|-+$/g, "");
  
  return slug;
};

export default function AdminCategoriesPage() {
  const { user, isAuthenticated, isLoading: isAuthLoading } = useAuth();
  const toast = useToast();
  const router = useRouter();

  // State Management
  const [categoriesTree, setCategoriesTree] = useState<Category[]>([]);
  const [isDataLoading, setIsDataLoading] = useState(false);
  const [expandedNodes, setExpandedNodes] = useState<Record<string, boolean>>({});
  const [isPending, startTransition] = useTransition();

  // Panel details & Action States
  const [action, setAction] = useState<"idle" | "create" | "edit">("idle");
  const [selectedCategory, setSelectedCategory] = useState<Category | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Category | null>(null);

  // Form states
  const [formData, setFormData] = useState({
    name: "",
    parentId: "" as string | null
  });

  // Guard routing & authorize admins
  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      toast.warning("Yêu cầu đăng nhập", "Vui lòng đăng nhập để truy cập trang quản trị.");
      router.push(ROUTES.LOGIN);
    }
  }, [isAuthenticated, isAuthLoading, router, toast]);

  // Fetch Category Tree
  const fetchCategories = useCallback(async () => {
    setIsDataLoading(true);
    try {
      const data = await categoryService.getCategoriesTree();
      setCategoriesTree(data || []);
      
      // Auto-expand root nodes on first fetch if they aren't configured
      if (Object.keys(expandedNodes).length === 0 && data) {
        const rootExpands: Record<string, boolean> = {};
        data.forEach((cat) => {
          rootExpands[cat.id] = true;
        });
        setExpandedNodes(rootExpands);
      }
    } catch (error: any) {
      console.error("Lỗi khi lấy cây danh mục:", error);
      toast.error("Lỗi tải dữ liệu", "Không thể lấy cây danh mục từ máy chủ.");
    } finally {
      setIsDataLoading(false);
    }
  }, [expandedNodes, toast]);

  useEffect(() => {
    if (isAuthenticated && user?.roles?.includes("ROLE_ADMIN")) {
      fetchCategories();
    }
  }, [isAuthenticated, user, fetchCategories]);

  // Toggle Collapse / Expand
  const toggleExpand = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setExpandedNodes(prev => ({
      ...prev,
      [id]: !prev[id]
    }));
  };

  // Flattened categories for the parent select dropdown
  const flatCategoriesList = useMemo(() => {
    const list: { id: string; name: string }[] = [];
    const recurse = (cats: Category[], level = 0) => {
      cats.forEach(cat => {
        // Render indent spacing for clarity
        const indent = "— ".repeat(level);
        list.push({ id: cat.id, name: `${indent}${cat.name}` });
        if (cat.children && cat.children.length > 0) {
          recurse(cat.children, level + 1);
        }
      });
    };
    recurse(categoriesTree);
    return list;
  }, [categoriesTree]);

  // Dynamic Slug Preview of current form Name input
  const slugPreview = useMemo(() => convertToSlug(formData.name), [formData.name]);

  // Calculate Metrics
  const metrics = useMemo(() => {
    let total = 0;
    let parents = 0;
    let leaves = 0;

    const recurse = (cats: Category[]) => {
      cats.forEach(cat => {
        total++;
        if (cat.children && cat.children.length > 0) {
          parents++;
          recurse(cat.children);
        } else {
          leaves++;
        }
      });
    };
    recurse(categoriesTree);

    return { total, parents, leaves };
  }, [categoriesTree]);

  // Trigger Add Root Category
  const handleOpenCreateRoot = () => {
    setSelectedCategory(null);
    setAction("create");
    setFormData({
      name: "",
      parentId: ""
    });
  };

  // Trigger Add Child Category
  const handleOpenCreateChild = (parentCat: Category, e: React.MouseEvent) => {
    e.stopPropagation();
    setSelectedCategory(parentCat);
    setAction("create");
    setFormData({
      name: "",
      parentId: parentCat.id
    });
  };

  // Trigger Edit Category
  const handleOpenEdit = (cat: Category, e: React.MouseEvent) => {
    e.stopPropagation();
    setSelectedCategory(cat);
    setAction("edit");
    setFormData({
      name: cat.name,
      parentId: cat.parentId || ""
    });
  };

  // Submit creation / update
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.name.trim()) {
      toast.warning("Thiếu dữ liệu", "Vui lòng nhập tên danh mục.");
      return;
    }

    startTransition(async () => {
      try {
        const payload = {
          name: formData.name.trim(),
          parentId: formData.parentId || null
        };

        if (action === "create") {
          await categoryService.createCategory(payload);
          toast.success("Tạo thành công", `Danh mục "${payload.name}" đã được khởi tạo.`);
        } else if (action === "edit" && selectedCategory) {
          // Guard loop parent referencing
          if (payload.parentId === selectedCategory.id) {
            toast.error("Lỗi phân cấp", "Không thể gán danh mục cha trùng với chính nó.");
            return;
          }
          await categoryService.updateCategory(selectedCategory.id, payload);
          toast.success("Cập nhật thành công", `Danh mục "${payload.name}" đã được chỉnh sửa.`);
        }

        setAction("idle");
        setSelectedCategory(null);
        fetchCategories();
      } catch (error: any) {
        console.error("Lỗi khi xử lý danh mục:", error);
        toast.error(
          "Thao tác thất bại",
          error.data?.message || error.message || "Không thể lưu danh mục vào hệ thống."
        );
      }
    });
  };

  // Safe Category Deletion Dialog confirmation
  const handleConfirmDelete = async () => {
    if (!deleteTarget) return;

    // Client-side safety guards check
    if (deleteTarget.children && deleteTarget.children.length > 0) {
      toast.error("Không thể xóa", "Danh mục này hiện chứa các danh mục con. Hãy dọn dẹp hoặc chuyển danh mục con trước.");
      setDeleteTarget(null);
      return;
    }

    startTransition(async () => {
      try {
        await categoryService.deleteCategory(deleteTarget.id);
        toast.success("Xóa thành công", `Đã xóa danh mục "${deleteTarget.name}".`);
        setDeleteTarget(null);
        setSelectedCategory(null);
        setAction("idle");
        fetchCategories();
      } catch (error: any) {
        console.error("Lỗi khi xóa danh mục:", error);
        toast.error(
          "Lỗi xóa danh mục",
          error.data?.message || error.message || "Có lỗi xảy ra hoặc danh mục đang liên kết với các sản phẩm đang bán."
        );
        setDeleteTarget(null);
      }
    });
  };

  // Dynamic category tree node renderer
  const renderTreeNode = (cat: Category, depth = 0) => {
    const isParent = cat.children && cat.children.length > 0;
    const isExpanded = expandedNodes[cat.id];
    
    // leaf criteria: No sub-categories AND has parent category (or depth > 0)
    const isLeaf = !isParent && (cat.parentId || depth > 0);

    return (
      <div key={cat.id} className="select-none transition-all">
        {/* Node Box */}
        <div 
          onClick={() => {
            setSelectedCategory(cat);
            if (action === "edit") {
              setFormData({ name: cat.name, parentId: cat.parentId || "" });
            }
          }}
          className={`group flex items-center justify-between py-3.5 px-4 rounded-2xl border mb-2 cursor-pointer transition-all duration-200 ${
            selectedCategory?.id === cat.id
              ? "bg-brand-50/50 border-brand-300 text-brand-900 shadow-sm"
              : "bg-white hover:bg-zinc-50/60 border-zinc-200/70 text-zinc-800 "
          }`}
        >
          <div className="flex items-center gap-3 min-w-0">
            {/* Collapse toggle button */}
            {isParent ? (
              <button 
                onClick={(e) => toggleExpand(cat.id, e)}
                className="h-6 w-6 rounded-lg flex items-center justify-center text-zinc-400 hover:text-zinc-650 hover:bg-zinc-100 transition-colors"
              >
                {isExpanded ? (
                  <ChevronDown className="h-4.5 w-4.5" />
                ) : (
                  <ChevronRight className="h-4.5 w-4.5" />
                )}
              </button>
            ) : (
              <span className="w-6 shrink-0" />
            )}

            {/* Folder or Document Icon */}
            {isParent ? (
              isExpanded ? (
                <FolderOpen className="h-5 w-5 text-brand-500 shrink-0" />
              ) : (
                <Folder className="h-5 w-5 text-brand-400 shrink-0" />
              )
            ) : (
              <Layers className="h-5 w-5 text-zinc-400 shrink-0" />
            )}

            {/* Title & URL preview */}
            <div className="flex flex-col min-w-0">
              <span className="font-bold text-sm truncate leading-snug">{cat.name}</span>
              <span className="text-[11px] text-zinc-400 font-mono tracking-tight mt-0.5 truncate">/{cat.slug}</span>
            </div>

            {/* Type badge */}
            <div className="hidden sm:inline-flex ml-2">
              {isLeaf ? (
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-extrabold bg-emerald-50 text-emerald-700 border border-emerald-100 ">
                  📌 Danh mục Lá
                </span>
              ) : (
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-extrabold bg-blue-50 text-blue-700 border border-blue-100 ">
                  📁 Danh mục Cha
                </span>
              )}
            </div>
          </div>

          {/* Actions panel */}
          <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
            <button
              onClick={(e) => handleOpenCreateChild(cat, e)}
              title="Thêm danh mục con"
              className="p-1.5 rounded-lg border border-zinc-200 hover:bg-brand-50 hover:text-brand-600 hover:border-brand-200 transition-all text-zinc-500 "
            >
              <FolderPlus className="h-4 w-4" />
            </button>
            <button
              onClick={(e) => handleOpenEdit(cat, e)}
              title="Chỉnh sửa thông tin"
              className="p-1.5 rounded-lg border border-zinc-200 hover:bg-zinc-50 hover:text-zinc-800 transition-all text-zinc-500 "
            >
              <Edit className="h-4 w-4" />
            </button>
            <button
              onClick={(e) => {
                e.stopPropagation();
                setDeleteTarget(cat);
              }}
              title="Xóa danh mục"
              className="p-1.5 rounded-lg border border-zinc-200 hover:bg-red-50 hover:text-red-500 hover:border-red-200 transition-all text-zinc-500 "
            >
              <Trash2 className="h-4 w-4" />
            </button>
          </div>
        </div>

        {/* Children Render recursion */}
        {isParent && isExpanded && (
          <div className="border-l-2 border-dashed border-zinc-200/70 ml-6 pl-4 transition-all duration-300 animate-in fade-in slide-in-from-top-1 duration-150">
            {cat.children!.map((child) => renderTreeNode(child, depth + 1))}
          </div>
        )}
      </div>
    );
  };

  // Auth Loading
  if (isAuthLoading) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center min-h-[70vh] bg-zinc-50 transition-colors duration-300">
        <Loader2 className="h-10 w-10 text-brand-500 animate-spin mb-4" />
        <p className="text-sm text-zinc-500 font-light">Đang xác thực quyền truy cập...</p>
      </div>
    );
  }

  // Security Guard Check Role Admin
  if (!isAuthenticated || !user?.roles?.includes("ROLE_ADMIN")) {
    return <Forbidden />;
  }

  return (
    <div className="flex-1 flex flex-col bg-zinc-50 px-6 py-10 transition-colors duration-300 relative min-h-screen">
      {/* Visual background blobs */}
      <div className="absolute top-[10%] right-[5%] w-80 h-80 bg-brand-100/10 rounded-full blur-[110px] pointer-events-none" />
      <div className="absolute bottom-[8%] left-[10%] w-96 h-96 bg-brand-200/10 rounded-full blur-[130px] pointer-events-none" />

      <div className="max-w-6xl w-full mx-auto relative z-10 flex-1 flex flex-col space-y-8">
        
        {/* ═══════════════════════════════════════════════════════════════ */}
        {/* DASHBOARD HEADER */}
        {/* ═══════════════════════════════════════════════════════════════ */}
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-zinc-200/60 pb-6">
          <div>
            <div className="flex items-center gap-3">
              <div className="p-2 bg-brand-50 rounded-2xl border border-brand-100/50 shadow-2xs">
                <FolderTree className="h-6 w-6 text-brand-600 " />
              </div>
              <h1 className="text-3xl font-extrabold text-zinc-950 tracking-tight">Quản trị Cây danh mục</h1>
            </div>
            <p className="text-zinc-500 text-sm mt-1.5">
              Tổ chức cấu trúc phân cấp danh mục ba cấp (Thời trang → Nam → Quần áo). Tạo mới, liên kết cha con và auto-slug.
            </p>
          </div>
          
          <div className="inline-flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-xs font-bold bg-amber-50 text-amber-700 border border-amber-200 shadow-2xs self-start md:self-center">
            <Activity className="h-3.5 w-3.5 text-amber-500 animate-pulse" />
            Hệ thống Quản trị Viên
          </div>
        </div>

        {/* ═══════════════════════════════════════════════════════════════ */}
        {/* METRICS ROW */}
        {/* ═══════════════════════════════════════════════════════════════ */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-5">
          <div className="bg-white border border-zinc-200/60 rounded-3xl p-5 shadow-sm flex items-center justify-between hover:shadow-md transition-all duration-300">
            <div className="space-y-1">
              <span className="text-xs font-bold text-zinc-400 uppercase tracking-wider block">Tổng số danh mục</span>
              <span className="text-3xl font-black text-zinc-900 tracking-tight">
                {isDataLoading && categoriesTree.length === 0 ? <Loader2 className="w-5 h-5 animate-spin text-zinc-400 inline" /> : metrics.total}
              </span>
            </div>
            <div className="p-3.5 bg-zinc-50 border border-zinc-100 rounded-2xl">
              <FolderTree className="h-6 w-6 text-zinc-500 " />
            </div>
          </div>

          <div className="bg-white border border-zinc-200/60 rounded-3xl p-5 shadow-sm flex items-center justify-between hover:shadow-md transition-all duration-300">
            <div className="space-y-1">
              <span className="text-xs font-bold text-zinc-400 uppercase tracking-wider block">Danh mục Cha (Container)</span>
              <span className="text-3xl font-black text-blue-600 tracking-tight">
                {isDataLoading && categoriesTree.length === 0 ? <Loader2 className="w-5 h-5 animate-spin text-zinc-400 inline" /> : metrics.parents}
              </span>
            </div>
            <div className="p-3.5 bg-blue-50 rounded-2xl border border-blue-100/50 ">
              <Folder className="h-6 w-6 text-blue-600 " />
            </div>
          </div>

          <div className="bg-white border border-zinc-200/60 rounded-3xl p-5 shadow-sm flex items-center justify-between hover:shadow-md transition-all duration-300">
            <div className="space-y-1">
              <span className="text-xs font-bold text-zinc-400 uppercase tracking-wider block">Danh mục Lá (Leaf Node)</span>
              <span className="text-3xl font-black text-emerald-600 tracking-tight">
                {isDataLoading && categoriesTree.length === 0 ? <Loader2 className="w-5 h-5 animate-spin text-zinc-400 inline" /> : metrics.leaves}
              </span>
            </div>
            <div className="p-3.5 bg-emerald-50 rounded-2xl border border-emerald-100/50 ">
              <Layers className="h-6 w-6 text-emerald-600 " />
            </div>
          </div>
        </div>

        {/* ═══════════════════════════════════════════════════════════════ */}
        {/* MAIN SPLIT GRID */}
        {/* ═══════════════════════════════════════════════════════════════ */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
          
          {/* Left Column: Interactive Tree View */}
          <div className="lg:col-span-2 space-y-4">
            <div className="bg-white rounded-3xl border border-zinc-200/70 p-5 sm:p-6 shadow-sm min-h-[450px] flex flex-col">
              <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3 border-b border-zinc-150/70 pb-4.5 mb-6">
                <div>
                  <h3 className="text-base font-bold text-zinc-900 ">Cơ cấu cây danh mục</h3>
                  <p className="text-xs text-zinc-400 font-light mt-0.5">Click vào danh mục để xem thông tin chi tiết hoặc mở rộng con.</p>
                </div>

                <div className="flex gap-2 w-full sm:w-auto">
                  <button
                    onClick={() => fetchCategories()}
                    className="flex h-10 w-10 items-center justify-center rounded-xl border border-zinc-200 hover:bg-zinc-50 text-zinc-600 "
                    title="Làm mới dữ liệu"
                  >
                    <RefreshCw className={`h-4 w-4 ${isDataLoading ? "animate-spin" : ""}`} />
                  </button>
                  <button
                    onClick={handleOpenCreateRoot}
                    className="flex h-10 items-center gap-1.5 rounded-xl bg-brand-500 hover:bg-brand-600 text-xs font-bold text-white px-4 active:scale-[0.98] transition-all shadow-sm shadow-brand-500/10 shrink-0 w-full sm:w-auto justify-center"
                  >
                    <Plus className="h-4 w-4" />
                    Thêm danh mục gốc
                  </button>
                </div>
              </div>

              {/* Tree Renderer */}
              <div className="flex-1">
                {isDataLoading && categoriesTree.length === 0 ? (
                  <div className="flex flex-col items-center justify-center py-24">
                    <Loader2 className="h-8 w-8 text-brand-500 animate-spin mb-3" />
                    <p className="text-xs text-zinc-400 font-light">Đang đồng bộ hóa cây danh mục...</p>
                  </div>
                ) : categoriesTree.length === 0 ? (
                  <div className="flex flex-col items-center justify-center py-20 text-center max-w-sm mx-auto">
                    <div className="h-14 w-14 rounded-2xl bg-zinc-50 border border-zinc-100 flex items-center justify-center text-zinc-400 mb-4.5 shadow-2xs">
                      <FolderTree className="h-6 w-6" />
                    </div>
                    <h4 className="text-sm font-bold text-zinc-800 ">Cây danh mục rỗng</h4>
                    <p className="text-xs text-zinc-400 mt-1 font-light leading-relaxed">
                      Chưa có danh mục nào được đăng ký trong hệ thống. Nhấp vào nút thêm ở trên để bắt đầu cấu hình.
                    </p>
                  </div>
                ) : (
                  <div className="space-y-1">
                    {categoriesTree.map((cat) => renderTreeNode(cat, 0))}
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* Right Column: Form Panel Card */}
          <div className="lg:col-span-1">
            <div className="bg-white rounded-3xl border border-zinc-200/70 p-6 shadow-sm sticky top-24">
              
              {action === "idle" ? (
                <div className="py-10 text-center space-y-4">
                  <div className="h-16 w-16 rounded-3xl bg-zinc-50 border border-zinc-100 flex items-center justify-center text-zinc-400 mx-auto shadow-2xs">
                    {selectedCategory ? (
                      selectedCategory.children && selectedCategory.children.length > 0 ? (
                        <Folder className="h-7 w-7 text-blue-500" />
                      ) : (
                        <Layers className="h-7 w-7 text-emerald-500" />
                      )
                    ) : (
                      <FolderTree className="h-7 w-7 text-zinc-400" />
                    )}
                  </div>

                  {selectedCategory ? (
                    <div className="space-y-4">
                      <div>
                        <h4 className="text-base font-extrabold text-zinc-900 ">{selectedCategory.name}</h4>
                        <p className="text-[11px] text-zinc-400 font-mono mt-0.5">ID: {selectedCategory.id}</p>
                      </div>

                      <div className="p-4 bg-zinc-50 rounded-2xl border border-zinc-150 space-y-3.5 text-xs text-left">
                        <div className="flex justify-between items-center">
                          <span className="text-zinc-400 font-semibold">Tên danh mục:</span>
                          <span className="font-bold text-zinc-800 ">{selectedCategory.name}</span>
                        </div>
                        <div className="flex justify-between items-center">
                          <span className="text-zinc-400 font-semibold">Slug URL:</span>
                          <span className="font-mono bg-zinc-100 px-2 py-0.5 rounded text-[11px] font-bold text-zinc-700 ">/{selectedCategory.slug}</span>
                        </div>
                        <div className="flex justify-between items-center">
                          <span className="text-zinc-400 font-semibold">Phân nhóm:</span>
                          {selectedCategory.children && selectedCategory.children.length > 0 ? (
                            <span className="px-2 py-0.5 rounded-full text-[10px] font-extrabold bg-blue-50 text-blue-700 border border-blue-100 ">📁 Danh mục Cha</span>
                          ) : (
                            <span className="px-2 py-0.5 rounded-full text-[10px] font-extrabold bg-emerald-50 text-emerald-700 border border-emerald-100 ">📌 Danh mục Lá</span>
                          )}
                        </div>
                        <div className="flex justify-between items-center">
                          <span className="text-zinc-400 font-semibold">Số cấp con trực tiếp:</span>
                          <span className="font-bold text-zinc-800 ">{selectedCategory.children?.length || 0} mục</span>
                        </div>
                      </div>

                      <div className="flex gap-2 pt-2">
                        <button
                          onClick={(e) => handleOpenEdit(selectedCategory, e)}
                          className="flex-1 flex h-11 items-center justify-center gap-1.5 rounded-xl border border-zinc-200 hover:bg-zinc-50 text-xs font-bold text-zinc-700 transition-colors"
                        >
                          <Edit className="h-3.5 w-3.5" />
                          Chỉnh sửa
                        </button>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            setDeleteTarget(selectedCategory);
                          }}
                          className="flex-1 flex h-11 items-center justify-center gap-1.5 rounded-xl border border-red-200 hover:bg-red-50 text-xs font-bold text-red-600 transition-colors"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                          Xóa bỏ
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div>
                      <h4 className="text-sm font-bold text-zinc-800 ">Bảng điều khiển chi tiết</h4>
                      <p className="text-xs text-zinc-400 mt-1 font-light leading-relaxed max-w-[200px] mx-auto">
                        Chọn một danh mục bên cây phân cấp để xem chi tiết, chỉnh sửa hoặc xóa nhanh.
                      </p>
                    </div>
                  )}

                </div>
              ) : (
                // Add / Edit Form
                <div className="space-y-6">
                  <div>
                    <h3 className="text-base font-extrabold text-zinc-900 flex items-center gap-2">
                      {action === "create" ? (
                        <>
                          <Plus className="h-5 w-5 text-brand-500" />
                          Tạo danh mục mới
                        </>
                      ) : (
                        <>
                          <Edit className="h-5 w-5 text-brand-500" />
                          Cập nhật danh mục
                        </>
                      )}
                    </h3>
                    <p className="text-xs text-zinc-400 font-light mt-0.5">
                      {action === "create" ? "Nhập thông tin để thêm một danh mục con hoặc gốc." : "Cập nhật lại tên hoặc cấp độ cha của danh mục."}
                    </p>
                  </div>

                  <form onSubmit={handleSubmit} className="space-y-4.5">
                    {/* Name input */}
                    <div>
                      <label className="block text-xs font-bold text-zinc-700 uppercase tracking-wider mb-1.5">Tên danh mục *</label>
                      <input
                        type="text"
                        required
                        placeholder="Ví dụ: Áo thun nam, Điện thoại..."
                        value={formData.name}
                        onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                        className="w-full h-11 px-4 rounded-xl bg-zinc-50 border border-zinc-200 text-sm focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 focus:bg-white text-zinc-950 transition-all duration-150"
                      />
                    </div>

                    {/* Slug Auto Generator display banner */}
                    {formData.name && (
                      <div className="p-3.5 bg-brand-50/50 border border-brand-100 rounded-2xl text-xs space-y-1 animate-in fade-in duration-200">
                        <span className="text-[10px] font-extrabold text-brand-600 uppercase tracking-wider block">Auto-slug URL preview</span>
                        <div className="flex items-center gap-1 text-brand-900 font-mono truncate">
                          <span className="opacity-45 select-none text-[11px]">vibecart.com/category/</span>
                          <span className="font-bold text-[11px]">{slugPreview}</span>
                        </div>
                        <span className="inline-flex items-center gap-1 text-[10px] text-brand-500 mt-1 font-light">
                          <CheckCircle className="h-3 w-3 shrink-0" />
                          Tiếng Việt có dấu sẽ tự động được chuyển hóa thành URL không dấu.
                        </span>
                      </div>
                    )}

                    {/* Parent select dropdown */}
                    <div>
                      <label className="block text-xs font-bold text-zinc-700 uppercase tracking-wider mb-1.5">Danh mục cha</label>
                      <select
                        value={formData.parentId || ""}
                        onChange={(e) => setFormData({ ...formData, parentId: e.target.value || "" })}
                        className="w-full h-11 px-4 rounded-xl bg-zinc-50 border border-zinc-200 text-sm focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 cursor-pointer text-zinc-950 transition-colors"
                      >
                        <option value="">Không có (Danh mục gốc - Root Category)</option>
                        {flatCategoriesList.map((cat) => {
                          // Exclude current edited node from its own parent choice list
                          if (action === "edit" && selectedCategory && cat.id === selectedCategory.id) {
                            return null;
                          }
                          return (
                            <option key={cat.id} value={cat.id}>
                              {cat.name}
                            </option>
                          );
                        })}
                      </select>
                    </div>

                    {/* Buttons row */}
                    <div className="flex gap-3 pt-3">
                      <button
                        type="button"
                        onClick={() => {
                          setAction("idle");
                          setSelectedCategory(null);
                        }}
                        className="flex-1 h-12 rounded-full border border-zinc-200 hover:bg-zinc-50 text-xs font-bold text-zinc-700 transition-colors"
                      >
                        Hủy bỏ
                      </button>
                      <button
                        type="submit"
                        disabled={isPending}
                        className="flex-1 h-12 rounded-full bg-brand-500 hover:bg-brand-600 disabled:opacity-50 text-xs font-bold text-white shadow-md shadow-brand-500/10 flex items-center justify-center gap-1.5 active:scale-[0.98] transition-all"
                      >
                        {isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                        Lưu lại
                      </button>
                    </div>
                  </form>
                </div>
              )}

            </div>
          </div>

        </div>

      </div>

      {/* ═══════════════════════════════════════════════════════════════ */}
      {/* MODAL: SAFE CONFIRM DELETE */}
      {/* ═══════════════════════════════════════════════════════════════ */}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-zinc-950/60 backdrop-blur-sm transition-opacity" onClick={() => setDeleteTarget(null)} />
          
          <div className="relative w-full max-w-md bg-white rounded-3xl border border-zinc-200 shadow-2xl p-6 sm:p-7 animate-toast-in">
            <button 
              onClick={() => setDeleteTarget(null)}
              className="absolute right-5 top-5 p-1 rounded-lg text-zinc-400 hover:text-zinc-700 hover:bg-zinc-50 transition-colors"
            >
              <X className="h-5 w-5" />
            </button>

            <h3 className="text-lg font-extrabold text-zinc-900 mb-1.5 flex items-center gap-2">
              <AlertTriangle className="h-5.5 w-5.5 text-red-500 shrink-0" />
              Xác nhận xóa danh mục
            </h3>
            <p className="text-xs text-zinc-400 font-light mb-5">
              Bạn có chắc chắn muốn xóa danh mục <span className="font-bold text-zinc-800 ">"{deleteTarget.name}"</span> khỏi hệ thống? Thao tác này không thể hoàn tác.
            </p>

            {deleteTarget.children && deleteTarget.children.length > 0 ? (
              <div className="p-3.5 bg-rose-50 border border-rose-100 rounded-2xl text-xs text-rose-700 space-y-1.5 mb-5 flex items-start gap-2">
                <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5 text-rose-500" />
                <span>
                  <strong>Lỗi nghiêm trọng:</strong> Danh mục này đang chứa <strong>{deleteTarget.children.length}</strong> danh mục con. Hệ thống chặn hoàn toàn thao tác này để ngăn chặn orphan nodes (các nút con mồ côi). Hãy xóa các danh mục con trước!
                </span>
              </div>
            ) : (
              <div className="p-3.5 bg-amber-50 border border-amber-100 rounded-2xl text-xs text-amber-700 space-y-1.5 mb-5 flex items-start gap-2 animate-pulse">
                <Info className="w-4 h-4 shrink-0 mt-0.5 text-amber-500" />
                <span>
                  <strong>Lưu ý:</strong> Thao tác xóa sẽ được kiểm chứng tính liên kết với các sản phẩm đang bán trên máy chủ. Nếu danh mục đang được liên kết với bất kỳ sản phẩm nào, hệ thống sẽ ném lỗi bảo vệ.
                </span>
              </div>
            )}

            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => setDeleteTarget(null)}
                className="flex-1 h-11 rounded-full border border-zinc-200 hover:bg-zinc-50 text-xs font-bold text-zinc-700 transition-colors"
              >
                Hủy bỏ
              </button>
              <button
                onClick={handleConfirmDelete}
                disabled={isPending || (deleteTarget.children && deleteTarget.children.length > 0)}
                className="flex-1 h-11 rounded-full bg-red-500 hover:bg-red-600 disabled:opacity-40 disabled:hover:bg-red-500 disabled:cursor-not-allowed text-xs font-bold text-white shadow-md shadow-red-500/10 flex items-center justify-center gap-1.5"
              >
                {isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                Đồng ý xóa
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}
