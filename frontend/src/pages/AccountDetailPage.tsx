import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { MoneyOperation, describeError, moneyPaths, type Transaction } from '../api/client';
import { useAccount, useBalanceHistory, useMonthlySummary, useRefresh, useStatement } from '../api/hooks';
import type { StatementLine } from '../api/types';
import { BalanceChart, MonthlyChart } from '../components/charts';
import { TransactionRow, transactionTypeLabel } from '../components/TransactionRow';
import { Alert, Amount, Card, Empty, Loading, Modal, Segmented } from '../components/ui';
import { formatDateTime } from '../lib/format';
import { formatCents } from '../lib/money';
import { useSelection } from '../lib/selection';

const PAGE_SIZE = 15;
type Range = '30' | '90' | '180' | '365';

export function AccountDetailPage() {
  const { id } = useParams();
  const { select } = useSelection();
  const account = useAccount(id);
  const [range, setRange] = useState<Range>('90');
  const [page, setPage] = useState(0);
  const history = useBalanceHistory(id, Number(range));
  const monthly = useMonthlySummary(id, 12);
  const statement = useStatement(id, page, PAGE_SIZE);
  const [opened, setOpened] = useState<StatementLine | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    if (id) select(id);
  }, [id]);

  if (account.isError) {
    return <Card><Empty>This account does not exist. <Link to="/accounts">Back to accounts</Link></Empty></Card>;
  }
  if (!account.data) {
    return <Loading />;
  }
  const a = account.data;

  return (
    <>
      <div className="page-head">
        <div>
          <div className="small"><Link to="/accounts">Accounts</Link> / {a.ownerName}</div>
          <h1 style={{ marginTop: 6 }}>{a.ownerName}</h1>
          <p className="mono">{a.id} · opened {formatDateTime(a.createdAt)}</p>
        </div>
        <div style={{ textAlign: 'right' }}>
          <div className="stat-label">Balance</div>
          <div className="num" style={{ fontSize: 30, fontWeight: 750, letterSpacing: '-0.02em' }}>{formatCents(a.balance, a.currency)}</div>
        </div>
      </div>

      <div className="grid grid-2">
        <Card title="Balance" action={
          <Segmented label="Range" value={range} onChange={setRange} options={[
            { value: '30', label: '30d' }, { value: '90', label: '90d' }, { value: '180', label: '6m' }, { value: '365', label: '1y' },
          ]} />}>
          {history.data ? <BalanceChart points={history.data.points} /> : <Loading />}
        </Card>
        <Card title="Money in and out, last 12 months">
          {monthly.data ? <MonthlyChart months={monthly.data.months} /> : <Loading />}
        </Card>
      </div>

      <Card title="Statement" action={statement.data && <span className="muted small">{statement.data.totalEntries} entries</span>}>
        {notice && <div style={{ marginBottom: 12 }}><Alert tone="success">{notice}</Alert></div>}
        {!statement.data ? <Loading /> : statement.data.entries.length === 0 ? <Empty>No transactions yet.</Empty> : (
          <>
            <div className="tx-list">
              {statement.data.entries.map((line) => (
                <TransactionRow key={line.entryId} line={line} onOpen={() => setOpened(line)} />
              ))}
            </div>
            <div className="pager">
              <button className="btn secondary small" disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</button>
              <span className="muted small">Page {page + 1} of {Math.max(statement.data.totalPages, 1)}</span>
              <button className="btn secondary small" disabled={page + 1 >= statement.data.totalPages}
                onClick={() => setPage(page + 1)}>Older</button>
            </div>
          </>
        )}
      </Card>

      {opened && (
        <TransactionDialog line={opened} onClose={() => setOpened(null)}
          onReversed={(tx) => { setOpened(null); setNotice(`Reversed. Booked reversal ${tx.id.slice(0, 8)}…`); }} />
      )}
    </>
  );
}

/** Why a line cannot be reversed, or null if it can. */
function notReversible(line: StatementLine): string | null {
  if (line.reversedByTransactionId) return 'This transaction has already been reversed. A transaction can be reversed only once.';
  if (line.transactionType === 'REVERSAL') return 'This is itself a reversal. To undo it, book the original payment again.';
  if (line.transactionType === 'TRADE') return 'Securities trades are not reversed: sell (or buy) back instead.';
  return null;
}

/** Details of one statement line, with the option to reverse it where that is allowed. */
function TransactionDialog({ line, onClose, onReversed }:
  { line: StatementLine; onClose: () => void; onReversed: (tx: Transaction) => void }) {
  const [operation] = useState(() => new MoneyOperation());
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const refresh = useRefresh();
  const blocked = notReversible(line);

  async function confirm() {
    setBusy(true);
    setError(null);
    try {
      const { result } = await operation.send<Transaction>(moneyPaths.reversal(line.transactionId));
      await refresh();
      onReversed(result);
    } catch (e) {
      setError(describeError(e));
      setBusy(false);
    }
  }

  return (
    <Modal title={line.description || transactionTypeLabel[line.transactionType]} onClose={onClose}>
      <div style={{ textAlign: 'center' }}>
        <div className="num" style={{ fontSize: 30, fontWeight: 750 }}><Amount cents={line.amount} signed /></div>
      </div>
      <dl className="summary-list">
        <dt>Type</dt><dd>{transactionTypeLabel[line.transactionType]}</dd>
        <dt>Booked</dt><dd>{formatDateTime(line.createdAt)}</dd>
        <dt>Transaction</dt><dd className="mono">{line.transactionId}</dd>
        {line.reversedByTransactionId && <><dt>Reversed by</dt><dd className="mono">{line.reversedByTransactionId}</dd></>}
      </dl>
      {confirming ? (
        <Alert tone="info">
          The original stays in the history. A new <strong>reversal</strong> books its mirror image, so every account
          involved gets back to where it was. This cannot be undone.
        </Alert>
      ) : blocked && <p className="muted small" style={{ margin: 0 }}>{blocked}</p>}
      {error && <Alert tone="error">{error}</Alert>}
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <button className="btn secondary" onClick={onClose}>Close</button>
        {!blocked && (confirming
          ? <button className="btn danger" disabled={busy} onClick={confirm}>Yes, reverse it</button>
          : <button className="btn danger" onClick={() => setConfirming(true)}>Reverse…</button>)}
      </div>
    </Modal>
  );
}
