import type { Metadata } from "next";
import { Be_Vietnam_Pro } from "next/font/google";
import "./globals.css";
import { ToastProvider } from "@/context/ToastContext";
import { AuthProvider } from "@/context/AuthContext";
import { CartProvider } from "@/context/CartContext";
import { ChatProvider } from "@/context/ChatContext";
import { NotificationProvider } from "@/context/NotificationContext";
import { Header } from "@/components/common/Header";
import { Footer } from "@/components/common/Footer";
import { LayoutWrapper } from "@/components/common/LayoutWrapper";
import { GoogleOAuthProvider } from "@react-oauth/google";

const beVietnamPro = Be_Vietnam_Pro({
  weight: ["300", "400", "500", "600", "700", "800"],
  subsets: ["latin", "vietnamese"],
  variable: "--font-sans",
  display: "swap",
});

export const metadata: Metadata = {
  title: "VibeCart - Social E-Commerce & Affiliate Platform",
  description: "Next-generation platform merging social network and e-commerce for Creators and Shoppers.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const googleClientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;

  const innerContent = (
    <ToastProvider>
      <AuthProvider>
        <ChatProvider>
          <NotificationProvider>
            <CartProvider>
              <LayoutWrapper>{children}</LayoutWrapper>
            </CartProvider>
          </NotificationProvider>
        </ChatProvider>
      </AuthProvider>
    </ToastProvider>
  );

  return (
    <html
      lang="vi"
      className={`${beVietnamPro.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col bg-white text-zinc-800 transition-colors duration-300">
        {googleClientId ? (
          <GoogleOAuthProvider clientId={googleClientId}>
            {innerContent}
          </GoogleOAuthProvider>
        ) : (
          innerContent
        )}
      </body>
    </html>
  );
}


