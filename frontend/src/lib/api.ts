import axios, { AxiosError, type AxiosInstance } from 'axios';
import type {
  AdminStats,
  AdminUser,
  AppNotification,
  ApiErrorBody,
  AuctionAnalytics,
  AuctionDetail,
  AuctionQuery,
  AuctionStatus,
  AuctionSummary,
  AuditEvent,
  AuthResponse,
  Bid,
  BidResult,
  CategoryOption,
  CreateAuctionPayload,
  DashboardStats,
  PageResponse,
  Role,
  SellerStats,
  AssistantAnswer,
  PlatformAnalytics,
  AssistantCapabilities,
  ChatMessage,
  Conversation,
  UploadedImage,
  User,
} from '../types';
import { observeServerTime } from './serverTime';

const TOKEN_KEY = 'auctix.token';

export function readToken(): string | null {
  try {
    return window.localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function writeToken(token: string | null): void {
  try {
    if (token) window.localStorage.setItem(TOKEN_KEY, token);
    else window.localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* Private browsing or blocked storage: the session simply lives in memory. */
  }
}

/**
 * Turns any failure into a message worth showing.
 *
 * The backend's own errors always carry a `message`, so those are used verbatim. Anything else —
 * an HTML page from the reverse proxy, a gateway timeout, a framework default — would otherwise
 * collapse into a useless "something went wrong", so the status code is reported instead along
 * with what to check.
 */
function describeFailure(body: Partial<ApiErrorBody>, status: number): string {
  if (typeof body.message === 'string' && body.message.trim()) {
    return body.message;
  }
  switch (true) {
    case status === 0:
      return 'Cannot reach the AuctiX server. Is the backend running?';
    case status === 404:
      return 'The API endpoint was not found (HTTP 404). Check that /api is proxied to the backend.';
    case status === 429:
      return 'Too many requests. Please slow down and try again.';
    case status === 502 || status === 503 || status === 504:
      return `The API is not responding (HTTP ${status}). The backend may still be starting — wait a few seconds and try again.`;
    case status >= 500:
      return `The server returned an error (HTTP ${status}). Run: docker compose logs backend`;
    default:
      return `The request failed (HTTP ${status}).`;
  }
}

function fallbackCode(status: number): string {
  if (status === 0) return 'NETWORK_ERROR';
  if (status === 502 || status === 503 || status === 504) return 'GATEWAY_ERROR';
  if (status >= 500) return 'SERVER_ERROR';
  return 'UNKNOWN';
}

/** Normalised error the UI can show directly. */
export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly details: Record<string, unknown>;

  constructor(body: unknown, status: number) {
    // A proxy error page arrives as an HTML string, not the JSON envelope.
    const envelope: Partial<ApiErrorBody> =
      body !== null && typeof body === 'object' ? (body as Partial<ApiErrorBody>) : {};
    super(describeFailure(envelope, status));
    this.name = 'ApiError';
    this.code = envelope.code ?? fallbackCode(status);
    this.status = status;
    this.details = (envelope.details as Record<string, unknown>) ?? {};
  }
}

const baseURL = import.meta.env.VITE_API_BASE_URL || '';

export const http: AxiosInstance = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 20000,
});

http.interceptors.request.use((config) => {
  const token = readToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let onUnauthenticated: (() => void) | null = null;

/** Lets the auth store clear its state when a token expires mid-session. */
export function setUnauthenticatedHandler(handler: () => void): void {
  onUnauthenticated = handler;
}

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorBody>) => {
    if (!error.response) {
      return Promise.reject(
        new ApiError({ code: 'NETWORK_ERROR', message: 'Cannot reach the AuctiX server.' }, 0),
      );
    }
    const { status, data } = error.response;
    if (status === 401 && readToken()) {
      writeToken(null);
      onUnauthenticated?.();
    }
    return Promise.reject(new ApiError(data ?? {}, status));
  },
);

function query(params: Record<string, unknown>): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  );
}

export const api = {
  // ---------------------------------------------------------------- system
  async serverTime(): Promise<{ serverTime: string; epochMillis: number }> {
    const { data } = await http.get('/api/system/time');
    observeServerTime(data.serverTime);
    return data;
  },

  async categories(): Promise<CategoryOption[]> {
    const { data } = await http.get<CategoryOption[]>('/api/categories');
    return data;
  },

  // ---------------------------------------------------------------- auth
  async login(email: string, password: string): Promise<AuthResponse> {
    const { data } = await http.post<AuthResponse>('/api/auth/login', { email, password });
    return data;
  },

  async register(payload: {
    fullName: string;
    email: string;
    password: string;
    role: Role;
  }): Promise<AuthResponse> {
    const { data } = await http.post<AuthResponse>('/api/auth/register', payload);
    return data;
  },

  async me(): Promise<User> {
    const { data } = await http.get<User>('/api/auth/me');
    return data;
  },

  async updateProfile(payload: {
    fullName?: string;
    bio?: string;
    avatarUrl?: string;
  }): Promise<User> {
    const { data } = await http.put<User>('/api/users/me', payload);
    return data;
  },

  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    await http.post('/api/users/me/password', { currentPassword, newPassword });
  },

  // ---------------------------------------------------------------- analytics
  async platformAnalytics(days = 30): Promise<PlatformAnalytics> {
    const { data } = await http.get<PlatformAnalytics>('/api/admin/analytics', {
      params: { days },
    });
    return data;
  },

  // ---------------------------------------------------------------- assistant
  async assistantCapabilities(): Promise<AssistantCapabilities> {
    const { data } = await http.get<AssistantCapabilities>('/api/assistant/capabilities');
    return data;
  },

  /** The question goes to the server; no provider key ever reaches the browser. */
  async askAssistant(question: string, auctionId?: number | null): Promise<AssistantAnswer> {
    const { data } = await http.post<AssistantAnswer>('/api/assistant/ask', {
      question,
      auctionId: auctionId ?? null,
    });
    return data;
  },

  // ---------------------------------------------------------------- messaging
  async conversations(): Promise<Conversation[]> {
    const { data } = await http.get<Conversation[]>('/api/messages/conversations');
    return data;
  },

  /** Returns the existing thread with this person about this lot, or opens one. */
  async startConversation(recipientId: number, auctionId?: number | null): Promise<Conversation> {
    const { data } = await http.post<Conversation>('/api/messages/conversations', {
      recipientId,
      auctionId: auctionId ?? null,
    });
    return data;
  },

  async messages(conversationId: number): Promise<ChatMessage[]> {
    const { data } = await http.get<ChatMessage[]>(`/api/messages/conversations/${conversationId}`);
    return data;
  },

  async sendMessage(conversationId: number, body: string): Promise<ChatMessage> {
    const { data } = await http.post<ChatMessage>(
      `/api/messages/conversations/${conversationId}`,
      { body },
    );
    return data;
  },

  async markConversationRead(conversationId: number): Promise<number> {
    const { data } = await http.post<{ marked: number }>(
      `/api/messages/conversations/${conversationId}/read`,
      {},
    );
    return data.marked;
  },

  async unreadMessageCount(): Promise<number> {
    const { data } = await http.get<{ count: number }>('/api/messages/unread-count');
    return data.count;
  },

  // ---------------------------------------------------------------- auctions
  async auctions(params: AuctionQuery = {}): Promise<PageResponse<AuctionSummary>> {
    const { data } = await http.get<PageResponse<AuctionSummary>>('/api/auctions', {
      params: query({ ...params }),
    });
    if (data.content[0]) observeServerTime(data.content[0].serverTime);
    return data;
  },

  async featured(limit = 8): Promise<AuctionSummary[]> {
    const { data } = await http.get<AuctionSummary[]>('/api/auctions/featured', { params: { limit } });
    if (data[0]) observeServerTime(data[0].serverTime);
    return data;
  },

  async auction(id: number): Promise<AuctionDetail> {
    const { data } = await http.get<AuctionDetail>(`/api/auctions/${id}`);
    observeServerTime(data.serverTime);
    return data;
  },

  /**
   * Uploads photographs and returns their server-relative URLs.
   *
   * Content-Type is cleared deliberately: the shared axios instance defaults to
   * application/json, and multipart needs a boundary that only axios can generate once it sees
   * the FormData. Setting the header by hand would send a boundary-less type the server cannot
   * parse.
   */
  async uploadImages(files: File[]): Promise<UploadedImage[]> {
    const body = new FormData();
    files.forEach((file) => body.append('files', file));
    const { data } = await http.post<UploadedImage[]>('/api/uploads/images', body, {
      headers: { 'Content-Type': undefined },
    });
    return data;
  },

  async createAuction(payload: CreateAuctionPayload): Promise<AuctionDetail> {
    const { data } = await http.post<AuctionDetail>('/api/auctions', payload);
    return data;
  },

  async updateAuction(id: number, payload: Partial<CreateAuctionPayload>): Promise<AuctionDetail> {
    const { data } = await http.put<AuctionDetail>(`/api/auctions/${id}`, payload);
    return data;
  },

  async cancelAuction(id: number): Promise<void> {
    await http.delete(`/api/auctions/${id}`);
  },

  // ---------------------------------------------------------------- bidding
  async placeBid(auctionId: number, amount: number, maxAmount?: number | null): Promise<BidResult> {
    const { data } = await http.post<BidResult>(`/api/auctions/${auctionId}/bids`, {
      amount,
      maxAmount: maxAmount ?? null,
      clientTimestamp: new Date().toISOString(),
    });
    observeServerTime(data.state?.serverTime);
    return data;
  },

  async bidHistory(auctionId: number, page = 0, size = 25): Promise<PageResponse<Bid>> {
    const { data } = await http.get<PageResponse<Bid>>(`/api/auctions/${auctionId}/bids`, {
      params: { page, size },
    });
    return data;
  },

  async toggleWatch(auctionId: number): Promise<{ auctionId: number; watched: boolean }> {
    const { data } = await http.post(`/api/auctions/${auctionId}/watchlist`);
    return data;
  },

  // ---------------------------------------------------------------- bidder dashboard
  async dashboard(): Promise<DashboardStats> {
    const { data } = await http.get<DashboardStats>('/api/users/me/dashboard');
    return data;
  },

  async myBids(page = 0, size = 25): Promise<PageResponse<Bid>> {
    const { data } = await http.get<PageResponse<Bid>>('/api/users/me/bids', { params: { page, size } });
    return data;
  },

  async myActiveAuctions(): Promise<AuctionSummary[]> {
    const { data } = await http.get<AuctionSummary[]>('/api/users/me/active');
    return data;
  },

  async myWins(): Promise<AuctionSummary[]> {
    const { data } = await http.get<AuctionSummary[]>('/api/users/me/wins');
    return data;
  },

  async myLosses(): Promise<AuctionSummary[]> {
    const { data } = await http.get<AuctionSummary[]>('/api/users/me/losses');
    return data;
  },

  async watchlist(): Promise<AuctionSummary[]> {
    const { data } = await http.get<AuctionSummary[]>('/api/users/me/watchlist');
    return data;
  },

  // ---------------------------------------------------------------- notifications
  async notifications(page = 0, size = 20): Promise<PageResponse<AppNotification>> {
    const { data } = await http.get<PageResponse<AppNotification>>('/api/notifications', {
      params: { page, size },
    });
    return data;
  },

  async unreadCount(): Promise<number> {
    const { data } = await http.get<{ unread: number }>('/api/notifications/unread-count');
    return data.unread;
  },

  async markNotificationRead(id: number): Promise<AppNotification> {
    const { data } = await http.post<AppNotification>(`/api/notifications/${id}/read`);
    return data;
  },

  async markAllNotificationsRead(): Promise<void> {
    await http.post('/api/notifications/read-all');
  },

  // ---------------------------------------------------------------- seller
  async sellerAuctions(page = 0, size = 20): Promise<PageResponse<AuctionSummary>> {
    const { data } = await http.get<PageResponse<AuctionSummary>>('/api/seller/auctions', {
      params: { page, size },
    });
    return data;
  },

  async sellerStats(): Promise<SellerStats> {
    const { data } = await http.get<SellerStats>('/api/seller/stats');
    return data;
  },

  async auctionAnalytics(id: number): Promise<AuctionAnalytics> {
    const { data } = await http.get<AuctionAnalytics>(`/api/seller/auctions/${id}/analytics`);
    return data;
  },

  // ---------------------------------------------------------------- admin
  async adminStats(): Promise<AdminStats> {
    const { data } = await http.get<AdminStats>('/api/admin/stats');
    return data;
  },

  async adminUsers(params: { role?: Role; query?: string; page?: number; size?: number } = {}): Promise<
    PageResponse<AdminUser>
  > {
    const { data } = await http.get<PageResponse<AdminUser>>('/api/admin/users', {
      params: query({ ...params }),
    });
    return data;
  },

  async adminSetUserStatus(id: number, status: 'ACTIVE' | 'SUSPENDED', reason?: string): Promise<AdminUser> {
    const { data } = await http.patch<AdminUser>(`/api/admin/users/${id}/status`, { status, reason });
    return data;
  },

  async adminSetUserRole(id: number, role: Role): Promise<AdminUser> {
    const { data } = await http.patch<AdminUser>(`/api/admin/users/${id}/role`, { role });
    return data;
  },

  async adminAuctions(
    params: { status?: AuctionStatus; search?: string; page?: number; size?: number } = {},
  ): Promise<PageResponse<AuctionSummary>> {
    const { data } = await http.get<PageResponse<AuctionSummary>>('/api/admin/auctions', {
      params: query({ ...params }),
    });
    return data;
  },

  async adminPending(page = 0, size = 20): Promise<PageResponse<AuctionSummary>> {
    const { data } = await http.get<PageResponse<AuctionSummary>>('/api/admin/auctions/pending', {
      params: { page, size },
    });
    return data;
  },

  async adminApprove(id: number): Promise<void> {
    await http.post(`/api/admin/auctions/${id}/approve`);
  },

  async adminReject(id: number, reason: string): Promise<void> {
    await http.post(`/api/admin/auctions/${id}/reject`, { reason });
  },

  async adminCancel(id: number): Promise<void> {
    await http.post(`/api/admin/auctions/${id}/cancel`);
  },

  async adminAudit(params: { action?: string; page?: number; size?: number } = {}): Promise<
    PageResponse<AuditEvent>
  > {
    const { data } = await http.get<PageResponse<AuditEvent>>('/api/admin/audit', {
      params: query({ ...params }),
    });
    return data;
  },

  async adminActivityReport(): Promise<{ action: string; count: number }[]> {
    const { data } = await http.get<{ action: string; count: number }[]>('/api/admin/reports/activity');
    return data;
  },
};
