"use client";

import React, { useState, useEffect, useRef, useTransition, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { 
  Send, 
  Paperclip, 
  Smile, 
  Search, 
  Plus, 
  Users, 
  MessageSquare, 
  ChevronLeft, 
  Info, 
  Loader2, 
  FileText, 
  User, 
  Image as ImageIcon,
  X,
  Play,
  WifiOff
} from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { useChat } from "@/context/ChatContext";
import { useToast } from "@/context/ToastContext";
import { chatService, PresignedUrlRequest } from "@/services/chat.service";
import { userService, FollowResponse } from "@/services/user.service";
import { ConversationResponse, MessageResponse } from "@/types";
import { ROUTES } from "@/constants/routes";
import { api } from "@/lib/api-client";

function ChatContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const toast = useToast();
  const { user } = useAuth();
  
  const {
    conversations,
    activeConversation,
    messages,
    messagesPage,
    isLoadingConversations,
    isLoadingMessages,
    hasMoreMessages,
    typingUsers,
    onlineStatusMap,
    isConnected,
    setActiveConversation,
    loadMoreMessages,
    sendMessage,
    sendTypingState,
    startDirectChat,
    fetchConversations
  } = useChat();

  const [searchConvQuery, setSearchConvQuery] = useState("");
  const [isNewChatOpen, setIsNewChatOpen] = useState(false);
  const [contacts, setContacts] = useState<FollowResponse[]>([]);
  const [isLoadingContacts, setIsLoadingContacts] = useState(false);
  const [selectedContactIds, setSelectedContactIds] = useState<string[]>([]);
  const [contactSearchQuery, setContactSearchQuery] = useState("");
  const [inputText, setInputText] = useState("");
  const [isTyping, setIsTyping] = useState(false);
  const typingTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadPercent, setUploadPercent] = useState(0);
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [zoomImageUrl, setZoomImageUrl] = useState<string | null>(null);
  const [isEmojiPickerOpen, setIsEmojiPickerOpen] = useState(false);
  const [showMemberInfo, setShowMemberInfo] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const chatScrollContainerRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const emojiPickerRef = useRef<HTMLDivElement>(null);
  const emojiButtonRef = useRef<HTMLButtonElement>(null);
  const [isPending, startTransition] = useTransition();
  const EMOJI_LIST = [
    "😀", "😂", "😍", "🥰", "😎", "🤩", "😢", "😡", "🤔", "🙏",
    "👍", "👎", "❤️", "🔥", "✨", "🎉", "💯", "👏", "🤝", "💪",
    "📦", "🛒", "💰", "🏷️", "⭐", "🌟", "💬", "📸", "🎁", "🚀"
  ];

  // Click-outside handler for emoji picker
  useEffect(() => {
    if (!isEmojiPickerOpen) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (
        emojiPickerRef.current && !emojiPickerRef.current.contains(e.target as Node) &&
        emojiButtonRef.current && !emojiButtonRef.current.contains(e.target as Node)
      ) {
        setIsEmojiPickerOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [isEmojiPickerOpen]);

  const startDirectChatCalledRef = useRef(false);

  // Effect 1: Handle startDirectChatUserId — runs once, guarded by ref
  useEffect(() => {
    const targetUserId = searchParams.get("startDirectChatUserId");
    if (!targetUserId || startDirectChatCalledRef.current) return;

    startDirectChatCalledRef.current = true;
    const handleStart = async () => {
      try {
        const roomId = await startDirectChat(targetUserId);
        router.replace(ROUTES.MESSAGES + `?convId=${roomId}`);
      } catch {
        router.replace(ROUTES.MESSAGES);
      }
    };
    handleStart();
  }, [searchParams, startDirectChat, router]);

  // Effect 2: Handle convId in URL — auto-select conversation from list (only on initial load)
  useEffect(() => {
    const targetUserId = searchParams.get("startDirectChatUserId");
    if (targetUserId) return; // let Effect 1 handle this case

    const urlConvId = searchParams.get("convId");
    if (urlConvId && conversations.length > 0 && !activeConversation) {
      const match = conversations.find(c => c.id === urlConvId);
      if (match) {
        setActiveConversation(match);
      }
    }
  }, [searchParams, conversations, activeConversation, setActiveConversation]);
  useEffect(() => {
    return () => {
      setActiveConversation(null);
    };
  }, [setActiveConversation]);
  const scrollToBottom = (behavior: ScrollBehavior = "smooth") => {
    messagesEndRef.current?.scrollIntoView({ behavior });
  };

  useEffect(() => {
    if (messages.length > 0 && messagesPage === 0) {
      scrollToBottom("auto");
    }
  }, [messages, messagesPage]);

  useEffect(() => {
    if (typingUsers.length > 0) {
      scrollToBottom("smooth");
    }
  }, [typingUsers]);
  const handleScroll = () => {
    if (!chatScrollContainerRef.current || isLoadingMessages || !hasMoreMessages) return;
    const { scrollTop } = chatScrollContainerRef.current;
    if (scrollTop < 40) {
      const previousScrollHeight = chatScrollContainerRef.current.scrollHeight;
      
      loadMoreMessages().then(() => {
        setTimeout(() => {
          if (chatScrollContainerRef.current) {
            const newHeight = chatScrollContainerRef.current.scrollHeight;
            chatScrollContainerRef.current.scrollTop = newHeight - previousScrollHeight;
          }
        }, 100);
      });
    }
  };
  const uploadFile = async (file: File) => {
    const req: PresignedUrlRequest = {
      fileName: file.name,
      fileSize: file.size,
      contentType: file.type
    };
    const presigned = await chatService.getPresignedUrl(req);
    await chatService.uploadAttachmentFile(
      presigned.uploadUrl,
      file,
      (percent) => setUploadPercent(percent)
    );
    await api.post<void>("/media/confirm", null, { params: { key: presigned.fileKey } });
    return presigned.fileUrl;
  };

  const handleSendTextSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const hasText = !!inputText.trim();
    const hasFile = !!pendingFile;
    if (!hasText && !hasFile) return;

    if (hasFile && pendingFile) {
      setIsUploading(true);
      setUploadPercent(0);
      try {
        const file = pendingFile;
        setPendingFile(null);
        const fileUrl = await uploadFile(file);
        
        const isImg = file.type.startsWith("image/");
        const isVid = file.type.startsWith("video/");
        const fileType = isImg ? "IMAGE" : isVid ? "VIDEO" : "DOCUMENT";
        
        // Use text input as caption in content field; fallback to fileUrl if no caption
        const messageContent = hasText ? inputText.trim() : fileUrl;

        sendMessage(messageContent, fileType, {
          fileUrl,
          fileName: file.name,
          fileSize: file.size,
          mimeType: file.type
        });

        // Clear caption input
        setInputText("");
        setIsEmojiPickerOpen(false);
        if (textareaRef.current) {
          textareaRef.current.style.height = 'auto';
        }
        if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);
        sendTypingState(false);
        setIsTyping(false);
      } catch (err: any) {
        console.error("Upload tệp đính kèm thất bại", err);
        toast.error("Tải tệp thất bại", err?.message || "Lỗi đường truyền tệp.");
      } finally {
        setIsUploading(false);
        setUploadPercent(0);
      }
    } else if (hasText) {
      sendMessage(inputText.trim(), "TEXT");
      setInputText("");
      setIsEmojiPickerOpen(false);
      if (textareaRef.current) {
        textareaRef.current.style.height = 'auto';
      }
      if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);
      sendTypingState(false);
      setIsTyping(false);
    }
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInputText(e.target.value);
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
      textareaRef.current.style.height = Math.min(textareaRef.current.scrollHeight, 96) + 'px';
    }
    
    if (!isTyping && activeConversation) {
      setIsTyping(true);
      sendTypingState(true);
    }
    
    if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);
    
    typingTimeoutRef.current = setTimeout(() => {
      sendTypingState(false);
      setIsTyping(false);
    }, 5000);
  };
  const handleFileUploadClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (file.size > 20 * 1024 * 1024) {
      toast.warning("File quá lớn", "Dung lượng tệp đính kèm không được vượt quá 20MB.");
      return;
    }
    setPendingFile(file);
  };
  const openNewChatModal = async () => {
    setIsNewChatOpen(true);
    setSelectedContactIds([]);
    setContactSearchQuery("");
    
    if (!user) return;
    setIsLoadingContacts(true);
    try {
      const followingList = await userService.getFollowing(user.id, 0, 50);
      setContacts(followingList.content || []);
    } catch (err) {
      console.error("Failed to load contacts:", err);
    } finally {
      setIsLoadingContacts(false);
    }
  };

  const handleContactSelectToggle = (contactId: string) => {
    setSelectedContactIds([contactId]);
  };

  const handleCreateChatSubmit = async () => {
    if (selectedContactIds.length === 0) {
      toast.warning("Chọn liên hệ", "Vui lòng chọn một người để bắt đầu chat.");
      return;
    }

    startTransition(async () => {
      try {
        const response = await chatService.createConversation(selectedContactIds);
        toast.success("Tạo hội thoại thành công");
        await fetchConversations();
        setActiveConversation(response);
        setIsNewChatOpen(false);
      } catch (err: any) {
        toast.error("Lỗi tạo phòng", err?.message || "Không thể tạo phòng chat.");
      }
    });
  };
  const filteredConversations = conversations.filter(c => {
    if (!searchConvQuery.trim()) return true;
    const resolvedName = c.members.find(m => m.id !== user?.id)?.fullName || c.name || "User";
    return resolvedName.toLowerCase().includes(searchConvQuery.toLowerCase());
  });

  const getConversationTitle = (c: ConversationResponse) => {
    const partner = c.members.find(m => m.id !== user?.id);
    return partner?.fullName || partner?.username || c.name || "Tin nhắn Direct";
  };

  const getConversationAvatar = (c: ConversationResponse) => {
    const partner = c.members.find(m => m.id !== user?.id);
    return partner?.avatarUrl;
  };

  const formatMessageTime = (dateStr: string) => {
    const d = new Date(dateStr);
    return d.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });
  };

  const formatConversationTime = (dateStr: string) => {
    const d = new Date(dateStr);
    const now = new Date();
    if (d.toDateString() === now.toDateString()) {
      return d.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });
    }
    return d.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" });
  };

  return (
    <div 
      className="flex-1 bg-zinc-50 dark:bg-zinc-950 transition-colors duration-300 relative flex h-[calc(100vh-80px)] overflow-hidden"
      onDragOver={(e) => {
        e.preventDefault();
        setIsDragging(true);
      }}
      onDragLeave={(e) => {
        e.preventDefault();
        setIsDragging(false);
      }}
      onDrop={(e) => {
        e.preventDefault();
        setIsDragging(false);
        const file = e.dataTransfer.files?.[0];
        if (file) {
          if (file.size > 20 * 1024 * 1024) {
            toast.warning("File quá lớn", "Dung lượng tệp đính kèm không được vượt quá 20MB.");
            return;
          }
          setPendingFile(file);
        }
      }}
    >
      {isDragging && (
        <div className="absolute inset-0 bg-brand-500/10 backdrop-blur-sm z-50 flex flex-col items-center justify-center border-4 border-dashed border-brand-500 rounded-3xl m-4 pointer-events-none animate-pulse">
          <Paperclip className="h-16 w-16 text-brand-500 mb-4" />
          <h3 className="text-lg font-black text-brand-600">Thả tệp vào đây</h3>
          <p className="text-xs text-brand-500 font-light">Để tải ảnh hoặc tài liệu đính kèm vào tin nhắn</p>
        </div>
      )}
      <div className="absolute top-[10%] left-[5%] w-80 h-80 bg-brand-100/5 rounded-full blur-[100px] pointer-events-none" />
      <div className="absolute bottom-[15%] right-[5%] w-96 h-96 bg-brand-200/5 rounded-full blur-[120px] pointer-events-none" />

      <div className="max-w-7xl w-full mx-auto relative z-10 flex flex-1 overflow-hidden border-x border-zinc-200/60 dark:border-zinc-800/60">
        <aside className={`w-full md:w-80 flex flex-col bg-white dark:bg-zinc-900 shrink-0 border-r border-zinc-200/60 dark:border-zinc-800/60 transition-transform duration-300 ${
          activeConversation ? "hidden md:flex" : "flex"
        }`}>
          <div className="p-4 space-y-3.5 border-b border-zinc-100 dark:border-zinc-800/40">
            <div className="flex justify-between items-center">
              <h1 className="text-xl font-black text-zinc-900 dark:text-white tracking-tight flex items-center gap-2">
                <MessageSquare className="h-5.5 w-5.5 text-brand-500" />
                Trò chuyện
              </h1>
              
              <button
                onClick={openNewChatModal}
                className="h-8.5 w-8.5 rounded-xl bg-brand-50 hover:bg-brand-100 dark:bg-brand-950/40 dark:hover:bg-brand-900/60 text-brand-600 flex items-center justify-center transition-colors active:scale-95"
                title="Bắt đầu cuộc chat mới"
              >
                <Plus className="h-4.5 w-4.5" />
              </button>
            </div>
            <div className="relative">
              <input
                type="text"
                placeholder="Tìm tin nhắn, phòng chat..."
                value={searchConvQuery}
                onChange={(e) => setSearchConvQuery(e.target.value)}
                className="w-full h-9 pl-9 pr-4 rounded-xl bg-zinc-50 dark:bg-zinc-950 border border-zinc-200 dark:border-zinc-800 text-xs focus:outline-none focus:border-brand-500"
              />
              <Search className="absolute left-3 top-2.5 h-4 w-4 text-zinc-400" />
            </div>
          </div>

          <div className="flex-1 overflow-y-auto custom-scrollbar p-2 space-y-1">
            {isLoadingConversations ? (
              <div className="flex flex-col items-center justify-center py-10 gap-2">
                <Loader2 className="h-5 w-5 text-brand-500 animate-spin" />
                <span className="text-[10px] text-zinc-400 font-light">Đang đồng bộ cuộc trò chuyện...</span>
              </div>
            ) : filteredConversations.length === 0 ? (
              <div className="text-center py-12 px-4 space-y-2.5">
                <div className="h-10 w-10 bg-zinc-50 dark:bg-zinc-950 rounded-xl flex items-center justify-center text-zinc-400 border border-zinc-100 mx-auto">
                  <MessageSquare className="h-5 w-5" />
                </div>
                <h4 className="text-xs font-bold text-zinc-700">Chưa có tin nhắn</h4>
                <p className="text-[10px] text-zinc-450 leading-relaxed font-light">
                  Click icon dấu cộng ở trên để tìm danh bạ hoặc bắt đầu trò chuyện với Creators.
                </p>
              </div>
            ) : (
              filteredConversations.map(conv => {
                const isActive = activeConversation?.id === conv.id;
                const convTitle = getConversationTitle(conv);
                const convAvatar = getConversationAvatar(conv);
                const partnerUser = conv.members.find(m => m.id !== user?.id);
                const isOnline = partnerUser ? onlineStatusMap[partnerUser.id] === "ONLINE" : false;
                const unreadCount = user ? conv.unreadCounts?.[user.id] || 0 : 0;

                return (
                  <button
                    key={conv.id}
                    onClick={() => {
                      setActiveConversation(conv);
                      router.replace(ROUTES.MESSAGES + `?convId=${conv.id}`);
                    }}
                    className={`w-full p-3 rounded-2xl flex items-center gap-3 transition-all text-left group ${
                      isActive 
                        ? "bg-brand-50/70 border border-brand-100/50 dark:bg-brand-950/20 dark:border-brand-900/30" 
                        : "border border-transparent hover:bg-zinc-50 dark:hover:bg-zinc-950"
                    }`}
                  >
                    <div className="relative shrink-0">
                      {convAvatar ? (
                        <img 
                          src={convAvatar} 
                          alt="" 
                          className="h-11 w-11 rounded-full object-cover border" 
                        />
                      ) : (
                        <div className="h-11 w-11 rounded-full bg-brand-100 dark:bg-brand-950 flex items-center justify-center font-bold text-brand-600 text-sm border border-brand-200">
                          {convTitle.charAt(0).toUpperCase()}
                        </div>
                      )}
                      
                      {isOnline && (
                        <span className="absolute bottom-0 right-0 h-3 w-3 bg-emerald-500 border-2 border-white rounded-full" />
                      )}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex justify-between items-start mb-0.5">
                        <h4 className="text-xs font-bold text-zinc-800 dark:text-zinc-200 truncate group-hover:text-brand-600 transition-colors">
                          {convTitle}
                        </h4>
                        {conv.lastMessage && (
                          <span className="text-[9px] text-zinc-400 font-light shrink-0">
                            {formatConversationTime(conv.lastMessage.createdAt)}
                          </span>
                        )}
                      </div>

                      <div className="flex justify-between items-center gap-2">
                        <p className={`text-[10px] truncate flex-1 font-light ${
                          unreadCount > 0 
                            ? "text-zinc-900 dark:text-zinc-150 font-bold" 
                            : "text-zinc-400 dark:text-zinc-500"
                        }`}>
                          {conv.lastMessage ? (
                            conv.lastMessage.type === "IMAGE" ? "📸 [Hình ảnh]" :
                            conv.lastMessage.type === "VIDEO" ? "🎬 [Video]" :
                            conv.lastMessage.type === "DOCUMENT" ? "📎 [Tệp đính kèm]" :
                            conv.lastMessage.content
                          ) : (
                            "Bắt đầu cuộc trò chuyện..."
                          )}
                        </p>

                        {unreadCount > 0 && (
                          <span className="flex h-4 min-w-4 items-center justify-center rounded-full bg-brand-500 px-1 text-[9px] font-bold text-white shrink-0">
                            {unreadCount}
                          </span>
                        )}
                      </div>
                    </div>
                  </button>
                );
              })
            )}
          </div>
        </aside>
        <main className={`flex-1 flex flex-col bg-white dark:bg-zinc-900 transition-colors ${
          activeConversation ? "flex" : "hidden md:flex"
        }`}>
          {activeConversation ? (
            <>
              <div className="p-4 border-b border-zinc-100 dark:border-zinc-800/40 flex items-center justify-between shrink-0">
                <div className="flex items-center gap-3 min-w-0">
                  <button 
                    onClick={() => setActiveConversation(null)}
                    className="md:hidden p-1.5 border rounded-xl hover:bg-zinc-50 text-zinc-500 shrink-0"
                  >
                    <ChevronLeft className="h-4.5 w-4.5" />
                  </button>

                  <div className="relative shrink-0">
                    {getConversationAvatar(activeConversation) ? (
                      <img 
                        src={getConversationAvatar(activeConversation)} 
                        alt="" 
                        className="h-10 w-10 rounded-full object-cover border" 
                      />
                    ) : (
                      <div className="h-10 w-10 rounded-full bg-brand-100 dark:bg-brand-950 flex items-center justify-center font-extrabold text-brand-600 text-xs border border-brand-200">
                        {getConversationTitle(activeConversation).charAt(0).toUpperCase()}
                      </div>
                    )}
                  </div>

                  <div className="min-w-0">
                    <h3 className="text-xs font-bold text-zinc-900 dark:text-white truncate">
                      {getConversationTitle(activeConversation)}
                    </h3>
                    <div className="text-[9px] text-zinc-400 font-light flex items-center gap-1">
                      {typingUsers.length > 0 ? (
                        <span className="text-brand-500 font-medium animate-pulse">
                          {typingUsers.map(username => {
                            const member = activeConversation.members.find(m => m.username === username);
                            return member?.fullName || username;
                          }).join(", ")} đang soạn tin...
                        </span>
                      ) : (() => {
                        const partner = activeConversation.members.find(m => m.id !== user?.id);
                        const isOnline = partner ? onlineStatusMap[partner.id] === "ONLINE" : false;
                        return isOnline 
                          ? <span className="text-emerald-500 font-bold uppercase tracking-wider">Đang hoạt động</span>
                          : <span>Ngoại tuyến</span>;
                      })()}
                    </div>
                  </div>
                </div>
                <div className="flex gap-1 shrink-0">
                  <button
                    onClick={() => setShowMemberInfo(!showMemberInfo)}
                    className={`h-8.5 w-8.5 rounded-xl border flex items-center justify-center transition-colors ${
                      showMemberInfo 
                        ? "border-brand-500 bg-brand-50 text-brand-600" 
                        : "border-zinc-200 hover:bg-zinc-50 text-zinc-500"
                    }`}
                    title="Thông tin phòng chat"
                  >
                    <Info className="h-3.5 w-3.5" />
                  </button>
                </div>
              </div>
              {!isConnected && (
                <div className="px-4 py-2 bg-amber-50 border-b border-amber-200 flex items-center gap-2 text-amber-700 text-[10px] font-bold shrink-0 animate-pulse">
                  <WifiOff className="h-3.5 w-3.5" />
                  Đang kết nối lại... Tin nhắn sẽ được gửi khi kết nối được khôi phục.
                </div>
              )}
              <div className="flex-1 flex overflow-hidden">
              <div 
                ref={chatScrollContainerRef}
                onScroll={handleScroll}
                className="flex-1 overflow-y-auto p-4 space-y-4 flex flex-col custom-scrollbar bg-zinc-50/50 dark:bg-zinc-950/20"
              >
                {isLoadingMessages && messages.length > 0 && (
                  <div className="flex justify-center py-2">
                    <Loader2 className="h-4 w-4 text-brand-500 animate-spin" />
                  </div>
                )}


                {isLoadingMessages && messages.length === 0 ? (
                  <div className="flex flex-col items-center justify-center py-20 gap-3">
                    <Loader2 className="h-8 w-8 text-brand-500 animate-spin" />
                    <span className="text-xs text-zinc-400 font-light">Đang tải lịch sử tin nhắn...</span>
                  </div>
                ) : messages.length === 0 ? (
                  <div className="text-center py-20">
                    <div className="h-14 w-14 rounded-2xl bg-white border flex items-center justify-center text-zinc-400 mx-auto shadow-sm mb-4">
                      <MessageSquare className="h-7 w-7 text-brand-400" />
                    </div>
                    <h4 className="text-sm font-bold text-zinc-800">Khởi đầu câu chuyện</h4>
                    <p className="text-xs text-zinc-400 mt-1 max-w-xs mx-auto leading-relaxed font-light">
                      Hãy gửi lời chào đầu tiên! Kết nối, hỏi han sản phẩm, hoặc thương thảo quyền lợi Affiliate.
                    </p>
                  </div>
                ) : (
                  [...messages].reverse().map((msg, idx, arr) => {
                    const isMe = msg.senderId === user?.id;
                    const sender = activeConversation.members.find(m => m.id === msg.senderId);
                    const seenByOthers = msg.readBy?.filter(r => r.userId !== user?.id) || [];

                    // Calculate if first in block (previous message is diff sender or > 2 mins)
                    const isFirst = (() => {
                      const prevMsg = arr[idx - 1];
                      if (!prevMsg) return true;
                      if (prevMsg.senderId !== msg.senderId) return true;
                      const diffMs = new Date(msg.createdAt).getTime() - new Date(prevMsg.createdAt).getTime();
                      return diffMs > 2 * 60 * 1000;
                    })();

                    // Calculate if last in block (next message is diff sender or > 2 mins)
                    const isLast = (() => {
                      const nextMsg = arr[idx + 1];
                      if (!nextMsg) return true;
                      if (nextMsg.senderId !== msg.senderId) return true;
                      const diffMs = new Date(nextMsg.createdAt).getTime() - new Date(msg.createdAt).getTime();
                      return diffMs > 2 * 60 * 1000;
                    })();

                    const showAvatar = !isMe && isLast;
                    const showTimestamp = isLast;

                    const showDateSeparator = (() => {
                      if (idx === 0) return true;
                      const prevMsg = arr[idx - 1];
                      const diffMs = new Date(msg.createdAt).getTime() - new Date(prevMsg.createdAt).getTime();
                      return diffMs > 15 * 60 * 1000;
                    })();

                    const formatSeparatorDate = (dateStr: string) => {
                      const d = new Date(dateStr);
                      const now = new Date();
                      const isToday = d.toDateString() === now.toDateString();
                      
                      const yesterday = new Date(now);
                      yesterday.setDate(now.getDate() - 1);
                      const isYesterday = d.toDateString() === yesterday.toDateString();
                      
                      const timeStr = d.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });
                      
                      if (isToday) return timeStr;
                      if (isYesterday) return `Hôm qua, ${timeStr}`;
                      
                      const isSameYear = d.getFullYear() === now.getFullYear();
                      if (isSameYear) {
                        return `${d.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" })}, ${timeStr}`;
                      }
                      return `${d.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" })}, ${timeStr}`;
                    };

                    const getBubbleClass = () => {
                      if (isMe) {
                        if (isFirst && isLast) return "rounded-[1.5rem] rounded-tr-none";
                        if (isFirst) return "rounded-[1.5rem] rounded-tr-none rounded-br-sm";
                        if (isLast) return "rounded-[1.5rem] rounded-tr-sm";
                        return "rounded-[1.5rem] rounded-tr-sm rounded-br-sm";
                      } else {
                        if (isFirst && isLast) return "rounded-[1.5rem] rounded-tl-none";
                        if (isFirst) return "rounded-[1.5rem] rounded-tl-none rounded-bl-sm";
                        if (isLast) return "rounded-[1.5rem] rounded-tl-sm";
                        return "rounded-[1.5rem] rounded-tl-sm rounded-bl-sm";
                      }
                    };

                    return (
                      <React.Fragment key={msg.id}>
                        {showDateSeparator && (
                          <div className="w-full flex justify-center my-4 select-none shrink-0 col-span-full">
                            <span className="text-[9px] font-bold text-zinc-400 dark:text-zinc-550 uppercase tracking-wider">
                              {formatSeparatorDate(msg.createdAt)}
                            </span>
                          </div>
                        )}
                        <div 
                          className={`flex gap-3 max-w-[85%] sm:max-w-[70%] ${
                            isMe ? "self-end flex-row-reverse" : "self-start"
                          }`}
                        >
                          {!isMe && (
                            <div className="relative shrink-0 w-7 h-7">
                              {showAvatar && (
                                sender?.avatarUrl ? (
                                  <img 
                                    src={sender.avatarUrl} 
                                    alt="" 
                                    className="h-7 w-7 rounded-full object-cover border" 
                                  />
                                ) : (
                                  <div className="h-7 w-7 rounded-full bg-brand-100 flex items-center justify-center text-[10px] font-bold text-brand-600 border border-brand-200">
                                    {sender?.fullName?.charAt(0).toUpperCase() || "U"}
                                  </div>
                                )
                              )}
                            </div>
                          )}

                          <div className="flex flex-col space-y-1">
                            <div className={`p-3.5 shadow-sm relative group/bubble ${getBubbleClass()} ${
                              isMe 
                                ? "bg-brand-500 text-white" 
                                : "bg-white dark:bg-zinc-800 text-zinc-800 dark:text-zinc-150 border border-zinc-200/50 dark:border-zinc-700/50"
                            }`}>
                              {msg.type === "IMAGE" && (
                                <div className="flex flex-col gap-2">
                                  <div 
                                    className="rounded-xl overflow-hidden cursor-pointer max-w-xs border aspect-auto bg-zinc-50"
                                    onClick={() => setZoomImageUrl(msg.attachmentMetadata?.fileUrl || msg.content)}
                                  >
                                    <img 
                                      src={msg.attachmentMetadata?.fileUrl || msg.content} 
                                      alt="Attachment" 
                                      className="max-h-60 object-contain hover:opacity-90 transition-opacity" 
                                    />
                                  </div>
                                  {msg.content && msg.content !== msg.attachmentMetadata?.fileUrl && !msg.content.startsWith("http") && (
                                    <p className="text-xs leading-relaxed font-light break-words whitespace-pre-wrap mt-1">
                                      {msg.content}
                                    </p>
                                  )}
                                </div>
                              )}
                              {msg.type === "VIDEO" && (
                                <div className="flex flex-col gap-2">
                                  <div className="rounded-xl overflow-hidden max-w-xs border bg-black">
                                    <video 
                                      src={msg.attachmentMetadata?.fileUrl || msg.content} 
                                      controls 
                                      className="max-h-60 w-full object-contain"
                                      preload="metadata"
                                    >
                                      Trình duyệt không hỗ trợ phát video.
                                    </video>
                                  </div>
                                  {msg.content && msg.content !== msg.attachmentMetadata?.fileUrl && !msg.content.startsWith("http") && (
                                    <p className="text-xs leading-relaxed font-light break-words whitespace-pre-wrap mt-1">
                                      {msg.content}
                                    </p>
                                  )}
                                </div>
                              )}
                              {msg.type === "DOCUMENT" && (
                                <div className="flex flex-col gap-2">
                                  <a 
                                    href={msg.attachmentMetadata?.fileUrl || msg.content} 
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="flex items-center gap-2.5 p-2.5 rounded-xl bg-zinc-50/80 dark:bg-zinc-800/50 border border-zinc-200/50 hover:border-brand-400 transition-colors"
                                  >
                                    <div className="h-9 w-9 rounded-lg bg-sky-100 flex items-center justify-center shrink-0">
                                      <FileText className="h-4 w-4 text-sky-600" />
                                    </div>
                                    <div className="min-w-0 flex-1">
                                      <p className="text-[10px] font-bold text-zinc-800 dark:text-zinc-200 truncate">
                                        {msg.attachmentMetadata?.fileName || 'Tệp đính kèm'}
                                      </p>
                                      <p className="text-[9px] text-zinc-400 font-light">
                                        {msg.attachmentMetadata ? `${Math.round(msg.attachmentMetadata.fileSize / 1024)} KB` : 'Tải xuống'}
                                      </p>
                                    </div>
                                  </a>
                                  {msg.content && msg.content !== msg.attachmentMetadata?.fileUrl && !msg.content.startsWith("http") && (
                                    <p className="text-xs leading-relaxed font-light break-words whitespace-pre-wrap mt-1">
                                      {msg.content}
                                    </p>
                                  )}
                                </div>
                              )}
                              {msg.type === "TEXT" && (
                                <p className="text-xs leading-relaxed font-light break-words whitespace-pre-wrap">
                                  {msg.content}
                                </p>
                              )}
                            </div>
                            {showTimestamp && (
                              <div className={`flex items-center gap-1.5 text-[9px] text-zinc-400 font-light ${
                                isMe ? "justify-end" : "justify-start"
                              }`}>
                                <span>{formatMessageTime(msg.createdAt)}</span>
                                {isMe && (
                                  <span>
                                    {seenByOthers.length > 0 ? (
                                      <span className="text-brand-500 font-bold uppercase">Đã xem</span>
                                    ) : (
                                      <span className="text-zinc-400">Đã gửi</span>
                                    )}
                                  </span>
                                )}
                              </div>
                            )}
                          </div>
                        </div>
                      </React.Fragment>
                    );
                  })
                )}
                {typingUsers.length > 0 && (
                  <div className="flex gap-2.5 items-start self-start">
                    <div className="relative shrink-0">
                      {(() => {
                        const typingUsername = typingUsers[0];
                        const typingMember = activeConversation.members.find(m => m.username === typingUsername);
                        return typingMember?.avatarUrl ? (
                          <img src={typingMember.avatarUrl} alt="" className="h-7 w-7 rounded-full object-cover border" />
                        ) : (
                          <div className="h-7 w-7 rounded-full bg-brand-100 flex items-center justify-center text-[10px] font-bold text-brand-600 border border-brand-200">
                            {typingMember?.fullName?.charAt(0).toUpperCase() || "U"}
                          </div>
                        );
                      })()}
                    </div>
                    <div className="bg-white dark:bg-zinc-800 p-3 rounded-[1.5rem] rounded-tl-none border border-zinc-200/50 dark:border-zinc-700/50 shadow-sm flex items-center gap-2.5 shrink-0">
                      <span className="text-[10px] text-zinc-500 dark:text-zinc-400 font-medium">
                        {(() => {
                          const typingUsername = typingUsers[0];
                          const typingMember = activeConversation.members.find(m => m.username === typingUsername);
                          const name = typingMember?.fullName || typingUsername;
                          return name.split(" ").pop() || name;
                        })()}{" "}
                        đang soạn tin
                      </span>
                      <div className="flex items-center gap-1">
                        <span className="h-1.5 w-1.5 rounded-full bg-brand-500 animate-bounce" />
                        <span className="h-1.5 w-1.5 rounded-full bg-brand-500 animate-bounce [animation-delay:0.2s]" />
                        <span className="h-1.5 w-1.5 rounded-full bg-brand-500 animate-bounce [animation-delay:0.4s]" />
                      </div>
                    </div>
                  </div>
                )}
                <div ref={messagesEndRef} className="h-[2px] shrink-0" />
              </div>
              {showMemberInfo && activeConversation && (
                <div className="w-64 border-l border-zinc-100 dark:border-zinc-800/40 bg-white dark:bg-zinc-900 p-4 overflow-y-auto shrink-0 custom-scrollbar">
                  <h4 className="text-[10px] font-black uppercase tracking-wider text-zinc-400 mb-4">Thông tin phòng chat</h4>
                  
                  <div className="text-center mb-5">
                    {getConversationAvatar(activeConversation) ? (
                      <img src={getConversationAvatar(activeConversation)} alt="" className="h-16 w-16 rounded-full object-cover border mx-auto mb-2" />
                    ) : (
                      <div className="h-16 w-16 rounded-full bg-brand-100 flex items-center justify-center font-extrabold text-brand-600 text-lg mx-auto mb-2 border border-brand-200">
                        {getConversationTitle(activeConversation).charAt(0).toUpperCase()}
                      </div>
                    )}
                    <h5 className="text-xs font-bold text-zinc-800 dark:text-zinc-200">{getConversationTitle(activeConversation)}</h5>
                    <p className="text-[9px] text-zinc-400">Trò chuyện riêng</p>
                  </div>

                  <div className="border-t pt-3">
                    <h5 className="text-[10px] font-bold text-zinc-500 uppercase tracking-wider mb-2">Thành viên ({activeConversation.members?.length || 0})</h5>
                    <div className="space-y-2">
                      {activeConversation.members?.map(member => {
                        const isOnline = onlineStatusMap[member.id] === 'ONLINE';
                        return (
                          <div key={member.id} className="flex items-center gap-2 p-1.5 rounded-lg hover:bg-zinc-50 dark:hover:bg-zinc-800">
                            <div className="relative">
                              {member.avatarUrl ? (
                                <img src={member.avatarUrl} alt="" className="h-7 w-7 rounded-full object-cover border" />
                              ) : (
                                <div className="h-7 w-7 rounded-full bg-brand-100 flex items-center justify-center text-[9px] font-bold text-brand-600 border">
                                  {member.fullName?.charAt(0).toUpperCase() || 'U'}
                                </div>
                              )}
                              {isOnline && <span className="absolute -bottom-0.5 -right-0.5 h-2.5 w-2.5 bg-emerald-500 border-2 border-white rounded-full" />}
                            </div>
                            <div className="min-w-0 flex-1">
                              <p className="text-[10px] font-bold text-zinc-800 dark:text-zinc-200 truncate">{member.fullName}</p>
                              <p className="text-[8px] text-zinc-400">@{member.username} {member.id === user?.id ? '(bạn)' : ''}</p>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                </div>
              )}
              </div>
              <div className="p-4 border-t border-zinc-100 dark:border-zinc-800/40 shrink-0 bg-white dark:bg-zinc-900">
                {pendingFile && (
                  <div className="mb-3 bg-zinc-50 dark:bg-zinc-800/50 border dark:border-zinc-700/60 p-3 rounded-2xl flex items-center justify-between gap-3 animate-toast-in relative">
                    <div className="flex items-center gap-3">
                      {pendingFile.type.startsWith("image/") ? (
                        <div className="h-12 w-12 rounded-lg overflow-hidden border shrink-0 bg-white">
                          <img src={URL.createObjectURL(pendingFile)} alt="Preview" className="h-full w-full object-cover" />
                        </div>
                      ) : (
                        <div className="h-12 w-12 rounded-lg bg-sky-100 dark:bg-sky-950 flex items-center justify-center shrink-0">
                          <FileText className="h-6 w-6 text-sky-600" />
                        </div>
                      )}
                      <div className="min-w-0">
                        <p className="text-xs font-bold text-zinc-850 dark:text-zinc-200 truncate">{pendingFile.name}</p>
                        <p className="text-[10px] text-zinc-400 font-light">{Math.round(pendingFile.size / 1024)} KB • {pendingFile.type || "Không rõ định dạng"}</p>
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() => setPendingFile(null)}
                      className="h-8 w-8 rounded-xl bg-zinc-100 hover:bg-zinc-200 dark:bg-zinc-850 dark:hover:bg-zinc-750 text-zinc-500 flex items-center justify-center active:scale-95 transition-all"
                    >
                      <X className="h-4 w-4" />
                    </button>
                  </div>
                )}

                {isUploading && (
                  <div className="mb-3 bg-zinc-50 border p-3 rounded-xl flex items-center gap-3 text-xs text-zinc-600 animate-pulse">
                    <Loader2 className="h-4 w-4 animate-spin text-brand-500" />
                    <span>Đang tải tệp đính kèm lên đám mây... ({uploadPercent}%)</span>
                  </div>
                )}

                <form onSubmit={handleSendTextSubmit} className="flex gap-2.5 items-end">
                  <input
                    type="file"
                    ref={fileInputRef}
                    onChange={handleFileChange}
                    className="hidden"
                  />
                  <button
                    type="button"
                    onClick={handleFileUploadClick}
                    disabled={isUploading}
                    className="h-10 w-10 border rounded-xl hover:bg-zinc-50 text-zinc-400 flex items-center justify-center shrink-0 active:scale-95 transition-all"
                    title="Đính kèm tệp tin"
                  >
                    <Paperclip className="h-4.5 w-4.5" />
                  </button>
                  <div className="relative">
                    <button
                      ref={emojiButtonRef}
                      type="button"
                      onClick={() => setIsEmojiPickerOpen(!isEmojiPickerOpen)}
                      className={`h-10 w-10 border rounded-xl flex items-center justify-center shrink-0 active:scale-95 transition-all ${
                        isEmojiPickerOpen
                          ? 'bg-brand-50 border-brand-400 text-brand-600'
                          : 'hover:bg-zinc-50 text-zinc-400'
                      }`}
                      title="Chèn biểu tượng cảm xúc"
                    >
                      <Smile className="h-4.5 w-4.5" />
                    </button>
                    {isEmojiPickerOpen && (
                      <div ref={emojiPickerRef} className="absolute bottom-12 left-0 w-64 bg-white dark:bg-zinc-800 border rounded-2xl shadow-xl p-3 z-20 animate-toast-in">
                        <div className="text-[9px] font-bold text-zinc-400 uppercase tracking-wider mb-2">Biểu tượng cảm xúc</div>
                        <div className="grid grid-cols-10 gap-1">
                          {EMOJI_LIST.map((emoji, i) => (
                            <button
                              key={i}
                              type="button"
                              onClick={() => {
                                setInputText(prev => prev + emoji);
                                setIsEmojiPickerOpen(false);
                              }}
                              className="text-lg h-7 w-7 rounded-lg hover:bg-zinc-100 dark:hover:bg-zinc-700 flex items-center justify-center transition-colors"
                            >
                              {emoji}
                            </button>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                  <textarea
                    ref={textareaRef}
                    rows={1}
                    placeholder="Nhập nội dung tin nhắn thời gian thực..."
                    value={inputText}
                    onChange={handleInputChange}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" && !e.shiftKey) {
                        e.preventDefault();
                        handleSendTextSubmit(e);
                      }
                    }}
                    className="flex-1 max-h-24 p-3 rounded-2xl bg-zinc-50 border border-zinc-200 text-xs focus:outline-none focus:bg-white resize-none custom-scrollbar dark:bg-zinc-800 dark:border-zinc-700 dark:text-zinc-200 dark:focus:bg-zinc-800"
                  />
                  <button
                    type="submit"
                    disabled={(!inputText.trim() && !pendingFile) || isUploading}
                    className="h-10 w-10 rounded-xl bg-brand-500 hover:bg-brand-600 disabled:opacity-40 text-white flex items-center justify-center shrink-0 shadow shadow-brand-500/10 active:scale-95 transition-all"
                  >
                    <Send className="h-4 w-4" />
                  </button>
                </form>
              </div>
            </>
          ) : (
            <div className="flex-1 flex flex-col items-center justify-center p-6 bg-zinc-50/50 dark:bg-zinc-950/20 text-center">
              <div className="h-16 w-16 bg-white dark:bg-zinc-900 border rounded-2xl flex items-center justify-center text-zinc-300 dark:text-zinc-650 mx-auto shadow-sm mb-4 animate-bounce">
                <MessageSquare className="h-8 w-8 text-brand-500" />
              </div>
              <h3 className="text-base font-extrabold text-zinc-800 dark:text-zinc-200">Khởi đầu câu chuyện</h3>
              <p className="text-xs text-zinc-405 mt-1 max-w-sm mx-auto leading-relaxed font-light">
                Chọn một hội thoại trong danh sách bên trái hoặc click &quot;Nhắn tin&quot; trên trang cá nhân của Creator để mở rộng tương tác thời gian thực.
              </p>
              
              <button
                onClick={openNewChatModal}
                className="mt-6 px-6 py-2.5 rounded-full bg-brand-500 hover:bg-brand-600 text-white font-bold text-xs shadow-md shadow-brand-500/10"
              >
                Bắt đầu một phòng chat mới
              </button>
            </div>
          )}
        </main>

      </div>
      {isNewChatOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={() => setIsNewChatOpen(false)} />
          
          <div className="relative w-full max-w-md bg-white dark:bg-zinc-900 rounded-[2rem] p-6 shadow-2xl animate-toast-in max-h-[85vh] overflow-y-auto custom-scrollbar border">
            <button 
              onClick={() => setIsNewChatOpen(false)}
              className="absolute right-5 top-5 p-1 rounded-lg hover:bg-zinc-100"
            >
              <X className="h-4.5 w-4.5 text-zinc-400" />
            </button>

            <h3 className="text-sm font-black text-zinc-900 dark:text-white mb-5 uppercase tracking-wider flex items-center gap-2">
              <Users className="h-5 w-5 text-brand-500" />
              Tạo cuộc trò chuyện mới
            </h3>
            <div className="space-y-4">
              <div className="font-bold text-[10px] text-zinc-400 uppercase tracking-wider">Danh bạ của bạn (Người bạn theo dõi)</div>
              
              {isLoadingContacts ? (
                <div className="flex justify-center py-6">
                  <Loader2 className="h-5 w-5 text-brand-500 animate-spin" />
                </div>
              ) : contacts.length === 0 ? (
                <p className="text-xs text-zinc-400 font-light text-center py-6 leading-relaxed">
                  Bạn chưa đăng ký theo dõi (Follow) nhà sáng tạo nào để kết nối liên hệ nhanh. 
                  Hãy qua <Link href={ROUTES.FEED} className="underline font-bold text-brand-600">Bảng tin</Link> để theo dõi Creators nhé!
                </p>
              ) : (
                <div className="space-y-2 max-h-56 overflow-y-auto pr-1 custom-scrollbar">
                  {contacts.map(contact => {
                    const isSelected = selectedContactIds.includes(contact.userId);
                    return (
                      <button
                        key={contact.userId}
                        onClick={() => handleContactSelectToggle(contact.userId)}
                        className={`w-full p-2.5 rounded-xl border text-left text-xs flex justify-between items-center transition-all ${
                          isSelected
                            ? "border-brand-500 bg-brand-50/20 font-semibold"
                            : "border-zinc-200/50 hover:border-zinc-400"
                        }`}
                      >
                        <div className="flex items-center gap-2.5">
                          {contact.avatarUrl ? (
                            <img src={contact.avatarUrl} alt="" className="h-7 w-7 rounded-full object-cover border" />
                          ) : (
                            <div className="h-7 w-7 rounded-full bg-brand-100 flex items-center justify-center font-bold text-brand-600 text-[10px]">
                              {contact.fullName.charAt(0).toUpperCase()}
                            </div>
                          )}
                          <div>
                            <div className="font-bold text-zinc-800">{contact.fullName}</div>
                            <div className="text-[9px] text-zinc-400">@{contact.username}</div>
                          </div>
                        </div>
                        <div className={`h-4.5 w-4.5 rounded-full border flex items-center justify-center shrink-0 ${
                          isSelected ? "bg-brand-500 border-brand-500 text-white" : "border-zinc-300 bg-white"
                        }`}>
                          {isSelected && <span className="h-2 w-2 rounded-full bg-white" />}
                        </div>
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
            <div className="flex gap-3 mt-6 pt-4 border-t">
              <button
                type="button"
                onClick={() => setIsNewChatOpen(false)}
                className="flex-1 h-11 border text-xs font-semibold rounded-full hover:bg-zinc-50"
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                disabled={isPending || selectedContactIds.length === 0}
                onClick={handleCreateChatSubmit}
                className="flex-1 h-11 bg-brand-500 hover:bg-brand-600 disabled:opacity-50 text-white text-xs font-bold rounded-full shadow-lg shadow-brand-500/10"
              >
                {isPending ? "Đang xử lý..." : "Khởi tạo Chat"}
              </button>
            </div>
          </div>
        </div>
      )}
      {zoomImageUrl && (
        <div className="fixed inset-0 z-50 bg-black/90 flex items-center justify-center p-4">
          <button 
            onClick={() => setZoomImageUrl(null)}
            className="absolute top-6 right-6 h-10 w-10 bg-white/10 hover:bg-white/20 text-white rounded-full flex items-center justify-center transition-colors"
          >
            <X className="h-5 w-5" />
          </button>
          <img src={zoomImageUrl} alt="Attachment Zoom" className="max-w-full max-h-[90vh] object-contain rounded-lg shadow-2xl animate-scale-up" />
        </div>
      )}

    </div>
  );
}

export default function ChatPage() {
  return (
    <Suspense fallback={
      <div className="flex h-screen items-center justify-center bg-zinc-50">
        <Loader2 className="h-8 w-8 animate-spin text-zinc-500" />
      </div>
    }>
      <ChatContent />
    </Suspense>
  );
}
