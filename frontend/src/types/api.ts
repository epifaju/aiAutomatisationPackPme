export type ApiError = {
  code: string;
  message: string;
  details?: Record<string, unknown>;
};

export type ApiResponse<T> = {
  success: boolean;
  data: T;
  error: ApiError | null;
  timestamp: string;
};

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type TokenResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
};

export type UserMe = {
  id: string;
  email: string;
  fullName: string;
  role: string;
  companyId: string;
  companyName: string;
};

export type ReportMetrics = {
  emailsReceived: number;
  emailsUrgent: number;
  newLeads: number;
  priorityLeads: number;
  documentsProcessed: number;
  documentsInError: number;
  overdueInvoices: number;
  overdueAmount: number | string;
  currency: string;
  remindersSent: number;
  automationsExecuted: number;
  automationErrors: number;
};

export type AuditLog = {
  id: string;
  companyId: string;
  workflow: string;
  action: string;
  entityType: string;
  entityId: string | null;
  status: string;
  timestamp: string;
  metadata: Record<string, unknown>;
};

export type DashboardSummary = {
  date: string;
  metrics: ReportMetrics;
  recentActivity: AuditLog[];
};

export type EmailAnalysis = {
  id: string;
  category: string;
  priority: string;
  intent: string | null;
  summary: string | null;
  suggestedReply: string | null;
  confidenceScore: number | string | null;
  status: string;
  approvalStatus: string | null;
};

export type EmailAttachment = {
  id: string;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  checksumSha256: string | null;
};

export type Email = {
  id: string;
  subject: string;
  fromAddress: string;
  toAddress: string;
  bodyText: string | null;
  receivedAt: string;
  status: string;
  analysis: EmailAnalysis | null;
  attachments?: EmailAttachment[];
  autoSendEnabled: boolean;
};

export type Lead = {
  id: string;
  source: string;
  status: string;
  email: string | null;
  fullName: string;
  companyName: string | null;
  phone: string | null;
  score: number;
  scoreBand: string | null;
  summary: string | null;
  probableNeed: string | null;
  urgency: string | null;
  recommendedAction: string | null;
  aiStatus: string | null;
  confidenceScore: number | string | null;
  createdAt: string;
};

export type LeadImportError = {
  row: number;
  message: string;
};

export type LeadImportResponse = {
  imported: number;
  failed: number;
  totalRows: number;
  leadIds: string[];
  errors: LeadImportError[];
};

export type DocumentExtraction = {
  id: string;
  extractedJson: Record<string, unknown> | null;
  confidenceScore: number | string | null;
  status: string;
};

export type Document = {
  id: string;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  documentType: string;
  status: string;
  extraction: DocumentExtraction | null;
  createdAt: string;
};

export type Customer = {
  id: string;
  name: string;
  email: string | null;
  phone: string | null;
  address: string | null;
};

export type InvoiceReminder = {
  id: string;
  reminderLevel: number;
  status: string;
  scheduledAt: string | null;
  sentAt: string | null;
};

export type Invoice = {
  id: string;
  invoiceNumber: string;
  invoiceDate: string;
  dueDate: string;
  amountIncludingTax: number | string;
  currency: string;
  status: string;
  daysOverdue: number;
  autoSendEnabled: boolean;
  customer: { id: string; name: string; email: string | null };
  reminders: InvoiceReminder[] | null;
};

export type Automation = {
  id: string;
  name: string;
  description: string;
  workflows: string[];
  historyPath: string;
  enabled: boolean;
  autoSendCompany: boolean;
  autoSendEffective: boolean;
  runnable: boolean;
  executionsToday: number;
  errorsToday: number;
};

export type Settings = {
  company: { id: string; name: string; siret: string | null; country: string; timezone: string };
  email: { autoSendEnv: boolean; autoSendCompany: boolean; confidenceThreshold: number | string };
  ai: {
    runtimeProvider: string;
    runtimeOllamaBaseUrl: string;
    runtimeOllamaModel: string;
    cacheEnabled: boolean;
    provider: string;
    ollamaBaseUrl: string;
    ollamaModel: string;
  };
  invoices: {
    autoSendEnv: boolean;
    autoSendCompany: boolean;
    schedulerEnabled: boolean;
    reminderDays: number[];
  };
  reports: {
    autoSendEnv: boolean;
    autoSendCompany: boolean;
    schedulerEnabled: boolean;
    dailyReportEmail: string | null;
  };
  leads: {
    scoreLowMax: number;
    scoreMediumMax: number;
    scoreHighMax: number;
    confidenceThreshold: number | string;
  };
  documents: { confidenceThreshold: number | string };
  security: { dataRetentionDays: number };
};
