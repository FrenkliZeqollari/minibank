import { NavLink, Outlet } from 'react-router-dom';
import { useLedgerHealth } from '../api/hooks';
import { useSelection } from '../lib/selection';
import { Badge } from './ui';
import { ChartIcon, HomeIcon, MarketIcon, SendIcon, ShieldIcon, WalletIcon } from './icons';

const nav = [
  { to: '/', label: 'Overview', icon: <HomeIcon />, end: true },
  { to: '/accounts', label: 'Accounts', icon: <WalletIcon /> },
  { to: '/payments', label: 'Payments', icon: <SendIcon /> },
  { to: '/investments', label: 'Investments', icon: <ChartIcon /> },
  { to: '/market', label: 'Market', icon: <MarketIcon /> },
  { to: '/ledger', label: 'Ledger', icon: <ShieldIcon /> },
];

function HealthBadge() {
  const { data, isError } = useLedgerHealth();
  if (isError) {
    return <Badge tone="bad"><i className="dot" />Ledger unreachable</Badge>;
  }
  if (!data) {
    return <Badge>Checking books…</Badge>;
  }
  return data.healthy
    ? <Badge tone="ok"><i className="dot" />Books balance</Badge>
    : <Badge tone="bad"><i className="dot" />Books do NOT balance</Badge>;
}

function AccountSwitcher() {
  const { accounts, selected, select } = useSelection();
  if (accounts.length === 0) {
    return null;
  }
  return (
    <select className="input" style={{ width: 'auto', maxWidth: 260 }} aria-label="Current account"
      value={selected?.id ?? ''} onChange={(e) => select(e.target.value)}>
      {accounts.map((a) => <option key={a.id} value={a.id}>{a.ownerName}</option>)}
    </select>
  );
}

export function Layout() {
  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.6"
              strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M5 18V7l7 6 7-6v11" /></svg>
          </span>
          Minibank
        </div>
        {nav.map((n) => (
          <NavLink key={n.to} to={n.to} end={n.end} className="nav-link">
            {n.icon}
            {n.label}
          </NavLink>
        ))}
        <div className="sidebar-footer">
          Demo bank: no login yet, every account is visible.
          <br />
          <a href="/swagger-ui.html" style={{ color: 'inherit', textDecoration: 'underline' }}>API documentation</a>
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <AccountSwitcher />
          <div className="spacer" />
          <HealthBadge />
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>

      <nav className="mobile-nav" aria-label="Main">
        {nav.slice(0, 5).map((n) => (
          <NavLink key={n.to} to={n.to} end={n.end}>
            {n.icon}
            {n.label}
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
