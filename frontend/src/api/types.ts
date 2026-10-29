// The shapes of the ledger API's JSON (see /swagger-ui.html). Amounts are always integer cents.

export type AccountType = 'CUSTOMER' | 'SYSTEM';
export type TransactionType = 'DEPOSIT' | 'TRANSFER' | 'REVERSAL' | 'TRADE';
export type TradeSide = 'BUY' | 'SELL';

export interface Account {
  id: string;
  ownerName: string;
  type: AccountType;
  currency: string;
  balance: number;
  status: 'ACTIVE' | 'CLOSED';
  createdAt: string;
}

export interface AccountPage {
  accounts: Account[];
  page: number;
  size: number;
  totalAccounts: number;
  totalPages: number;
}

export interface StatementLine {
  entryId: string;
  transactionId: string;
  transactionType: TransactionType;
  description: string | null;
  amount: number;
  createdAt: string;
  /** Set if this transaction was reversed: the reversal's transaction id. */
  reversedByTransactionId: string | null;
}

export interface Statement {
  accountId: string;
  entries: StatementLine[];
  page: number;
  size: number;
  totalEntries: number;
  totalPages: number;
}

export interface Transaction {
  id: string;
  type: TransactionType;
  description: string | null;
  reversesTransactionId?: string;
  createdAt: string;
  entries: { accountId: string; amount: number }[];
}

export interface BalanceHistory {
  accountId: string;
  points: { date: string; balance: number }[];
}

export interface MonthlySummary {
  accountId: string;
  months: { month: string; moneyIn: number; moneyOut: number }[];
}

export interface Instrument {
  symbol: string;
  name: string;
  currency: string;
  lastPrice: number | null;
  priceAsOf: string | null;
}

export interface Price {
  symbol: string;
  price: number;
  createdAt: string;
}

export interface Position {
  symbol: string;
  name: string;
  quantity: number;
  costBasis: number;
  lastPrice: number;
  priceAsOf: string;
  marketValue: number;
  unrealizedPnl: number;
  realizedPnl: number;
}

export interface Portfolio {
  accountId: string;
  currency: string;
  cash: number;
  positions: Position[];
  marketValue: number;
  costBasis: number;
  unrealizedPnl: number;
  realizedPnl: number;
  totalValue: number;
}

export interface Trade {
  id: string;
  accountId: string;
  symbol: string;
  side: TradeSide;
  quantity: number;
  price: number;
  grossAmount: number;
  transactionId: string;
  createdAt: string;
}

export interface LedgerHealth {
  healthy: boolean;
  sumOfAllEntries: number;
  balanceMismatches: { accountId: string; cachedBalance: number; entriesSum: number }[];
}

/** RFC 7807 Problem Details, as the API returns them for errors. */
export interface Problem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
}
