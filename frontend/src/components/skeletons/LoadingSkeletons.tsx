import React from "react";

// 1. PostSkeleton - Mô phỏng bài viết trên Bảng tin
export function PostSkeleton() {
  return (
    <div className="rounded-2xl border border-zinc-150/70 bg-white p-5 shadow-sm animate-pulse">
      {/* Header */}
      <div className="flex items-center justify-between gap-3 mb-4">
        <div className="flex items-center gap-3">
          <div className="h-10 w-10 rounded-full bg-zinc-200" />
          <div className="flex flex-col gap-1.5">
            <div className="h-3.5 w-24 rounded bg-zinc-200" />
            <div className="h-2 w-16 rounded bg-zinc-150" />
          </div>
        </div>
        <div className="h-8 w-24 rounded-full bg-zinc-200" />
      </div>

      {/* Content */}
      <div className="space-y-2 mb-4">
        <div className="h-4 w-full rounded bg-zinc-200" />
        <div className="h-4 w-11/12 rounded bg-zinc-200" />
        <div className="h-4 w-3/4 rounded bg-zinc-200" />
      </div>

      {/* Media placeholder */}
      <div className="rounded-2xl bg-zinc-200 mb-5 aspect-[4/3] sm:aspect-[16/9]" />

      {/* Footer */}
      <div className="flex items-center pt-2 mt-1 border-t border-zinc-150 gap-4">
        <div className="flex-1 h-9 rounded-xl bg-zinc-100 animate-pulse" />
        <div className="flex-1 h-9 rounded-xl bg-zinc-100 animate-pulse" />
        <div className="flex-1 h-9 rounded-xl bg-zinc-100 animate-pulse" />
      </div>
    </div>
  );
}

// 2. CreatorListSkeleton - Mô phỏng một dòng trong danh sách Creator ở Sidebar
export function CreatorListSkeleton() {
  return (
    <div className="flex items-center justify-between gap-3 p-2 rounded-xl animate-pulse">
      <div className="flex items-center gap-2.5 min-w-0">
        <div className="h-8 w-8 rounded-full bg-zinc-205 shrink-0" />
        <div className="flex flex-col gap-1 min-w-0">
          <div className="h-3 w-16 rounded bg-zinc-205" />
          <div className="h-2 w-20 rounded bg-zinc-150" />
        </div>
      </div>
      <div className="h-6 w-16 rounded-full bg-zinc-205 shrink-0" />
    </div>
  );
}

// 3. ProductDetailsSkeleton - Mô phỏng trang Chi tiết sản phẩm
export function ProductDetailsSkeleton() {
  return (
    <div className="min-h-screen bg-zinc-50 py-8 px-4 sm:px-6 lg:px-8 animate-pulse">
      <div className="max-w-6xl mx-auto flex flex-col gap-8">
        {/* Back Link */}
        <div className="h-4 w-24 rounded bg-zinc-200" />

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 bg-white rounded-3xl p-6 shadow-sm border border-zinc-150">
          {/* Gallery placeholder (left) */}
          <div className="lg:col-span-7 flex flex-col gap-4">
            <div className="aspect-[4/3] sm:aspect-[16/9] rounded-2xl bg-zinc-200" />
            <div className="flex gap-2">
              <div className="h-16 w-16 rounded-lg bg-zinc-200" />
              <div className="h-16 w-16 rounded-lg bg-zinc-200" />
              <div className="h-16 w-16 rounded-lg bg-zinc-200" />
            </div>
          </div>

          {/* Info placeholder (right) */}
          <div className="lg:col-span-5 flex flex-col gap-5 justify-between">
            <div className="space-y-4">
              <div className="h-3 w-20 rounded bg-zinc-200" />
              <div className="h-7 w-11/12 rounded bg-zinc-200" />
              <div className="h-5 w-1/3 rounded bg-zinc-200" />
              <div className="h-4 w-full rounded bg-zinc-150" />
              <div className="h-4 w-full rounded bg-zinc-150" />
            </div>

            {/* Variants */}
            <div className="space-y-3 pt-4 border-t border-zinc-100">
              <div className="h-3 w-16 rounded bg-zinc-200" />
              <div className="flex flex-wrap gap-2">
                <div className="h-9 w-20 rounded-xl bg-zinc-100" />
                <div className="h-9 w-20 rounded-xl bg-zinc-100" />
              </div>
            </div>

            {/* Actions */}
            <div className="space-y-3 pt-4 border-t border-zinc-100">
              <div className="flex gap-4">
                <div className="flex-1 h-12 rounded-2xl bg-zinc-200" />
                <div className="h-12 w-12 rounded-2xl bg-zinc-200" />
              </div>
              <div className="h-12 w-full rounded-2xl bg-zinc-100" />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

// 4. CreatorProfileSkeleton - Mô phỏng trang Hồ sơ Creator
export function CreatorProfileSkeleton() {
  return (
    <div className="min-h-screen bg-zinc-50 pb-16 animate-pulse">
      {/* Header Container */}
      <div className="mx-auto max-w-5xl px-4 sm:px-6 lg:px-8 pt-8">
        {/* Back Link */}
        <div className="h-4 w-20 rounded bg-zinc-200 mb-6" />

        {/* Profile Card */}
        <div className="bg-white rounded-3xl border border-zinc-150/70 overflow-hidden shadow-sm">
          {/* Cover Image Placeholder */}
          <div className="h-40 sm:h-52 bg-zinc-200" />

          {/* Profile details */}
          <div className="px-6 pb-6 relative">
            {/* Avatar block */}
            <div className="h-24 w-24 sm:h-28 sm:w-28 rounded-full bg-zinc-300 border-4 border-white -mt-12 sm:-mt-14 mb-4" />

            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mt-2">
              <div className="space-y-2">
                <div className="h-6 w-48 rounded bg-zinc-200" />
                <div className="h-3 w-32 rounded bg-zinc-150" />
              </div>
              <div className="flex gap-2">
                <div className="h-10 w-24 rounded-full bg-zinc-200" />
                <div className="h-10 w-24 rounded-full bg-zinc-150" />
              </div>
            </div>

            {/* Description */}
            <div className="h-3 w-11/12 rounded bg-zinc-150 mt-5" />
            <div className="h-3 w-9/12 rounded bg-zinc-150 mt-2" />

            {/* Stats row */}
            <div className="flex gap-6 mt-6 pt-5 border-t border-zinc-100">
              <div className="h-4 w-16 rounded bg-zinc-200" />
              <div className="h-4 w-16 rounded bg-zinc-200" />
              <div className="h-4 w-16 rounded bg-zinc-200" />
            </div>
          </div>
        </div>

        {/* Profile Tabs */}
        <div className="flex gap-4 border-b border-zinc-200 mt-8 pb-3">
          <div className="h-5 w-24 rounded bg-zinc-200" />
          <div className="h-5 w-24 rounded bg-zinc-200" />
        </div>

        {/* Feed List Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mt-8">
          <div className="h-64 rounded-2xl bg-white border border-zinc-150" />
          <div className="h-64 rounded-2xl bg-white border border-zinc-150" />
        </div>
      </div>
    </div>
  );
}

// 5. OrderListSkeleton - Mô phỏng một mục Đơn hàng trong danh sách lịch sử
export function OrderListSkeleton() {
  return (
    <div className="bg-white rounded-3xl border border-zinc-200/60 shadow-sm overflow-hidden flex flex-col animate-pulse">
      {/* Order Head */}
      <div className="bg-zinc-50 px-6 py-4 border-b border-zinc-150 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="h-3 w-20 rounded bg-zinc-200" />
          <div className="h-3 w-28 rounded bg-zinc-200" />
        </div>
        <div className="h-5 w-20 rounded-full bg-zinc-200" />
      </div>

      {/* Items list */}
      <div className="p-6 flex flex-col gap-4 border-b border-zinc-100">
        <div className="h-4 w-24 rounded bg-zinc-200" />
        <div className="space-y-3">
          <div className="h-3.5 w-11/12 rounded bg-zinc-150" />
          <div className="h-3.5 w-9/12 rounded bg-zinc-150" />
        </div>
      </div>

      {/* Price & Action */}
      <div className="px-6 py-4 flex justify-between items-center bg-zinc-50/20">
        <div className="flex flex-col gap-1">
          <div className="h-2 w-16 rounded bg-zinc-200" />
          <div className="h-4 w-24 rounded bg-zinc-200" />
        </div>
        <div className="h-9 w-28 rounded-full bg-zinc-200" />
      </div>
    </div>
  );
}

// 6. OrderDetailSkeleton - Mô phỏng trang Chi tiết đơn hàng
export function OrderDetailSkeleton() {
  return (
    <div className="min-h-screen bg-zinc-50 py-10 px-4 sm:px-6 lg:px-8 animate-pulse">
      <div className="max-w-4xl w-full mx-auto space-y-8">
        {/* Back link */}
        <div className="h-4 w-28 rounded bg-zinc-200" />

        {/* Order code & status banner */}
        <div className="bg-white rounded-3xl p-6 border border-zinc-200/60 shadow-sm flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
          <div className="space-y-2">
            <div className="h-6 w-32 rounded bg-zinc-200" />
            <div className="h-3.5 w-48 rounded bg-zinc-150" />
          </div>
          <div className="h-7 w-28 rounded-full bg-zinc-200" />
        </div>

        {/* Delivery Info */}
        <div className="bg-white rounded-3xl p-6 border border-zinc-200/60 shadow-sm space-y-4">
          <div className="h-4 w-32 rounded bg-zinc-200" />
          <div className="space-y-2">
            <div className="h-3.5 w-1/3 rounded bg-zinc-150" />
            <div className="h-3.5 w-2/3 rounded bg-zinc-150" />
          </div>
        </div>

        {/* Items list */}
        <div className="bg-white rounded-3xl border border-zinc-200/60 shadow-sm overflow-hidden">
          <div className="px-6 py-4.5 border-b border-zinc-150">
            <div className="h-4 w-40 rounded bg-zinc-200" />
          </div>
          <div className="p-6 space-y-4">
            <div className="flex gap-4">
              <div className="h-16 w-16 rounded-xl bg-zinc-200 shrink-0" />
              <div className="flex-1 space-y-2">
                <div className="h-3.5 w-1/2 rounded bg-zinc-205" />
                <div className="h-3 w-1/4 rounded bg-zinc-150" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

// 7. CartSkeleton - Mô phỏng giỏ hàng đang tải
export function CartSkeleton() {
  return (
    <div className="min-h-screen bg-zinc-50 py-10 px-4 sm:px-6 lg:px-8 animate-pulse">
      <div className="max-w-6xl w-full mx-auto space-y-8">
        {/* Header */}
        <div className="h-10 w-48 rounded bg-zinc-200" />

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 items-start">
          {/* Main List */}
          <div className="lg:col-span-2 space-y-6">
            <div className="bg-white rounded-3xl border border-zinc-200/60 shadow-sm p-6 space-y-6">
              {/* Creator row */}
              <div className="flex items-center justify-between pb-4 border-b border-zinc-100">
                <div className="flex items-center gap-3">
                  <div className="h-4.5 w-4.5 rounded bg-zinc-200" />
                  <div className="h-4 w-32 rounded bg-zinc-200" />
                </div>
              </div>
              {/* Items */}
              <div className="flex gap-4">
                <div className="h-4.5 w-4.5 rounded bg-zinc-200 self-center" />
                <div className="h-20 w-20 rounded-xl bg-zinc-200 shrink-0" />
                <div className="flex-1 space-y-2">
                  <div className="h-3.5 w-1/2 rounded bg-zinc-205" />
                  <div className="h-3 w-24 rounded bg-zinc-150" />
                  <div className="h-4.5 w-16 rounded bg-zinc-200" />
                </div>
              </div>
            </div>
          </div>

          {/* Pricing Box */}
          <div className="bg-white rounded-3xl border border-zinc-200/60 shadow-sm p-6 space-y-5">
            <div className="h-4 w-28 rounded bg-zinc-200" />
            <div className="space-y-3">
              <div className="flex justify-between">
                <div className="h-3 w-16 rounded bg-zinc-150" />
                <div className="h-3 w-12 rounded bg-zinc-150" />
              </div>
              <div className="flex justify-between">
                <div className="h-3 w-20 rounded bg-zinc-150" />
                <div className="h-3 w-8 rounded bg-zinc-150" />
              </div>
            </div>
            <div className="pt-4 border-t border-zinc-100 flex justify-between">
              <div className="h-4 w-16 rounded bg-zinc-200" />
              <div className="h-5 w-24 rounded bg-zinc-200" />
            </div>
            <div className="h-11 w-full rounded-full bg-zinc-200 mt-2" />
          </div>
        </div>
      </div>
    </div>
  );
}
