"use client";

import React, { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";
import { ROUTES } from "@/constants/routes";
import RegisterForm from "@/components/auth/RegisterForm";

export default function RegisterPage() {
  const { isAuthenticated, isLoading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      router.replace(ROUTES.HOME);
    }
  }, [isAuthenticated, isLoading, router]);

  if (isLoading || isAuthenticated) {
    return null;
  }

  return (
    <div
      className="flex-1 flex items-center justify-center min-h-[calc(100vh-76px-80px)] px-4 py-12"
      style={{
        background: `radial-gradient(ellipse 60% 60% at 50% 40%, #d1fae5 0%, #ecfdf5 30%, #ffffff 65%)`,
      }}
    >
      <RegisterForm />
    </div>
  );
}
