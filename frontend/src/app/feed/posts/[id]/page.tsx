"use client";

import React, { useState, useEffect, useCallback } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { 
  Heart, 
  MessageSquare, 
  Share2, 
  ChevronLeft, 
  ShoppingBag, 
  Tag, 
  Trash2, 
  UserPlus, 
  CornerDownRight, 
  Send, 
  Loader2, 
  ChevronRight,
  Reply,
  Calendar,
  X
} from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useCart } from "@/hooks/useCart";
import { useToast } from "@/context/ToastContext";
import { postService, PageResponse } from "@/services/post.service";
import { userService } from "@/services/user.service";
import { productService } from "@/services/product.service";
import { Post, Comment, Product } from "@/types";
import { ROUTES } from "@/constants/routes";
import { PostSkeleton } from "@/components/skeletons/LoadingSkeletons";


export default function PostDetailPage() {
  const params = useParams();
  const router = useRouter();
  const postId = params.id as string;

  const { user, isAuthenticated } = useAuth();
  const { addToCart } = useCart();
  const toast = useToast();
  const [post, setPost] = useState<Post | null>(null);
  const [comments, setComments] = useState<Comment[]>([]);
  const [loading, setLoading] = useState(true);
  const [submittingComment, setSubmittingComment] = useState(false);
  const [rootCommentText, setRootCommentText] = useState("");
  const [products, setProducts] = useState<Product[]>([]);
  const [activeImageIdx, setActiveImageIdx] = useState(0);
  const [variantPickerProduct, setVariantPickerProduct] = useState<Product | null>(null);
  const loadPostDetails = useCallback(async () => {
    setLoading(true);
    try {
      const postData = await postService.getPostById(postId);
      setPost(postData);
      const commentsRes = await postService.getComments(postId, 0, 100);
      if (commentsRes && commentsRes.content) {
        setComments(commentsRes.content);
      }
    } catch (err: any) {
      console.error("Lỗi khi tải chi tiết bài viết:", err);
      toast.error("Không tìm thấy bài viết", "Bài viết có thể đã bị xóa hoặc liên kết không chính xác.");
      router.push(ROUTES.FEED);
    } finally {
      setLoading(false);
    }
  }, [postId, router, toast]);

  useEffect(() => {
    loadPostDetails();
  }, [loadPostDetails]);
  useEffect(() => {
    if (!post || !post.taggedProductIds || post.taggedProductIds.length === 0) return;
    
    const productIds = post.taggedProductIds;
    
    async function fetchProducts() {
      try {
        const fetched = await Promise.all(
          productIds.map(async (id) => {
            try {
              return await productService.getProductById(id);
            } catch {
              return null;
            }
          })
        );
        setProducts(fetched.filter((p): p is Product => p !== null));
      } catch (err) {
        console.error("Lỗi tải sản phẩm gắn thẻ:", err);
      }
    }

    fetchProducts();
  }, [post]);
  const handleLikeToggle = async () => {
    if (!isAuthenticated || !post) {
      toast.warning("Yêu cầu đăng nhập", "Vui lòng đăng nhập để thích bài viết.");
      return;
    }

    setPost(prev => {
      if (!prev) return null;
      return {
        ...prev,
        likedByMe: !prev.likedByMe,
        likeCount: prev.likedByMe ? prev.likeCount - 1 : prev.likeCount + 1
      };
    });

    try {
      await postService.toggleLike(postId);
    } catch (err: any) {
      setPost(prev => {
        if (!prev) return null;
        return {
          ...prev,
          likedByMe: !prev.likedByMe,
          likeCount: prev.likedByMe ? prev.likeCount + 1 : prev.likeCount - 1
        };
      });
      toast.error("Lỗi tương tác", "Không thể cập nhật lượt thích.");
    }
  };
  const handleShare = () => {
    if (!post) return;
    const postUrl = window.location.href;
    navigator.clipboard.writeText(postUrl);
    toast.success("Đã sao chép liên kết", "Gửi link này cho bạn bè để chia sẻ bài viết!");
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
  const handleAddRootComment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isAuthenticated) {
      toast.warning("Yêu cầu đăng nhập", "Vui lòng đăng nhập để bình luận.");
      return;
    }
    if (!rootCommentText.trim()) return;

    setSubmittingComment(true);
    try {
      const newComment = await postService.addComment(postId, rootCommentText.trim());
      setComments(prev => [newComment, ...prev]);
      setRootCommentText("");
      setPost(prev => {
        if (!prev) return null;
        return { ...prev, commentCount: prev.commentCount + 1 };
      });
      
      toast.success("Bình luận thành công", "Bình luận của bạn đã được đăng.");
    } catch (err: any) {
      toast.error("Lỗi đăng bình luận", err?.message || "Bình luận chứa từ ngữ thô tục nhạy cảm bị từ chối.");
    } finally {
      setSubmittingComment(false);
    }
  };
  const handleAddReply = async (parentId: string, content: string) => {
    try {
      const newReply = await postService.addComment(postId, content, parentId);
      const insertReply = (list: Comment[]): Comment[] => {
        return list.map(item => {
          if (item.id === parentId) {
            return {
              ...item,
              replies: [newReply, ...(item.replies || [])]
            };
          } else if (item.replies && item.replies.length > 0) {
            return {
              ...item,
              replies: insertReply(item.replies)
            };
          }
          return item;
        });
      };

      setComments(prev => insertReply(prev));
      setPost(prev => {
        if (!prev) return null;
        return { ...prev, commentCount: prev.commentCount + 1 };
      });

      toast.success("Trả lời thành công");
    } catch (err: any) {
      toast.error("Lỗi trả lời", err?.message || "Nội dung vi phạm bộ lọc từ ngữ cấm.");
      throw err;
    }
  };
  const handleDeleteComment = async (commentId: string) => {
    if (!window.confirm("Bạn có chắc chắn muốn xóa bình luận này không?")) return;

    try {
      await postService.deleteComment(postId, commentId);
      toast.success("Đã xóa bình luận");
      const filterComments = (list: Comment[]): Comment[] => {
        return list
          .filter(item => item.id !== commentId)
          .map(item => {
            if (item.replies && item.replies.length > 0) {
              return {
                ...item,
                replies: filterComments(item.replies)
              };
            }
            return item;
          });
      };

      setComments(prev => filterComments(prev));
      setPost(prev => {
        if (!prev) return null;
        return { ...prev, commentCount: Math.max(0, prev.commentCount - 1) };
      });
    } catch (err: any) {
      toast.error("Lỗi xóa bình luận", err?.message || "Bạn không có quyền thực hiện tác vụ này.");
    }
  };
  const handleDeletePost = async () => {
    if (!post) return;
    if (!window.confirm("Bạn có chắc chắn muốn xóa vĩnh viễn bài đăng này không?")) return;

    try {
      await postService.deletePost(postId);
      toast.success("Đã xóa bài đăng thành công");
      router.push(ROUTES.FEED);
    } catch (err: any) {
      toast.error("Lỗi xóa bài đăng", err?.message || "Không có quyền xóa bài đăng.");
    }
  };
  const formatPostTime = (dateStr: string) => {
    const d = new Date(dateStr);
    return d.toLocaleDateString("vi-VN", { 
      day: "2-digit", 
      month: "2-digit", 
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit"
    });
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-zinc-50/50 py-8 px-4 sm:px-6 lg:px-8">
        <div className="mx-auto max-w-6xl mb-6">
          <div className="h-4 w-32 rounded bg-zinc-200 animate-pulse" />
        </div>
        <div className="mx-auto max-w-6xl grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          <div className="lg:col-span-7 flex flex-col gap-6">
            <PostSkeleton />
          </div>
          <div className="lg:col-span-5 bg-white rounded-2xl border border-zinc-150 p-5 space-y-4 animate-pulse">
            <div className="h-5 w-28 rounded bg-zinc-200" />
            <div className="h-10 w-full rounded-xl bg-zinc-100" />
            <div className="space-y-3 pt-4 border-t border-zinc-150">
              {Array.from({ length: 3 }).map((_, i) => (
                <div key={i} className="flex gap-3">
                  <div className="h-8 w-8 rounded-full bg-zinc-200 shrink-0" />
                  <div className="flex-1 space-y-1.5">
                    <div className="h-3 w-20 rounded bg-zinc-200" />
                    <div className="h-3.5 w-full rounded bg-zinc-150" />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (!post) return null;

  const mediaList = post.mediaUrls || [];

  return (
    <div className="min-h-screen bg-zinc-50/50 py-8 px-4 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-6xl mb-6">
        <Link 
          href={ROUTES.FEED}
          className="inline-flex items-center gap-1.5 text-sm font-semibold text-zinc-600 hover:text-brand-600 transition-colors"
        >
          <ChevronLeft className="h-4.5 w-4.5" />
          Quay lại Bảng tin
        </Link>
      </div>
      <div className="mx-auto max-w-6xl grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        <div className="lg:col-span-7 flex flex-col gap-6">
          <div className="rounded-3xl border border-zinc-150/70 bg-white p-6 shadow-sm">
            <div className="flex items-center justify-between gap-3 mb-5">
              <Link href={ROUTES.CREATOR_PROFILE(post.creatorId)} className="flex items-center gap-3 group">
                {post.creatorAvatarUrl ? (
                  <img 
                    src={post.creatorAvatarUrl} 
                    alt={post.creatorUsername} 
                    className="h-11 w-11 rounded-full object-cover ring-2 ring-brand-50" 
                    onError={(e) => {
                      e.currentTarget.src = `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(post.creatorUsername)}`;
                    }}
                  />
                ) : (
                  <div className="flex h-11 w-11 items-center justify-center rounded-full bg-zinc-100 text-zinc-600 font-bold text-sm">
                    {post.creatorUsername.charAt(0).toUpperCase()}
                  </div>
                )}
                <div className="flex flex-col">
                  <span className="text-sm font-extrabold text-zinc-800 group-hover:text-brand-600 transition-colors">
                    @{post.creatorUsername}
                  </span>
                  <span className="text-[10px] text-zinc-400 font-medium flex items-center gap-1">
                    <Calendar className="h-3 w-3" />
                    {formatPostTime(post.createdAt)}
                  </span>
                </div>
              </Link>
              {(user?.id === post.creatorId || user?.roles?.includes("ROLE_ADMIN")) && (
                <button
                  onClick={handleDeletePost}
                  className="h-9 w-9 rounded-full bg-rose-50 hover:bg-rose-100 text-rose-600 flex items-center justify-center transition-colors shadow-sm"
                  title="Xóa bài đăng sáng tạo"
                >
                  <Trash2 className="h-4.5 w-4.5" />
                </button>
              )}
            </div>
            <p className="text-sm sm:text-base text-zinc-700 leading-relaxed whitespace-pre-line mb-6 font-light">
              {post.content}
            </p>
            {mediaList.length > 0 && (() => {
              const currentMedia = mediaList[activeImageIdx];
              const isVideo = currentMedia.match(/\.(mp4|webm|ogg|mov)$/i) || currentMedia.includes("video") || currentMedia.includes("mp4");
              return (
              <div className="relative rounded-2xl overflow-hidden bg-zinc-950 mb-6 aspect-[4/3] sm:aspect-[16/9] flex items-center justify-center group/slider">
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
                    alt={`Hình ảnh bài đăng ${activeImageIdx + 1}`} 
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
            {products.length > 0 && (
              <div className="border border-brand-100/50 bg-brand-50/10 rounded-2xl p-5 mb-6">
                <h3 className="text-xs font-extrabold text-brand-600 uppercase tracking-wider mb-4 flex items-center gap-1.5">
                  <Tag className="h-4 w-4" />
                  Sản phẩm trong bài đăng
                </h3>
                <div className="flex flex-col gap-3">
                  {products.map((product) => (
                    <div 
                      key={product.id}
                      className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 p-3 bg-white border border-zinc-150 rounded-xl hover:border-brand-200 transition-colors shadow-sm"
                    >
                      <Link href={ROUTES.PRODUCT_DETAILS(product.id)} className="flex gap-3 items-center group flex-1">
                        <img src={product.images?.find(i => i.isThumbnail)?.imageUrl || product.images?.[0]?.imageUrl || ''} alt={product.name} className="h-14 w-14 rounded-lg object-cover border" />
                        <div className="min-w-0">
                          <h4 className="text-xs sm:text-sm font-bold text-zinc-800 line-clamp-1 group-hover:text-brand-600 transition-colors">
                            {product.name}
                          </h4>
                          <p className="text-xs text-zinc-400 mt-0.5 line-clamp-1">{product.categoryName || product.category}</p>
                          <span className="text-xs font-extrabold text-brand-600 mt-1 block">
                            {(product.variants?.[0]?.discountPrice || product.variants?.[0]?.price || product.price || 0).toLocaleString("vi-VN")}đ
                          </span>
                        </div>
                      </Link>
                      
                      <div className="flex gap-2 w-full sm:w-auto border-t sm:border-t-0 pt-2 sm:pt-0">
                        <Link
                          href={ROUTES.PRODUCT_DETAILS(product.id)}
                          className="flex-1 sm:flex-none h-9 px-4 rounded-xl border border-brand-200 text-brand-600 hover:bg-brand-50 text-xs font-bold transition-all flex items-center justify-center"
                        >
                          Chi tiết
                        </Link>
                        <button
                          onClick={() => handleAddProductToCart(product)}
                          className="h-9 px-4 rounded-xl bg-brand-500 hover:bg-brand-600 text-white flex items-center justify-center gap-1.5 text-xs font-bold transition-colors flex-1 sm:flex-none"
                        >
                          <ShoppingBag className="h-4 w-4" />
                          Thêm giỏ hàng
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
            <div className="flex items-center justify-between pt-4 border-t border-zinc-100 text-zinc-500">
              <button
                onClick={handleLikeToggle}
                className={`flex items-center gap-1.5 text-xs font-extrabold px-4 py-2 rounded-full transition-all ${
                  post.likedByMe 
                    ? "text-rose-600 bg-rose-50 hover:bg-rose-100" 
                    : "text-zinc-500 hover:text-rose-500 hover:bg-zinc-50"
                }`}
              >
                <Heart className={`h-4 w-4 ${post.likedByMe ? "fill-rose-600" : ""}`} />
                {post.likeCount.toLocaleString("vi-VN")} lượt thích
              </button>

              <div className="flex items-center gap-1.5 text-xs font-bold px-4 py-2">
                <MessageSquare className="h-4 w-4 text-brand-400" />
                {post.commentCount.toLocaleString("vi-VN")} bình luận
              </div>

              <button
                onClick={handleShare}
                className="flex items-center gap-1.5 text-xs font-extrabold text-zinc-500 hover:text-brand-500 px-4 py-2 rounded-full hover:bg-zinc-50 transition-all"
              >
                <Share2 className="h-4 w-4" />
                Chia sẻ
              </button>
            </div>

          </div>
        </div>
        <div className="lg:col-span-5 flex flex-col gap-6 sticky top-20">
          <div className="rounded-3xl border border-zinc-150/70 bg-white p-6 shadow-sm flex flex-col max-h-[85vh]">
            <h2 className="text-base font-extrabold text-zinc-800 pb-3.5 border-b border-zinc-100 tracking-wider">
              BÌNH LUẬN ({post.commentCount.toLocaleString("vi-VN")})
            </h2>
            <div className="flex-1 overflow-y-auto no-scrollbar py-4 flex flex-col gap-5">
              {comments.length === 0 ? (
                <div className="text-center py-12 flex flex-col items-center">
                  <div className="h-12 w-12 bg-zinc-50 rounded-full flex items-center justify-center text-zinc-400 mb-3">
                    <MessageSquare className="h-5 w-5" />
                  </div>
                  <h4 className="text-xs font-bold text-zinc-700">Chưa có bình luận</h4>
                  <p className="text-[10px] text-zinc-400 mt-0.5">Trở thành người đầu tiên tương tác bài viết!</p>
                </div>
              ) : (
                comments.map((comment) => (
                  <CommentCard 
                    key={comment.id}
                    comment={comment}
                    postId={postId}
                    currentUserId={user?.id}
                    postAuthorId={post.creatorId}
                    isAdmin={user?.roles?.includes("ROLE_ADMIN") || false}
                    isAuthenticated={isAuthenticated}
                    onReplySubmit={handleAddReply}
                    onDelete={handleDeleteComment}
                  />
                ))
              )}
            </div>
            <div className="pt-4 border-t border-zinc-100">
              {isAuthenticated ? (
                <form onSubmit={handleAddRootComment} className="flex gap-2">
                  <input
                    type="text"
                    value={rootCommentText}
                    onChange={(e) => setRootCommentText(e.target.value)}
                    placeholder="Viết bình luận của bạn..."
                    className="flex-1 h-10 rounded-xl bg-zinc-50 border border-zinc-200/80 px-4 text-xs outline-none focus:border-brand-400 focus:bg-white focus:ring-1 focus:ring-brand-200/50"
                  />
                  <button
                    type="submit"
                    disabled={submittingComment || !rootCommentText.trim()}
                    className="h-10 w-10 rounded-xl bg-brand-500 hover:bg-brand-600 disabled:opacity-40 text-white flex items-center justify-center transition-colors shadow-sm active:scale-95"
                  >
                    {submittingComment ? (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    ) : (
                      <Send className="h-4 w-4" />
                    )}
                  </button>
                </form>
              ) : (
                <div className="bg-zinc-50 border rounded-xl p-3 text-center text-xs text-zinc-500">
                  Vui lòng{" "}
                  <Link href={ROUTES.LOGIN} className="font-bold text-brand-600 hover:underline">
                    Đăng nhập
                  </Link>{" "}
                  để viết bình luận bài viết này.
                </div>
              )}
            </div>

          </div>
        </div>

      </div>
      {variantPickerProduct && (
        <div className="fixed inset-0 z-[60] flex items-end sm:items-center justify-center px-4 pb-4 bg-zinc-900/60 backdrop-blur-sm animate-fade-in">
          <div className="bg-white rounded-2xl sm:rounded-3xl w-full max-w-md overflow-hidden shadow-2xl animate-scale-up border border-zinc-100">
            <div className="flex items-center justify-between px-5 py-4 border-b border-zinc-100">
              <h3 className="text-sm font-extrabold text-zinc-800 flex items-center gap-2">
                <ShoppingBag className="h-4 w-4 text-brand-500" />
                Chọn phân loại sản phẩm
              </h3>
              <button onClick={() => setVariantPickerProduct(null)} className="h-8 w-8 rounded-full hover:bg-zinc-100 text-zinc-400 hover:text-zinc-600 flex items-center justify-center transition-colors">
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
interface CommentCardProps {
  comment: Comment;
  postId: string;
  currentUserId?: string;
  postAuthorId: string;
  isAdmin: boolean;
  isAuthenticated: boolean;
  depth?: number;
  onReplySubmit: (parentId: string, content: string) => Promise<void>;
  onDelete: (commentId: string) => void;
}

function CommentCard({
  comment,
  postId,
  currentUserId,
  postAuthorId,
  isAdmin,
  isAuthenticated,
  depth = 0,
  onReplySubmit,
  onDelete
}: CommentCardProps) {
  const [showReplyInput, setShowReplyInput] = useState(false);
  const [replyText, setReplyText] = useState("");
  const [submittingReply, setSubmittingReply] = useState(false);
  const displayDepth = Math.min(depth, 2);
  const canDelete = currentUserId === comment.userId || currentUserId === postAuthorId || isAdmin;
  const formatTime = (dateStr: string) => {
    const d = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - d.getTime();
    const diffMin = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMin / 60);

    if (diffMin < 1) return "Vừa xong";
    if (diffMin < 60) return `${diffMin} phút trước`;
    if (diffHours < 24) return `${diffHours} giờ trước`;
    return d.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" });
  };

  const handleReplyClick = () => {
    if (!isAuthenticated) {
      alert("Bạn cần đăng nhập để trả lời bình luận này!");
      return;
    }
    setShowReplyInput(!showReplyInput);
  };

  const handleReplySubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!replyText.trim()) return;

    setSubmittingReply(true);
    try {
      await onReplySubmit(comment.id, replyText.trim());
      setReplyText("");
      setShowReplyInput(false);
    } catch {
    } finally {
      setSubmittingReply(false);
    }
  };

  return (
    <div 
      className="flex flex-col gap-2"
      style={{ marginLeft: `${displayDepth * 24}px` }}
    >
      <div className="flex gap-2.5 items-start group">
        {depth > 0 && (
          <div className="text-zinc-300 mt-1 flex-shrink-0">
            <CornerDownRight className="h-4 w-4" />
          </div>
        )}
        {comment.userAvatarUrl ? (
          <img 
            src={comment.userAvatarUrl} 
            alt={comment.username} 
            className="h-7 w-7 rounded-full object-cover border" 
            onError={(e) => {
              e.currentTarget.src = `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(comment.username)}`;
            }}
          />
        ) : (
          <div className="flex h-7 w-7 items-center justify-center rounded-full bg-zinc-100 text-zinc-600 font-bold text-xs">
            {comment.username.charAt(0).toUpperCase()}
          </div>
        )}
        <div className="flex-1 min-w-0 bg-zinc-50 border border-zinc-100 rounded-2xl p-3">
          
          <div className="flex items-center justify-between gap-2 mb-0.5">
            <div className="flex items-center gap-1.5 flex-wrap">
              <span className="text-xs font-bold text-zinc-800">
                @{comment.username}
              </span>
              {comment.userId === postAuthorId && (
                <span className="text-[8px] bg-brand-100 text-brand-700 px-1.5 py-0.2 rounded-full font-bold uppercase tracking-wider">
                  Tác giả
                </span>
              )}
            </div>
            <span className="text-[9px] text-zinc-400 font-medium">
              {formatTime(comment.createdAt)}
            </span>

          </div>
          <p className="text-xs text-zinc-650 leading-relaxed font-light">
            {comment.content}
          </p>
          <div className="flex items-center gap-3 mt-2 text-[10px] text-zinc-400 font-semibold border-t border-zinc-200/40 pt-1.5">
            <button
              onClick={handleReplyClick}
              className="flex items-center gap-1 hover:text-brand-600 transition-colors"
            >
              <Reply className="h-3 w-3" />
              Phản hồi
            </button>
            {canDelete && (
              <button
                onClick={() => onDelete(comment.id)}
                className="flex items-center gap-1 text-rose-400 hover:text-rose-600 hover:bg-rose-50 px-1.5 py-0.5 rounded transition-colors ml-auto opacity-0 group-hover:opacity-100 focus:opacity-100"
              >
                <Trash2 className="h-3 w-3" />
                Xóa
              </button>
            )}

          </div>

        </div>

      </div>
      {showReplyInput && (
        <form 
          onSubmit={handleReplySubmit} 
          className="flex gap-2 items-center mt-1"
          style={{ marginLeft: `${(displayDepth + 1) * 12}px` }}
        >
          <div className="text-zinc-300">
            <CornerDownRight className="h-3.5 w-3.5" />
          </div>
          <input
            type="text"
            value={replyText}
            onChange={(e) => setReplyText(e.target.value)}
            placeholder={`Trả lời @${comment.username}...`}
            className="flex-1 h-8 rounded-lg bg-white border border-zinc-200 px-3 text-[11px] outline-none focus:border-brand-400"
            autoFocus
          />
          <button
            type="submit"
            disabled={submittingReply || !replyText.trim()}
            className="h-8 px-3 rounded-lg bg-brand-500 text-white font-bold text-[10px] hover:bg-brand-600 transition-colors disabled:opacity-40"
          >
            {submittingReply ? "..." : "Gửi"}
          </button>
          <button
            type="button"
            onClick={() => setShowReplyInput(false)}
            className="h-8 px-2.5 rounded-lg border border-zinc-200 text-zinc-400 hover:bg-zinc-50 text-[10px] transition-colors"
          >
            Hủy
          </button>
        </form>
      )}
      {comment.replies && comment.replies.length > 0 && (
        <div className="flex flex-col gap-2 mt-1">
          {comment.replies.map(reply => (
            <CommentCard 
              key={reply.id}
              comment={reply}
              postId={postId}
              currentUserId={currentUserId}
              postAuthorId={postAuthorId}
              isAdmin={isAdmin}
              isAuthenticated={isAuthenticated}
              depth={depth + 1}
              onReplySubmit={onReplySubmit}
              onDelete={onDelete}
            />
          ))}
        </div>
      )}

    </div>
  );
}
