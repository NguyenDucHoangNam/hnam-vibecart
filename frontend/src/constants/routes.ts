export const ROUTES = {
  HOME: "/",
  LOGIN: "/login",
  REGISTER: "/register",
  VERIFY_OTP: "/verify-otp",
  FORGOT_PASSWORD: "/forgot-password",
  RESET_PASSWORD: "/reset-password",
  PROFILE: "/profile",
  PRODUCTS: "/products",
  PRODUCT_DETAILS: (id: string) => `/products/${id}`,
  CART: "/cart",
  CHECKOUT: "/checkout",
  ORDERS: "/orders",
  ORDER_DETAILS: (id: string) => `/orders/${id}`,
  
  // Social
  FEED: "/feed",
  POST_DETAILS: (id: string) => `/feed/posts/${id}`,
  CREATOR_PROFILE: (id: string) => `/creators/${id}`,
  
  // Affiliate / Creator dashboard
  CREATOR_DASHBOARD: "/creator/dashboard",
  AFFILIATE_LINKS: "/creator/affiliate-links",
  
  // Chat
  MESSAGES: "/messages",
  CHAT_ROOM: (id: string) => `/messages/${id}`,
  
  
  // Admin
  ADMIN: "/admin",
  ADMIN_PRODUCTS: "/admin/products",
  ADMIN_CATEGORIES: "/admin/categories",
  ADMIN_ORDERS: "/admin/orders",
  ADMIN_USERS: "/admin/users",
};
