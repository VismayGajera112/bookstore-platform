export interface User {
  id: number;
  username: string;
  email: string;
  role: string;
  createdAt: string;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  user: User;
}

export interface Book {
  id: number;
  title: string;
  authorId: number;
  authorName: string;
  isbn: string | null;
  price: number;
  stock: number;
  coverUrl: string | null;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface CartItem {
  bookId: number;
  title: string;
  unitPrice: number;
  quantity: number;
}

export interface PlaceOrderRequest {
  items: { bookId: number; quantity: number }[];
}

export interface OrderItem {
  bookId: number;
  bookTitle: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface Order {
  id: number;
  userId: number;
  username: string;
  status: string;
  totalAmount: number;
  stockReserved: boolean;
  stockReleasePending: boolean;
  statusReason: string | null;
  paymentId: number | null;
  items: OrderItem[];
  createdAt: string;
  updatedAt: string;
}

export interface PaymentRequest {
  orderId: number;
  cardLast4?: string;
  simulateFailure?: boolean;
}

export interface Payment {
  id: number;
  orderId: number;
  userId: number;
  amount: number;
  status: string;
  cardLast4: string | null;
  failureReason: string | null;
  orderNotified: boolean;
  createdAt: string;
}

export interface ApiError {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
  service?: string;
}
