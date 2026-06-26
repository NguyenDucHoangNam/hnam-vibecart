"use client";

import React, { useEffect } from "react";
import { usePathname } from "next/navigation";
import { Header } from "./Header";
import { Footer } from "./Footer";
const FULL_HEIGHT_ROUTES = ["/messages"];

export function LayoutWrapper({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const hideFooter = FULL_HEIGHT_ROUTES.some(route => pathname.startsWith(route));

  useEffect(() => {
    if (hideFooter) {
      document.body.style.height = "100vh";
      document.body.style.overflow = "hidden";
      document.documentElement.style.height = "100vh";
      document.documentElement.style.overflow = "hidden";
    } else {
      document.body.style.height = "";
      document.body.style.overflow = "";
      document.documentElement.style.height = "";
      document.documentElement.style.overflow = "";
    }
    return () => {
      document.body.style.height = "";
      document.body.style.overflow = "";
      document.documentElement.style.height = "";
      document.documentElement.style.overflow = "";
    };
  }, [hideFooter]);

  return (
    <>
      <Header />
      <main className={`flex-1 flex flex-col ${hideFooter ? 'overflow-hidden' : ''}`}>
        {children}
      </main>
      {!hideFooter && <Footer />}
    </>
  );
}
