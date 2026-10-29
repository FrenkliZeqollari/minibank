import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';

// Data fetching with caching via TanStack Query. Every money movement calls useRefresh()
// afterwards, so balances, statements and charts always show the server's current state.

export const useAccounts = () => useQuery({ queryKey: ['accounts'], queryFn: api.listAccounts });

export const useAccount = (id: string | undefined) =>
  useQuery({ queryKey: ['account', id], queryFn: () => api.getAccount(id!), enabled: !!id });

export const useStatement = (id: string | undefined, page: number, size: number) =>
  useQuery({ queryKey: ['statement', id, page, size], queryFn: () => api.getStatement(id!, page, size), enabled: !!id });

export const useBalanceHistory = (id: string | undefined, days: number) =>
  useQuery({ queryKey: ['balance-history', id, days], queryFn: () => api.getBalanceHistory(id!, days), enabled: !!id });

export const useMonthlySummary = (id: string | undefined, months: number) =>
  useQuery({ queryKey: ['monthly', id, months], queryFn: () => api.getMonthlySummary(id!, months), enabled: !!id });

export const usePortfolio = (id: string | undefined) =>
  useQuery({ queryKey: ['portfolio', id], queryFn: () => api.getPortfolio(id!), enabled: !!id });

export const useInstruments = () => useQuery({ queryKey: ['instruments'], queryFn: api.listInstruments });

export const usePriceHistory = (symbol: string | undefined, limit: number) =>
  useQuery({ queryKey: ['prices', symbol, limit], queryFn: () => api.getPriceHistory(symbol!, limit), enabled: !!symbol });

export const useLedgerHealth = () =>
  useQuery({ queryKey: ['health'], queryFn: api.ledgerHealth, refetchInterval: 30_000 });

/** After any change: refetch everything that is shown. */
export function useRefresh() {
  const client = useQueryClient();
  return () => client.invalidateQueries();
}
