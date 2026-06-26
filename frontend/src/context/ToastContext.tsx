"use client";

import React, { createContext, useContext, useState, useCallback, useRef, useEffect } from "react";
import { CheckCircle, XCircle, AlertTriangle, Info, X } from "lucide-react";
export type ToastType = "success" | "error" | "warning" | "info";

export interface Toast {
  id: string;
  type: ToastType;
  title: string;
  message?: string;
  duration?: number;
}

interface ToastContextType {
  toasts: Toast[];
  addToast: (toast: Omit<Toast, "id">) => void;
  removeToast: (id: string) => void;
  success: (title: string, message?: string) => void;
  error: (title: string, message?: string) => void;
  warning: (title: string, message?: string) => void;
  info: (title: string, message?: string) => void;
}
const ToastContext = createContext<ToastContextType | undefined>(undefined);
const toastConfig = {
  success: {
    icon: CheckCircle,
    bg: "bg-white dark:bg-zinc-900",
    border: "border border-zinc-200/80 dark:border-zinc-800/80 border-l-brand-500 dark:border-l-brand-500",
    iconColor: "text-brand-500 dark:text-brand-400",
    titleColor: "text-zinc-900 dark:text-zinc-50 font-bold",
    msgColor: "text-zinc-500 dark:text-zinc-400",
    progress: "bg-brand-500",
    closeColor: "text-zinc-400 hover:text-zinc-900 hover:bg-zinc-100 dark:text-zinc-500 dark:hover:text-zinc-200 dark:hover:bg-zinc-800",
    shadow: "shadow-xl shadow-zinc-200/60 dark:shadow-black/40",
  },
  error: {
    icon: XCircle,
    bg: "bg-white dark:bg-zinc-900",
    border: "border border-zinc-200/80 dark:border-zinc-800/80 border-l-rose-500 dark:border-l-rose-500",
    iconColor: "text-rose-500 dark:text-rose-400",
    titleColor: "text-zinc-900 dark:text-zinc-50 font-bold",
    msgColor: "text-zinc-500 dark:text-zinc-400",
    progress: "bg-rose-500",
    closeColor: "text-zinc-400 hover:text-zinc-900 hover:bg-zinc-100 dark:text-zinc-500 dark:hover:text-zinc-200 dark:hover:bg-zinc-800",
    shadow: "shadow-xl shadow-zinc-200/60 dark:shadow-black/40",
  },
  warning: {
    icon: AlertTriangle,
    bg: "bg-white dark:bg-zinc-900",
    border: "border border-zinc-200/80 dark:border-zinc-800/80 border-l-amber-500 dark:border-l-amber-500",
    iconColor: "text-amber-500 dark:text-amber-400",
    titleColor: "text-zinc-900 dark:text-zinc-50 font-bold",
    msgColor: "text-zinc-500 dark:text-zinc-400",
    progress: "bg-amber-500",
    closeColor: "text-zinc-400 hover:text-zinc-900 hover:bg-zinc-100 dark:text-zinc-500 dark:hover:text-zinc-200 dark:hover:bg-zinc-800",
    shadow: "shadow-xl shadow-zinc-200/60 dark:shadow-black/40",
  },
  info: {
    icon: Info,
    bg: "bg-white dark:bg-zinc-900",
    border: "border border-zinc-200/80 dark:border-zinc-800/80 border-l-sky-500 dark:border-l-sky-500",
    iconColor: "text-sky-500 dark:text-sky-400",
    titleColor: "text-zinc-900 dark:text-zinc-50 font-bold",
    msgColor: "text-zinc-500 dark:text-zinc-400",
    progress: "bg-sky-500",
    closeColor: "text-zinc-400 hover:text-zinc-900 hover:bg-zinc-100 dark:text-zinc-500 dark:hover:text-zinc-200 dark:hover:bg-zinc-800",
    shadow: "shadow-xl shadow-zinc-200/60 dark:shadow-black/40",
  },
};

function ToastItem({ toast, onRemove }: { toast: Toast; onRemove: (id: string) => void }) {
  const [isExiting, setIsExiting] = useState(false);
  const [progress, setProgress] = useState(100);
  const [isPaused, setIsPaused] = useState(false);
  
  const duration = toast.duration ?? 4000;
  const config = toastConfig[toast.type];
  const Icon = config.icon;

  const remainingTimeRef = useRef(duration);
  const lastTickRef = useRef(Date.now());
  const rafRef = useRef<number | null>(null);
  const isClosingRef = useRef(false);

  const handleClose = useCallback(() => {
    if (isClosingRef.current) return;
    isClosingRef.current = true;
    setIsExiting(true);
    setTimeout(() => onRemove(toast.id), 300);
  }, [onRemove, toast.id]);

  useEffect(() => {
    if (isExiting) {
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
      return;
    }

    if (isPaused) {
      lastTickRef.current = Date.now();
      return;
    }

    lastTickRef.current = Date.now();

    const tick = () => {
      const now = Date.now();
      const elapsedSinceLastTick = now - lastTickRef.current;
      lastTickRef.current = now;

      remainingTimeRef.current = Math.max(0, remainingTimeRef.current - elapsedSinceLastTick);
      
      const pct = (remainingTimeRef.current / duration) * 100;
      setProgress(pct);

      if (remainingTimeRef.current <= 0) {
        handleClose();
      } else {
        rafRef.current = requestAnimationFrame(tick);
      }
    };

    rafRef.current = requestAnimationFrame(tick);

    return () => {
      if (rafRef.current) {
        cancelAnimationFrame(rafRef.current);
      }
    };
  }, [isPaused, isExiting, duration, handleClose]);

  return (
    <div
      role="alert"
      onMouseEnter={() => setIsPaused(true)}
      onMouseLeave={() => setIsPaused(false)}
      className={`
        relative overflow-hidden w-full max-w-sm rounded-xl border-l-4
        ${config.bg} ${config.border} ${config.shadow}
        ${isExiting ? "animate-toast-out" : "animate-toast-in"}
        transition-all duration-300
      `}
    >
      <div className="flex items-start gap-3 p-4">
        <Icon className={`w-5 h-5 shrink-0 mt-0.5 ${config.iconColor}`} />
        <div className="flex-1 min-w-0">
          <p className={`text-sm font-semibold ${config.titleColor}`}>{toast.title}</p>
          {toast.message && (
            <p className={`text-xs mt-0.5 leading-relaxed ${config.msgColor}`}>{toast.message}</p>
          )}
        </div>
        <button
          onClick={handleClose}
          className={`shrink-0 p-0.5 rounded-lg transition-colors ${config.closeColor}`}
        >
          <X className="w-4 h-4" />
        </button>
      </div>
      <div className="absolute bottom-0 left-0 right-0 h-[3px] bg-black/5 dark:bg-white/5">
        <div
          className={`h-full ${config.progress} transition-none rounded-full opacity-60`}
          style={{ width: `${progress}%` }}
        />
      </div>
    </div>
  );
}
function ToastContainer({ toasts, onRemove }: { toasts: Toast[]; onRemove: (id: string) => void }) {
  if (toasts.length === 0) return null;

  return (
    <div className="fixed top-4 right-4 z-[9999] flex flex-col gap-3 pointer-events-none">
      {toasts.map((toast) => (
        <div key={toast.id} className="pointer-events-auto">
          <ToastItem toast={toast} onRemove={onRemove} />
        </div>
      ))}
    </div>
  );
}
export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const addToast = useCallback((toast: Omit<Toast, "id">) => {
    const id = `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
    setToasts((prev) => [...prev, { ...toast, id }]);
  }, []);

  const success = useCallback(
    (title: string, message?: string) => addToast({ type: "success", title, message }),
    [addToast]
  );
  const error = useCallback(
    (title: string, message?: string) => addToast({ type: "error", title, message, duration: 6000 }),
    [addToast]
  );
  const warning = useCallback(
    (title: string, message?: string) => addToast({ type: "warning", title, message, duration: 5000 }),
    [addToast]
  );
  const info = useCallback(
    (title: string, message?: string) => addToast({ type: "info", title, message }),
    [addToast]
  );

  return (
    <ToastContext.Provider value={{ toasts, addToast, removeToast, success, error, warning, info }}>
      {children}
      <ToastContainer toasts={toasts} onRemove={removeToast} />
    </ToastContext.Provider>
  );
}
export function useToast() {
  const context = useContext(ToastContext);
  if (context === undefined) {
    throw new Error("useToast must be used within a ToastProvider");
  }
  return context;
}
