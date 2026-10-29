import { HashRouter, Navigate, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { SelectionProvider } from './lib/selection';
import { AccountDetailPage } from './pages/AccountDetailPage';
import { AccountsPage } from './pages/AccountsPage';
import { InvestmentsPage } from './pages/InvestmentsPage';
import { LedgerPage } from './pages/LedgerPage';
import { MarketPage } from './pages/MarketPage';
import { OverviewPage } from './pages/OverviewPage';
import { PaymentsPage } from './pages/PaymentsPage';

// HashRouter (URLs like /#/accounts/…): page addresses never collide with the API's own paths
// such as /accounts/{id}, and a page reload always loads the app, not an API response.
export function App() {
  return (
    <HashRouter>
      <SelectionProvider>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<OverviewPage />} />
            <Route path="accounts" element={<AccountsPage />} />
            <Route path="accounts/:id" element={<AccountDetailPage />} />
            <Route path="payments" element={<PaymentsPage />} />
            <Route path="investments" element={<InvestmentsPage />} />
            <Route path="market" element={<MarketPage />} />
            <Route path="ledger" element={<LedgerPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </SelectionProvider>
    </HashRouter>
  );
}
