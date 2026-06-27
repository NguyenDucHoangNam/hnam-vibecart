"use client";

import React, { useState, useEffect, useCallback } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { 
  ChevronLeft, 
  Compass, 
  UserPlus, 
  UserMinus, 
  Loader2, 
  ShoppingBag, 
  Calendar,
  Grid,
  FileText,
  Users,
  Info,
  Heart,
  MessageSquare,
  Share2,
  Tag
} from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useCart } from "@/hooks/useCart";
import { useToast } from "@/context/ToastContext";
import { postService, PageResponse } from "@/services/post.service";
import { userService } from "@/services/user.service";
import { productService } from "@/services/product.service";
import { Post, Product } from "@/types";
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

export default function CreatorProfilePage() {
  const params = useParams();
  const router = useRouter();
  const creatorId = params.id as string;

  const { user, isAuthenticated } = useAuth();
  const { addToCart } = useCart();
  const toast = useToast();
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<"posts" | "products">("posts");
  const [products, setProducts] = useState<Product[]>([]);
  const [productsLoading, setProductsLoading] = useState(false);
  const [isFollowing, setIsFollowing] = useState(false);
  const [followerCount, setFollowerCount] = useState(0);
  const [followingCount, setFollowingCount] = useState(0);
  const [checkingFollow, setCheckingFollow] = useState(false);
  const [creatorName, setCreatorName] = useState("");
  const [creatorFullName, setCreatorFullName] = useState("");
  const [creatorAvatar, setCreatorAvatar] = useState("");
  const [creatorJoinDate, setCreatorJoinDate] = useState("");
  const [creatorRoles, setCreatorRoles] = useState<string[]>([]);

  const getRoleBadgeStyle = (role: string) => {
    const r = role.toUpperCase();
    if (r === "ROLE_ADMIN" || r === "ADMIN") {
      return {
        label: "Admin",
        className: "bg-rose-50 text-rose-600 border border-rose-200",
      };
    }
    if (r === "ROLE_CREATOR" || r === "CREATOR") {
      return {
        label: "Creator",
        className: "bg-emerald-50 text-emerald-650 border border-emerald-200",
      };
    }
    return {
      label: "Thành viên",
      className: "bg-blue-50 text-blue-600 border border-blue-200",
    };
  };

  const loadProfileData = useCallback(async () => {
    setLoading(true);
    try {
      try {
        const profile = await userService.getUserProfile(creatorId);
        if (profile) {
          setCreatorName(profile.username || "");
          setCreatorFullName(profile.fullName || "");
          setCreatorAvatar(profile.avatarUrl || "");
          setCreatorJoinDate(profile.createdAt || "");
          setCreatorRoles(profile.roles || []);
        }
      } catch (err) {
        console.error("Lỗi lấy profile, fallback từ bài viết:", err);
      }
      const postsRes = await postService.getPosts(0, 30, creatorId);
      if (postsRes && postsRes.content) {
        setPosts(postsRes.content);
        if (!creatorName && postsRes.content.length > 0) {
          setCreatorName(postsRes.content[0].creatorUsername);
          setCreatorAvatar(postsRes.content[0].creatorAvatarUrl || "");
        }
      }
      setProductsLoading(true);
      try {
        const prodRes = await productService.getProductsByCreator(creatorId, {
          page: 0,
          size: 100,
        });
        if (prodRes && prodRes.content) {
          setProducts(prodRes.content);
        }
      } catch (prodErr) {
        console.error("Lỗi lấy danh sách sản phẩm của creator:", prodErr);
      } finally {
        setProductsLoading(false);
      }
      const [followers, following] = await Promise.all([
        userService.getFollowerCount(creatorId),
        userService.getFollowingCount(creatorId)
      ]);
      setFollowerCount(followers);
      setFollowingCount(following);
      if (isAuthenticated && user?.id !== creatorId) {
        setCheckingFollow(true);
        const check = await userService.checkFollow(creatorId);
        setIsFollowing(check);
        setCheckingFollow(false);
      }
    } catch (err: any) {
      console.error("Lỗi tải trang hồ sơ creator:", err);
      toast.error("Lỗi tải thông tin", "Không thể lấy dữ liệu đầy đủ từ nhà sáng tạo này.");
    } finally {
      setLoading(false);
    }
  }, [creatorId, isAuthenticated, user?.id, toast]);

  useEffect(() => {
    loadProfileData();
  }, [loadProfileData]);

  const handleFollowToggle = async () => {
    if (!isAuthenticated) {
      toast.warning("Yêu cầu đăng nhập", "Bạn cần đăng nhập để theo dõi nhà sáng tạo này!");
      return;
    }
    setIsFollowing(prev => !prev);
    setFollowerCount(prev => isFollowing ? prev - 1 : prev + 1);

    try {
      const res = await userService.toggleFollow(creatorId);
      if (res) {
        toast.success("Đã theo dõi", `Bạn đã đăng ký theo dõi @${creatorName || 'creator'}`);
      } else {
        toast.info("Đã hủy theo dõi", `Bạn đã dừng theo dõi @${creatorName || 'creator'}`);
      }
    } catch (err: any) {
      setIsFollowing(prev => !prev);
      setFollowerCount(prev => isFollowing ? prev + 1 : prev - 1);
      toast.error("Lỗi thao tác", err?.message || "Không thể gửi yêu cầu lên máy chủ.");
    }
  };

  const handleShareProfile = () => {
    navigator.clipboard.writeText(window.location.href);
    toast.success("Sao chép thành công", "Liên kết hồ sơ đã được lưu vào bộ nhớ tạm.");
  };

  const handleLikeToggle = async (postId: string) => {
    if (!isAuthenticated) {
      toast.warning("Yêu cầu đăng nhập", "Bạn cần đăng nhập để thích bài viết.");
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
      toast.error("Lỗi tương tác", "Không thể cập nhật lượt thích bài viết.");
    }
  };

  const handleAddProductToCart = async (product: Product) => {
    if (!product.variants || product.variants.length === 0) {
      toast.warning("Sản phẩm chưa có biến thể", "Không thể thêm sản phẩm này vào giỏ hàng.");
      return;
    }
    await addToCart(product.variants[0], product, 1);
  };

  if (loading) {
    return (
      <div className="min-h-[80vh] flex flex-col items-center justify-center gap-3">
        <Loader2 className="h-10 w-10 text-brand-500 animate-spin" />
        <p className="text-sm text-zinc-500 animate-pulse font-medium">Đang tải hồ sơ nhà sáng tạo...</p>
      </div>
    );
  }

  const finalName = creatorName || `Creator_${creatorId.substring(0, 6)}`;

  return (
    <div className="min-h-screen bg-zinc-50/50 pb-16">
      <div className="mx-auto max-w-5xl px-4 sm:px-6 lg:px-8 pt-8 pb-4 flex justify-between items-center">
        <Link 
          href={ROUTES.FEED}
          className="inline-flex items-center gap-1.5 text-xs font-bold text-zinc-500 hover:text-brand-650 bg-white border border-zinc-200/80 px-4.5 py-2.5 rounded-full shadow-sm hover:shadow transition-all group"
        >
          <ChevronLeft className="h-4 w-4 group-hover:-translate-x-0.5 transition-transform" />
          Bảng tin
        </Link>

        <button
          onClick={handleShareProfile}
          className="inline-flex items-center gap-1.5 text-xs font-bold text-zinc-550 hover:text-zinc-950 bg-white border border-zinc-200/80 px-4.5 py-2.5 rounded-full shadow-sm hover:shadow transition-all active:scale-[0.98]"
        >
          <Share2 className="h-4 w-4" />
          Chia sẻ hồ sơ
        </button>
      </div>
      <div className="mx-auto max-w-5xl px-4 sm:px-6 lg:px-8 relative z-20">
        <div className="bg-white rounded-3xl border border-zinc-150 p-6 sm:p-8 shadow-sm flex flex-col sm:flex-row justify-between items-center sm:items-center gap-6">
          <div className="flex flex-col sm:flex-row items-center sm:items-center gap-5 text-center sm:text-left">
            <div className="relative">
              {creatorAvatar ? (
                <img 
                  src={creatorAvatar} 
                  alt={finalName} 
                  className="h-24 w-24 sm:h-28 sm:w-28 rounded-full object-cover border-4 border-white shadow-md ring-2 ring-brand-100" 
                  onError={(e) => {
                    e.currentTarget.src = `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(finalName)}`;
                  }}
                />
              ) : (
                <div className="flex h-24 w-24 sm:h-28 sm:w-28 items-center justify-center rounded-full bg-gradient-to-br from-brand-400 to-brand-600 text-white font-extrabold text-3xl border-4 border-white shadow-md">
                  {finalName.charAt(0).toUpperCase()}
                </div>
              )}
              <span className="absolute bottom-1 right-1 h-5 w-5 bg-brand-500 border-2 border-white rounded-full flex items-center justify-center" title="Đã xác thực Creator">
                <span className="h-2 w-2 bg-white rounded-full animate-ping" />
              </span>
            </div>
 
            <div className="flex flex-col gap-1.5">
              <div className="flex items-center gap-2 justify-center sm:justify-start">
                <h1 className="text-xl sm:text-2xl font-black text-zinc-900 leading-tight">
                  @{finalName}
                </h1>
                {creatorRoles.length > 0 ? (
                  creatorRoles.map((role) => {
                    const badge = getRoleBadgeStyle(role);
                    return (
                      <span
                        key={role}
                        className={`text-[9px] border px-2.5 py-0.5 rounded-full font-bold uppercase tracking-wider ${badge.className}`}
                      >
                        {badge.label}
                      </span>
                    );
                  })
                ) : (
                  <span className="text-[9px] bg-brand-50 text-brand-650 border border-brand-100 px-2.5 py-0.5 rounded-full font-bold uppercase tracking-wider">
                    Creator
                  </span>
                )}
              </div>
              <p className="text-xs text-zinc-400 font-medium">
                {creatorFullName || "Thành viên sáng tạo chính thức của mạng lưới VibeCart"}
              </p>
              <div className="flex items-center gap-1.5 justify-center sm:justify-start text-[10px] text-zinc-400 font-semibold mt-1">
                <Calendar className="h-3.5 w-3.5" />
                {creatorJoinDate
                  ? `Tham gia tháng ${new Date(creatorJoinDate).toLocaleDateString("vi-VN", { month: "2-digit", year: "numeric" })}`
                  : "Thành viên VibeCart"
                }
              </div>
              <div className="flex items-center gap-5 mt-2.5 justify-center sm:justify-start text-xs text-zinc-500 font-medium">
                <div>
                  <span className="font-extrabold text-zinc-900 mr-1">{posts.length}</span> bài viết
                </div>
                <div>
                  <span className="font-extrabold text-zinc-900 mr-1">{followerCount.toLocaleString("vi-VN")}</span> người theo dõi
                </div>
                <div>
                  <span className="font-extrabold text-zinc-900 mr-1">{followingCount.toLocaleString("vi-VN")}</span> đang theo dõi
                </div>
              </div>
            </div>
          </div>
          {isAuthenticated && user?.id !== creatorId ? (
            <div className="w-full sm:w-auto flex flex-col sm:flex-row gap-3">
              <button
                onClick={handleFollowToggle}
                disabled={checkingFollow}
                className={`w-full sm:w-auto h-11 px-8 rounded-2xl font-bold text-sm shadow-md transition-all active:scale-[0.98] flex items-center justify-center gap-2 ${
                  isFollowing
                    ? "bg-zinc-100 hover:bg-zinc-200 text-zinc-700 border border-zinc-200/80 shadow-none"
                    : "bg-brand-500 hover:bg-brand-600 text-white shadow-brand-500/10"
                }`}
              >
                {isFollowing ? (
                  <>
                    <UserMinus className="h-4 w-4" />
                    Đang theo dõi
                  </>
                ) : (
                  <>
                    <UserPlus className="h-4 w-4" />
                    Theo dõi
                  </>
                )}
              </button>
 
              <Link
                href={`${ROUTES.MESSAGES}?startDirectChatUserId=${creatorId}`}
                className="w-full sm:w-auto h-11 px-8 rounded-2xl font-bold text-sm border border-zinc-200 bg-white hover:bg-zinc-50 text-zinc-700 flex items-center justify-center gap-2 active:scale-[0.98] transition-all"
              >
                <MessageSquare className="h-4 w-4 text-zinc-450" />
                Nhắn tin
              </Link>
            </div>
          ) : user?.id === creatorId ? (
            <div>
              <span className="text-xs text-zinc-450 italic bg-zinc-50 border border-zinc-150/70 px-4 py-2 rounded-xl block">
                Đây là trang cá nhân của bạn
              </span>
            </div>
          ) : (
            <div className="w-full sm:w-auto">
              <Link
                href={ROUTES.LOGIN}
                className="w-full sm:w-auto h-11 px-8 rounded-2xl bg-brand-500 hover:bg-brand-600 text-white font-bold text-sm shadow-md flex items-center justify-center gap-2"
              >
                <UserPlus className="h-4 w-4" />
                Đăng nhập để theo dõi
              </Link>
            </div>
          )}
        </div>
      </div>

      <div className="mx-auto max-w-5xl px-4 sm:px-6 lg:px-8 mt-8">
        <div className="flex border-b border-zinc-200 mb-6 gap-8">
          <button
            onClick={() => setActiveTab("posts")}
            className={`pb-3 text-sm font-extrabold relative transition-colors ${
              activeTab === "posts"
                ? "text-brand-500 after:absolute after:bottom-0 after:left-0 after:right-0 after:h-0.5 after:bg-brand-500"
                : "text-zinc-500 hover:text-zinc-800"
            }`}
          >
            Tất cả bài viết ({posts.length})
          </button>
          
          <button
            onClick={() => setActiveTab("products")}
            className={`pb-3 text-sm font-extrabold relative transition-colors ${
              activeTab === "products"
                ? "text-brand-500 after:absolute after:bottom-0 after:left-0 after:right-0 after:h-0.5 after:bg-brand-500"
                : "text-zinc-500 hover:text-zinc-800"
            }`}
          >
            Tất cả sản phẩm ({products.length})
          </button>
        </div>

        <div className="w-full flex flex-col gap-6">
          {activeTab === "posts" ? (
            <>
              {posts.length === 0 ? (
                <div className="rounded-3xl bg-white border border-zinc-150 p-12 text-center flex flex-col items-center shadow-sm">
                  <div className="h-12 w-12 bg-zinc-50 rounded-full flex items-center justify-center text-zinc-400 mb-3">
                    <Grid className="h-5 w-5" />
                  </div>
                  <h4 className="text-sm font-bold text-zinc-700">Chưa có bài đăng nào</h4>
                  <p className="text-xs text-zinc-400 mt-0.5">Nhà sáng tạo này chưa chia sẻ nội dung nào trên sàn.</p>
                </div>
              ) : (
                <div className="flex flex-col gap-6">
                  {posts.map((post) => (
                    <PostCardMini 
                      key={post.id} 
                      post={post}
                      isAuthenticated={isAuthenticated}
                      onLikeToggle={handleLikeToggle}
                      onAddToCart={handleAddProductToCart}
                    />
                  ))}
                </div>
              )}
            </>
          ) : (
            productsLoading ? (
              <div className="rounded-3xl bg-white border border-zinc-150 p-12 text-center flex flex-col items-center shadow-sm justify-center gap-3">
                <Loader2 className="h-8 w-8 text-brand-500 animate-spin" />
                <p className="text-xs text-zinc-500 font-light">Đang tải danh sách sản phẩm...</p>
              </div>
            ) : products.length === 0 ? (
              <div className="rounded-3xl bg-white border border-zinc-150 p-12 text-center flex flex-col items-center shadow-sm">
                <div className="h-12 w-12 bg-zinc-50 rounded-full flex items-center justify-center text-zinc-400 mb-3">
                  <ShoppingBag className="h-5 w-5" />
                </div>
                <h4 className="text-sm font-bold text-zinc-700">Chưa có sản phẩm nào</h4>
                <p className="text-xs text-zinc-400 mt-0.5">Nhà sáng tạo này chưa đăng bán sản phẩm nào.</p>
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6 animate-toast-in">
                {products.map((product) => {
                  const activeVariants = product.variants?.filter((v: any) => v.status === "ACTIVE") || [];
                  const prices = activeVariants.map((v: any) => {
                    const dp = v.discountPrice;
                    return (dp && dp > 0 && dp < v.price) ? dp : v.price;
                  }).filter((p: number) => p > 0);

                  const minPrice_ = prices.length > 0 ? Math.min(...prices) : 0;
                  const maxPrice_ = prices.length > 0 ? Math.max(...prices) : 0;
                  const hasRange = minPrice_ !== maxPrice_ && maxPrice_ > 0;

                  const thumbnail = product.images?.find((img) => img.isThumbnail)?.imageUrl || product.images?.[0]?.imageUrl || "";

                  return (
                    <Link
                      href={ROUTES.PRODUCT_DETAILS(product.id)}
                      key={product.id}
                      className="group bg-white rounded-3xl border border-zinc-200/50 p-4 shadow-sm hover:shadow-xl hover:shadow-brand-500/5 hover:-translate-y-1 transition-all duration-300 flex flex-col h-full overflow-hidden cursor-pointer"
                    >
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
                        className="relative w-full h-[180px] rounded-2xl overflow-hidden bg-zinc-50 mb-4 border border-zinc-100"
                      >
                        {thumbnail ? (
                          isVideoUrl(thumbnail) ? (
                            <video 
                              src={thumbnail} 
                              muted 
                              loop 
                              playsInline 
                              preload="metadata"
                              className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                            />
                          ) : (
                            <img
                              src={thumbnail}
                              alt={product.name}
                              className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                            />
                          )
                        ) : (
                          <div className="w-full h-full flex items-center justify-center text-zinc-300">
                            <ShoppingBag className="h-10 w-10" />
                          </div>
                        )}
                      </div>

                      <div className="flex flex-col flex-1 min-w-0 px-1">
                        <span className="text-[10px] font-semibold text-brand-600 uppercase tracking-widest mb-1.5 block">
                          {product.categoryName || "Danh mục"}
                        </span>
                        <h4 className="text-sm font-extrabold text-zinc-800 line-clamp-1 group-hover:text-brand-650 transition-colors">
                          {product.name}
                        </h4>
                        <p className="text-xs text-zinc-400 mt-1 line-clamp-2 font-light leading-relaxed">
                          {product.description || "Chưa cập nhật mô tả..."}
                        </p>

                        <div className="flex items-center justify-between mt-auto pt-4 border-t border-zinc-100">
                          <div className="flex flex-col">
                            {hasRange ? (
                              <span className="text-xs font-bold text-zinc-950">
                                {minPrice_.toLocaleString("vi-VN")}đ - {maxPrice_.toLocaleString("vi-VN")}đ
                              </span>
                            ) : (
                              <span className="text-xs font-bold text-zinc-950">
                                {(minPrice_ || 0).toLocaleString("vi-VN")}đ
                              </span>
                            )}
                          </div>
                        </div>
                      </div>
                    </Link>
                  );
                })}
              </div>
            )
          )}
        </div>
 
      </div>
 
    </div>
  );
}

interface PostCardMiniProps {
  post: Post;
  isAuthenticated: boolean;
  onLikeToggle: (id: string) => void;
  onAddToCart: (product: Product) => void;
}

function PostCardMini({ 
  post, 
  isAuthenticated,
  onLikeToggle,
  onAddToCart 
}: PostCardMiniProps) {
  const [products, setProducts] = useState<Product[]>([]);
  const [loadingProducts, setLoadingProducts] = useState(false);
  const mediaList = post.mediaUrls || [];
  const toast = useToast();

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

  const handleShare = () => {
    const postUrl = `${window.location.origin}${ROUTES.POST_DETAILS(post.id)}`;
    navigator.clipboard.writeText(postUrl);
    toast.success("Sao chép thành công", "Liên kết bài viết đã được lưu.");
  };

  const formatPostTime = (dateStr: string) => {
    const d = new Date(dateStr);
    return d.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" });
  };

  return (
    <div className="rounded-2xl border border-zinc-150/70 bg-white p-5 shadow-sm hover:shadow-md transition-shadow duration-300">
      <div className="text-[10px] text-zinc-400 font-bold mb-2 flex items-center gap-1">
        <Calendar className="h-3.5 w-3.5" />
        Đăng ngày {formatPostTime(post.createdAt)}
      </div>
      <p className="text-sm text-zinc-700 leading-relaxed mb-4 whitespace-pre-line font-light">
        {post.content}
      </p>
      {mediaList.length > 0 && (
        <div className="relative rounded-xl overflow-hidden bg-zinc-900 mb-4 aspect-[16/9] max-h-72 flex items-center justify-center">
          <img src={mediaList[0]} alt="Ảnh bài viết" className="w-full h-full object-contain" />
        </div>
      )}
      {products.length > 0 && (
        <div className="border border-brand-100/50 bg-brand-50/10 rounded-xl p-3 mb-4 flex flex-col gap-2">
          <span className="text-[9px] font-extrabold text-brand-600 uppercase tracking-widest flex items-center gap-1">
            <Tag className="h-3 w-3" />
            Sản phẩm gắn kèm bài viết
          </span>
          <div className="flex flex-wrap gap-2">
            {products.map(p => (
              <div key={p.id} className="bg-white border rounded-lg p-2 flex items-center gap-2 w-full sm:w-auto">
                <img src={p.imageUrl} alt={p.name} className="h-10 w-10 rounded object-cover border" />
                <div className="min-w-0 max-w-[200px]">
                  <Link href={ROUTES.PRODUCT_DETAILS(p.id)} className="text-[11px] font-bold text-zinc-800 hover:text-brand-600 truncate block">
                    {p.name}
                  </Link>
                  <span className="text-[10px] font-bold text-brand-600 block mt-0.5">
                    {(p.price || 0).toLocaleString("vi-VN")}đ
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
      <div className="flex items-center justify-between pt-3 border-t border-zinc-100">
        <button
          onClick={() => onLikeToggle(post.id)}
          className={`flex items-center gap-1.5 text-[11px] font-bold px-3 py-1 rounded-full transition-all ${
            post.likedByMe 
              ? "text-rose-600 bg-rose-50 hover:bg-rose-100" 
              : "text-zinc-500 hover:text-rose-500 hover:bg-zinc-50"
          }`}
        >
          <Heart className={`h-3.5 w-3.5 ${post.likedByMe ? "fill-rose-600" : ""}`} />
          {post.likeCount.toLocaleString("vi-VN")} thích
        </button>
        <Link
          href={ROUTES.POST_DETAILS(post.id)}
          className="flex items-center gap-1.5 text-[11px] font-bold text-zinc-500 hover:text-brand-500 px-3 py-1 rounded-full hover:bg-zinc-50"
        >
          <MessageSquare className="h-3.5 w-3.5" />
          {post.commentCount.toLocaleString("vi-VN")} bình luận
        </Link>
        <button
          onClick={handleShare}
          className="flex items-center gap-1.5 text-[11px] font-bold text-zinc-500 hover:text-brand-500 px-3 py-1 rounded-full hover:bg-zinc-50"
        >
          <Share2 className="h-3.5 w-3.5" />
          Chia sẻ
        </button>
      </div>

    </div>
  );
}
