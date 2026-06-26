"use client";

import React, { useState, useEffect, useCallback } from "react";
import { useAuth } from "@/hooks/useAuth";
import { useToast } from "@/context/ToastContext";
import { api } from "@/lib/api-client";
import { ENDPOINTS } from "@/constants/api";
import { Forbidden } from "@/components/common/Forbidden";
import {
  ShieldAlert,
  UserX,
  UserCheck,
  Loader2,
  Search,
  CheckCircle,
  HelpCircle,
  Clock,
  Users,
  ChevronLeft,
  ChevronRight,
  Filter,
  Calendar,
  Mail,
  Shield,
  Activity,
  Edit2,
  User,
  X
} from "lucide-react";

interface ApiErrorType {
  message?: string;
  data?: {
    message?: string;
  };
}

interface UserResponse {
  id: string;
  username: string;
  email: string;
  fullName: string | null;
  avatarUrl: string | null;
  status: string;
  oauthProvider: string;
  roles: string[];
  createdAt: string;
}

interface PaginatedUsers {
  content: UserResponse[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
}

export default function AdminUsersPage() {
  const { user: currentUser, isAuthenticated, isLoading: authLoading } = useAuth();
  const toast = useToast();
  const [searchTerm, setSearchTerm] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [roleFilter, setRoleFilter] = useState("ALL");
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize] = useState(8);
  const [usersPage, setUsersPage] = useState<PaginatedUsers | null>(null);
  const [isLoadingData, setIsLoadingData] = useState(false);
  const [selectedUser, setSelectedUser] = useState<UserResponse | null>(null);
  const [isStatusModalOpen, setIsStatusModalOpen] = useState(false);
  const [updatingStatus, setUpdatingStatus] = useState("ACTIVE");
  const [isUpdatingStatusSubmit, setIsUpdatingStatusSubmit] = useState(false);

  const [isRoleModalOpen, setIsRoleModalOpen] = useState(false);
  const [updatingRoles, setUpdatingRoles] = useState<string[]>([]);
  const [isUpdatingRolesSubmit, setIsUpdatingRolesSubmit] = useState(false);
  const fetchUsers = useCallback(async () => {
    setIsLoadingData(true);
    try {
      const response = await api.get<PaginatedUsers>(ENDPOINTS.AUTH.ADMIN_LIST_USERS, {
        params: {
          search: searchTerm,
          status: statusFilter === "ALL" ? "" : statusFilter,
          role: roleFilter === "ALL" ? "" : roleFilter,
          page: currentPage,
          size: pageSize,
          sort: "createdAt,desc"
        }
      });
      setUsersPage(response);
    } catch (err: unknown) {
      console.error("Failed to fetch users:", err);
      const apiErr = err as ApiErrorType;
      toast.error(
        "Lỗi tải dữ liệu",
        apiErr.data?.message || apiErr.message || "Không thể tải danh sách người dùng."
      );
    } finally {
      setIsLoadingData(false);
    }
  }, [searchTerm, statusFilter, roleFilter, currentPage, pageSize, toast]);
  useEffect(() => {
    setCurrentPage(0);
  }, [searchTerm, statusFilter, roleFilter]);

  useEffect(() => {
    if (isAuthenticated && currentUser?.roles?.includes("ROLE_ADMIN")) {
      fetchUsers();
    }
  }, [isAuthenticated, currentUser, fetchUsers, currentPage]);
  const openStatusModal = (user: UserResponse) => {
    if (user.username === currentUser?.username) {
      toast.warning("Hạn chế bảo mật", "Bạn không thể tự thay đổi trạng thái của chính mình.");
      return;
    }
    setSelectedUser(user);
    setUpdatingStatus(user.status);
    setIsStatusModalOpen(true);
  };
  const handleStatusSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedUser) return;

    setIsUpdatingStatusSubmit(true);
    try {
      await api.put<UserResponse>(ENDPOINTS.AUTH.ADMIN_UPDATE_STATUS(selectedUser.id), {
        status: updatingStatus
      });
      toast.success(
        "Cập nhật thành công",
        `Đã cập nhật trạng thái của người dùng ${selectedUser.username} thành ${updatingStatus}.`
      );
      setIsStatusModalOpen(false);
      setSelectedUser(null);
      fetchUsers();
    } catch (err: unknown) {
      console.error("Failed to update status:", err);
      const apiErr = err as ApiErrorType;
      toast.error(
        "Cập nhật thất bại",
        apiErr.data?.message || apiErr.message || "Không thể cập nhật trạng thái người dùng."
      );
    } finally {
      setIsUpdatingStatusSubmit(false);
    }
  };
  const openRoleModal = (user: UserResponse) => {
    if (user.username === currentUser?.username) {
      toast.warning("Hạn chế bảo mật", "Bạn không thể tự thay đổi vai trò của chính mình.");
      return;
    }
    setSelectedUser(user);
    setUpdatingRoles([...user.roles]);
    setIsRoleModalOpen(true);
  };
  const handleRoleChange = (roleName: string) => {
    setUpdatingRoles([roleName]);
  };
  const handleRoleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedUser) return;

    setIsUpdatingRolesSubmit(true);
    try {
      await api.put<UserResponse>(ENDPOINTS.AUTH.ADMIN_UPDATE_ROLES(selectedUser.id), {
        roles: updatingRoles
      });
      toast.success(
        "Phân quyền thành công",
        `Đã cập nhật các vai trò của người dùng ${selectedUser.username} thành công.`
      );
      setIsRoleModalOpen(false);
      setSelectedUser(null);
      fetchUsers();
    } catch (err: unknown) {
      console.error("Failed to update roles:", err);
      const apiErr = err as ApiErrorType;
      toast.error(
        "Phân quyền thất bại",
        apiErr.data?.message || apiErr.message || "Không thể cập nhật quyền hạn người dùng."
      );
    } finally {
      setIsUpdatingRolesSubmit(false);
    }
  };
  const formatDate = (dateStr: string) => {
    try {
      const d = new Date(dateStr);
      return d.toLocaleDateString("vi-VN", {
        year: "numeric",
        month: "long",
        day: "numeric"
      });
    } catch (e) {
      return dateStr;
    }
  };
  const RenderRoleBadge = ({ roles }: { roles: string[] }) => {
    return (
      <div className="flex flex-wrap gap-1">
        {roles.map((role) => {
          let styles = "bg-zinc-100 text-zinc-700 border-zinc-200";
          let label = role;
          if (role === "ROLE_ADMIN") {
            styles = "bg-amber-50 text-amber-700 border-amber-200 ring-1 ring-amber-450/10";
            label = "Quản trị viên";
          } else if (role === "ROLE_CREATOR") {
            styles = "bg-indigo-50 text-indigo-700 border-indigo-200 ring-1 ring-indigo-400/10";
            label = "Nhà sáng tạo";
          } else if (role === "ROLE_USER") {
            styles = "bg-emerald-50 text-emerald-700 border-emerald-200 ring-1 ring-emerald-400/10";
            label = "Thành viên";
          }
          return (
            <span
              key={role}
              className={`inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-bold border tracking-wide shadow-2xs ${styles}`}
            >
              {label}
            </span>
          );
        })}
      </div>
    );
  };
  const RenderStatusBadge = ({ status }: { status: string }) => {
    let styles = "bg-zinc-100 text-zinc-600 border-zinc-200";
    let label = status;

    if (status === "ACTIVE") {
      styles = "bg-emerald-100/60 text-emerald-700 border-emerald-200/50";
      label = "Đang hoạt động";
    } else if (status === "BANNED") {
      styles = "bg-rose-100/65 text-rose-700 border-rose-200/50";
      label = "Đang bị khóa";
    } else if (status === "PENDING_VERIFICATION") {
      styles = "bg-amber-100/60 text-amber-700 border-amber-200/50";
      label = "Chờ xác thực";
    } else if (status === "PENDING_DELETION") {
      styles = "bg-zinc-100 text-zinc-500 border-zinc-200/50";
      label = "Chờ xóa";
    }

    return (
      <span
        className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold border ${styles}`}
      >
        <span className="w-1.5 h-1.5 rounded-full bg-current mr-1.5 animate-pulse" />
        {label}
      </span>
    );
  };
  if (authLoading) {
    return (
      <div className="flex-1 flex items-center justify-center min-h-[60vh]">
        <Loader2 className="h-8 w-8 text-brand-600 animate-spin" />
      </div>
    );
  }

  if (!isAuthenticated || !currentUser) {
    return <Forbidden />;
  }

  if (!currentUser.roles?.includes("ROLE_ADMIN")) {
    return <Forbidden />;
  }

  return (
    <div className="flex-1 w-full bg-zinc-50/50 py-10 px-4 sm:px-6 lg:px-8 min-h-screen">
      <div className="max-w-6xl mx-auto space-y-8">
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-zinc-200/60 pb-6">
          <div>
            <div className="flex items-center gap-2">
              <div className="p-2 bg-emerald-50 rounded-xl border border-emerald-100 shadow-2xs">
                <Shield className="h-6 w-6 text-emerald-600" />
              </div>
              <h1 className="text-3xl font-extrabold text-zinc-950 tracking-tight">Quản trị Người dùng</h1>
            </div>
            <p className="text-zinc-500 text-sm mt-1.5">
              Hệ thống tìm kiếm phân trang, quản lý trạng thái tài khoản, vô hiệu hóa và phân bổ vai trò
            </p>
          </div>
          
          <div className="inline-flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-xs font-bold bg-amber-50 text-amber-700 border border-amber-200 shadow-2xs self-start md:self-center">
            <Activity className="h-3.5 w-3.5 text-amber-500 animate-pulse" />
            Hệ thống Quản trị Viên
          </div>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-5">
          <div className="bg-white border border-zinc-200/60 rounded-2xl p-5 shadow-xs flex items-center justify-between hover:shadow-md transition-all duration-300">
            <div className="space-y-1">
              <span className="text-xs font-semibold text-zinc-400 uppercase tracking-wider block">Tổng số người dùng</span>
              <span className="text-3xl font-black text-zinc-900 tracking-tight">
                {usersPage ? usersPage.totalElements : <Loader2 className="w-5 h-5 animate-spin text-zinc-400 inline" />}
              </span>
            </div>
            <div className="p-3.5 bg-zinc-50 rounded-xl border border-zinc-100">
              <Users className="h-6 w-6 text-zinc-500" />
            </div>
          </div>

          <div className="bg-white border border-zinc-200/60 rounded-2xl p-5 shadow-xs flex items-center justify-between hover:shadow-md transition-all duration-300">
            <div className="space-y-1">
              <span className="text-xs font-semibold text-zinc-400 uppercase tracking-wider block">Đang hoạt động</span>
              <span className="text-3xl font-black text-emerald-600 tracking-tight">
                {usersPage ? usersPage.content.filter(u => u.status === "ACTIVE").length : <Loader2 className="w-5 h-5 animate-spin text-zinc-400 inline" />}
              </span>
            </div>
            <div className="p-3.5 bg-emerald-50 rounded-xl border border-emerald-100/50">
              <UserCheck className="h-6 w-6 text-emerald-600" />
            </div>
          </div>

          <div className="bg-white border border-zinc-200/60 rounded-2xl p-5 shadow-xs flex items-center justify-between hover:shadow-md transition-all duration-300">
            <div className="space-y-1">
              <span className="text-xs font-semibold text-zinc-400 uppercase tracking-wider block">Tài khoản bị khóa</span>
              <span className="text-3xl font-black text-rose-600 tracking-tight">
                {usersPage ? usersPage.content.filter(u => u.status === "BANNED").length : <Loader2 className="w-5 h-5 animate-spin text-zinc-400 inline" />}
              </span>
            </div>
            <div className="p-3.5 bg-rose-50 rounded-xl border border-rose-100/50">
              <UserX className="h-6 w-6 text-rose-600" />
            </div>
          </div>
        </div>
        <div className="bg-white border border-zinc-200/60 rounded-2xl p-5 shadow-2xs space-y-4">
          
          <div className="flex flex-col md:flex-row gap-4 items-center justify-between">
            <div className="relative w-full md:max-w-md group">
              <span className="absolute inset-y-0 left-0 flex items-center pl-3.5 text-zinc-400 group-focus-within:text-emerald-500">
                <Search className="w-4 h-4 transition-colors" />
              </span>
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="Tìm tên đăng nhập, email hoặc họ tên..."
                className="w-full pl-10 pr-4 py-2.5 bg-zinc-50 border border-zinc-200 rounded-xl text-sm text-zinc-800 placeholder-zinc-400 focus:outline-none focus:bg-white focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500 transition-all"
              />
            </div>
            <div className="flex items-center gap-3 w-full md:w-auto self-start md:self-center">
              <span className="text-xs font-bold text-zinc-400 uppercase tracking-wider hidden sm:inline flex-shrink-0">Lọc vai trò:</span>
              <div className="relative w-full sm:w-48">
                <select
                  value={roleFilter}
                  onChange={(e) => setRoleFilter(e.target.value)}
                  className="w-full px-3 py-2.5 bg-zinc-50 border border-zinc-200 rounded-xl text-xs font-bold text-zinc-700 focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500 cursor-pointer"
                >
                  <option value="ALL">Tất cả vai trò</option>
                  <option value="ROLE_USER">Thành viên</option>
                  <option value="ROLE_CREATOR">Nhà sáng tạo</option>
                  <option value="ROLE_ADMIN">Quản trị viên</option>
                </select>
              </div>
            </div>
          </div>
          <div className="flex flex-wrap gap-2 border-t border-zinc-100 pt-3">
            {[
              { code: "ALL", label: "Tất cả" },
              { code: "ACTIVE", label: "Đang hoạt động" },
              { code: "BANNED", label: "Đang bị khóa" },
              { code: "PENDING_VERIFICATION", label: "Chờ xác thực" },
              { code: "PENDING_DELETION", label: "Chờ xóa" }
            ].map((tab) => (
              <button
                key={tab.code}
                onClick={() => setStatusFilter(tab.code)}
                className={`px-4 py-1.5 rounded-lg text-xs font-bold border transition-all cursor-pointer ${
                  statusFilter === tab.code
                    ? "bg-zinc-900 border-zinc-900 text-white shadow-xs"
                    : "bg-white hover:bg-zinc-50 border-zinc-200 text-zinc-600 hover:text-zinc-900"
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>

        </div>
        <div className="bg-white border border-zinc-200/60 rounded-2xl shadow-xs overflow-hidden">
          
          <div className="overflow-x-auto w-full">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-zinc-50 border-b border-zinc-150 text-[11px] font-bold text-zinc-400 uppercase tracking-wider">
                  <th className="py-4.5 px-6">Tài khoản</th>
                  <th className="py-4.5 px-6">Email</th>
                  <th className="py-4.5 px-6">Vai trò</th>
                  <th className="py-4.5 px-6">Ngày tham gia</th>
                  <th className="py-4.5 px-6">Trạng thái</th>
                  <th className="py-4.5 px-6 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100">
                {isLoadingData ? (
                  <tr>
                    <td colSpan={6} className="py-16 text-center">
                      <div className="flex flex-col items-center justify-center space-y-2">
                        <Loader2 className="w-8 h-8 text-emerald-500 animate-spin" />
                        <span className="text-zinc-400 text-xs font-bold uppercase tracking-wider">Đang cập nhật danh sách...</span>
                      </div>
                    </td>
                  </tr>
                ) : !usersPage || usersPage.content.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="py-16 text-center text-zinc-400 space-y-2">
                      <HelpCircle className="h-10 w-10 text-zinc-200 mx-auto" />
                      <p className="text-sm font-semibold">Không tìm thấy người dùng phù hợp.</p>
                      <p className="text-xs text-zinc-400">Vui lòng thay đổi từ khóa tìm kiếm hoặc bộ lọc.</p>
                    </td>
                  </tr>
                ) : (
                  usersPage.content.map((userItem) => {
                    const isSelf = userItem.username === currentUser?.username;
                    return (
                      <tr 
                        key={userItem.id} 
                        className={`hover:bg-zinc-50/50 transition-colors ${isSelf ? "bg-emerald-50/15" : ""}`}
                      >
                        <td className="py-4.5 px-6">
                          <div className="flex items-center gap-3">
                            {userItem.avatarUrl ? (
                              <img
                                src={userItem.avatarUrl}
                                alt={userItem.fullName || userItem.username}
                                className="w-10 h-10 rounded-full object-cover border border-zinc-200 shadow-2xs"
                              />
                            ) : (
                              <div className="w-10 h-10 rounded-full bg-emerald-50 border border-emerald-100 flex items-center justify-center text-emerald-600 text-sm font-bold shadow-2xs">
                                {userItem.fullName ? userItem.fullName.substring(0, 1).toUpperCase() : userItem.username.substring(0, 1).toUpperCase()}
                              </div>
                            )}
                            <div className="flex flex-col min-w-0">
                              <span className="font-bold text-zinc-900 text-sm truncate max-w-[150px] sm:max-w-xs flex items-center gap-1.5">
                                {userItem.fullName || "Chưa cập nhật"}
                                {isSelf && (
                                  <span className="inline-flex px-1.5 py-0.5 rounded-full text-[9px] font-black uppercase tracking-wider bg-emerald-100 text-emerald-800 border border-emerald-200">
                                    Bạn
                                  </span>
                                )}
                              </span>
                              <span className="text-zinc-400 text-xs font-mono truncate">@{userItem.username}</span>
                            </div>
                          </div>
                        </td>
                        <td className="py-4.5 px-6 text-sm text-zinc-600 max-w-[120px] sm:max-w-xs truncate">
                          <div className="flex items-center gap-1.5">
                            <Mail className="w-3.5 h-3.5 text-zinc-400 shrink-0" />
                            <span className="truncate">{userItem.email}</span>
                          </div>
                          <span className="text-[9px] font-extrabold uppercase bg-zinc-100 text-zinc-400 border border-zinc-150 px-1 rounded-sm tracking-wider inline-block mt-0.5">
                            {userItem.oauthProvider}
                          </span>
                        </td>
                        <td className="py-4.5 px-6">
                          <RenderRoleBadge roles={userItem.roles} />
                        </td>
                        <td className="py-4.5 px-6 text-xs text-zinc-500 font-semibold">
                          <div className="flex items-center gap-1.5">
                            <Calendar className="w-3.5 h-3.5 text-zinc-400" />
                            <span>{userItem.createdAt ? formatDate(userItem.createdAt) : "Chưa rõ"}</span>
                          </div>
                        </td>
                        <td className="py-4.5 px-6">
                          <RenderStatusBadge status={userItem.status} />
                        </td>
                        <td className="py-4.5 px-6 text-right">
                          <div className="flex items-center justify-end gap-2">
                            <button
                              onClick={() => openRoleModal(userItem)}
                              disabled={isSelf}
                              title={isSelf ? "Bạn không thể tự đổi vai trò của mình" : "Thay đổi vai trò"}
                              className={`p-1.5 border border-zinc-200 rounded-lg hover:bg-zinc-50 transition-colors shadow-2xs cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed ${
                                isSelf ? "text-zinc-300" : "text-zinc-600 hover:text-zinc-950"
                              }`}
                            >
                              <Edit2 className="w-3.5 h-3.5" />
                            </button>
                            <button
                              onClick={() => openStatusModal(userItem)}
                              disabled={isSelf}
                              title={isSelf ? "Bạn không thể tự thay đổi trạng thái của mình" : "Thay đổi trạng thái"}
                              className={`p-1.5 border border-zinc-200 rounded-lg hover:bg-zinc-50 transition-colors shadow-2xs cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed ${
                                isSelf ? "text-zinc-300" : userItem.status === "BANNED" ? "text-emerald-500 hover:bg-emerald-50" : "text-rose-500 hover:bg-rose-50"
                              }`}
                            >
                              {userItem.status === "BANNED" ? (
                                <UserCheck className="w-3.5 h-3.5" />
                              ) : (
                                <UserX className="w-3.5 h-3.5" />
                              )}
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
          {usersPage && usersPage.totalPages > 1 && (
            <div className="bg-zinc-50/50 px-6 py-4.5 border-t border-zinc-100 flex items-center justify-between">
              <span className="text-xs font-bold text-zinc-500">
                Hiển thị trang {usersPage.number + 1} / {usersPage.totalPages} ({usersPage.totalElements} tài khoản)
              </span>
              
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setCurrentPage((prev) => Math.max(prev - 1, 0))}
                  disabled={usersPage.number === 0 || isLoadingData}
                  className="p-1.5 border border-zinc-200 bg-white rounded-lg hover:bg-zinc-50 disabled:opacity-40 transition-colors shadow-2xs cursor-pointer disabled:cursor-not-allowed text-zinc-600"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <button
                  onClick={() => setCurrentPage((prev) => Math.min(prev + 1, usersPage.totalPages - 1))}
                  disabled={usersPage.number === usersPage.totalPages - 1 || isLoadingData}
                  className="p-1.5 border border-zinc-200 bg-white rounded-lg hover:bg-zinc-50 disabled:opacity-40 transition-colors shadow-2xs cursor-pointer disabled:cursor-not-allowed text-zinc-600"
                >
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          )}

        </div>

      </div>
      {isStatusModalOpen && selectedUser && (
        <div className="fixed inset-0 bg-zinc-950/40 backdrop-blur-xs flex items-center justify-center p-4 z-50 animate-in fade-in duration-200">
          <div className="bg-white border border-zinc-200 rounded-2xl w-full max-w-md shadow-2xl overflow-hidden scale-in-95 duration-200">
            <div className="px-6 py-4 border-b border-zinc-100 flex items-center justify-between bg-zinc-50">
              <div className="flex items-center gap-2">
                <ShieldAlert className="h-5 w-5 text-rose-500" />
                <h3 className="font-extrabold text-zinc-900 text-base">Cập nhật trạng thái tài khoản</h3>
              </div>
              <button
                onClick={() => setIsStatusModalOpen(false)}
                className="text-zinc-400 hover:text-zinc-600 cursor-pointer p-1"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <form onSubmit={handleStatusSubmit} className="p-6 space-y-4">
              
              <div className="p-4 bg-zinc-50 rounded-xl border border-zinc-150 space-y-1.5 text-xs">
                <div className="flex justify-between">
                  <span className="text-zinc-400 font-semibold">Tên đăng nhập:</span>
                  <span className="font-bold text-zinc-800">@{selectedUser.username}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-zinc-400 font-semibold">Họ và tên:</span>
                  <span className="font-bold text-zinc-800">{selectedUser.fullName || "Chưa cập nhật"}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-zinc-400 font-semibold">Email:</span>
                  <span className="font-bold text-zinc-800 break-all">{selectedUser.email}</span>
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-bold text-zinc-500 uppercase tracking-wider block">Lựa chọn trạng thái</label>
                <select
                  value={updatingStatus}
                  onChange={(e) => setUpdatingStatus(e.target.value)}
                  className="w-full px-4 py-3 bg-zinc-50 border border-zinc-200 rounded-xl text-sm font-bold text-zinc-700 focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500 cursor-pointer"
                >
                  <option value="ACTIVE" className="text-emerald-600 font-bold">ACTIVE (Đang hoạt động)</option>
                  <option value="PENDING_VERIFICATION" className="text-amber-600 font-bold">PENDING_VERIFICATION (Chờ xác thực OTP)</option>
                  <option value="BANNED" className="text-rose-600 font-bold">BANNED (Khóa tài khoản vĩnh viễn)</option>
                  <option value="PENDING_DELETION" className="text-zinc-500 font-bold">PENDING_DELETION (Chờ xóa/hủy tài khoản)</option>
                </select>
              </div>

              {updatingStatus === "BANNED" && (
                <div className="p-3 bg-rose-50 border border-rose-100 rounded-xl text-xs text-rose-700 leading-relaxed flex items-start gap-2">
                  <ShieldAlert className="w-4 h-4 shrink-0 mt-0.5 text-rose-500" />
                  <span>
                    <strong>Cảnh báo:</strong> Khi chuyển sang BANNED, tất cả các phiên đăng nhập (Active Sessions) của người dùng này trên Redis sẽ bị thu hồi lập tức, bắt buộc logout khỏi hệ thống trên mọi thiết bị.
                  </span>
                </div>
              )}

              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setIsStatusModalOpen(false)}
                  className="px-4 py-2 border border-zinc-200 bg-white hover:bg-zinc-50 text-zinc-700 text-sm font-bold rounded-xl transition-all cursor-pointer"
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  disabled={isUpdatingStatusSubmit}
                  className="px-5 py-2 bg-zinc-950 hover:bg-zinc-800 text-white text-sm font-bold rounded-xl shadow-md hover:scale-[1.01] active:scale-[0.99] transition-all cursor-pointer flex items-center justify-center gap-1.5"
                >
                  {isUpdatingStatusSubmit ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <>
                      <UserCheck className="w-4 h-4 text-emerald-400" />
                      <span>Cập nhật ngay</span>
                    </>
                  )}
                </button>
              </div>

            </form>
          </div>
        </div>
      )}
      {isRoleModalOpen && selectedUser && (
        <div className="fixed inset-0 bg-zinc-950/40 backdrop-blur-xs flex items-center justify-center p-4 z-50 animate-in fade-in duration-200">
          <div className="bg-white border border-zinc-200 rounded-2xl w-full max-w-md shadow-2xl overflow-hidden scale-in-95 duration-200">
            <div className="px-6 py-4 border-b border-zinc-100 flex items-center justify-between bg-zinc-50">
              <div className="flex items-center gap-2">
                <Shield className="h-5 w-5 text-emerald-600" />
                <h3 className="font-extrabold text-zinc-900 text-base">Phân bổ vai trò (Phân quyền)</h3>
              </div>
              <button
                onClick={() => setIsRoleModalOpen(false)}
                className="text-zinc-400 hover:text-zinc-600 cursor-pointer p-1"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <form onSubmit={handleRoleSubmit} className="p-6 space-y-5">
              
              <div className="p-4 bg-zinc-50 rounded-xl border border-zinc-150 space-y-1.5 text-xs">
                <div className="flex justify-between">
                  <span className="text-zinc-400 font-semibold">Tên đăng nhập:</span>
                  <span className="font-bold text-zinc-800">@{selectedUser.username}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-zinc-400 font-semibold">Họ và tên:</span>
                  <span className="font-bold text-zinc-800">{selectedUser.fullName || "Chưa cập nhật"}</span>
                </div>
              </div>

              <div className="space-y-3">
                <label className="text-xs font-bold text-zinc-500 uppercase tracking-wider block">Các vai trò được gán</label>
                
                <div className="space-y-2.5">
                  {[
                    { code: "ROLE_USER", name: "Thành viên (ROLE_USER)", desc: "Quyền hạn cơ bản mua sắm, tương tác mạng xã hội" },
                    { code: "ROLE_CREATOR", name: "Nhà sáng tạo (ROLE_CREATOR)", desc: "Quyền bán hàng, đăng nội dung sáng tạo, rút tiền" },
                    { code: "ROLE_ADMIN", name: "Quản trị viên (ROLE_ADMIN)", desc: "Toàn quyền quản trị kỹ thuật hệ thống" }
                  ].map((roleObj) => {
                    const isChecked = updatingRoles.includes(roleObj.code);
                    return (
                      <label
                        key={roleObj.code}
                        onClick={() => handleRoleChange(roleObj.code)}
                        className={`flex items-start gap-3 p-3 border rounded-xl cursor-pointer hover:bg-zinc-50/50 transition-all select-none ${
                          isChecked 
                            ? "bg-emerald-50/10 border-emerald-500/40 shadow-2xs" 
                            : "border-zinc-200"
                        }`}
                      >
                        <input
                          type="radio"
                          checked={isChecked}
                          onChange={() => {}}
                          className="h-4.5 w-4.5 border-zinc-300 text-emerald-600 focus:ring-emerald-500/20 mt-0.5 pointer-events-none accent-emerald-600"
                        />
                        <div className="flex flex-col gap-0.5">
                          <span className="font-bold text-sm text-zinc-800">{roleObj.name}</span>
                          <span className="text-zinc-400 text-xs leading-normal">{roleObj.desc}</span>
                        </div>
                      </label>
                    );
                  })}
                </div>
              </div>

              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setIsRoleModalOpen(false)}
                  className="px-4 py-2 border border-zinc-200 bg-white hover:bg-zinc-50 text-zinc-700 text-sm font-bold rounded-xl transition-all cursor-pointer"
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  disabled={isUpdatingRolesSubmit}
                  className="px-5 py-2 bg-zinc-950 hover:bg-zinc-800 text-white text-sm font-bold rounded-xl shadow-md hover:scale-[1.01] active:scale-[0.99] transition-all cursor-pointer flex items-center justify-center gap-1.5"
                >
                  {isUpdatingRolesSubmit ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <>
                      <CheckCircle className="w-4 h-4 text-emerald-400" />
                      <span>Lưu phân quyền</span>
                    </>
                  )}
                </button>
              </div>

            </form>
          </div>
        </div>
      )}

    </div>
  );
}
