"use client";

import React, { useState, useEffect, useCallback, useRef } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { 
  ChevronLeft, 
  ChevronRight,
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
  Tag,
  Plus,
  Image as ImageIcon,
  Send,
  X,
  Globe,
  Lock,
  ChevronDown
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

  // Post Creation States
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [content, setContent] = useState("");
  const [mediaUrls, setMediaUrls] = useState<string[]>([]);
  const [taggedProducts, setTaggedProducts] = useState<Product[]>([]);
  const [visibility, setVisibility] = useState<"PUBLIC" | "FOLLOWERS" | "PRIVATE">("PUBLIC");
  const [isVisibilityDropdownOpen, setIsVisibilityDropdownOpen] = useState(false);
  const [submittingPost, setSubmittingPost] = useState(false);
  const [isUploadingMedia, setIsUploadingMedia] = useState(false);
  const [showMediaSection, setShowMediaSection] = useState(false);
  const [showProductSection, setShowProductSection] = useState(false);
  const [productSearch, setProductSearch] = useState("");
  const [isProductDropdownOpen, setIsProductDropdownOpen] = useState(false);
  const mediaInputRef = useRef<HTMLInputElement>(null);

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
        taggedProductIds: taggedProducts.map(p => p.id),
        visibility
      });

      toast.success("Đăng bài thành công", "Bài viết của bạn đã được xuất bản lên VibeCart!");
      setContent("");
      setMediaUrls([]);
      setTaggedProducts([]);
      setShowCreateModal(false);
      setIsProductDropdownOpen(false);
      setVisibility("PUBLIC");
      setShowMediaSection(false);
      setShowProductSection(false);
      setPosts(prev => [newPost, ...prev]);
    } catch (err: any) {
      toast.error("Lỗi đăng bài", err?.message || "Không thể lưu bài viết mới.");
    } finally {
      setSubmittingPost(false);
    }
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
              {isAuthenticated && user?.id === creatorId && user?.roles?.includes("ROLE_CREATOR") && (
                <div className="rounded-2xl border border-brand-100/60 bg-white p-5 shadow-sm hover:border-brand-200/80 transition-all duration-300 mb-6 w-full">
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
                      currentUserId={user?.id}
                      isAuthenticated={isAuthenticated}
                      isFollowing={isFollowing}
                      onLikeToggle={handleLikeToggle}
                      onFollowToggle={handleFollowToggle}
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
                <div className="flex flex-col items-start gap-1">
                  <h4 className="text-sm font-bold text-zinc-800 leading-none mb-0.5">{user?.fullName}</h4>
                  <div className="relative">
                    <button
                      type="button"
                      onClick={() => setIsVisibilityDropdownOpen(!isVisibilityDropdownOpen)}
                      className="flex items-center gap-1.5 px-2 py-1 rounded-md bg-zinc-100 hover:bg-zinc-200/80 text-zinc-650 transition-colors select-none text-[10px] font-bold"
                    >
                      {visibility === "PUBLIC" && (
                        <>
                          <Globe className="h-3 w-3 text-zinc-500" />
                          <span>Công khai</span>
                        </>
                      )}
                      {visibility === "FOLLOWERS" && (
                        <>
                          <Users className="h-3 w-3 text-zinc-500" />
                          <span>Người theo dõi</span>
                        </>
                      )}
                      {visibility === "PRIVATE" && (
                        <>
                          <Lock className="h-3 w-3 text-zinc-500" />
                          <span>Chỉ mình tôi</span>
                        </>
                      )}
                      <ChevronDown className="h-3 w-3 shrink-0 text-zinc-500" />
                    </button>
                    {isVisibilityDropdownOpen && (
                      <div className="absolute left-0 mt-1.5 w-64 bg-white rounded-xl border border-zinc-150 shadow-xl p-1.5 z-50 animate-in fade-in duration-150">
                        <button
                          type="button"
                          onClick={() => { setVisibility("PUBLIC"); setIsVisibilityDropdownOpen(false); }}
                          className={`flex items-start gap-2.5 w-full rounded-lg px-3 py-2 text-left transition-colors hover:bg-zinc-50 ${visibility === "PUBLIC" ? "bg-brand-50/40" : ""}`}
                        >
                          <Globe className="h-4 w-4 text-zinc-500 mt-0.5 shrink-0" />
                          <div className="flex-1">
                            <p className="text-xs font-bold text-zinc-800">Công khai</p>
                            <p className="text-[9px] text-zinc-400 mt-0.5 leading-snug">Bất kỳ ai ở trong hoặc ngoài VibeCart đều nhìn thấy.</p>
                          </div>
                        </button>
                        <button
                          type="button"
                          onClick={() => { setVisibility("FOLLOWERS"); setIsVisibilityDropdownOpen(false); }}
                          className={`flex items-start gap-2.5 w-full rounded-lg px-3 py-2 text-left transition-colors hover:bg-zinc-50 mt-1 ${visibility === "FOLLOWERS" ? "bg-brand-50/40" : ""}`}
                        >
                          <Users className="h-4 w-4 text-zinc-500 mt-0.5 shrink-0" />
                          <div className="flex-1">
                            <p className="text-xs font-bold text-zinc-800">Người theo dõi</p>
                            <p className="text-[9px] text-zinc-400 mt-0.5 leading-snug">Chỉ những người đang theo dõi bạn mới có thể xem.</p>
                          </div>
                        </button>
                        <button
                          type="button"
                          onClick={() => { setVisibility("PRIVATE"); setIsVisibilityDropdownOpen(false); }}
                          className={`flex items-start gap-2.5 w-full rounded-lg px-3 py-2 text-left transition-colors hover:bg-zinc-50 mt-1 ${visibility === "PRIVATE" ? "bg-brand-50/40" : ""}`}
                        >
                          <Lock className="h-4 w-4 text-zinc-500 mt-0.5 shrink-0" />
                          <div className="flex-1">
                            <p className="text-xs font-bold text-zinc-800">Chỉ mình tôi</p>
                            <p className="text-[9px] text-zinc-400 mt-0.5 leading-snug">Chỉ bạn mới có thể nhìn thấy bài viết này.</p>
                          </div>
                        </button>
                      </div>
                    )}
                  </div>
                </div>
              </div>
              <div>
                <textarea
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
                  placeholder="Hãy chia sẻ đánh giá, hình ảnh sản phẩm hoặc các kinh nghiệm thú vị nhé..."
                  className="w-full h-32 border-0 outline-none text-base text-zinc-800 placeholder-zinc-400 resize-none font-medium leading-relaxed"
                  maxLength={5000}
                />
                <div className="text-right text-[10px] text-zinc-300">
                  {content.length}/5000 ký tự
                </div>
              </div>

              {showMediaSection && (
                <div className="border border-zinc-150 rounded-2xl p-4 bg-zinc-50/50">
                  <div className="flex items-center justify-between mb-3.5">
                    <span className="text-xs font-bold text-zinc-700 flex items-center gap-1.5">
                      <ImageIcon className="h-4 w-4 text-zinc-500" />
                      Hình ảnh hoặc Video ({mediaUrls.length}/10)
                    </span>
                    <button
                      type="button"
                      onClick={() => setShowMediaSection(false)}
                      className="text-[10px] font-bold text-zinc-400 hover:text-zinc-650"
                    >
                      Đóng
                    </button>
                  </div>
                  
                  <div className="grid grid-cols-4 gap-3.5">
                    {mediaUrls.map((url, idx) => {
                      const isVideo = isVideoUrl(url);
                      return (
                        <div key={idx} className="relative aspect-square rounded-xl overflow-hidden border border-zinc-200 bg-zinc-950 flex items-center justify-center group/item">
                          {isVideo ? (
                            <video src={url} className="w-full h-full object-cover" />
                          ) : (
                            <img src={url} alt="" className="w-full h-full object-cover" />
                          )}
                          <button
                            type="button"
                            onClick={() => handleRemoveMedia(idx)}
                            className="absolute top-1 right-1 h-5 w-5 bg-black/60 hover:bg-black/80 rounded-full text-white flex items-center justify-center transition-colors shadow"
                          >
                            <X className="h-3 w-3" />
                          </button>
                        </div>
                      );
                    })}
                    {mediaUrls.length < 10 && (
                      <button
                        type="button"
                        onClick={() => mediaInputRef.current?.click()}
                        disabled={isUploadingMedia}
                        className="aspect-square rounded-xl border-2 border-dashed border-zinc-250 hover:border-brand-350 hover:bg-brand-50/5 flex flex-col items-center justify-center gap-1.5 transition-all text-zinc-400 hover:text-brand-600 disabled:opacity-50"
                      >
                        {isUploadingMedia ? (
                          <Loader2 className="h-5 w-5 animate-spin text-brand-500" />
                        ) : (
                          <>
                            <Plus className="h-5 w-5" />
                            <span className="text-[10px] font-bold">Thêm tệp</span>
                          </>
                        )}
                      </button>
                    )}
                  </div>
                  <input
                    ref={mediaInputRef}
                    type="file"
                    multiple
                    accept="image/*,video/*"
                    onChange={handleMediaUpload}
                    className="hidden"
                  />
                </div>
              )}

              {showProductSection && (
                <div className="border border-zinc-150 rounded-2xl p-4 bg-zinc-50/50 flex flex-col gap-3">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-zinc-700 flex items-center gap-1.5">
                      <Tag className="h-4 w-4 text-zinc-500" />
                      Gắn thẻ sản phẩm ({taggedProducts.length}/5)
                    </span>
                    <button
                      type="button"
                      onClick={() => setShowProductSection(false)}
                      className="text-[10px] font-bold text-zinc-400 hover:text-zinc-650"
                    >
                      Đóng
                    </button>
                  </div>

                  {taggedProducts.length > 0 && (
                    <div className="flex flex-wrap gap-2 pb-2 border-b border-zinc-200/60">
                      {taggedProducts.map(prod => (
                        <div key={prod.id} className="inline-flex items-center gap-1.5 bg-brand-50 border border-brand-100 px-2.5 py-1 rounded-full">
                          <span className="text-[10px] font-bold text-brand-700 truncate max-w-[120px]">{prod.name}</span>
                          <button
                            type="button"
                            onClick={() => handleUntagProduct(prod.id)}
                            className="text-brand-500 hover:text-brand-700 rounded-full h-3.5 w-3.5 flex items-center justify-center"
                          >
                            <X className="h-2.5 w-2.5" />
                          </button>
                        </div>
                      ))}
                    </div>
                  )}

                  {taggedProducts.length < 5 && (
                    <div className="relative">
                      <input
                        type="text"
                        placeholder="Tìm sản phẩm của bạn..."
                        value={productSearch}
                        onChange={(e) => {
                          setProductSearch(e.target.value);
                          setIsProductDropdownOpen(true);
                        }}
                        onFocus={() => setIsProductDropdownOpen(true)}
                        className="w-full h-9 rounded-xl border border-zinc-250 px-3 text-xs outline-none focus:border-brand-500 transition-colors"
                      />
                      {isProductDropdownOpen && (
                        <div className="absolute left-0 right-0 mt-1 max-h-48 overflow-y-auto bg-white border border-zinc-150 rounded-xl shadow-xl z-50 p-1">
                          {products
                            .filter(p => p.name.toLowerCase().includes(productSearch.toLowerCase()))
                            .filter(p => !taggedProducts.some(tp => tp.id === p.id))
                            .length === 0 ? (
                              <p className="text-[10px] text-zinc-400 text-center py-3">Không có sản phẩm nào khả dụng</p>
                            ) : (
                              products
                                .filter(p => p.name.toLowerCase().includes(productSearch.toLowerCase()))
                                .filter(p => !taggedProducts.some(tp => tp.id === p.id))
                                .map(p => (
                                  <button
                                    key={p.id}
                                    type="button"
                                    onClick={() => {
                                      handleTagProduct(p);
                                      setIsProductDropdownOpen(false);
                                    }}
                                    className="w-full text-left px-3 py-2 text-xs rounded-lg hover:bg-zinc-50 flex items-center gap-2"
                                  >
                                    <img src={p.imageUrl} alt="" className="h-6 w-6 rounded object-cover border" />
                                    <span className="font-semibold text-zinc-700 truncate flex-1">{p.name}</span>
                                    <span className="text-[10px] font-bold text-brand-600 shrink-0">{(p.price || 0).toLocaleString("vi-VN")}đ</span>
                                  </button>
                                ))
                            )}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )}

              <div className="flex items-center justify-between border border-zinc-200 rounded-2xl px-4 py-3 bg-white shadow-sm">
                <span className="text-xs font-extrabold text-zinc-755">Thêm vào bài viết của bạn</span>
                <div className="flex items-center gap-2.5">
                  <button
                    type="button"
                    onClick={() => setShowMediaSection(!showMediaSection)}
                    className={`h-8 w-8 rounded-full flex items-center justify-center transition-colors ${showMediaSection ? "bg-zinc-100 text-zinc-800" : "hover:bg-zinc-100 text-zinc-400 hover:text-zinc-650"}`}
                    title="Đính kèm ảnh/video"
                  >
                    <ImageIcon className="h-4.5 w-4.5" />
                  </button>
                  <button
                    type="button"
                    onClick={() => setShowProductSection(!showProductSection)}
                    className={`h-8 w-8 rounded-full flex items-center justify-center transition-colors ${showProductSection ? "bg-zinc-100 text-zinc-800" : "hover:bg-zinc-100 text-zinc-400 hover:text-zinc-650"}`}
                    title="Gắn thẻ sản phẩm bán hàng"
                  >
                    <Tag className="h-4.5 w-4.5" />
                  </button>
                </div>
              </div>

              <button
                type="submit"
                disabled={submittingPost}
                className="w-full h-11 bg-brand-500 hover:bg-brand-600 disabled:bg-brand-300 text-white font-bold text-sm rounded-full shadow-lg shadow-brand-500/10 hover:shadow-brand-500/20 hover:scale-[1.01] active:scale-[0.99] transition-all flex items-center justify-center gap-2"
              >
                {submittingPost ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    Đang xuất bản bài viết...
                  </>
                ) : (
                  <>
                    <Send className="h-4 w-4" />
                    Đăng bài viết ngay
                  </>
                )}
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

interface PostCardMiniProps {
  post: Post;
  currentUserId?: string;
  isAuthenticated: boolean;
  isFollowing?: boolean;
  onLikeToggle: (id: string) => void;
  onFollowToggle?: (id: string) => void;
  onAddToCart: (product: Product) => void;
}

function PostCardMini({ 
  post, 
  currentUserId,
  isAuthenticated,
  isFollowing = false,
  onLikeToggle,
  onFollowToggle,
  onAddToCart 
}: PostCardMiniProps) {
  const [activeImageIdx, setActiveImageIdx] = useState(0);
  const mediaList = post.mediaUrls || [];
  const [products, setProducts] = useState<Product[]>([]);
  const [loadingProducts, setLoadingProducts] = useState(false);
  const productCarouselRef = useRef<HTMLDivElement>(null);
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

  const scrollCarousel = (direction: "left" | "right") => {
    if (productCarouselRef.current) {
      const scrollAmt = 260;
      productCarouselRef.current.scrollBy({
        left: direction === "left" ? -scrollAmt : scrollAmt,
        behavior: "smooth"
      });
    }
  };

  const handleShare = () => {
    const postUrl = `${window.location.origin}${ROUTES.POST_DETAILS(post.id)}`;
    navigator.clipboard.writeText(postUrl);
    toast.success("Sao chép thành công", "Liên kết bài viết đã được lưu vào bộ nhớ tạm.");
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
    <div className="rounded-2xl border border-zinc-150/70 bg-white p-5 shadow-sm hover:shadow-md transition-shadow duration-300 w-full text-left">
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
            <div className="flex h-10 w-10 items-center justify-center rounded-full bg-zinc-100 text-zinc-650 font-bold text-sm">
              {post.creatorUsername.charAt(0).toUpperCase()}
            </div>
          )}
          <div className="flex flex-col">
            <span className="text-sm font-extrabold text-zinc-800 group-hover:text-brand-600 transition-colors">
              @{post.creatorUsername}
            </span>
            <div className="flex items-center gap-1 mt-0.5 text-[10px] text-zinc-400 font-medium">
              <span>{formatPostTime(post.createdAt)}</span>
              <span>·</span>
              {post.visibility === "PUBLIC" && <span title="Công khai"><Globe className="h-3 w-3 text-zinc-400" /></span>}
              {post.visibility === "FOLLOWERS" && <span title="Người theo dõi"><Users className="h-3 w-3 text-zinc-400" /></span>}
              {post.visibility === "PRIVATE" && <span title="Chỉ mình tôi"><Lock className="h-3 w-3 text-zinc-400" /></span>}
            </div>
          </div>
        </Link>
        {isAuthenticated && post.creatorId !== currentUserId && onFollowToggle && (
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
                  className="h-6 w-6 rounded-full bg-white hover:bg-zinc-50 border flex items-center justify-center text-zinc-650 transition-colors"
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
                  className={`${products.length === 1 ? 'w-full' : 'flex-shrink-0 w-60'} rounded-xl bg-white border border-zinc-150 p-2.5 flex flex-col justify-between shadow-sm hover:border-brand-200 transition-colors`}
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
      <div className="flex items-center justify-between pb-2 text-xs text-zinc-400 font-medium">
        <div className="flex items-center gap-1">
          {post.likeCount > 0 && (
            <>
              <span className="flex h-4 w-4 items-center justify-center rounded-full bg-rose-500 text-white text-[8px] shadow-sm select-none">❤️</span>
              <span className="text-zinc-550">{post.likeCount.toLocaleString("vi-VN")} lượt thích</span>
            </>
          )}
        </div>
        <div className="flex items-center gap-3">
          {post.commentCount > 0 && (
            <span className="text-zinc-500">{post.commentCount.toLocaleString("vi-VN")} bình luận</span>
          )}
        </div>
      </div>
      <div className="flex items-center pt-1 mt-1 border-t border-zinc-150">
        <button
          onClick={() => onLikeToggle(post.id)}
          className={`flex-1 flex items-center justify-center gap-2 h-9 rounded-xl text-xs font-bold transition-all hover:bg-zinc-50 select-none ${
            post.likedByMe 
              ? "text-rose-600" 
              : "text-zinc-550 hover:text-zinc-800"
          }`}
        >
          <Heart className={`h-4.5 w-4.5 transition-transform duration-200 ${post.likedByMe ? "fill-rose-600 text-rose-600 scale-105" : ""}`} />
          Thích
        </button>
        <Link
          href={ROUTES.POST_DETAILS(post.id)}
          className="flex-1 flex items-center justify-center gap-2 h-9 rounded-xl text-xs font-bold text-zinc-550 hover:text-zinc-800 hover:bg-zinc-50 transition-all"
        >
          <MessageSquare className="h-4.5 w-4.5" />
          Bình luận
        </Link>
        <button
          onClick={handleShare}
          className="flex-1 flex items-center justify-center gap-2 h-9 rounded-xl text-xs font-bold text-zinc-550 hover:text-zinc-800 hover:bg-zinc-50 transition-all"
        >
          <Share2 className="h-4.5 w-4.5" />
          Chia sẻ
        </button>
      </div>
    </div>
  );
}
