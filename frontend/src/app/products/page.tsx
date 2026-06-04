"use client";

import React, { useState, useEffect, useRef, useCallback } from "react";
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

export default function ShopPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const toast = useToast();
  const { isAuthenticated, user: currentUser } = useAuth();

  // URL params
  const urlQuery = searchParams.get("q") || searchParams.get("query") || "";
  const urlCategory = searchParams.get("categoryId") || searchParams.get("category") || "";

  // Data states
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

  // Filter states
  const [searchVal, setSearchVal] = useState(urlQuery);
  const [selectedCategory, setSelectedCategory] = useState(urlCategory);
  const [minPrice, setMinPrice] = useState<number | "">("");
  const [maxPrice, setMaxPrice] = useState<number | "">("");
  const [sortBy, setSortBy] = useState("relevance");
  const [isFilterMobileOpen, setIsFilterMobileOpen] = useState(false);

  // Autocomplete / Popover
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const [trendingKeywords, setTrendingKeywords] = useState<string[]>([]);
  const [recentKeywords, setRecentKeywords] = useState<{ keyword: string; searchedAt: string }[]>([]);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // ───────────────────────────────────────────────────────────────
  // INIT
  // ───────────────────────────────────────────────────────────────

  useEffect(() => {
    categoryService.getCategoriesTree().then(d => setCategories(d || [])).catch(() => {});
    searchService.getTrending().then(d => setTrendingKeywords(d || [])).catch(() => {});
  }, []);

  // Sync URL → state
  useEffect(() => {
    setSearchVal(searchParams.get("q") || searchParams.get("query") || "");
    setSelectedCategory(searchParams.get("categoryId") || searchParams.get("category") || "");
    setPage(0);
  }, [searchParams]);

  // Load recent search history
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

  // Close dropdown on outside click
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

  // ───────────────────────────────────────────────────────────────
  // AUTOCOMPLETE (debounce 300ms)
  // ───────────────────────────────────────────────────────────────
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

  // ───────────────────────────────────────────────────────────────
  // SEARCH PRODUCTS (Elasticsearch)
  // ───────────────────────────────────────────────────────────────
  const executeProductSearch = useCallback(async () => {
    setIsLoadingProducts(true);
    try {
      const res = await searchService.search({
        q: searchVal || undefined,
        categoryId: selectedCategory || undefined,
        minPrice: minPrice !== "" ? minPrice : undefined,
        maxPrice: maxPrice !== "" ? maxPrice : undefined,
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, size, searchVal, selectedCategory, minPrice, maxPrice, sortBy]);

  useEffect(() => { executeProductSearch(); }, [executeProductSearch]);

  // ───────────────────────────────────────────────────────────────
  // SEARCH CREATORS (Elasticsearch) — triggers when searchVal changes
  // ───────────────────────────────────────────────────────────────
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchVal]);

  useEffect(() => { executeCreatorSearch(); }, [executeCreatorSearch]);

  // ───────────────────────────────────────────────────────────────
  // HELPERS
  // ───────────────────────────────────────────────────────────────
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
    setSearchVal(""); setSelectedCategory(""); setMinPrice(""); setMaxPrice(""); setSortBy("relevance"); setPage(0);
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

  // ───────────────────────────────────────────────────────────────
  // RENDER
  // ───────────────────────────────────────────────────────────────
  return (
    <div className="flex-1 bg-zinc-50 min-h-screen transition-colors duration-300 relative">

      {/* Hero search header */}
      <div className="bg-white border-b border-zinc-200/60">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8 sm:py-10">
          <h1 className="text-center text-xl sm:text-2xl font-extrabold text-zinc-900 mb-6 tracking-tight">
            Tìm sản phẩm & Creators trên <span className="text-brand-500">VibeCart</span>
          </h1>

          {/* Search bar */}
          <div className="relative max-w-2xl mx-auto">
            <form onSubmit={(e) => handleSearchSubmit(e)}
              className="relative flex rounded-full overflow-hidden border border-zinc-200 shadow-lg shadow-zinc-200/40 focus-within:border-brand-500 focus-within:ring-2 focus-within:ring-brand-500/20 transition-all duration-300 bg-white">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 h-4.5 w-4.5 text-zinc-400" />
              <input ref={inputRef} type="text"
                placeholder="Tìm kiếm sản phẩm, thương hiệu, creators..."
                value={searchVal}
                onFocus={() => setIsDropdownOpen(true)}
                onChange={(e) => { setSearchVal(e.target.value); setIsDropdownOpen(true); }}
                className="w-full h-12 pl-11 pr-10 bg-transparent text-sm text-zinc-900 placeholder-zinc-400 focus:outline-none"
              />
              {searchVal && (
                <button type="button"
                  onClick={() => { setSearchVal(""); setSuggestions([]); inputRef.current?.focus(); }}
                  className="absolute right-[90px] top-1/2 -translate-y-1/2 text-zinc-400 hover:text-zinc-600">
                  <X className="h-4 w-4" />
                </button>
              )}
              <button type="submit"
                className="h-12 px-6 bg-brand-500 hover:bg-brand-600 text-white text-xs font-bold rounded-r-full transition-colors active:scale-95">
                Tìm kiếm
              </button>
            </form>

            {/* Dropdown popover */}
            {isDropdownOpen && (
              <div ref={dropdownRef}
                className="absolute left-0 right-0 mt-2 bg-white border border-zinc-200 rounded-2xl shadow-2xl p-4 z-30 text-left max-h-[320px] overflow-y-auto custom-scrollbar animate-toast-in">

                {/* Autocomplete suggestions */}
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

                {/* Recent + Trending when empty */}
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

      {/* Spellcheck suggestion */}
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

      {/* Main content area */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">

        {/* ═══════ CREATORS SECTION (appears when searching) ═══════ */}
        {searchVal.trim() && creators.length > 0 && (
          <div className="mb-8">
            <div className="flex items-center gap-2 mb-4">
              <div className="h-8 w-8 rounded-xl bg-brand-50 border border-brand-100 flex items-center justify-center">
                <Users className="h-4 w-4 text-brand-500" />
              </div>
              <h2 className="text-sm font-bold text-zinc-900">Creators</h2>
              <span className="text-[10px] text-zinc-400 font-light">({totalCreators} kết quả)</span>
            </div>

            <div className="flex gap-4 overflow-x-auto pb-3 custom-scrollbar -mx-1 px-1">
              {creators.map(creator => (
                <div key={creator.id}
                  className="group min-w-[200px] max-w-[200px] bg-white rounded-2xl border border-zinc-200/60 p-5 shadow-sm hover:shadow-lg hover:shadow-brand-500/5 hover:-translate-y-0.5 transition-all duration-300 flex flex-col items-center text-center shrink-0">
                  {/* Avatar */}
                  <div className="relative w-14 h-14 rounded-full overflow-hidden mb-3 border-2 border-brand-100 group-hover:border-brand-300 transition-colors bg-zinc-50">
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

                  {/* Badge + Stats */}
                  <span className="inline-flex items-center gap-1 rounded-full bg-brand-50 border border-brand-100 px-2 py-0.5 text-[9px] font-bold text-brand-600 uppercase tracking-wider mt-2">
                    <Sparkles className="h-2.5 w-2.5" /> Creator
                  </span>
                  <p className="text-[10px] text-zinc-400 font-light mt-1.5">
                    <span className="font-bold text-zinc-700">{creator.followerCount || 0}</span> người theo dõi
                  </p>

                  {/* Follow button */}
                  <button onClick={() => handleFollowToggle(creator.id)}
                    className={`w-full h-8 rounded-xl text-[10px] uppercase font-bold tracking-wider mt-3 transition-all duration-300 ${
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
          </div>
        )}

        {/* ═══════ PRODUCTS + FILTERS ═══════ */}
        <div className="flex flex-col lg:flex-row gap-8">

          {/* Sidebar filters */}
          <aside className="hidden lg:block w-60 shrink-0 bg-white rounded-2xl border border-zinc-200/60 p-5 shadow-sm h-fit sticky top-24">
            <div className="flex items-center justify-between pb-3 border-b border-zinc-100 mb-5">
              <h3 className="text-xs font-bold text-zinc-900 uppercase tracking-wider flex items-center gap-1.5">
                <Filter className="h-3.5 w-3.5 text-brand-500" /> Bộ lọc
              </h3>
              <button onClick={handleResetFilters} className="text-[10px] text-zinc-400 hover:text-brand-500 font-semibold transition-colors">Xóa tất cả</button>
            </div>

            {/* Categories */}
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

            {/* Price */}
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
              <button onClick={() => { setPage(0); executeProductSearch(); }}
                className="w-full h-8 rounded-xl bg-zinc-900 hover:bg-black text-white text-[10px] uppercase font-bold tracking-wider mt-2.5 transition-colors">
                Áp dụng
              </button>
            </div>

            {/* Sort */}
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

          {/* Product grid */}
          <main className="flex-1 flex flex-col">

            {/* Mobile filter bar */}
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

            {/* Results meta */}
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
            </div>

            {/* Loading skeleton */}
            {isLoadingProducts ? (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5 min-h-[400px]">
                {Array.from({ length: 6 }).map((_, idx) => (
                  <div key={idx} className="bg-white rounded-2xl border border-zinc-200/40 p-4 shadow-sm animate-pulse flex flex-col h-[340px]">
                    <div className="w-full h-[180px] bg-zinc-100 rounded-xl mb-3" />
                    <div className="h-3 bg-zinc-100 rounded w-1/3 mb-2" />
                    <div className="h-4 bg-zinc-100 rounded w-3/4 mb-2" />
                    <div className="h-3 bg-zinc-100 rounded w-full mb-auto" />
                    <div className="h-8 bg-zinc-100 rounded-xl w-full mt-3" />
                  </div>
                ))}
              </div>

            ) : products.length === 0 ? (
              <div className="flex-1 flex flex-col items-center justify-center py-16 bg-white rounded-2xl border border-zinc-200/50 text-center px-6">
                <div className="h-14 w-14 bg-zinc-50 text-zinc-400 rounded-2xl flex items-center justify-center mb-4 border border-zinc-200/60">
                  <Package className="h-7 w-7" />
                </div>
                <h3 className="text-sm font-bold text-zinc-800">Không tìm thấy sản phẩm</h3>
                <p className="text-xs text-zinc-400 mt-1 max-w-xs leading-relaxed font-light">
                  Không có kết quả nào khớp với từ khóa hoặc bộ lọc. Hãy thử lại hoặc tham khảo xu hướng bên dưới.
                </p>
                {trendingKeywords.length > 0 && (
                  <div className="mt-6">
                    <span className="text-[9px] font-bold text-zinc-400 uppercase tracking-widest block mb-2.5">Có thể bạn quan tâm</span>
                    <div className="flex flex-wrap gap-1.5 justify-center">
                      {trendingKeywords.slice(0, 5).map((item, idx) => (
                        <button key={idx}
                          onClick={() => { setSearchVal(item); handleSearchSubmit(undefined, item); }}
                          className="px-3 py-1.5 rounded-full bg-zinc-50 hover:bg-brand-50 border border-zinc-200 text-[10px] text-zinc-700 font-semibold transition-colors">
                          {item}
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </div>

            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
                {products.map(product => {
                  const min = product.minPrice || 0;
                  const max = product.maxPrice || 0;
                  const hasRange = max > min;
                  return (
                    <Link href={ROUTES.PRODUCT_DETAILS(product.id)} key={product.id}
                      className="group bg-white rounded-2xl border border-zinc-200/50 p-3.5 shadow-sm hover:shadow-xl hover:shadow-brand-500/5 hover:-translate-y-0.5 transition-all duration-300 flex flex-col h-full overflow-hidden">
                      {/* Image */}
                      <div className="relative w-full h-[190px] rounded-xl overflow-hidden bg-zinc-50 mb-3 border border-zinc-100">
                        {product.thumbnailUrl ? (
                          <img src={product.thumbnailUrl} alt={product.name}
                            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
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
                      </div>

                      {/* Info */}
                      <div className="flex-1 flex flex-col">
                        <span className="text-[9px] text-zinc-400 uppercase tracking-widest font-semibold">{product.categoryName || "Danh mục"}</span>
                        <h4 className="text-xs font-bold text-zinc-900 mt-1 group-hover:text-brand-500 transition-colors line-clamp-2 leading-snug">
                          {product.name}
                        </h4>
                        <p className="text-[11px] text-zinc-400 mt-1 line-clamp-2 leading-relaxed font-light">
                          {product.description || "Không có mô tả..."}
                        </p>

                        {/* Price + CTA */}
                        <div className="flex items-center justify-between mt-auto pt-3 border-t border-zinc-100">
                          <div>
                            <span className="text-[9px] text-zinc-400 font-light block mb-0.5">Giá từ</span>
                            <span className="text-xs font-bold text-zinc-900">
                              {hasRange ? (
                                <>{new Intl.NumberFormat("vi-VN").format(min)}đ – {new Intl.NumberFormat("vi-VN").format(max)}đ</>
                              ) : (
                                <>{new Intl.NumberFormat("vi-VN").format(min)}đ</>
                              )}
                            </span>
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

            {/* Pagination */}
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
      </div>

      {/* Mobile filter drawer */}
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
              <button onClick={() => { setPage(0); executeProductSearch(); setIsFilterMobileOpen(false); }}
                className="w-full h-9 rounded-xl bg-zinc-900 text-white text-xs font-semibold mt-2.5">
                Áp dụng
              </button>
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
