import type {
  Account,
  AccountPage,
  BalanceHistory,
  Instrument,
  LedgerHealth,
  MonthlySummary,
  Portfolio,
  Price,
  Problem,
  Statement,
  Trade,
  TradeSide,
  Transaction,
} from './types';

/** An error answer from the API. {@code body} is the parsed JSON (usually Problem Details). */
export class ApiError extends Error {
  readonly status: number;
  readonly body: unknown;

  constructor(status: number, body: unknown) {
    const problem = (body ?? {}) as Problem;
    super(problem.detail || problem.title || `HTTP ${status}`);
    this.status = status;
    this.body = body;
  }

  get problem(): Problem {
    return (this.body ?? {}) as Problem;
  }
}

/** No answer at all (network down, server unreachable): the request may or may not have happened. */
export class NetworkError extends Error {}

async function request<T>(method: string, path: string, body?: unknown, headers: Record<string, string> = {}) {
  let response: Response;
  try {
    response = await fetch(path, {
      method,
      headers: { ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}), ...headers },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new NetworkError('No answer from the server. The request may or may not have been processed.');
  }
  const text = await response.text();
  const data: unknown = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new ApiError(response.status, data);
  }
  return { data: data as T, headers: response.headers };
}

function newIdempotencyKey(): string {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  // Fallback for browsers that only offer randomUUID on https pages.
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return Array.from(bytes, (b: number) => b.toString(16).padStart(2, '0')).join('');
}

/**
 * One money-moving operation the user intends to do, e.g. "transfer 25,00 € to Bob".
 *
 * The Idempotency-Key is created once per operation and kept until the server gives a definite
 * answer. If the network fails, sending again reuses the same key, so the server books the
 * operation at most once, even if the first attempt did arrive. When the user changes the
 * operation (edits the form), call reset(): it is a new operation and needs a new key.
 */
export class MoneyOperation {
  private key: string | null = null;

  get pendingKey(): string | null {
    return this.key;
  }

  reset(): void {
    this.key = null;
  }

  async send<T>(path: string, body?: unknown): Promise<{ result: T; replayed: boolean }> {
    this.key ??= newIdempotencyKey();
    try {
      const { data, headers } = await request<T>('POST', path, body, { 'Idempotency-Key': this.key });
      this.key = null; // definite answer: done
      return { result: data, replayed: headers.get('Idempotent-Replayed') === 'true' };
    } catch (e) {
      if (!(e instanceof NetworkError)) {
        this.key = null; // the server answered (e.g. 422): a definite "no", nothing was booked
      }
      throw e;
    }
  }
}

const enc = encodeURIComponent;

export const api = {
  listAccounts: () => request<AccountPage>('GET', '/accounts?size=100').then((r) => r.data),
  getAccount: (id: string) => request<Account>('GET', `/accounts/${enc(id)}`).then((r) => r.data),
  openAccount: (ownerName: string) =>
    request<Account>('POST', '/accounts', { ownerName, currency: 'EUR' }).then((r) => r.data),
  getStatement: (id: string, page: number, size: number) =>
    request<Statement>('GET', `/accounts/${enc(id)}/entries?page=${page}&size=${size}`).then((r) => r.data),
  getBalanceHistory: (id: string, days: number) =>
    request<BalanceHistory>('GET', `/accounts/${enc(id)}/balance-history?days=${days}`).then((r) => r.data),
  getMonthlySummary: (id: string, months: number) =>
    request<MonthlySummary>('GET', `/accounts/${enc(id)}/monthly-summary?months=${months}`).then((r) => r.data),
  getPortfolio: (id: string) => request<Portfolio>('GET', `/accounts/${enc(id)}/portfolio`).then((r) => r.data),
  listInstruments: () => request<Instrument[]>('GET', '/instruments').then((r) => r.data),
  getPriceHistory: (symbol: string, limit: number) =>
    request<Price[]>('GET', `/instruments/${enc(symbol)}/prices?limit=${limit}`).then((r) => r.data),
  createInstrument: (symbol: string, name: string) =>
    request<Instrument>('POST', '/instruments', { symbol, name, currency: 'EUR' }).then((r) => r.data),
  recordPrice: (symbol: string, price: number) =>
    request<Price>('POST', `/instruments/${enc(symbol)}/prices`, { price }).then((r) => r.data),
  /** 200 when the books balance, 503 (with the same body) when they do not. */
  ledgerHealth: async (): Promise<LedgerHealth> => {
    try {
      return (await request<LedgerHealth>('GET', '/ledger/health')).data;
    } catch (e) {
      if (e instanceof ApiError && e.status === 503) {
        return e.body as LedgerHealth;
      }
      throw e;
    }
  },
};

/** Request bodies of the money-moving endpoints (used with MoneyOperation). */
export const moneyPaths = {
  deposit: '/deposits',
  transfer: '/transfers',
  reversal: (transactionId: string) => `/transactions/${enc(transactionId)}/reversal`,
  trade: (accountId: string) => `/accounts/${enc(accountId)}/trades`,
};

export type DepositBody = { accountId: string; amount: number; currency: 'EUR'; description?: string };
export type TransferBody = {
  fromAccountId: string;
  toAccountId: string;
  amount: number;
  currency: 'EUR';
  description?: string;
};
export type TradeBody = { side: TradeSide; symbol: string; quantity: number };
export type { Trade, Transaction };

/** A short, human-readable message for a failed request. */
export function describeError(error: unknown): string {
  if (error instanceof NetworkError) {
    return `${error.message} Try again: the same Idempotency-Key is reused, so it cannot be booked twice.`;
  }
  if (error instanceof ApiError) {
    const { title, detail } = error.problem;
    return title && detail ? `${title}: ${detail}` : error.message;
  }
  return error instanceof Error ? error.message : String(error);
}
