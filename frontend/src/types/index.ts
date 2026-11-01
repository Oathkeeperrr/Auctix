/**
 * Mirrors the backend DTOs. These are the only shapes the UI is allowed to rely on:
 * every price, countdown and status comes from the server.
 */

export type Role = 'BIDDER' | 'SELLER' | 'ADMIN';
export type UserStatus = 'ACTIVE' | 'SUSPENDED';

export type AuctionStatus =
  | 'PENDING_APPROVAL'
  | 'REJECTED'
  | 'SCHEDULED'
  | 'LIVE'
  | 'ENDING'
  | 'CLOSED'
  | 'SETTLED'
  | 'ARCHIVED'
  | 'CANCELLED';

export type ModelKind = 'WATCH' | 'CAR' | 'SNEAKER' | 'ARTWORK' | 'COLLECTIBLE';

export type BidStatus = 'WINNING' | 'OUTBID' | 'WON' | 'LOST';

export interface User {
  id: number;
  fullName: string;
  email: string;
  role: Role;
  status: UserStatus;
  avatarUrl?: string | null;
  bio?: string | null;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresInMinutes: number;
  user: User;
}

export interface CategoryOption {
  name: string;
  label: string;
  modelKind: ModelKind;
}

export interface AuctionSummary {
  id: number;
  title: string;
  category: string;
  categoryLabel: string;
  modelKind: ModelKind;
  imageUrl?: string | null;
  status: AuctionStatus;
  startingPrice: number;
  currentPrice: number;
  nextMinimumBid: number;
  bidCount: number;
  startTime: string;
  endTime: string;
  serverTime: string;
  secondsRemaining: number;
  sellerName: string;
  watched: boolean;
  watchCount: number;
}

export interface Bid {
  id: number;
  auctionId: number;
  bidderId: number;
  bidderMasked: string;
  amount: number;
  automatic: boolean;
  status: BidStatus;
  serverTimestamp: string;
}

export interface AuctionState {
  auctionId: number;
  status: AuctionStatus;
  currentPrice: number;
  nextMinimumBid: number;
  minIncrement: number;
  bidCount: number;
  distinctBidders: number;
  highestBidderId?: number | null;
  highestBidderMasked?: string | null;
  startTime: string;
  endTime: string;
  serverTime: string;
  secondsRemaining: number;
  extensionCount: number;
  reserveMet: boolean;
}

/** One frame of a lot's gallery. `url` is server-relative, e.g. /images/lots/submariner-1.jpg */
export interface ItemImage {
  url: string;
  caption?: string | null;
  sortOrder: number;
}

export interface AuctionDetail {
  id: number;
  title: string;
  description: string;
  category: string;
  categoryLabel: string;
  modelKind: ModelKind;
  /** Single cover, kept for catalogue tiles. The gallery below is the full set. */
  imageUrl?: string | null;
  /** Ordered gallery; absent or empty when the lot has no photographs yet. */
  images?: ItemImage[];
  brand?: string | null;
  conditionLabel?: string | null;
  status: AuctionStatus;
  startingPrice: number;
  currentPrice: number;
  nextMinimumBid: number;
  minIncrement: number;
  reservePrice?: number | null;
  reserveMet: boolean;
  bidCount: number;
  distinctBidders: number;
  highestBidderId?: number | null;
  highestBidderMasked?: string | null;
  startTime: string;
  endTime: string;
  originalEndTime: string;
  serverTime: string;
  secondsRemaining: number;
  extensionCount: number;
  sellerId: number;
  sellerName: string;
  sellerJoinedAt: string;
  winnerMasked?: string | null;
  winningAmount?: number | null;
  watched: boolean;
  watchCount: number;
  antiSnipeEnabled: boolean;
  antiSnipeWindowSeconds: number;
  antiSnipeExtensionSeconds: number;
  recentBids: Bid[];
}

export interface BidResult {
  accepted: boolean;
  message: string;
  bid?: Bid | null;
  state: AuctionState;
}

export type NotificationType =
  | 'BID_ACCEPTED'
  | 'OUTBID'
  | 'AUCTION_STARTED'
  | 'AUCTION_EXTENDED'
  | 'AUCTION_ENDING'
  | 'AUCTION_CLOSED'
  | 'AUCTION_WON'
  | 'AUCTION_LOST'
  | 'AUCTION_SOLD'
  | 'LISTING_APPROVED'
  | 'LISTING_REJECTED'
  | 'ACCOUNT_SUSPENDED'
  | 'SYSTEM';

export interface AppNotification {
  id: number;
  type: NotificationType;
  title: string;
  message: string;
  auctionId?: number | null;
  read: boolean;
  createdAt: string;
}

export type AuctionEventType =
  | 'BID_PLACED'
  | 'BID_REJECTED'
  | 'OUTBID'
  | 'AUCTION_STARTED'
  | 'AUCTION_EXTENDED'
  | 'AUCTION_ENDING'
  | 'AUCTION_CLOSED'
  | 'AUCTION_SETTLED'
  | 'AUCTION_WON'
  | 'AUCTION_APPROVED'
  | 'AUCTION_REJECTED'
  | 'NOTIFICATION'
  | 'WATCHERS';

/** The payload delivered over STOMP, published by the backend through Redis pub/sub. */
export interface AuctionEvent {
  type: AuctionEventType;
  auctionId?: number | null;
  serverTime: string;
  state?: AuctionState | null;
  bid?: Bid | null;
  notification?: AppNotification | null;
  message?: string | null;
  targetUser?: string | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface DashboardStats {
  activeBids: number;
  auctionsWon: number;
  auctionsLost: number;
  watchlistSize: number;
  totalBids: number;
  unreadNotifications: number;
  totalWinningValue: number;
}

export interface SellerStats {
  totalAuctions: number;
  pendingApproval: number;
  scheduled: number;
  live: number;
  closed: number;
  settled: number;
  rejected: number;
  totalBidsReceived: number;
  totalRevenue: number;
}

export interface AdminStats {
  totalUsers: number;
  bidders: number;
  sellers: number;
  admins: number;
  suspendedUsers: number;
  totalAuctions: number;
  pendingApprovals: number;
  liveAuctions: number;
  scheduledAuctions: number;
  closedAuctions: number;
  settledAuctions: number;
  totalBids: number;
  settledValue: number;
}

export interface AdminUser {
  id: number;
  fullName: string;
  email: string;
  role: Role;
  status: UserStatus;
  createdAt: string;
  lastLoginAt?: string | null;
  auctionsCreated: number;
  bidsPlaced: number;
}

export interface AuditEvent {
  id: number;
  actorId?: number | null;
  actorEmail?: string | null;
  action: string;
  entityType?: string | null;
  entityId?: number | null;
  details?: string | null;
  createdAt: string;
}

export interface AuctionAnalytics {
  auctionId: number;
  title: string;
  status: AuctionStatus;
  bidCount: number;
  distinctBidders: number;
  startingPrice: number;
  currentPrice: number;
  uplift: number;
  timeline: { epochSeconds: number; amount: number }[];
}

/** Error envelope produced by the backend's GlobalExceptionHandler. */
export interface ApiErrorBody {
  code: string;
  message: string;
  status: number;
  path: string;
  timestamp: string;
  details?: Record<string, unknown>;
}

/** One day of a daily series. Days with no activity are present with a count of 0. */
export interface DailyPoint {
  date: string;
  count: number;
}

export interface AnalyticsSlice {
  key: string;
  label: string;
  count: number;
}

export interface SellerPerformanceRow {
  sellerId: number;
  name: string;
  lots: number;
  settled: number;
  revenue: number;
}

/** Everything the analytics screen draws. Every figure is counted from real rows. */
export interface PlatformAnalytics {
  generatedAt: string;
  days: number;
  bidsPerDay: DailyPoint[];
  auctionsPerDay: DailyPoint[];
  signUpsPerDay: DailyPoint[];
  byCategory: AnalyticsSlice[];
  byStatus: AnalyticsSlice[];
  topSellers: SellerPerformanceRow[];
}

/** A lot the assistant named, so it can be opened directly. */
export interface AssistantReference {
  auctionId: number;
  title: string;
  categoryLabel: string;
  currentPrice: number;
  bidCount: number;
  status: AuctionStatus;
}

export interface AssistantAnswer {
  answer: string;
  /** 'RULES' means a documented rule engine, not a language model. */
  engine: string;
  engineLabel: string;
  suggestions: string[];
  references: AssistantReference[];
}

export interface AssistantCapabilities {
  engine: string;
  engineLabel: string;
  modelConfigured: boolean;
  suggestions: string[];
}

/** A private thread between two people, optionally about one lot. */
export interface Conversation {
  id: number;
  otherPartyId: number;
  otherPartyName: string;
  otherPartyAvatarUrl?: string | null;
  otherPartyLastSeen?: string | null;
  auctionId?: number | null;
  auctionTitle?: string | null;
  lastMessage?: string | null;
  lastMessageSenderId?: number | null;
  lastMessageAt: string;
  unreadCount: number;
}

export interface ChatMessage {
  id: number;
  conversationId: number;
  senderId: number;
  senderName: string;
  body: string;
  sentAt: string;
  /** Null until the recipient opens the thread. */
  readAt?: string | null;
  mine: boolean;
}

/** Pushed to /user/queue/messages. */
export interface MessagingEvent {
  type: 'MESSAGE' | 'READ';
  conversationId: number;
  message?: ChatMessage | null;
  byUserId: number;
  at: string;
}

/** What POST /api/uploads/images returns for each stored file. */
export interface UploadedImage {
  url: string;
  originalName: string;
  sizeBytes: number;
}

export interface CreateAuctionPayload {
  title: string;
  description: string;
  category: string;
  modelKind?: ModelKind;
  imageUrl?: string;
  brand?: string;
  conditionLabel?: string;
  startingPrice: number;
  reservePrice?: number | null;
  minIncrement: number;
  startTime: string;
  endTime: string;
  /** Uploaded image URLs in display order; index 0 becomes the catalogue cover. */
  imageUrls?: string[];
}

export interface AuctionQuery {
  search?: string;
  category?: string;
  status?: AuctionStatus;
  minPrice?: number;
  maxPrice?: number;
  sort?: string;
  page?: number;
  size?: number;
}
