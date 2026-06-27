"use client";

import React, { useState, useEffect, useCallback, useRef } from "react";
import Link from "next/link";
import { 
  Heart, 
  MessageSquare, 
  Share2, 
  Plus, 
  Image as ImageIcon, 
  Tag, 
  Trash2, 
  Compass, 
  UserPlus, 
  UserMinus, 
  Loader2, 
  ShoppingBag, 
  ChevronLeft, 
  ChevronRight, 
  Send,
  X
} from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useCart } from "@/hooks/useCart";
import { useToast } from "@/context/ToastContext";
import { postService, PageResponse } from "@/services/post.service";
import { userService } from "@/services/user.service";
import { productService } from "@/services/product.service";
import { Post, Product } from "@/types";
import { ROUTES } from "@/constants/routes";
import { api } from "@/lib/api-client";
import { uploadFilePresigned } from "@/services/media.service";

export default function FeedPage() {
  const { user, isAuthenticated } = useAuth();
  const { addToCart } = useCart();
  const toast = useToast();
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [content, setContent] = useState("");
  const [mediaUrls, setMediaUrls] = useState<string[]>([]);
  const [taggedProducts, setTaggedProducts] = useState<Product[]>([]);
  const [productSearch, setProductSearch] = useState("");
  const [searchedProducts, setSearchedProducts] = useState<Product[]>([]);
  const [searchingProducts, setSearchingProducts] = useState(false);
  const [submittingPost, setSubmittingPost] = useState(false);
  const [isUploadingMedia, setIsUploadingMedia] = useState(false);
  const [isProductDropdownOpen, setIsProductDropdownOpen] = useState(false);
  const [allCreatorProducts, setAllCreatorProducts] = useState<Product[]>([]);
  const mediaInputRef = useRef<HTMLInputElement>(null);
  const productDropdownRef = useRef<HTMLDivElement>(null);
  const [followedCreators, setFollowedCreators] = useState<any[]>([]);
  const [loadingFollowed, setLoadingFollowed] = useState(false);
  const [activeUsers, setActiveUsers] = useState<any[]>([]);
  const [loadingActive, setLoadingActive] = useState(false);
  const [followingMap, setFollowingMap] = useState<Record<string, boolean>>({});
  const [variantPickerProduct, setVariantPickerProduct] = useState<Product | null>(null);
  const loadFeed = useCallback(async (pageNum = 0, isLoadMore = false) => {
    if (pageNum === 0) setLoading(true);
    else setLoadingMore(true);

    try {
      let res: PageResponse<Post>;
      if (isAuthenticated) {
        res = await postService.getFeed(pageNum, 10);
      } else {
        res = await postService.getPosts(pageNum, 10);
      }

      if (res && res.content) {
        if (isLoadMore) {
          setPosts(prev => {
            const existingIds = new Set(prev.map(p => p.id));
            const newPosts = res.content.filter(p => !existingIds.has(p.id));
            return [...prev, ...newPosts];
          });
        } else {
          setPosts(res.content);
        }
        setHasMore(!res.last);
      }
    } catch (err: any) {
      console.error("Lỗi khi tải bảng tin:", err);
      toast.error("Lỗi tải bảng tin", err?.message || "Không thể kết nối máy chủ");
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  }, [isAuthenticated, toast]);
  const loadFollowData = useCallback(async () => {
    if (!isAuthenticated || !user?.id) return;
    setLoadingFollowed(true);
    try {
      const res = await userService.getFollowing(user.id, 0, 100);
      if (res && res.content) {
        setFollowedCreators(res.content.slice(0, 10));
        const map: Record<string, boolean> = {};
        res.content.forEach(item => {
          map[item.userId] = true;
        });
        setFollowingMap(map);
      } else {
        setFollowedCreators([]);
        setFollowingMap({});
      }
    } catch (err) {
      console.error("Lỗi lấy danh sách đang theo dõi:", err);
    } finally {
      setLoadingFollowed(false);
    }
  }, [isAuthenticated, user?.id]);
  const loadActiveUsers = useCallback(async () => {
    if (!isAuthenticated) return;
    setLoadingActive(true);
    try {
      const res = await userService.getActiveUsers();
      setActiveUsers(res || []);
    } catch (err) {
      console.error("Lỗi lấy danh sách đang hoạt động:", err);
    } finally {
      setLoadingActive(false);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    loadFeed(0, false);
    loadFollowData();
  }, [loadFeed, loadFollowData]);
  useEffect(() => {
    if (!isAuthenticated) return;
    loadActiveUsers();
    const interval = setInterval(() => {
      loadActiveUsers();
    }, 60000);
    return () => clearInterval(interval);
  }, [isAuthenticated, loadActiveUsers]);
  const loadMoreRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!hasMore || loading) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && hasMore && !loadingMore) {
          const nextPage = page + 1;
          setPage(nextPage);
          loadFeed(nextPage, true);
        }
      },
      { rootMargin: "200px" }
    );
    const el = loadMoreRef.current;
    if (el) observer.observe(el);
    return () => { if (el) observer.unobserve(el); };
  }, [hasMore, loading, loadingMore, page, loadFeed]);
  const loadCreatorProducts = useCallback(async () => {
    if (!user?.id || allCreatorProducts.length > 0) return;
    setSearchingProducts(true);
    try {
      const res = await productService.getProductsByCreator(user.id, { size: 100 });
      if (res && res.content) {
        const active = res.content.filter(p => p.status === "ACTIVE");
        setAllCreatorProducts(active);
        setSearchedProducts(active.slice(0, 5));
      }
    } catch (err) {
      console.error("Lỗi tải sản phẩm của Creator:", err);
    } finally {
      setSearchingProducts(false);
    }
  }, [user?.id, allCreatorProducts.length]);
  useEffect(() => {
    if (!productSearch.trim()) {
      setSearchedProducts(allCreatorProducts.slice(0, 5));
      return;
    }
    const filtered = allCreatorProducts.filter(p =>
      p.name.toLowerCase().includes(productSearch.toLowerCase())
    );
    setSearchedProducts(filtered.slice(0, 5));
  }, [productSearch, allCreatorProducts]);
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (productDropdownRef.current && !productDropdownRef.current.contains(e.target as Node)) {
        setIsProductDropdownOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);
  const handleLikeToggle = async (postId: string) => {
    if (!isAuthenticated) {
      toast.warning("Yêu cầu đăng nhập", "Bạn cần đăng nhập để thực hiện tính năng này!");
      return;
    }
    setPosts(prev => prev.map(p => {
      if (p.id === postId) {
        return {
          ...p,
          likedByMe: !p.likedByMe,
          likeCount: p.likedByMe ? p.likeCount - 1 : p.likeCount + 1
        };
      }
      return p;
    }));

    try {
      await postService.toggleLike(postId);
    } catch (err: any) {
      setPosts(prev => prev.map(p => {
        if (p.id === postId) {
          return {
            ...p,
            likedByMe: !p.likedByMe,
            likeCount: p.likedByMe ? p.likeCount + 1 : p.likeCount - 1
          };
        }
        return p;
      }));
      toast.error("Lỗi tương tác", err?.message || "Không thể thực hiện thích bài viết");
    }
  };
  const handleFollowToggle = async (creatorId: string) => {
    if (!isAuthenticated) {
      toast.warning("Yêu cầu đăng nhập", "Vui lòng đăng nhập để theo dõi Creator!");
      return;
    }

    const wasFollowing = !!followingMap[creatorId];
    setFollowingMap(prev => ({ ...prev, [creatorId]: !wasFollowing }));

    try {
      const isNowFollowing = await userService.toggleFollow(creatorId);
      setFollowingMap(prev => ({ ...prev, [creatorId]: isNowFollowing }));
      if (isNowFollowing) {
        toast.success("Đã theo dõi", "Bạn sẽ nhận được các bài viết mới từ nhà sáng tạo này!");
      } else {
        toast.info("Đã hủy theo dõi", "Đã dừng nhận bài viết từ nhà sáng tạo này.");
      }
      loadFollowData();
      loadActiveUsers();
      loadFeed(0, false);
    } catch (err: any) {
      setFollowingMap(prev => ({ ...prev, [creatorId]: wasFollowing }));
      toast.error("Lỗi theo dõi", err?.message || "Không thể thực hiện tác vụ");
    }
  };
  const handleShare = (post: Post) => {
    const postUrl = `${window.location.origin}${ROUTES.POST_DETAILS(post.id)}`;
    navigator.clipboard.writeText(postUrl);
    toast.success("Đã sao chép liên kết", "Bạn có thể chia sẻ bài viết này cho bạn bè!");
  };
  const handleAddProductToCart = (product: Product) => {
    if (!isAuthenticated) {
      toast.warning("Yêu cầu đăng nhập", "Bạn cần đăng nhập để thêm sản phẩm vào giỏ hàng.");
      return;
    }
    if (!product.variants || product.variants.length === 0) {
      toast.warning("Sản phẩm chưa có biến thể", "Không thể thêm sản phẩm này vào giỏ hàng.");
      return;
    }
    if (product.variants.length === 1) {
      confirmAddToCart(product, product.variants[0]);
      return;
    }
    setVariantPickerProduct(product);
  };

  const confirmAddToCart = async (product: Product, variant: import("@/types").ProductVariant) => {
    try {
      await addToCart(variant, product, 1);
      setVariantPickerProduct(null);
    } catch (err) {
    }
  };
  const handleRemoveMedia = (index: number) => {
    setMediaUrls(mediaUrls.filter((_, i) => i !== index));
  };
  const handleMediaUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;

    if (mediaUrls.length + files.length > 10) {
      toast.warning("Giới hạn hình ảnh/video", "Bạn chỉ được đăng tối đa 10 hình ảnh/video.");
      return;
    }

    setIsUploadingMedia(true);
    try {
      const validFiles: File[] = [];
      for (let i = 0; i < files.length; i++) {
        const file = files[i];
        if (file.size > 50 * 1024 * 1024) {
          toast.error("Tệp quá lớn", `Tệp ${file.name} vượt quá giới hạn cho phép (50MB).`);
          continue;
        }
        validFiles.push(file);
      }

      if (validFiles.length > 0) {
        const uploadPromises = validFiles.map((file) =>
          uploadFilePresigned({ file, folder: "posts" })
        );
        const results = await Promise.all(uploadPromises);
        const uploadedUrls = results.map((r) => r.url);
        setMediaUrls(prev => [...prev, ...uploadedUrls]);
        toast.success("Tải tệp thành công", `Đã tải lên ${uploadedUrls.length} tệp phương tiện.`);
      }
    } catch (err: any) {
      console.error("Lỗi tải tệp lên storage:", err);
      toast.error("Tải tệp thất bại", err?.data?.message || err?.message || "Không thể kết nối máy chủ.");
    } finally {
      setIsUploadingMedia(false);
      if (mediaInputRef.current) {
        mediaInputRef.current.value = "";
      }
    }
  };
  const handleTagProduct = (product: Product) => {
    if (taggedProducts.some(p => p.id === product.id)) {
      toast.warning("Sản phẩm đã được chọn", "Sản phẩm này đã được gắn thẻ.");
      return;
    }
    if (taggedProducts.length >= 5) {
      toast.warning("Giới hạn gắn thẻ", "Bạn chỉ được gắn thẻ tối đa 5 sản phẩm.");
      return;
    }
    setTaggedProducts([...taggedProducts, product]);
    setProductSearch("");
  };
  const handleUntagProduct = (productId: string) => {
    setTaggedProducts(taggedProducts.filter(p => p.id !== productId));
  };
  const handleSubmitPost = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!content.trim()) {
      toast.warning("Nội dung trống", "Vui lòng nhập nội dung bài viết.");
      return;
    }

    setSubmittingPost(true);
    try {
      const newPost = await postService.createPost({
        content: content.trim(),
        mediaUrls,
        taggedProductIds: taggedProducts.map(p => p.id)
      });

      toast.success("Đăng bài thành công", "Bài viết của bạn đã được xuất bản lên VibeCart!");
      setContent("");
      setMediaUrls([]);
      setTaggedProducts([]);
      setShowCreateModal(false);
      setAllCreatorProducts([]);
      setIsProductDropdownOpen(false);
      setPosts(prev => [newPost, ...prev]);
    } catch (err: any) {
      toast.error("Lỗi đăng bài", err?.message || "Không thể lưu bài viết mới.");
    } finally {
      setSubmittingPost(false);
    }
  };

  return (
    <div className="min-h-screen bg-zinc-50/50 py-8 px-4 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-6xl grid grid-cols-1 lg:grid-cols-12 gap-8">
        <div className="lg:col-span-8 flex flex-col gap-6">
          {user?.roles?.includes("ROLE_CREATOR") && (
            <div className="rounded-2xl border border-brand-100/60 bg-white p-5 shadow-sm hover:border-brand-200/80 transition-all duration-300">
              <div className="flex gap-4 items-center">
                {user.avatarUrl ? (
                  <img 
                    src={user.avatarUrl} 
                    alt={user.fullName} 
                    className="h-10 w-10 rounded-full object-cover ring-2 ring-brand-100" 
                    onError={(e) => {
                      e.currentTarget.src = `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(user.fullName)}`;
                    }}
                  />
                ) : (
                  <div className="flex h-10 w-10 items-center justify-center rounded-full bg-brand-500 text-white text-sm font-bold">
                    {user.fullName?.charAt(0)}
                  </div>
                )}
                <button
                  onClick={() => setShowCreateModal(true)}
                  className="flex-1 h-11 rounded-full bg-zinc-50 border border-zinc-200/80 hover:bg-zinc-100/70 text-left px-5 text-zinc-400 text-sm transition-all duration-200"
                >
                  {user.fullName} ơi, bạn đang nghĩ gì thế?
                </button>
                <button 
                  onClick={() => setShowCreateModal(true)}
                  className="h-10 w-10 rounded-full bg-brand-50 hover:bg-brand-100 text-brand-600 flex items-center justify-center transition-colors"
                  title="Tạo bài viết mới"
                >
                  <Plus className="h-5 w-5" />
                </button>
              </div>
            </div>
          )}
          <div className="flex items-center justify-between pb-2 border-b border-zinc-100">
            <h1 className="text-xl font-extrabold text-zinc-800 flex items-center gap-2">
              <Compass className="h-5 w-5 text-brand-500" />
              {isAuthenticated ? "Bảng tin của bạn" : "Khám phá bài viết từ Creator"}
            </h1>
            <span className="text-xs text-zinc-400 font-medium bg-zinc-100 px-3 py-1 rounded-full">
              {posts.length} bài viết
            </span>
          </div>
          {loading ? (
            <div className="flex flex-col items-center justify-center py-20 gap-3">
              <Loader2 className="h-10 w-10 text-brand-500 animate-spin" />
              <p className="text-sm text-zinc-500 animate-pulse font-medium">Đang tải bảng tin...</p>
            </div>
          ) : posts.length === 0 ? (
            <div className="rounded-2xl bg-white border border-zinc-100 p-12 text-center flex flex-col items-center shadow-sm">
              <div className="h-16 w-16 bg-brand-50 rounded-full flex items-center justify-center mb-4 text-brand-500">
                <Compass className="h-8 w-8" />
              </div>
              <h3 className="text-lg font-bold text-zinc-800 mb-1">Chưa có bài đăng nào</h3>
              <p className="text-sm text-zinc-400 max-w-md">
                Theo dõi các Creator nổi bật để cập nhật những nội dung số hấp dẫn và các gợi ý mua sắm chất lượng!
              </p>
            </div>
          ) : (
            <div className="flex flex-col gap-6">
              {posts.map((post) => (
                <PostCard 
                  key={post.id} 
                  post={post} 
                  currentUserId={user?.id}
                  isAuthenticated={isAuthenticated}
                  isFollowing={!!followingMap[post.creatorId]}
                  onLikeToggle={handleLikeToggle}
                  onFollowToggle={handleFollowToggle}
                  onShare={handleShare}
                  onAddToCart={handleAddProductToCart}
                />
              ))}
              <div ref={loadMoreRef} className="w-full py-4 flex items-center justify-center">
                {loadingMore && (
                  <div className="flex items-center gap-2 text-sm text-zinc-400 animate-pulse">
                    <Loader2 className="h-4 w-4 animate-spin text-brand-500" />
                    Đang tải thêm bài viết...
                  </div>
                )}
                {!hasMore && posts.length > 0 && (
                  <p className="text-xs text-zinc-300 font-medium">Bạn đã xem hết bài viết 🎉</p>
                )}
              </div>
            </div>
          )}
        </div>
        <div className="lg:col-span-4 hidden lg:flex flex-col gap-6 sticky top-20 h-fit">
          {isAuthenticated && (
            <div className="rounded-2xl border border-zinc-100 bg-white p-5 shadow-sm">
              <h2 className="text-sm font-extrabold text-zinc-800 mb-4 tracking-wider uppercase flex items-center gap-2">
                <span className="h-2 w-2 rounded-full bg-brand-500" />
                Đang theo dõi
              </h2>

              {loadingFollowed ? (
                <div className="flex justify-center py-6">
                  <Loader2 className="h-6 w-6 text-brand-500 animate-spin" />
                </div>
              ) : followedCreators.length === 0 ? (
                <p className="text-xs text-zinc-400 text-center py-4">Bạn chưa theo dõi Creator nào.</p>
              ) : (
                <div className="flex flex-col gap-4">
                  {followedCreators.map((creator) => (
                    <div key={creator.userId} className="flex items-center justify-between gap-3">
                      <Link href={ROUTES.CREATOR_PROFILE(creator.userId)} className="flex items-center gap-3 group">
                        {creator.avatarUrl ? (
                          <img 
                            src={creator.avatarUrl} 
                            alt={creator.username} 
                            className="h-9 w-9 rounded-full object-cover ring-1 ring-zinc-100 group-hover:scale-105 transition-transform"
                            onError={(e) => {
                              e.currentTarget.src = `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(creator.username)}`;
                            }}
                          />
                        ) : (
                          <div className="flex h-9 w-9 items-center justify-center rounded-full bg-zinc-100 text-zinc-600 text-xs font-bold">
                            {creator.username.charAt(0).toUpperCase()}
                          </div>
                        )}
                        <div className="flex flex-col">
                          <span className="text-xs font-bold text-zinc-800 group-hover:text-brand-600 transition-colors">
                            @{creator.username}
                          </span>
                          <span className="text-[10px] text-zinc-400 truncate max-w-[120px]">{creator.fullName || "Nhà sáng tạo"}</span>
                        </div>
                      </Link>

                      <button
                        onClick={() => handleFollowToggle(creator.userId)}
                        className="inline-flex h-7 items-center justify-center gap-1 rounded-full bg-zinc-100 hover:bg-zinc-200 px-3 text-[10px] font-bold text-zinc-650 transition-colors"
                      >
                        Đang theo dõi
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
          {isAuthenticated && (
            <div className="rounded-2xl border border-zinc-100 bg-white p-5 shadow-sm">
              <h2 className="text-sm font-extrabold text-zinc-800 mb-4 tracking-wider uppercase flex items-center gap-2">
                <span className="relative flex h-2 w-2">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                  <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
                </span>
                Đang hoạt động
              </h2>

              {loadingActive ? (
                <div className="flex justify-center py-6">
                  <Loader2 className="h-6 w-6 text-brand-500 animate-spin" />
                </div>
              ) : activeUsers.length === 0 ? (
                <p className="text-xs text-zinc-400 text-center py-4">Không có ai đang hoạt động.</p>
              ) : (
                <div className="flex flex-col gap-4">
                  {activeUsers.map((activeUser) => {
                    const isFollowing = !!followingMap[activeUser.userId];
                    return (
                      <div key={activeUser.userId} className="flex items-center justify-between gap-3">
                        <Link href={ROUTES.CREATOR_PROFILE(activeUser.userId)} className="flex items-center gap-3 group">
                          <div className="relative">
                            {activeUser.avatarUrl ? (
                              <img 
                                src={activeUser.avatarUrl} 
                                alt={activeUser.username} 
                                className="h-9 w-9 rounded-full object-cover ring-1 ring-zinc-100 group-hover:scale-105 transition-transform"
                                onError={(e) => {
                                  e.currentTarget.src = `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(activeUser.username)}`;
                                }}
                              />
                            ) : (
                              <div className="flex h-9 w-9 items-center justify-center rounded-full bg-zinc-100 text-zinc-600 text-xs font-bold">
                                {activeUser.username.charAt(0).toUpperCase()}
                              </div>
                            )}
                            <span className="absolute bottom-0 right-0 block h-2.5 w-2.5 rounded-full bg-emerald-500 ring-2 ring-white"></span>
                          </div>
                          
                          <div className="flex flex-col">
                            <span className="text-xs font-bold text-zinc-800 group-hover:text-brand-600 transition-colors">
                              @{activeUser.username}
                            </span>
                            <span className="text-[10px] text-zinc-400 truncate max-w-[120px]">{activeUser.fullName || "Thành viên"}</span>
                          </div>
                        </Link>

                        <button
                          onClick={() => handleFollowToggle(activeUser.userId)}
                          className={`inline-flex h-7 items-center justify-center gap-1 rounded-full px-3 text-[10px] font-bold transition-colors ${
                            isFollowing
                              ? "bg-zinc-100 hover:bg-zinc-200 text-zinc-650"
                              : "bg-brand-50 hover:bg-brand-100 text-brand-600"
                          }`}
                        >
                          {isFollowing ? "Đang theo dõi" : "Theo dõi"}
                        </button>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}

        </div>
      </div>
      {showCreateModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center px-4 bg-zinc-900/60 backdrop-blur-sm animate-fade-in">
          <div className="bg-white rounded-3xl w-full max-w-lg overflow-hidden shadow-2xl flex flex-col animate-scale-up border border-zinc-100">
            <div className="flex items-center justify-between px-6 py-4 border-b border-zinc-100">
              <h3 className="text-base font-extrabold text-zinc-800 flex items-center gap-2">
                <Compass className="h-4.5 w-4.5 text-brand-500" />
                Tạo bài đăng sáng tạo mới
              </h3>
              <button 
                onClick={() => setShowCreateModal(false)}
                className="h-8 w-8 rounded-full hover:bg-zinc-100 text-zinc-400 hover:text-zinc-600 flex items-center justify-center transition-colors"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            <form onSubmit={handleSubmitPost} className="p-6 flex flex-col gap-5 overflow-y-auto max-h-[75vh]">
              <div className="flex items-center gap-3">
                {user?.avatarUrl ? (
                  <img 
                    src={user.avatarUrl} 
                    alt={user.fullName} 
                    className="h-10 w-10 rounded-full object-cover ring-2 ring-brand-100" 
                  />
                ) : (
                  <div className="flex h-10 w-10 items-center justify-center rounded-full bg-brand-500 text-white text-sm font-bold">
                    {user?.fullName?.charAt(0)}
                  </div>
                )}
                <div>
                  <h4 className="text-sm font-bold text-zinc-800">{user?.fullName}</h4>
                  <span className="text-[10px] bg-brand-50 text-brand-600 px-2 py-0.5 rounded-full font-bold uppercase tracking-wider">
                    Creator
                  </span>
                </div>
              </div>
              <div>
                <textarea
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
                  placeholder="Hãy chia sẻ đánh giá, hình ảnh sản phẩm hoặc các kinh nghiệm thú vị nhé..."
                  className="w-full h-28 border-0 outline-none text-sm text-zinc-700 placeholder-zinc-400 resize-none"
                  maxLength={5000}
                />
                <div className="text-right text-[10px] text-zinc-300">
                  {content.length}/5000 ký tự
                </div>
              </div>
              <div className="flex flex-col gap-2.5">
                <label className="text-xs font-bold text-zinc-700 flex items-center gap-1.5">
                  <ImageIcon className="h-3.5 w-3.5 text-zinc-400" />
                  Phương tiện đính kèm (Hình ảnh/Video, Tối đa 10)
                </label>
                <div 
                  onClick={() => !isUploadingMedia && mediaInputRef.current?.click()}
                  className={`border-2 border-dashed border-zinc-250/80 hover:border-brand-400 rounded-2xl p-4 text-center cursor-pointer transition-all duration-300 bg-zinc-50/50 hover:bg-brand-50/10 flex flex-col items-center justify-center gap-1.5 select-none ${
                    isUploadingMedia ? "pointer-events-none opacity-60" : ""
                  }`}
                >
                  <input
                    type="file"
                    ref={mediaInputRef}
                    onChange={handleMediaUpload}
                    accept="image/*,video/*"
                    multiple
                    className="hidden"
                  />
                  
                  {isUploadingMedia ? (
                    <>
                      <Loader2 className="h-5 w-5 text-brand-500 animate-spin" />
                      <span className="text-xs text-zinc-400 font-medium animate-pulse">Đang tải tệp lên máy chủ...</span>
                    </>
                  ) : (
                    <>
                      <div className="h-9 w-9 rounded-xl bg-white shadow-xs border border-zinc-150 flex items-center justify-center text-brand-500 group-hover:scale-105 transition-transform duration-200">
                        <ImageIcon className="h-5 w-5 text-brand-500" />
                      </div>
                      <span className="text-xs font-extrabold text-zinc-700">Tải ảnh/video từ thiết bị</span>
                      <span className="text-[10px] text-zinc-400">Chọn hoặc kéo thả nhiều tệp (tối đa 20MB mỗi tệp)</span>
                    </>
                  )}
                </div>
                {mediaUrls.length > 0 && (
                  <div className="flex flex-wrap gap-2 mt-1 bg-zinc-50/50 p-2 border border-zinc-100 rounded-xl">
                    {mediaUrls.map((url, idx) => {
                      const isVideo = url.match(/\.(mp4|webm|ogg|mov)$/i) || url.includes("video") || url.includes("mp4");
                      return (
                        <div key={idx} className="relative group rounded-lg overflow-hidden border border-zinc-200 h-14 w-14 bg-zinc-900 flex items-center justify-center shadow-xs">
                          {isVideo ? (
                            <video src={url} className="h-full w-full object-cover" muted />
                          ) : (
                            <img 
                              src={url} 
                              alt="Review" 
                              className="h-full w-full object-cover" 
                              onError={(e) => {
                                e.currentTarget.src = "https://api.dicebear.com/7.x/initials/svg?seed=Media";
                              }}
                            />
                          )}
                          <button
                            type="button"
                            onClick={() => handleRemoveMedia(idx)}
                            className="absolute inset-0 bg-black/60 opacity-0 group-hover:opacity-100 flex items-center justify-center text-white transition-opacity"
                            title="Xóa phương tiện này"
                          >
                            <Trash2 className="h-4 w-4 text-white" />
                          </button>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>

              <div className="flex flex-col gap-2 relative" ref={productDropdownRef}>
                <label className="text-xs font-bold text-zinc-700 flex items-center gap-1.5">
                  <Tag className="h-3.5 w-3.5 text-zinc-400" />
                  Gắn thẻ sản phẩm từ sàn (Tối đa 5)
                </label>
                <input
                  type="text"
                  placeholder="Bấm vào đây để chọn sản phẩm hoặc tìm kiếm..."
                  value={productSearch}
                  onFocus={() => { setIsProductDropdownOpen(true); loadCreatorProducts(); }}
                  onChange={(e) => setProductSearch(e.target.value)}
                  className="w-full h-9 rounded-xl border border-zinc-200 px-3.5 text-xs outline-none focus:border-brand-400 focus:ring-1 focus:ring-brand-200/50"
                />
                {isProductDropdownOpen && (
                  <div className="absolute top-[68px] left-0 w-full z-10 bg-white border border-zinc-100 rounded-xl shadow-lg max-h-48 overflow-y-auto p-1.5 flex flex-col gap-1">
                    {searchingProducts ? (
                      <div className="text-center py-4 text-xs text-zinc-400 flex justify-center items-center gap-1">
                        <Loader2 className="h-3 w-3 animate-spin text-brand-500" />
                        Đang tìm kiếm...
                      </div>
                    ) : searchedProducts.length === 0 ? (
                      <div className="text-center py-4 text-xs text-zinc-400">Không tìm thấy sản phẩm hợp lệ</div>
                    ) : (
                      searchedProducts.map(p => {
                        const isAlreadyTagged = taggedProducts.some(tagged => tagged.id === p.id);
                        return (
                          <button
                            key={p.id}
                            type="button"
                            onClick={() => isAlreadyTagged ? handleUntagProduct(p.id) : handleTagProduct(p)}
                            className={`flex items-center gap-3 p-2 rounded-lg text-left w-full transition-colors ${
                              isAlreadyTagged ? "bg-emerald-50/50 hover:bg-rose-50" : "hover:bg-zinc-50"
                            }`}
                          >
                            <img src={p.images?.find(i => i.isThumbnail)?.imageUrl || p.images?.[0]?.imageUrl || ''} alt={p.name} className="h-8 w-8 rounded object-cover border" />
                            <div className="flex-1 min-w-0">
                              <h5 className="text-xs font-semibold text-zinc-800 truncate">{p.name}</h5>
                              <span className="text-[10px] text-zinc-400 font-bold">
                                {(p.variants?.[0]?.discountPrice || p.variants?.[0]?.price || p.price || 0).toLocaleString("vi-VN")}đ
                              </span>
                            </div>
                            {isAlreadyTagged ? (
                              <span className="text-[9px] bg-emerald-50 text-emerald-700 px-2 py-0.5 rounded-full font-bold hover:bg-rose-50 hover:text-rose-600 transition-colors">
                                ✓ Gỡ thẻ
                              </span>
                            ) : (
                              <Plus className="h-3.5 w-3.5 text-brand-600" />
                            )}
                          </button>
                        );
                      })
                    )}
                  </div>
                )}
                {taggedProducts.length > 0 && (
                  <div className="flex flex-col gap-1.5 mt-1">
                    {taggedProducts.map(p => (
                      <div key={p.id} className="flex items-center justify-between bg-zinc-50 border border-zinc-150 rounded-xl p-2">
                        <div className="flex items-center gap-2">
                          <img src={p.images?.find(i => i.isThumbnail)?.imageUrl || p.images?.[0]?.imageUrl || ''} alt={p.name} className="h-7 w-7 rounded object-cover border" />
                          <span className="text-xs text-zinc-700 font-medium truncate max-w-[280px]">
                            {p.name}
                          </span>
                        </div>
                        <button
                          type="button"
                          onClick={() => handleUntagProduct(p.id)}
                          className="text-rose-500 hover:text-rose-600 p-1 hover:bg-rose-50 rounded-lg"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
              <button
                type="submit"
                disabled={submittingPost}
                className="h-11 rounded-2xl bg-brand-500 hover:bg-brand-600 text-white font-semibold text-sm transition-all shadow-md shadow-brand-500/10 active:scale-[0.98] disabled:opacity-50 mt-2 flex items-center justify-center gap-2"
              >
                {submittingPost ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    Đang lưu và lan tỏa bài viết...
                  </>
                ) : (
                  <>
                    <Send className="h-4 w-4" />
                    Đăng bài viết mới
                  </>
                )}
              </button>
            </form>
          </div>
        </div>
      )}
      {variantPickerProduct && (
        <div className="fixed inset-0 z-[60] flex items-end sm:items-center justify-center px-4 pb-4 bg-zinc-900/60 backdrop-blur-sm animate-fade-in">
          <div className="bg-white rounded-2xl sm:rounded-3xl w-full max-w-md overflow-hidden shadow-2xl animate-scale-up border border-zinc-100">
            <div className="flex items-center justify-between px-5 py-4 border-b border-zinc-100">
              <h3 className="text-sm font-extrabold text-zinc-800 flex items-center gap-2">
                <ShoppingBag className="h-4 w-4 text-brand-500" />
                Chọn phân loại sản phẩm
              </h3>
              <button onClick={() => setVariantPickerProduct(null)}
                className="h-8 w-8 rounded-full hover:bg-zinc-100 text-zinc-400 hover:text-zinc-600 flex items-center justify-center transition-colors">
                <X className="h-4 w-4" />
              </button>
            </div>
            <div className="px-5 py-4 flex gap-3 items-center border-b border-zinc-50">
              <img src={variantPickerProduct.images?.find(i => i.isThumbnail)?.imageUrl || variantPickerProduct.images?.[0]?.imageUrl || ''} alt={variantPickerProduct.name} className="h-14 w-14 rounded-xl object-cover border border-zinc-200" />
              <div className="flex-1 min-w-0">
                <h4 className="text-xs font-bold text-zinc-900 line-clamp-2 leading-snug">{variantPickerProduct.name}</h4>
                <p className="text-[10px] text-zinc-400 mt-0.5">{variantPickerProduct.categoryName}</p>
              </div>
            </div>
            <div className="px-5 py-4 max-h-[50vh] overflow-y-auto">
              <p className="text-[10px] font-bold text-zinc-400 uppercase tracking-widest mb-3">Chọn một phân loại</p>
              <div className="flex flex-col gap-2">
                {variantPickerProduct.variants.filter(v => v.status === "ACTIVE").map(variant => {
                  const isOutOfStock = variant.availableStock <= 0;
                  return (
                    <button key={variant.id} onClick={() => !isOutOfStock && confirmAddToCart(variantPickerProduct, variant)} disabled={isOutOfStock}
                      className={`w-full flex items-center justify-between p-3 rounded-xl border text-left transition-all duration-200 ${isOutOfStock ? "opacity-50 cursor-not-allowed border-zinc-100 bg-zinc-50" : "border-zinc-200 hover:border-brand-400 hover:bg-brand-50/30 hover:shadow-sm active:scale-[0.98]"}`}>
                      <div className="flex flex-col gap-0.5">
                        <span className="text-xs font-bold text-zinc-800">{variant.variantName}</span>
                        <span className="text-[10px] text-zinc-400">SKU: {variant.skuCode} · Kho: {variant.availableStock.toLocaleString("vi-VN")}</span>
                      </div>
                      <div className="flex flex-col items-end gap-0.5">
                        {variant.discountPrice > 0 && variant.discountPrice < variant.price ? (
                          <>
                            <span className="text-xs font-extrabold text-brand-600">{variant.discountPrice.toLocaleString("vi-VN")}đ</span>
                            <span className="text-[10px] text-zinc-400 line-through">{variant.price.toLocaleString("vi-VN")}đ</span>
                          </>
                        ) : (
                          <span className="text-xs font-extrabold text-brand-600">{variant.price.toLocaleString("vi-VN")}đ</span>
                        )}
                        {isOutOfStock && <span className="text-[9px] text-rose-500 font-bold">Hết hàng</span>}
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}
interface PostCardProps {
  post: Post;
  currentUserId?: string;
  isAuthenticated: boolean;
  isFollowing: boolean;
  onLikeToggle: (id: string) => void;
  onFollowToggle: (creatorId: string) => void;
  onShare: (post: Post) => void;
  onAddToCart: (product: Product) => void;
}

function PostCard({ 
  post, 
  currentUserId,
  isAuthenticated,
  isFollowing,
  onLikeToggle,
  onFollowToggle,
  onShare,
  onAddToCart 
}: PostCardProps) {
  const [activeImageIdx, setActiveImageIdx] = useState(0);
  const mediaList = post.mediaUrls || [];
  const [products, setProducts] = useState<Product[]>([]);
  const [loadingProducts, setLoadingProducts] = useState(false);
  const productCarouselRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!post.taggedProductIds || post.taggedProductIds.length === 0) return;
    
    async function fetchProducts() {
      setLoadingProducts(true);
      try {
        const fetched = await Promise.all(
          post.taggedProductIds!.map(async (id) => {
            try {
              return await productService.getProductById(id);
            } catch {
              return null;
            }
          })
        );
        setProducts(fetched.filter((p): p is Product => p !== null));
      } catch (err) {
        console.error("Lỗi lấy sản phẩm gắn thẻ:", err);
      } finally {
        setLoadingProducts(false);
      }
    }

    fetchProducts();
  }, [post.taggedProductIds]);
  const scrollCarousel = (direction: "left" | "right") => {
    if (productCarouselRef.current) {
      const scrollAmt = 260;
      productCarouselRef.current.scrollBy({
        left: direction === "left" ? -scrollAmt : scrollAmt,
        behavior: "smooth"
      });
    }
  };
  const formatPostTime = (dateStr: string) => {
    const d = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - d.getTime();
    const diffMin = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMin / 60);

    if (diffMin < 1) return "Vừa xong";
    if (diffMin < 60) return `${diffMin} phút trước`;
    if (diffHours < 24) return `${diffHours} giờ trước`;
    return d.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" });
  };

  return (
    <div className="rounded-2xl border border-zinc-150/70 bg-white p-5 shadow-sm hover:shadow-md transition-shadow duration-300">
      <div className="flex items-center justify-between gap-3 mb-4">
        <Link href={ROUTES.CREATOR_PROFILE(post.creatorId)} className="flex items-center gap-3 group">
          {post.creatorAvatarUrl ? (
            <img 
              src={post.creatorAvatarUrl} 
              alt={post.creatorUsername} 
              className="h-10 w-10 rounded-full object-cover ring-2 ring-brand-50 group-hover:scale-[1.03] transition-transform" 
              onError={(e) => {
                e.currentTarget.src = `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(post.creatorUsername)}`;
              }}
            />
          ) : (
            <div className="flex h-10 w-10 items-center justify-center rounded-full bg-zinc-100 text-zinc-600 font-bold text-sm">
              {post.creatorUsername.charAt(0).toUpperCase()}
            </div>
          )}
          <div className="flex flex-col">
            <span className="text-sm font-extrabold text-zinc-800 group-hover:text-brand-600 transition-colors">
              @{post.creatorUsername}
            </span>
            <span className="text-[10px] text-zinc-400 font-medium">
              {formatPostTime(post.createdAt)}
            </span>
          </div>
        </Link>
        {isAuthenticated && post.creatorId !== currentUserId && (
          <button
            onClick={() => onFollowToggle(post.creatorId)}
            className={`h-8 px-4 rounded-full border transition-all flex items-center gap-1 text-xs font-semibold ${
              isFollowing 
                ? "border-zinc-200 bg-zinc-100 hover:bg-zinc-200 text-zinc-650"
                : "border-brand-200 bg-brand-50 hover:bg-brand-100 text-brand-700"
            }`}
          >
            {isFollowing ? (
              <>
                <UserMinus className="h-3.5 w-3.5" />
                Đang theo dõi
              </>
            ) : (
              <>
                <UserPlus className="h-3.5 w-3.5" />
                Theo dõi
              </>
            )}
          </button>
        )}
      </div>
      <p className="text-sm text-zinc-700 leading-relaxed mb-4 whitespace-pre-line font-light">
        {post.content}
      </p>
      {mediaList.length > 0 && (() => {
        const currentMedia = mediaList[activeImageIdx];
        const isVideo = currentMedia.match(/\.(mp4|webm|ogg|mov)$/i) || currentMedia.includes("video") || currentMedia.includes("mp4");
        return (
        <div className="relative rounded-2xl overflow-hidden bg-zinc-950 mb-5 aspect-[4/3] sm:aspect-[16/9] flex items-center justify-center group/slider">
          {isVideo ? (
            <video 
              key={currentMedia}
              src={currentMedia} 
              controls
              className="w-full h-full object-contain"
            />
          ) : (
            <img 
              src={currentMedia} 
              alt={`Ảnh bài đăng ${activeImageIdx + 1}`}
              className="w-full h-full object-contain" 
            />
          )}
          {mediaList.length > 1 && (
            <>
              <button
                onClick={() => setActiveImageIdx(prev => (prev === 0 ? mediaList.length - 1 : prev - 1))}
                className="absolute left-3.5 h-8 w-8 rounded-full bg-black/40 hover:bg-black/60 flex items-center justify-center text-white backdrop-blur-sm opacity-0 group-hover/slider:opacity-100 transition-opacity"
              >
                <ChevronLeft className="h-4.5 w-4.5" />
              </button>
              <button
                onClick={() => setActiveImageIdx(prev => (prev === mediaList.length - 1 ? 0 : prev + 1))}
                className="absolute right-3.5 h-8 w-8 rounded-full bg-black/40 hover:bg-black/60 flex items-center justify-center text-white backdrop-blur-sm opacity-0 group-hover/slider:opacity-100 transition-opacity"
              >
                <ChevronRight className="h-4.5 w-4.5" />
              </button>
              <div className="absolute bottom-3 left-1/2 -translate-x-1/2 flex gap-1.5 bg-black/30 px-3 py-1 rounded-full backdrop-blur-sm">
                {mediaList.map((_, i) => (
                  <span 
                    key={i}
                    className={`h-1.5 w-1.5 rounded-full transition-all duration-200 ${i === activeImageIdx ? "bg-brand-400 w-3" : "bg-white/60"}`}
                  />
                ))}
              </div>
            </>
          )}
        </div>
        );
      })()}
      {post.taggedProductIds && post.taggedProductIds.length > 0 && (
        <div className="relative border border-brand-100/50 bg-brand-50/10 rounded-2xl p-4 mb-5">
          <div className="flex items-center justify-between gap-2 mb-3">
            <span className="text-[11px] font-extrabold text-brand-600 uppercase tracking-widest flex items-center gap-1.5">
              <Tag className="h-3.5 w-3.5" />
              Mua ngay trên bài viết
            </span>
            {products.length > 2 && (
              <div className="flex gap-1.5">
                <button
                  onClick={() => scrollCarousel("left")}
                  className="h-6 w-6 rounded-full bg-white hover:bg-zinc-50 border flex items-center justify-center text-zinc-600 transition-colors"
                >
                  <ChevronLeft className="h-3.5 w-3.5" />
                </button>
                <button
                  onClick={() => scrollCarousel("right")}
                  className="h-6 w-6 rounded-full bg-white hover:bg-zinc-50 border flex items-center justify-center text-zinc-600 transition-colors"
                >
                  <ChevronRight className="h-3.5 w-3.5" />
                </button>
              </div>
            )}
          </div>

          {loadingProducts ? (
            <div className="flex justify-center py-6">
              <Loader2 className="h-6 w-6 text-brand-500 animate-spin" />
            </div>
          ) : (
            <div 
              ref={productCarouselRef}
              className="flex gap-4 overflow-x-auto no-scrollbar scroll-smooth"
              style={{ scrollbarWidth: "none" }}
            >
              {products.map((product) => (
                <div 
                  key={product.id} 
                  className="flex-shrink-0 w-60 rounded-xl bg-white border border-zinc-150 p-2.5 flex flex-col justify-between shadow-sm hover:border-brand-200 transition-colors"
                >
                  <Link href={ROUTES.PRODUCT_DETAILS(product.id)} className="flex gap-3 items-center group">
                    <img 
                      src={product.images?.find(i => i.isThumbnail)?.imageUrl || product.images?.[0]?.imageUrl || ''} 
                      alt={product.name} 
                      className="h-14 w-14 rounded-lg object-cover border group-hover:opacity-90 transition-opacity" 
                    />
                    <div className="flex-1 min-w-0">
                      <h4 className="text-xs font-bold text-zinc-800 line-clamp-2 leading-tight group-hover:text-brand-600 transition-colors">
                        {product.name}
                      </h4>
                      <span className="text-xs font-extrabold text-brand-600 mt-1 block">
                        {(product.variants?.[0]?.discountPrice || product.variants?.[0]?.price || product.price || 0).toLocaleString("vi-VN")}đ
                      </span>
                    </div>
                  </Link>

                  <div className="flex gap-2 mt-2.5 pt-2 border-t border-zinc-100">
                    <Link
                      href={ROUTES.PRODUCT_DETAILS(product.id)}
                      className="flex-1 h-7.5 rounded-lg border border-brand-200 text-brand-600 hover:bg-brand-50 flex items-center justify-center text-[10px] font-bold transition-all"
                    >
                      Chi tiết
                    </Link>
                    <button
                      onClick={() => onAddToCart(product)}
                      className="h-7.5 w-7.5 rounded-lg bg-brand-500 hover:bg-brand-600 text-white flex items-center justify-center transition-colors active:scale-95"
                      title="Thêm nhanh vào giỏ hàng"
                    >
                      <ShoppingBag className="h-3.5 w-3.5" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
      <div className="flex items-center justify-between pt-3.5 border-t border-zinc-100">
        <button
          onClick={() => onLikeToggle(post.id)}
          className={`flex items-center gap-1.5 text-xs font-bold px-3 py-1.5 rounded-full transition-all ${
            post.likedByMe 
              ? "text-rose-600 bg-rose-50 hover:bg-rose-100 scale-105" 
              : "text-zinc-500 hover:text-rose-500 hover:bg-zinc-50 active:scale-95"
          }`}
        >
          <Heart className={`h-4 w-4 transition-transform ${post.likedByMe ? "fill-rose-600 scale-110" : ""}`} />
          {post.likeCount.toLocaleString("vi-VN")} lượt thích
        </button>
        <Link
          href={ROUTES.POST_DETAILS(post.id)}
          className="flex items-center gap-1.5 text-xs font-bold text-zinc-500 hover:text-brand-500 px-3 py-1.5 rounded-full hover:bg-zinc-50 transition-all"
        >
          <MessageSquare className="h-4 w-4" />
          {post.commentCount.toLocaleString("vi-VN")} bình luận
        </Link>
        <button
          onClick={() => onShare(post)}
          className="flex items-center gap-1.5 text-xs font-bold text-zinc-500 hover:text-brand-500 px-3 py-1.5 rounded-full hover:bg-zinc-50 transition-all"
        >
          <Share2 className="h-4 w-4" />
          Chia sẻ
        </button>
      </div>

    </div>
  );
}
