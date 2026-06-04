"use client";

import React, { createContext, useContext, useState, useEffect } from "react";
import { CartItem, Product, ProductVariant } from "@/types";
import { useAuth } from "@/hooks/useAuth";
import { cartService } from "@/services/cart.service";
import { useToast } from "@/context/ToastContext";

interface CartContextType {
  items: CartItem[];
  totalOriginalAmount: number;
  totalDiscountAmount: number;
  totalSavingAmount: number;
  totalQuantity: number;
  isLoading: boolean;
  addToCart: (variant: ProductVariant, product: Product, quantity?: number) => Promise<void>;
  updateQuantity: (variantId: string, quantity: number) => Promise<void>;
  removeFromCart: (variantId: string) => Promise<void>;
  clearCart: () => Promise<void>;
  fetchCart: () => Promise<void>;
}

const CartContext = createContext<CartContextType | undefined>(undefined);

export function CartProvider({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, user } = useAuth();
  const toast = useToast();
  const [items, setItems] = useState<CartItem[]>([]);
  const [totalOriginalAmount, setTotalOriginalAmount] = useState(0);
  const [totalDiscountAmount, setTotalDiscountAmount] = useState(0);
  const [totalSavingAmount, setTotalSavingAmount] = useState(0);
  const [isLoading, setIsLoading] = useState(true);

  // Derive total quantity
  const totalQuantity = items.reduce((acc, item) => acc + item.quantity, 0);

  // Fetch Cart from Backend or Local Storage
  const fetchCart = async () => {
    if (isAuthenticated) {
      try {
        const backendCart = await cartService.getCart();
        setItems(backendCart.items || []);
        setTotalOriginalAmount(Number(backendCart.totalOriginalAmount || 0));
        setTotalDiscountAmount(Number(backendCart.totalDiscountAmount || 0));
        setTotalSavingAmount(Number(backendCart.totalSavingAmount || 0));
      } catch (err) {
        console.error("Cart Context: Failed to fetch backend cart", err);
        setItems([]);
      }
    } else {
      // Guest: Load from Local Storage
      try {
        const localItemsJson = localStorage.getItem("local_cart");
        if (localItemsJson) {
          const localItems: CartItem[] = JSON.parse(localItemsJson);
          setItems(localItems);

          // Re-calculate guest totals locally
          const original = localItems.reduce((acc, item) => acc + item.originalPrice * item.quantity, 0);
          const discountPriceSum = localItems.reduce((acc, item) => acc + (item.discountPrice || item.originalPrice) * item.quantity, 0);
          setTotalOriginalAmount(original);
          setTotalDiscountAmount(discountPriceSum);
          setTotalSavingAmount(original - discountPriceSum);
        } else {
          setItems([]);
          setTotalOriginalAmount(0);
          setTotalDiscountAmount(0);
          setTotalSavingAmount(0);
        }
      } catch (err) {
        console.error("Cart Context: Failed to load local cart", err);
        setItems([]);
      }
    }
  };

  // Merge Guest Cart to Backend upon Login
  useEffect(() => {
    async function initCart() {
      setIsLoading(true);
      if (isAuthenticated) {
        try {
          const localItemsJson = localStorage.getItem("local_cart");
          if (localItemsJson) {
            const localItems: CartItem[] = JSON.parse(localItemsJson);
            if (localItems.length > 0) {
              // Convert to merge format
              const mergePayload = localItems.map((item) => ({
                variantId: item.variantId,
                quantity: item.quantity,
              }));

              toast.info("Đang đồng bộ", "Đang đồng bộ giỏ hàng của bạn...");
              await cartService.mergeCart(mergePayload);
              localStorage.removeItem("local_cart");
              toast.success("Đồng bộ thành công", "Giỏ hàng của bạn đã được gộp thành công.");
            }
          }
        } catch (err) {
          console.error("Cart Context: Failed to merge carts", err);
        }
      }
      await fetchCart();
      setIsLoading(false);
    }

    initCart();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated, user]);

  // Add Item to Cart
  const addToCart = async (variant: ProductVariant, product: Product, quantity = 1) => {
    if (quantity <= 0) return;

    // Check available stock
    if (variant.availableStock < quantity) {
      toast.warning("Hết hàng", `Không thể thêm. Tồn kho khả dụng chỉ còn ${variant.availableStock} sản phẩm.`);
      return;
    }

    if (isAuthenticated) {
      try {
        await cartService.addItem(variant.id, quantity);
        await fetchCart();
        toast.success("Đã thêm", `Đã thêm biến thể "${variant.variantName}" của "${product.name}" vào giỏ hàng.`);
      } catch (err: any) {
        console.error("Cart Context: Failed to add item to backend cart", err);
        toast.error("Lỗi thêm vào giỏ", err?.message || "Không thể thêm sản phẩm vào giỏ hàng.");
      }
    } else {
      // Guest Storage Logic
      setItems((prevItems) => {
        const existingIndex = prevItems.findIndex((item) => item.variantId === variant.id);
        let newItems = [...prevItems];

        if (existingIndex > -1) {
          let newQty = newItems[existingIndex].quantity + quantity;
          if (newQty > 100) {
            toast.warning("Giới hạn số lượng", "Số lượng tối đa cho mỗi sản phẩm trong giỏ hàng là 100.");
            return prevItems;
          }
          if (newQty > variant.availableStock) {
            toast.warning("Tồn kho không đủ", `Số lượng vượt quá tồn kho khả dụng (${variant.availableStock}).`);
            newQty = variant.availableStock;
          }

          newItems[existingIndex].quantity = newQty;

          // Recompute status for guest
          let status: "AVAILABLE" | "OUT_OF_STOCK" | "INSUFFICIENT_STOCK" = "AVAILABLE";
          if (variant.availableStock <= 0) {
            status = "OUT_OF_STOCK";
          } else if (variant.availableStock < newQty) {
            status = "INSUFFICIENT_STOCK";
          }
          newItems[existingIndex].status = status;
        } else {
          // Find thumbnail if any
          const thumbnail = product.images?.find((img) => img.isThumbnail)?.imageUrl || product.images?.[0]?.imageUrl || "";

          // Determine status for guest item
          let status: "AVAILABLE" | "OUT_OF_STOCK" | "INSUFFICIENT_STOCK" = "AVAILABLE";
          if (variant.availableStock <= 0) {
            status = "OUT_OF_STOCK";
          } else if (variant.availableStock < quantity) {
            status = "INSUFFICIENT_STOCK";
          }

          newItems.push({
            variantId: variant.id,
            spuId: product.id,
            productName: product.name,
            variantName: variant.variantName,
            thumbnailUrl: thumbnail,
            creatorId: product.creatorId,
            creatorName: product.creatorName || "Creator",
            quantity,
            originalPrice: variant.price,
            discountPrice: variant.discountPrice,
            availableStock: variant.availableStock,
            status,
          });
        }
        localStorage.setItem("local_cart", JSON.stringify(newItems));

        // Recompute local guest totals
        const original = newItems.reduce((acc, item) => acc + item.originalPrice * item.quantity, 0);
        const discountPriceSum = newItems.reduce((acc, item) => acc + (item.discountPrice || item.originalPrice) * item.quantity, 0);
        setTotalOriginalAmount(original);
        setTotalDiscountAmount(discountPriceSum);
        setTotalSavingAmount(original - discountPriceSum);

        toast.success("Đã thêm", `Đã thêm "${variant.variantName}" vào giỏ hàng (Chế độ Guest).`);
        return newItems;
      });
    }
  };

  // Update Item Quantity
  const updateQuantity = async (variantId: string, quantity: number) => {
    if (quantity <= 0) {
      await removeFromCart(variantId);
      return;
    }

    if (quantity > 100) {
      toast.warning("Giới hạn số lượng", "Số lượng tối đa cho mỗi biến thể trong giỏ hàng là 100.");
      return;
    }

    if (isAuthenticated) {
      try {
        await cartService.updateQuantity(variantId, quantity);
        await fetchCart();
      } catch (err: any) {
        console.error("Cart Context: Failed to update quantity on backend", err);
        // Force reload cart from backend to sync
        await fetchCart();
        toast.error("Lỗi cập nhật", err?.message || "Không thể cập nhật số lượng tồn kho khả dụng.");
      }
    } else {
      // Guest Update Quantity
      setItems((prevItems) => {
        const item = prevItems.find((i) => i.variantId === variantId);
        if (!item) return prevItems;

        if (quantity > item.availableStock) {
          toast.warning("Tồn kho không đủ", `Điều chỉnh về tồn kho khả dụng tối đa: ${item.availableStock}`);
          quantity = item.availableStock;
        }

        const newItems = prevItems.map((i) => {
          if (i.variantId === variantId) {
            let status: "AVAILABLE" | "OUT_OF_STOCK" | "INSUFFICIENT_STOCK" = "AVAILABLE";
            if (i.availableStock <= 0) {
              status = "OUT_OF_STOCK";
            } else if (i.availableStock < quantity) {
              status = "INSUFFICIENT_STOCK";
            }
            return { ...i, quantity, status };
          }
          return i;
        });
        localStorage.setItem("local_cart", JSON.stringify(newItems));

        // Recompute local guest totals
        const original = newItems.reduce((acc, item) => acc + item.originalPrice * item.quantity, 0);
        const discountPriceSum = newItems.reduce((acc, item) => acc + (item.discountPrice || item.originalPrice) * item.quantity, 0);
        setTotalOriginalAmount(original);
        setTotalDiscountAmount(discountPriceSum);
        setTotalSavingAmount(original - discountPriceSum);

        return newItems;
      });
    }
  };

  // Remove Item from Cart
  const removeFromCart = async (variantId: string) => {
    if (isAuthenticated) {
      try {
        await cartService.removeItem(variantId);
        await fetchCart();
        toast.success("Đã xóa", "Đã loại bỏ sản phẩm khỏi giỏ hàng.");
      } catch (err: any) {
        console.error("Cart Context: Failed to delete item on backend", err);
        toast.error("Lỗi xóa sản phẩm", "Không thể xóa sản phẩm khỏi giỏ hàng.");
      }
    } else {
      // Guest Remove Item
      setItems((prevItems) => {
        const newItems = prevItems.filter((item) => item.variantId !== variantId);
        localStorage.setItem("local_cart", JSON.stringify(newItems));

        // Recompute local guest totals
        const original = newItems.reduce((acc, item) => acc + item.originalPrice * item.quantity, 0);
        const discountPriceSum = newItems.reduce((acc, item) => acc + (item.discountPrice || item.originalPrice) * item.quantity, 0);
        setTotalOriginalAmount(original);
        setTotalDiscountAmount(discountPriceSum);
        setTotalSavingAmount(original - discountPriceSum);

        toast.success("Đã xóa", "Đã loại bỏ sản phẩm khỏi giỏ hàng.");
        return newItems;
      });
    }
  };

  // Clear Cart
  const clearCart = async () => {
    if (isAuthenticated) {
      try {
        await cartService.clearCart();
        setItems([]);
        setTotalOriginalAmount(0);
        setTotalDiscountAmount(0);
        setTotalSavingAmount(0);
      } catch (err) {
        console.error("Cart Context: Failed to clear cart", err);
      }
    } else {
      setItems([]);
      setTotalOriginalAmount(0);
      setTotalDiscountAmount(0);
      setTotalSavingAmount(0);
      localStorage.removeItem("local_cart");
    }
  };

  return (
    <CartContext.Provider
      value={{
        items,
        totalOriginalAmount,
        totalDiscountAmount,
        totalSavingAmount,
        totalQuantity,
        isLoading,
        addToCart,
        updateQuantity,
        removeFromCart,
        clearCart,
        fetchCart,
      }}
    >
      {children}
    </CartContext.Provider>
  );
}

export function useCart() {
  const context = useContext(CartContext);
  if (context === undefined) {
    throw new Error("useCart must be used within a CartProvider");
  }
  return context;
}
