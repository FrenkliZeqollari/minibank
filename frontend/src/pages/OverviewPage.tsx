import { Link, useNavigate } from 'react-router-dom';
import { useBalanceHistory, useMonthlySummary, usePortfolio, useStatement } from '../api/hooks';
import { BalanceChart, MonthlyChart } from '../components/charts';
import { TransactionRow } from '../components/TransactionRow';
import { Card, Empty, Loading, Stat } from '../components/ui';
import { formatCents, formatSignedCents } from '../lib/money';
import { useSelection } from '../lib/selection';
import { PlusIcon, SendIcon, TradeIcon } from '../components/icons';

function greeting(): string {
  const hour = new Date().getHours();
  return hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
}

export function OverviewPage() {
  const { accounts, selected, select, loading } = useSelection();
  const navigate = useNavigate();
  const history = useBalanceHistory(selected?.id, 90);
  const monthly = useMonthlySummary(selected?.id, 6);
  const portfolio = usePortfolio(selected?.id);
  const recent = useStatement(selected?.id, 0, 6);

  if (loading) {
    return <Loading />;
  }
  if (!selected) {
    return (
      <Card>
        <Empty>
          <p>There are no accounts yet.</p>
          <p className="small">Open one under <Link to="/accounts">Accounts</Link>, or start the app with the <code>demo</code> profile
            to get a bank full of example data.</p>
        </Empty>
      </Card>
    );
  }

  const thisMonth = monthly.data?.months.at(-1);
  const firstName = selected.ownerName.split(' ')[0];

  return (
    <>
      <div className="page-head">
        <div>
          <h1>{greeting()}, {firstName}</h1>
          <p>Here is what is happening with your money.</p>
        </div>
        <div className="row">
          <button className="btn secondary" onClick={() => navigate('/payments?tab=topup')}><PlusIcon />Top up</button>
          <button className="btn secondary" onClick={() => navigate('/investments')}><TradeIcon />Invest</button>
          <button className="btn" onClick={() => navigate('/payments')}><SendIcon />Send money</button>
        </div>
      </div>

      <div className="account-cards" role="group" aria-label="Accounts">
        {accounts.map((a) => (
          <button key={a.id} className="account-card" aria-pressed={a.id === selected.id} onClick={() => select(a.id)}>
            <div>
              <div style={{ fontWeight: 650 }}>{a.ownerName}</div>
              <div className="muted small mono">{a.id.slice(0, 8)}…</div>
            </div>
            <div className="balance num">{formatCents(a.balance, a.currency)}</div>
          </button>
        ))}
      </div>

      <div className="grid grid-4">
        <Stat label="Balance" value={formatCents(selected.balance)} sub="Available now" />
        <Stat label="Money in this month" value={formatCents(thisMonth?.moneyIn ?? 0)} tone="positive" />
        <Stat label="Money out this month" value={formatCents(thisMonth?.moneyOut ?? 0)} />
        <Stat label="Investments" value={formatCents(portfolio.data?.marketValue ?? 0)}
          sub={portfolio.data && portfolio.data.positions.length > 0
            ? <span className={portfolio.data.unrealizedPnl >= 0 ? 'positive' : 'negative'}>
                {formatSignedCents(portfolio.data.unrealizedPnl)} unrealized</span>
            : 'No shares held'} />
      </div>

      <div className="grid grid-3">
        <Card title="Balance, last 90 days">
          {history.data ? <BalanceChart points={history.data.points} /> : <Loading />}
        </Card>
        <Card title="Recent activity" action={<Link to={`/accounts/${selected.id}`} className="small">View all</Link>}>
          {recent.data && recent.data.entries.length > 0 ? (
            <div className="tx-list">
              {recent.data.entries.map((line) => <TransactionRow key={line.entryId} line={line} />)}
            </div>
          ) : recent.data ? <Empty>No transactions yet.</Empty> : <Loading />}
        </Card>
      </div>

      <Card title="Money in and out, last 6 months">
        {monthly.data ? <MonthlyChart months={monthly.data.months} /> : <Loading />}
      </Card>
    </>
  );
}
