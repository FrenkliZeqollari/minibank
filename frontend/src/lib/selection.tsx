import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { useAccounts } from '../api/hooks';
import type { Account } from '../api/types';

// The account the user is working with ("my account"). Until login exists (Milestone 8) every
// account in the bank is visible, so the user picks one; the choice is remembered per browser.
const STORAGE_KEY = 'minibank.selectedAccount';

interface Selection {
  accounts: Account[];
  selected: Account | undefined;
  select: (id: string) => void;
  loading: boolean;
}

const SelectionContext = createContext<Selection | null>(null);

function remembered(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

export function SelectionProvider({ children }: { children: ReactNode }) {
  const { data, isLoading } = useAccounts();
  const accounts = data?.accounts ?? [];
  const [selectedId, setSelectedId] = useState<string | null>(remembered);

  const selected = accounts.find((a) => a.id === selectedId) ?? accounts[0];

  useEffect(() => {
    if (selected && selected.id !== selectedId) {
      setSelectedId(selected.id);
    }
  }, [selected, selectedId]);

  const select = (id: string) => {
    setSelectedId(id);
    try {
      localStorage.setItem(STORAGE_KEY, id);
    } catch {
      // storage blocked: the choice is simply not remembered
    }
  };

  return (
    <SelectionContext.Provider value={{ accounts, selected, select, loading: isLoading }}>
      {children}
    </SelectionContext.Provider>
  );
}

export function useSelection(): Selection {
  const value = useContext(SelectionContext);
  if (!value) {
    throw new Error('useSelection must be used inside SelectionProvider');
  }
  return value;
}
