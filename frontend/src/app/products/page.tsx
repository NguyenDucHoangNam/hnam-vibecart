"use client";

import React, { useState, useEffect, useRef, useCallback, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import {
  Search,
  SlidersHorizontal,
  Tag,
  Package,
  ChevronRight,
  Sparkles,
  Filter,
  X,
  Flame,
  History,
  Clock,
  ArrowRight,
  TrendingUp,
  AlertCircle,
  HelpCircle,
  Trash2,
  Users,
  UserCheck,
  UserPlus
} from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useToast } from "@/context/ToastContext";
import { searchService, SearchParams } from "@/services/search.service";
import { categoryService } from "@/services/category.service";
import { userService } from "@/services/user.service";
import { ProductDocument, Category, UserSearchResponse } from "@/types";
import { ROUTES } from "@/constants/routes";

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

function ShopContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const toast = useToast();
  const { isAuthenticated, user: currentUser } = useAuth();
  const urlQuery = searchParams.get("q") || searchParams.get("query") || "";
  const urlCategory = searchParams.get("categoryId") || searchParams.get("category") || "";
  const [products, setProducts] = useState<ProductDocument[]>([]);
  const [creators, setCreators] = useState<UserSearchResponse[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [totalProducts, setTotalProducts] = useState(0);
  const [totalCreators, setTotalCreators] = useState(0);
  const [isLoadingProducts, setIsLoadingProducts] = useState(true);
  const [isLoadingCreators, setIsLoadingCreators] = useState(false);
  const [page, setPage] = useState(0);
  const [size] = useState(12);
  const [productSuggestion, setProductSuggestion] = useState<string | null>(null);
  const [searchVal, setSearchVal] = useState(urlQuery);
  const [debouncedSearchVal, setDebouncedSearchVal] = useState(urlQuery);
  const [selectedCategory, setSelectedCategory] = useState(urlCategory);
  const [minPrice, setMinPrice] = useState<number | "">("");
  const [debouncedMinPrice, setDebouncedMinPrice] = useState<number | "">("");
  const [maxPrice, setMaxPrice] = useState<number | "">("");
  const [debouncedMaxPrice, setDebouncedMaxPrice] = useState<number | "">("");
  const [sortBy, setSortBy] = useState("relevance");
  const [isFilterMobileOpen, setIsFilterMobileOpen] = useState(false);
  const [isFilterVisible, setIsFilterVisible] = useState(true);
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const [trendingKeywords, setTrendingKeywords] = useState<string[]>([]);
  const [recentKeywords, setRecentKeywords] = useState<{ keyword: string; searchedAt: string }[]>([]);
  const [creatorMap, setCreatorMap] = useState<Record<string, { username: string; fullName: string; avatarUrl?: string }>>({});
  const [activeTab, setActiveTab] = useState<"products" | "creators">("products");
  const dropdownRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    categoryService.getCategoriesTree().then(d => setCategories(d || [])).catch(() => {});
    searchService.getTrending().then(d => setTrendingKeywords(d || [])).catch(() => {});
  }, []);
  useEffect(() => {
    if (products.length === 0) return;
    const uniqueCreatorIds = Array.from(new Set(products.map(p => p.creatorId).filter(Boolean)));
    const fetchMissingCreators = async () => {
      const missingIds = uniqueCreatorIds.filter(id => !creatorMap[id]);
      if (missingIds.length === 0) return;

      const newProfiles = await Promise.all(
        missingIds.map(async (id) => {
          try {
            const profile = await userService.getUserProfile(id);
            return { id, profile };
          } catch (e) {
            console.error("Failed to fetch profile for", id, e);
            return { id, profile: null };
          }
        })
      );

      setCreatorMap(prev => {
        const next = { ...prev };
        newProfiles.forEach(({ id, profile }) => {
          if (profile) {
            next[id] = profile;
          }
        });
        return next;
      });
    };

    fetchMissingCreators();
  }, [products]);
  useEffect(() => {
    const q = searchParams.get("q") || searchParams.get("query") || "";
    setSearchVal(q);
    setDebouncedSearchVal(q);
    setSelectedCategory(searchParams.get("categoryId") || searchParams.get("category") || "");
    setPage(0);
  }, [searchParams]);
  const loadRecentSearches = useCallback(async () => {
    if (isAuthenticated) {
      try {
        const localRaw = localStorage.getItem("vibecart_search_history");
        if (localRaw) {
          const local = JSON.parse(localRaw) as { keyword: string; searchedAt: string }[];
          if (local.length > 0) {
            await searchService.mergeHistory(local);
            localStorage.removeItem("vibecart_search_history");
          }
        }
        const server = await searchService.getHistory();
        setRecentKeywords(server || []);
      } catch { setRecentKeywords([]); }
    } else {
      try {
        const raw = localStorage.getItem("vibecart_search_history");
        setRecentKeywords(raw ? JSON.parse(raw) : []);
      } catch { setRecentKeywords([]); }
    }
  }, [isAuthenticated]);

  useEffect(() => { loadRecentSearches(); }, [loadRecentSearches]);
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node) &&
          inputRef.current && !inputRef.current.contains(e.target as Node)) {
        setIsDropdownOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);
  useEffect(() => {
    if (!searchVal.trim()) { setSuggestions([]); return; }
    const t = setTimeout(async () => {
      try {
        const data = await searchService.getAutocomplete(searchVal);
        setSuggestions(data || []);
      } catch { setSuggestions([]); }
    }, 300);
    return () => clearTimeout(t);
  }, [searchVal]);

  useEffect(() => {
    const t = setTimeout(() => {
      setDebouncedSearchVal(searchVal);
    }, 500);
    return () => clearTimeout(t);
  }, [searchVal]);

  useEffect(() => {
    const t = setTimeout(() => {
      setDebouncedMinPrice(minPrice);
      setDebouncedMaxPrice(maxPrice);
    }, 500);
    return () => clearTimeout(t);
  }, [minPrice, maxPrice]);
  const executeProductSearch = useCallback(async () => {
    setIsLoadingProducts(true);
    try {
      const res = await searchService.search({
        q: debouncedSearchVal || undefined,
        categoryId: selectedCategory || undefined,
        minPrice: debouncedMinPrice !== "" ? debouncedMinPrice : undefined,
        maxPrice: debouncedMaxPrice !== "" ? debouncedMaxPrice : undefined,
        page, size,
        sort: sortBy
      });
      setProducts(res.content || []);
      setTotalProducts(res.totalElements || 0);
      setProductSuggestion(res.suggestion || null);
    } catch {
      toast.error("Lỗi kết nối", "Không thể truy vấn danh sách sản phẩm.");
    } finally {
      setIsLoadingProducts(false);
    }
  }, [page, size, debouncedSearchVal, selectedCategory, debouncedMinPrice, debouncedMaxPrice, sortBy]);

  useEffect(() => { executeProductSearch(); }, [executeProductSearch]);
  const executeCreatorSearch = useCallback(async () => {
    if (!searchVal.trim()) { setCreators([]); setTotalCreators(0); return; }
    setIsLoadingCreators(true);
    try {
      const res = await searchService.searchUsers({ q: searchVal, page: 0, size: 6 });
      const filtered = currentUser
        ? (res.content || []).filter((u: UserSearchResponse) => u.username !== currentUser.username)
        : (res.content || []);
      setCreators(filtered);
      setTotalCreators(filtered.length);
    } catch { setCreators([]); }
    finally { setIsLoadingCreators(false); }
  }, [searchVal]);

  useEffect(() => { executeCreatorSearch(); }, [executeCreatorSearch]);
  const saveSearchToHistory = async (kw: string) => {
    if (!kw.trim()) return;
    const now = new Date().toISOString();
    if (isAuthenticated) {
      setTimeout(() => loadRecentSearches(), 500);
    } else {
      const raw = localStorage.getItem("vibecart_search_history");
      let list: { keyword: string; searchedAt: string }[] = raw ? JSON.parse(raw) : [];
      list = list.filter(h => h.keyword.toLowerCase() !== kw.toLowerCase());
      list.unshift({ keyword: kw, searchedAt: now });
      list = list.slice(0, 10);
      localStorage.setItem("vibecart_search_history", JSON.stringify(list));
      setRecentKeywords(list);
    }
  };

  const handleSearchSubmit = (e?: React.FormEvent, overrideKw?: string) => {
    if (e) e.preventDefault();
    const kw = overrideKw !== undefined ? overrideKw : searchVal;
    setIsDropdownOpen(false);
    setPage(0);
    setSearchVal(kw);
    setDebouncedSearchVal(kw);
    const params = new URLSearchParams();
    if (kw) params.set("q", kw);
    if (selectedCategory) params.set("categoryId", selectedCategory);
    router.push(`${ROUTES.PRODUCTS}?${params.toString()}`);
    if (kw.trim()) saveSearchToHistory(kw.trim());
  };

  const handleFollowToggle = async (userId: string) => {
    if (!isAuthenticated) {
      toast.error("Yêu cầu đăng nhập", "Bạn cần đăng nhập để theo dõi Creator.");
      return;
    }
    setCreators(prev => prev.map(u => {
      if (u.id !== userId) return u;
      const next = !u.isFollowing;
      return { ...u, isFollowing: next, followerCount: next ? (u.followerCount || 0) + 1 : Math.max(0, (u.followerCount || 0) - 1) };
    }));
    try {
      const isNow = await userService.toggleFollow(userId);
      toast.success(isNow ? "Đã theo dõi Creator" : "Đã hủy theo dõi");
    } catch {
      toast.error("Lỗi", "Không thể cập nhật trạng thái theo dõi.");
      setCreators(prev => prev.map(u => {
        if (u.id !== userId) return u;
        const next = !u.isFollowing;
        return { ...u, isFollowing: next, followerCount: next ? (u.followerCount || 0) + 1 : Math.max(0, (u.followerCount || 0) - 1) };
      }));
    }
  };

  const handleResetFilters = () => {
    setSearchVal("");
    setDebouncedSearchVal("");
    setSelectedCategory("");
    setMinPrice("");
    setDebouncedMinPrice("");
    setMaxPrice("");
    setDebouncedMaxPrice("");
    setSortBy("relevance");
    setPage(0);
    router.push(ROUTES.PRODUCTS);
  };

  const handleDeleteHistoryItem = async (e: React.MouseEvent, kw: string) => {
    e.stopPropagation();
    if (isAuthenticated) {
      try { await searchService.deleteHistoryKeyword(kw); setRecentKeywords(prev => prev.filter(i => i.keyword !== kw)); } catch {}
    } else {
      const raw = localStorage.getItem("vibecart_search_history");
      if (raw) {
        let list = JSON.parse(raw) as { keyword: string; searchedAt: string }[];
        list = list.filter(i => i.keyword !== kw);
        localStorage.setItem("vibecart_search_history", JSON.stringify(list));
        setRecentKeywords(list);
      }
    }
  };

  const handleClearAllHistory = async () => {
    if (isAuthenticated) {
      try { await searchService.clearHistory(); } catch {}
    } else {
      localStorage.removeItem("vibecart_search_history");
    }
    setRecentKeywords([]);
  };

  const totalPages = Math.ceil(totalProducts / size);

  const renderCategoryOptions = (cats: Category[], depth = 0): React.ReactNode[] => {
    let opts: React.ReactNode[] = [];
    cats.forEach(cat => {
      opts.push(
        <button key={cat.id}
          onClick={() => {
            setSelectedCategory(cat.id); setPage(0); setIsFilterMobileOpen(false);
            const p = new URLSearchParams();
            if (searchVal) p.set("q", searchVal);
            p.set("categoryId", cat.id);
            router.push(`${ROUTES.PRODUCTS}?${p.toString()}`);
          }}
          className={`w-full text-left py-2 px-3 rounded-xl text-xs transition-all duration-200 flex items-center justify-between ${
            selectedCategory === cat.id ? "bg-brand-500 text-white font-semibold shadow-sm" : "text-zinc-600 hover:bg-zinc-100"
          }`}
          style={{ paddingLeft: `${depth * 12 + 12}px` }}
        >
          <span>{cat.name}</span>
          {selectedCategory === cat.id && <Sparkles className="h-3.5 w-3.5" />}
        </button>
      );
      if (cat.children?.length) opts = [...opts, ...renderCategoryOptions(cat.children, depth + 1)];
    });
    return opts;
  };
  const isSearchActive = !!searchVal.trim() || !!selectedCategory;

  return (
    <div className="flex-1 bg-zinc-50 min-h-screen transition-colors duration-300 relative">
      <div className="bg-white border-b border-zinc-200/60">
        <div className={`max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 transition-all duration-500 ease-in-out ${
          isSearchActive ? "py-3 sm:py-3.5" : "py-5 sm:py-6"
        }`}>
          <h1 className={`text-center font-extrabold text-zinc-900 tracking-tight transition-all duration-500 ease-in-out origin-center ${
            isSearchActive 
              ? "max-h-0 opacity-0 mb-0 scale-95 overflow-hidden" 
              : "max-h-16 opacity-100 text-lg sm:text-xl mb-4"
          }`}>
            Tìm sản phẩm & Creators trên <span className="text-brand-500">VibeCart</span>
          </h1>
          <div className="relative max-w-2xl mx-auto">
            <form onSubmit={(e) => handleSearchSubmit(e)}
              className="group/search-form relative rounded-full border-2 border-zinc-100/80 hover:border-brand-200 focus-within:border-brand-500 shadow-xl shadow-zinc-150/40 focus-within:shadow-brand-500/10 focus-within:ring-4 focus-within:ring-brand-500/10 transition-all duration-300 bg-white h-[52px] flex items-center">
              
              <div className="pl-4.5 pr-2.5 flex items-center justify-center shrink-0">
                <Search className="h-5 w-5 text-brand-500 transition-all duration-300 group-hover/search-form:scale-105 group-focus-within/search-form:scale-110 group-focus-within/search-form:text-brand-600" />
              </div>
              
              <input ref={inputRef} type="text"
                placeholder="Tìm kiếm sản phẩm, thương hiệu, creators..."
                value={searchVal}
                onFocus={() => setIsDropdownOpen(true)}
                onChange={(e) => { setSearchVal(e.target.value); setIsDropdownOpen(true); }}
                className="w-full h-full bg-transparent text-sm text-zinc-800 placeholder-zinc-400/85 focus:outline-none pr-12 font-medium"
              />
              
              {searchVal && (
                <button type="button"
                  onClick={() => { setSearchVal(""); setSuggestions([]); inputRef.current?.focus(); }}
                  className="absolute right-3.5 p-1.5 text-zinc-400 hover:text-rose-500 hover:bg-rose-50/80 active:scale-90 rounded-full transition-all duration-200">
                  <X className="h-4 w-4" />
                </button>
              )}
            </form>
            {isDropdownOpen && (
              <div ref={dropdownRef}
                className="absolute left-0 right-0 mt-2 bg-white border border-zinc-200 rounded-2xl shadow-2xl p-4 z-30 text-left max-h-[320px] overflow-y-auto custom-scrollbar animate-toast-in">
                {searchVal.trim() && (
                  <div className="mb-3">
                    <div className="text-[9px] font-black text-zinc-400 uppercase tracking-widest flex items-center gap-1 mb-2">
                      <Sparkles className="h-3 w-3 text-brand-500" /> Gợi ý
                    </div>
                    {suggestions.length === 0 ? (
                      <p className="text-xs text-zinc-400 font-light italic pl-3 py-1">Không tìm thấy gợi ý nào khớp...</p>
                    ) : (
                      <div className="space-y-0.5">
                        {suggestions.map((item, idx) => (
                          <button key={idx}
                            onClick={() => { setSearchVal(item); handleSearchSubmit(undefined, item); }}
                            className="w-full text-left py-2 px-3 rounded-xl hover:bg-brand-50 text-xs text-zinc-700 flex items-center gap-2 group transition-colors">
                            <Search className="h-3.5 w-3.5 text-zinc-400 group-hover:text-brand-500 shrink-0" />
                            <span className="truncate group-hover:text-brand-600 font-medium">{item}</span>
                            <ArrowRight className="h-3 w-3 text-brand-500 ml-auto opacity-0 group-hover:opacity-100 -translate-x-1 group-hover:translate-x-0 transition-all" />
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                )}
                {!searchVal.trim() && (
                  <div className="space-y-4">
                    {recentKeywords.length > 0 && (
                      <div>
                        <div className="flex justify-between items-center mb-2">
                          <span className="text-[9px] font-black text-zinc-400 uppercase tracking-widest flex items-center gap-1">
                            <History className="h-3 w-3 text-brand-500" /> Tìm kiếm gần đây
                          </span>
                          <button onClick={handleClearAllHistory} className="text-[9px] text-rose-500 hover:underline font-bold">Xóa tất cả</button>
                        </div>
                        <div className="flex flex-wrap gap-1.5">
                          {recentKeywords.map((item, idx) => (
                            <div key={idx}
                              onClick={() => { setSearchVal(item.keyword); handleSearchSubmit(undefined, item.keyword); }}
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-zinc-50 hover:bg-brand-50 border border-zinc-200/60 text-[10px] text-zinc-600 font-medium cursor-pointer transition-colors group">
                              <Clock className="h-2.5 w-2.5 text-zinc-400" />
                              <span>{item.keyword}</span>
                              <button onClick={(e) => handleDeleteHistoryItem(e, item.keyword)}
                                className="p-0.5 rounded-full hover:bg-rose-50 hover:text-rose-600 text-zinc-400">
                                <X className="h-3 w-3" />
                              </button>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {trendingKeywords.length > 0 && (
                      <div>
                        <div className="text-[9px] font-black text-zinc-400 uppercase tracking-widest flex items-center gap-1 mb-2">
                          <Flame className="h-3 w-3 text-orange-500 animate-pulse" /> Xu hướng tìm kiếm
                        </div>
                        <div className="flex flex-wrap gap-1.5">
                          {trendingKeywords.map((item, idx) => (
                            <button key={idx}
                              onClick={() => { setSearchVal(item); handleSearchSubmit(undefined, item); }}
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-orange-50 hover:bg-orange-100 border border-orange-200/50 text-[10px] text-orange-700 font-bold transition-all cursor-pointer hover:scale-105 active:scale-95">
                              <TrendingUp className="h-3 w-3 shrink-0" /> {item}
                            </button>
                          ))}
                        </div>
                      </div>
                    )}

                    {recentKeywords.length === 0 && trendingKeywords.length === 0 && (
                      <div className="text-center py-4 text-xs text-zinc-400 font-light flex items-center justify-center gap-1.5">
                        <HelpCircle className="h-4 w-4" /> Nhập từ khóa để nhận gợi ý.
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
      {productSuggestion && (
        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 mt-6">
          <div className="bg-amber-50 border border-amber-200 p-3 px-4 rounded-2xl flex items-center gap-3 text-xs text-amber-700">
            <AlertCircle className="h-4 w-4 shrink-0 text-amber-500" />
            <span className="font-light">Có phải bạn muốn tìm:{" "}
              <button onClick={() => { setSearchVal(productSuggestion); handleSearchSubmit(undefined, productSuggestion); }}
                className="font-bold underline text-amber-800 hover:text-amber-900">
                {productSuggestion}
              </button> ?
            </span>
          </div>
        </div>
      )}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Tab Switcher */}
        <div className="flex border-b border-zinc-200/80 mb-6 gap-6 select-none">
          <button
            onClick={() => setActiveTab("products")}
            className={`pb-3 text-sm font-bold transition-all relative flex items-center gap-1.5 ${
              activeTab === "products"
                ? "text-brand-500 font-extrabold"
                : "text-zinc-400 hover:text-zinc-600"
            }`}
          >
            Sản phẩm
            <span className={`text-[10px] px-2 py-0.5 rounded-full transition-colors ${
              activeTab === "products" ? "bg-brand-50 text-brand-600 font-bold" : "bg-zinc-100 text-zinc-400 font-medium"
            }`}>
              {totalProducts}
            </span>
            {activeTab === "products" && (
              <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-brand-500 rounded-full animate-toast-in" />
            )}
          </button>
          <button
            onClick={() => setActiveTab("creators")}
            className={`pb-3 text-sm font-bold transition-all relative flex items-center gap-1.5 ${
              activeTab === "creators"
                ? "text-brand-500 font-extrabold"
                : "text-zinc-400 hover:text-zinc-600"
            }`}
          >
            Creators
            <span className={`text-[10px] px-2 py-0.5 rounded-full transition-colors ${
              activeTab === "creators" ? "bg-brand-50 text-brand-600 font-bold" : "bg-zinc-100 text-zinc-400 font-medium"
            }`}>
              {totalCreators}
            </span>
            {activeTab === "creators" && (
              <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-brand-500 rounded-full animate-toast-in" />
            )}
          </button>
        </div>

        {activeTab === "products" ? (
          <div className="flex flex-col lg:flex-row gap-8">
            <aside className={`${isFilterVisible ? "lg:block" : "lg:hidden"} hidden w-60 shrink-0 bg-white rounded-2xl border border-zinc-200/60 p-5 shadow-sm h-fit sticky top-24`}>
              <div className="flex items-center justify-between pb-3 border-b border-zinc-100 mb-5">
                <h3 className="text-xs font-bold text-zinc-900 uppercase tracking-wider flex items-center gap-1.5">
                  <Filter className="h-3.5 w-3.5 text-brand-500" /> Bộ lọc
                </h3>
                <button onClick={handleResetFilters} className="text-[10px] text-zinc-400 hover:text-brand-500 font-semibold transition-colors">Xóa tất cả</button>
              </div>
              <div className="mb-5">
                <h4 className="text-[10px] font-bold text-zinc-400 uppercase tracking-widest mb-2.5">Danh mục</h4>
                <div className="space-y-0.5 max-h-56 overflow-y-auto custom-scrollbar">
                  <button
                    onClick={() => { setSelectedCategory(""); setPage(0); const p = new URLSearchParams(); if (searchVal) p.set("q", searchVal); router.push(`${ROUTES.PRODUCTS}?${p.toString()}`); }}
                    className={`w-full text-left py-2 px-3 rounded-xl text-xs transition-all flex items-center justify-between ${!selectedCategory ? "bg-brand-500 text-white font-semibold" : "text-zinc-600 hover:bg-zinc-100"}`}>
                    <span>Tất cả</span>
                    {!selectedCategory && <Sparkles className="h-3 w-3" />}
                  </button>
                  {renderCategoryOptions(categories)}
                </div>
              </div>
              <div className="mb-5">
                <h4 className="text-[10px] font-bold text-zinc-400 uppercase tracking-widest mb-2.5">Khoảng giá</h4>
                <div className="flex gap-2 items-center">
                  <input type="number" placeholder="Từ" value={minPrice}
                    onChange={(e) => setMinPrice(e.target.value ? Number(e.target.value) : "")}
                    className="w-full h-9 px-2.5 bg-zinc-50 border border-zinc-200 rounded-xl text-xs focus:outline-none focus:border-brand-500" />
                  <span className="text-zinc-300">—</span>
                  <input type="number" placeholder="Đến" value={maxPrice}
                    onChange={(e) => setMaxPrice(e.target.value ? Number(e.target.value) : "")}
                    className="w-full h-9 px-2.5 bg-zinc-50 border border-zinc-200 rounded-xl text-xs focus:outline-none focus:border-brand-500" />
                </div>
              </div>
              <div>
                <h4 className="text-[10px] font-bold text-zinc-400 uppercase tracking-widest mb-2.5">Sắp xếp</h4>
                <select value={sortBy} onChange={(e) => { setSortBy(e.target.value); setPage(0); }}
                  className="w-full h-9 px-2.5 rounded-xl bg-zinc-50 border border-zinc-200 text-xs text-zinc-900 focus:outline-none focus:border-brand-500">
                  <option value="relevance">Liên quan nhất</option>
                  <option value="price_asc">Giá tăng dần</option>
                  <option value="price_desc">Giá giảm dần</option>
                  <option value="newest">Mới nhất</option>
                </select>
              </div>
            </aside>
            <main className="flex-1 flex flex-col">
              <div className="lg:hidden flex items-center justify-between bg-white border border-zinc-200/60 p-3 rounded-2xl mb-5">
                <button onClick={() => setIsFilterMobileOpen(true)}
                  className="flex items-center gap-2 px-3 py-2 bg-zinc-50 rounded-xl border border-zinc-200 text-xs font-semibold text-zinc-700">
                  <SlidersHorizontal className="h-3.5 w-3.5 text-brand-500" /> Bộ lọc
                </button>
                <select value={sortBy} onChange={(e) => { setSortBy(e.target.value); setPage(0); }}
                  className="h-9 px-3 rounded-xl bg-zinc-50 border border-zinc-200 text-xs text-zinc-700 focus:outline-none">
                  <option value="relevance">Liên quan</option>
                  <option value="price_asc">Giá ↑</option>
                  <option value="price_desc">Giá ↓</option>
                  <option value="newest">Mới nhất</option>
                </select>
              </div>
              <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-2">
                  <div className="h-7 w-7 rounded-lg bg-brand-50 border border-brand-100 flex items-center justify-center">
                    <Package className="h-3.5 w-3.5 text-brand-500" />
                  </div>
                  <h2 className="text-sm font-bold text-zinc-900">Sản phẩm</h2>
                  {!isLoadingProducts && (
                    <span className="text-[10px] text-zinc-400 font-light">({totalProducts} kết quả)</span>
                  )}
                </div>
                <button 
                  onClick={() => setIsFilterVisible(!isFilterVisible)}
                  className="hidden lg:flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-zinc-200 hover:border-zinc-300 bg-white hover:bg-zinc-50 text-zinc-600 hover:text-zinc-800 text-[10px] font-bold transition-all shadow-sm active:scale-95"
                >
                  <SlidersHorizontal className="h-3 w-3 text-brand-500" />
                  {isFilterVisible ? "Ẩn bộ lọc" : "Hiện bộ lọc"}
                </button>
              </div>
              {isLoadingProducts ? (
                <div className={`grid grid-cols-1 sm:grid-cols-2 gap-5 min-h-[400px] ${isFilterVisible ? 'lg:grid-cols-3' : 'lg:grid-cols-4'}`}>
                  {Array.from({ length: 6 }).map((_, idx) => (
                    <div key={idx} className="bg-white rounded-2xl border border-zinc-200/40 p-4 shadow-sm animate-pulse flex flex-col h-[340px]">
                      <div className="w-full h-[180px] bg-zinc-100 rounded-xl mb-3" />
                      <div className="h-3 w-2/3 bg-zinc-100 rounded mb-2" />
                      <div className="h-4 w-full bg-zinc-100 rounded mb-2" />
                      <div className="h-8 w-full bg-zinc-100 rounded mt-auto" />
                    </div>
                  ))}
                </div>
              ) : products.length === 0 ? (
                <div className="text-center py-20 bg-white rounded-2xl border border-zinc-200/50 shadow-sm max-w-2xl mx-auto my-8">
                  <div className="h-14 w-14 rounded-2xl bg-zinc-50 border flex items-center justify-center text-zinc-400 mx-auto mb-4">
                    <Package className="h-7 w-7 text-zinc-400" />
                  </div>
                  <h4 className="text-sm font-bold text-zinc-800">Không tìm thấy sản phẩm nào</h4>
                  <p className="text-xs text-zinc-400 mt-1 max-w-xs mx-auto leading-relaxed font-light">
                    Hãy thử tìm bằng từ khóa khác hoặc xóa bớt các bộ lọc đang chọn.
                  </p>
                </div>
              ) : (
                <div className={`grid grid-cols-1 sm:grid-cols-2 gap-5 ${isFilterVisible ? 'lg:grid-cols-3' : 'lg:grid-cols-4'}`}>
                  {products.map((product) => {
                    const discountPercent = product.minOriginalPrice && product.minPrice && product.minOriginalPrice > product.minPrice
                      ? Math.round(((product.minOriginalPrice - product.minPrice) / product.minOriginalPrice) * 100)
                      : 0;
                    const hasDiscount = discountPercent > 0;
                    const min = product.minPrice;
                    const max = product.maxPrice;
                    const hasRange = min !== max;
                    const originalMin = product.minOriginalPrice || 0;
                    const originalMax = product.maxOriginalPrice || 0;
                    const hasOriginalRange = originalMin !== originalMax;
                    return (
                      <Link href={`${ROUTES.PRODUCTS}/${product.id}`} key={product.id} className="group bg-white rounded-2xl border border-zinc-200/40 p-4 shadow-sm hover:shadow-xl hover:shadow-brand-500/5 hover:-translate-y-0.5 transition-all duration-300 flex flex-col h-[380px]">
                        <div
                          onClick={(e) => {
                            if (product.status !== 'ACTIVE') {
                              e.preventDefault();
                              toast.warning("Thông báo", "Sản phẩm này hiện đang tạm ngưng kinh doanh.");
                            }
                          }}
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
                          className="relative w-full aspect-square rounded-xl overflow-hidden bg-zinc-50 mb-3 border border-zinc-100"
                        >
                          {product.thumbnailUrl ? (
                            isVideoUrl(product.thumbnailUrl) ? (
                              <video 
                                src={product.thumbnailUrl} 
                                muted 
                                loop 
                                playsInline 
                                preload="metadata"
                                className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                              />
                            ) : (
                              <img src={product.thumbnailUrl} alt={product.name}
                                className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
                            )
                          ) : (
                            <div className="w-full h-full flex items-center justify-center text-zinc-300">
                              <Package className="h-10 w-10" />
                            </div>
                          )}
                          {product.status !== "ACTIVE" && (
                            <div className="absolute top-2.5 left-2.5 bg-zinc-700/80 text-white text-[9px] font-bold px-2 py-0.5 rounded-full uppercase tracking-wider">
                              Tạm ngưng
                            </div>
                          )}
                          {hasDiscount && discountPercent > 0 && (
                            <div className="absolute top-2.5 right-2.5 bg-rose-500 text-white text-[9px] font-extrabold px-1.5 py-0.5 rounded-lg shadow-md z-10">
                              -{discountPercent}%
                            </div>
                          )}
                        </div>
                        <div className="flex-1 flex flex-col">
                          {creatorMap[product.creatorId] && (
                            <div className="flex items-center gap-1.5 mb-2 shrink-0">
                              <div className="h-5 w-5 rounded-full bg-brand-50 border border-brand-200/60 overflow-hidden flex items-center justify-center shrink-0">
                                {creatorMap[product.creatorId].avatarUrl ? (
                                  <img src={creatorMap[product.creatorId].avatarUrl} alt="" className="w-full h-full object-cover" />
                                ) : (
                                  <span className="text-[8px] font-bold text-brand-600 uppercase">
                                    {creatorMap[product.creatorId].username.charAt(0)}
                                  </span>
                                )}
                              </div>
                              <span className="text-[10px] font-bold text-zinc-500 truncate">
                                {creatorMap[product.creatorId].fullName || creatorMap[product.creatorId].username}
                              </span>
                            </div>
                          )}
                          <span className="text-[9px] text-zinc-400 uppercase tracking-widest font-semibold">{product.categoryName || "Danh mục"}</span>
                          <h4 className="text-xs font-bold text-zinc-900 mt-1 group-hover:text-brand-500 transition-colors line-clamp-2 leading-snug">
                            {product.name}
                          </h4>
                          <div className="flex items-center justify-between mt-auto pt-3 border-t border-zinc-100">
                            <div>
                              <span className="text-[9px] text-zinc-400 font-light block mb-0.5">Giá từ</span>
                              <div className="flex flex-col gap-0.5">
                                <span className={`text-xs font-bold ${hasDiscount && discountPercent > 0 ? 'text-rose-600' : 'text-zinc-900'}`}>
                                  {hasRange ? (
                                    <>{new Intl.NumberFormat("vi-VN").format(min)}đ – {new Intl.NumberFormat("vi-VN").format(max)}đ</>
                                  ) : (
                                    <>{new Intl.NumberFormat("vi-VN").format(min)}đ</>
                                  )}
                                </span>
                                {hasDiscount && discountPercent > 0 && (
                                  <span className="text-[9px] text-zinc-400 line-through font-light">
                                    {hasOriginalRange ? (
                                      <>{new Intl.NumberFormat("vi-VN").format(originalMin)}đ – {new Intl.NumberFormat("vi-VN").format(originalMax)}đ</>
                                    ) : (
                                      <>{new Intl.NumberFormat("vi-VN").format(originalMin)}đ</>
                                    )}
                                  </span>
                                )}
                              </div>
                            </div>
                            <div className="flex h-8 items-center gap-0.5 bg-brand-50 group-hover:bg-brand-500 group-hover:text-white text-brand-700 rounded-full px-3 text-[10px] font-semibold transition-all duration-300">
                              Chi tiết <ChevronRight className="h-3 w-3" />
                            </div>
                          </div>
                        </div>
                      </Link>
                    );
                  })}
                </div>
              )}
              {totalPages > 1 && (
                <div className="flex items-center justify-center gap-2 mt-8">
                  <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0 || isLoadingProducts}
                    className="px-4 py-2 border border-zinc-200 hover:bg-zinc-100 disabled:opacity-40 disabled:pointer-events-none rounded-xl text-xs font-medium text-zinc-700 transition-colors">
                    Trước
                  </button>
                  {(() => {
                    const maxVis = 7;
                    const pgs: (number | "...")[] = [];
                    if (totalPages <= maxVis) { for (let i = 0; i < totalPages; i++) pgs.push(i); }
                    else {
                      pgs.push(0);
                      const s = Math.max(1, page - 1), e = Math.min(totalPages - 2, page + 1);
                      if (s > 1) pgs.push("...");
                      for (let i = s; i <= e; i++) pgs.push(i);
                      if (e < totalPages - 2) pgs.push("...");
                      pgs.push(totalPages - 1);
                    }
                    return pgs.map((p, idx) => p === "..." ? (
                      <span key={`e-${idx}`} className="h-8 w-8 flex items-center justify-center text-xs text-zinc-400">...</span>
                    ) : (
                      <button key={p} onClick={() => setPage(p as number)}
                        className={`h-8 w-8 rounded-xl text-xs font-semibold transition-all ${page === p ? "bg-brand-500 text-white shadow-md shadow-brand-500/15" : "border border-zinc-200 hover:bg-zinc-100 text-zinc-700"}`}>
                        {(p as number) + 1}
                      </button>
                    ));
                  })()}
                  <button onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page === totalPages - 1 || isLoadingProducts}
                    className="px-4 py-2 border border-zinc-200 hover:bg-zinc-100 disabled:opacity-40 disabled:pointer-events-none rounded-xl text-xs font-medium text-zinc-700 transition-colors">
                    Sau
                  </button>
                </div>
              )}
            </main>
          </div>
        ) : (
          <div className="w-full">
            {isLoadingCreators ? (
              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-5 py-4 animate-fade-in">
                {Array.from({ length: 5 }).map((_, idx) => (
                  <div key={idx} className="bg-white rounded-2xl border border-zinc-200/40 p-5 shadow-sm animate-pulse h-60 flex flex-col items-center justify-center">
                    <div className="w-16 h-16 rounded-full bg-zinc-100 mb-4" />
                    <div className="h-3.5 w-24 bg-zinc-100 rounded mb-2" />
                    <div className="h-3 w-16 bg-zinc-100 rounded mb-4" />
                    <div className="h-8 w-full bg-zinc-100 rounded-xl" />
                  </div>
                ))}
              </div>
            ) : creators.length === 0 ? (
              <div className="text-center py-20 bg-white rounded-2xl border border-zinc-200/50 shadow-sm max-w-2xl mx-auto my-8 animate-fade-in">
                <div className="h-14 w-14 rounded-2xl bg-zinc-50 border flex items-center justify-center text-zinc-400 mx-auto mb-4">
                  <Users className="h-7 w-7 text-zinc-400" />
                </div>
                <h4 className="text-sm font-bold text-zinc-800">Không tìm thấy Creator nào</h4>
                <p className="text-xs text-zinc-400 mt-1 max-w-xs mx-auto leading-relaxed font-light">
                  Hãy thử tìm bằng từ khóa khác hoặc kiểm tra lại tên tài khoản.
                </p>
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-5 py-4 animate-fade-in">
                {creators.map(creator => (
                  <div key={creator.id}
                    className="group bg-white rounded-2xl border border-zinc-200/60 p-5 shadow-sm hover:shadow-lg hover:shadow-brand-500/5 hover:-translate-y-0.5 transition-all duration-300 flex flex-col items-center text-center">
                    <div className="relative w-16 h-16 rounded-full overflow-hidden mb-3 border-2 border-brand-100 group-hover:border-brand-300 transition-colors bg-zinc-50">
                      {creator.avatarUrl ? (
                        <img src={creator.avatarUrl} alt={creator.fullName || creator.username} className="w-full h-full object-cover" />
                      ) : (
                        <div className="w-full h-full flex items-center justify-center text-brand-500 font-black text-lg uppercase bg-brand-50">
                          {creator.username.charAt(0)}
                        </div>
                      )}
                    </div>

                    <h4 className="text-xs font-bold text-zinc-900 group-hover:text-brand-500 transition-colors line-clamp-1 leading-snug">
                      {creator.fullName || "Creator VibeCart"}
                    </h4>
                    <span className="text-[10px] text-zinc-400 font-medium mt-0.5">@{creator.username}</span>
                    <span className="inline-flex items-center gap-1 rounded-full bg-brand-50 border border-brand-100 px-2.5 py-0.5 text-[9px] font-bold text-brand-600 uppercase tracking-wider mt-2.5">
                      <Sparkles className="h-2.5 w-2.5" /> Creator
                    </span>
                    <p className="text-[10px] text-zinc-400 font-light mt-2">
                      <span className="font-bold text-zinc-700">{(creator.followerCount || 0).toLocaleString("vi-VN")}</span> người theo dõi
                    </p>
                    <button onClick={() => handleFollowToggle(creator.id)}
                      className={`w-full h-8 rounded-xl text-[10px] uppercase font-bold tracking-wider mt-4 transition-all duration-300 ${
                        creator.isFollowing
                          ? "bg-zinc-100 hover:bg-zinc-200 text-zinc-600"
                          : "bg-brand-500 hover:bg-brand-600 text-white shadow-sm shadow-brand-500/15 active:scale-95"
                      }`}>
                      {creator.isFollowing ? (
                        <span className="flex items-center justify-center gap-1"><UserCheck className="h-3 w-3" />Đang theo dõi</span>
                      ) : (
                        <span className="flex items-center justify-center gap-1"><UserPlus className="h-3 w-3" />Theo dõi</span>
                      )}
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
      {isFilterMobileOpen && (
        <div className="fixed inset-0 z-50 flex lg:hidden animate-fade-in">
          <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={() => setIsFilterMobileOpen(false)} />
          <div className="relative w-80 max-w-full bg-white p-5 flex flex-col h-full shadow-2xl overflow-y-auto animate-toast-in">
            <div className="flex items-center justify-between pb-3 border-b border-zinc-100 mb-5">
              <h3 className="text-xs font-bold text-zinc-900 uppercase tracking-wider flex items-center gap-1.5">
                <SlidersHorizontal className="h-4 w-4 text-brand-500" /> Bộ lọc
              </h3>
              <button onClick={() => setIsFilterMobileOpen(false)} className="p-1 rounded-lg hover:bg-zinc-100">
                <X className="h-5 w-5 text-zinc-400" />
              </button>
            </div>

            <div className="mb-5">
              <h4 className="text-[10px] font-bold text-zinc-400 uppercase tracking-widest mb-2.5">Danh mục</h4>
              <div className="space-y-0.5 max-h-48 overflow-y-auto custom-scrollbar">
                <button onClick={() => { setSelectedCategory(""); setPage(0); setIsFilterMobileOpen(false); router.push(ROUTES.PRODUCTS); }}
                  className={`w-full text-left py-2 px-3 rounded-xl text-xs ${!selectedCategory ? "bg-brand-500 text-white font-semibold" : "text-zinc-600"}`}>
                  Tất cả
                </button>
                {renderCategoryOptions(categories)}
              </div>
            </div>

            <div className="mb-5">
              <h4 className="text-[10px] font-bold text-zinc-400 uppercase tracking-widest mb-2.5">Khoảng giá</h4>
              <div className="flex gap-2 items-center">
                <input type="number" placeholder="Từ" value={minPrice}
                  onChange={(e) => setMinPrice(e.target.value ? Number(e.target.value) : "")}
                  className="w-full h-9 px-3 bg-zinc-50 border border-zinc-200 rounded-xl text-xs" />
                <span>—</span>
                <input type="number" placeholder="Đến" value={maxPrice}
                  onChange={(e) => setMaxPrice(e.target.value ? Number(e.target.value) : "")}
                  className="w-full h-9 px-3 bg-zinc-50 border border-zinc-200 rounded-xl text-xs" />
              </div>
            </div>

            <button onClick={() => { handleResetFilters(); setIsFilterMobileOpen(false); }}
              className="mt-auto w-full h-10 rounded-full border border-zinc-200 text-zinc-700 text-xs font-semibold hover:bg-zinc-50">
              Xóa tất cả bộ lọc
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export default function ShopPage() {
  return (
    <Suspense fallback={
      <div className="flex h-screen items-center justify-center bg-zinc-50">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-zinc-200 border-t-brand-500" />
      </div>
    }>
      <ShopContent />
    </Suspense>
  );
}
