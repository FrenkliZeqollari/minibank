import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { MoneyOperation, describeError, moneyPaths, type DepositBody, type Transaction, type TransferBody } from '../api/client';
import { useRefresh } from '../api/hooks';
import type { Account } from '../api/types';
import { Alert, Amount, Card, Segmented } from '../components/ui';
import { CheckIcon } from '../components/icons';
import { formatDateTime } from '../lib/format';
import { formatCents, parseAmountToCents } from '../lib/money';
import { useSelection } from '../lib/selection';

type Tab = 'transfer' | 'topup';

export function PaymentsPage() {
  const [params, setParams] = useSearchParams();
  const tab: Tab = params.get('tab') === 'topup' ? 'topup' : 'transfer';
  const { selected } = useSelection();

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Payments</h1>
          <p>Send money to another account, or top up an account.</p>
        </div>
        <Segmented label="Payment type" value={tab} onChange={(t) => setParams(t === 'topup' ? { tab: t } : {})}
          options={[{ value: 'transfer', label: 'Transfer' }, { value: 'topup', label: 'Top up (demo)' }]} />
      </div>
      <div className="grid grid-3">
        <Card>{selected ? (tab === 'transfer' ? <TransferFlow from={selected} key={selected.id} /> : <TopUpForm account={selected} key={selected.id} />)
          : <p className="muted">Open an account first.</p>}</Card>
        <Card title="How it works">
          <div className="muted small" style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <p style={{ margin: 0 }}>Every payment is a <strong>double-entry</strong> booking: the same amount leaves one account
              and arrives in the other, in one database transaction. Money is never created or lost.</p>
            <p style={{ margin: 0 }}>Each payment carries an <strong>Idempotency-Key</strong>. If the connection drops and you
              press the button again, the bank recognises the retry and books the payment only once.</p>
            <p style={{ margin: 0 }}>A transfer can never overdraw an account, even if several payments arrive at the same moment.</p>
          </div>
        </Card>
      </div>
    </>
  );
}

type Step = 'details' | 'review' | 'done';

function TransferFlow({ from }: { from: Account }) {
  const { accounts } = useSelection();
  const others = useMemo(() => accounts.filter((a) => a.id !== from.id), [accounts, from.id]);
  const [step, setStep] = useState<Step>('details');
  const [toId, setToId] = useState(others[0]?.id ?? '');
  const [otherId, setOtherId] = useState('');
  const [amountText, setAmountText] = useState('');
  const [reference, setReference] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [receipt, setReceipt] = useState<{ tx: Transaction; replayed: boolean } | null>(null);
  // One operation = one Idempotency-Key, kept across retries until the bank gives a definite answer.
  const [operation] = useState(() => new MoneyOperation());
  const refresh = useRefresh();

  const recipientId = toId === 'other' ? otherId.trim() : toId;
  const recipient = accounts.find((a) => a.id === recipientId);

  function edit<T>(setter: (v: T) => void) {
    return (value: T) => {
      operation.reset(); // a changed payment is a new operation
      setError(null);
      setter(value);
    };
  }

  let amount: number | null = null;
  let amountError: string | null = null;
  if (amountText.trim()) {
    try {
      amount = parseAmountToCents(amountText);
    } catch (e) {
      amountError = (e as Error).message;
    }
  }

  function review(e: React.FormEvent) {
    e.preventDefault();
    if (!recipientId) return setError('Choose who receives the money.');
    if (recipientId === from.id) return setError('You cannot send money to the same account.');
    if (amount === null) return setError(amountError ?? 'Enter an amount.');
    setError(null);
    setStep('review');
  }

  async function send() {
    if (amount === null) return;
    setBusy(true);
    setError(null);
    const body: TransferBody = { fromAccountId: from.id, toAccountId: recipientId, amount, currency: 'EUR',
      description: reference.trim() || undefined };
    try {
      const { result, replayed } = await operation.send<Transaction>(moneyPaths.transfer, body);
      await refresh();
      setReceipt({ tx: result, replayed });
      setStep('done');
    } catch (err) {
      setError(describeError(err));
    } finally {
      setBusy(false);
    }
  }

  function startOver() {
    setStep('details');
    setAmountText('');
    setReference('');
    setReceipt(null);
  }

  return (
    <>
      <div className="steps" aria-label="Progress">
        <span className={step === 'details' ? 'on' : ''}><i className="n">1</i>Details</span>
        <span className={step === 'review' ? 'on' : ''}><i className="n">2</i>Review</span>
        <span className={step === 'done' ? 'on' : ''}><i className="n">3</i>Done</span>
      </div>

      {step === 'details' && (
        <form className="form" onSubmit={review}>
          <div className="field">
            <span className="label">From</span>
            <div className="row" style={{ justifyContent: 'space-between' }}>
              <strong>{from.ownerName}</strong>
              <span className="muted">Available <Amount cents={from.balance} /></span>
            </div>
          </div>
          <div className="field">
            <label htmlFor="to">To</label>
            <select id="to" className="input" value={toId} onChange={(e) => edit(setToId)(e.target.value)}>
              {others.map((a) => <option key={a.id} value={a.id}>{a.ownerName} · {a.id.slice(0, 8)}</option>)}
              <option value="other">Another account (enter its id)…</option>
            </select>
          </div>
          {toId === 'other' && (
            <div className="field">
              <label htmlFor="otherId">Recipient account id</label>
              <input id="otherId" className="input mono" value={otherId} onChange={(e) => edit(setOtherId)(e.target.value)}
                placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" />
            </div>
          )}
          <div className="field">
            <label htmlFor="amount">Amount (EUR)</label>
            <input id="amount" className="input amount-input num" inputMode="decimal" value={amountText}
              onChange={(e) => edit(setAmountText)(e.target.value)} placeholder="0,00" />
            {amountError ? <span className="hint negative">{amountError}</span>
              : amount !== null && amount > from.balance ? <span className="hint negative">More than the available balance.</span>
              : <span className="hint">Use a comma or a dot for cents, e.g. 25,50.</span>}
          </div>
          <div className="field">
            <label htmlFor="reference">Reference</label>
            <input id="reference" className="input" maxLength={255} value={reference}
              onChange={(e) => edit(setReference)(e.target.value)} placeholder="e.g. Rent October" />
          </div>
          {error && <Alert tone="error">{error}</Alert>}
          <button className="btn block">Review transfer</button>
        </form>
      )}

      {step === 'review' && amount !== null && (
        <div className="form">
          <div style={{ textAlign: 'center' }}>
            <div className="muted small">You are sending</div>
            <div className="num" style={{ fontSize: 34, fontWeight: 750 }}>{formatCents(amount)}</div>
          </div>
          <dl className="summary-list">
            <dt>From</dt><dd>{from.ownerName}</dd>
            <dt>To</dt><dd>{recipient ? recipient.ownerName : <span className="mono">{recipientId}</span>}</dd>
            <dt>Reference</dt><dd>{reference.trim() || '—'}</dd>
            <dt>Balance after</dt><dd><Amount cents={from.balance - amount} /></dd>
          </dl>
          {error && <Alert tone="error">{error}</Alert>}
          <div className="row">
            <button className="btn secondary grow" onClick={() => { setStep('details'); setError(null); }}>Back</button>
            <button className="btn grow" disabled={busy} onClick={send}>{busy ? 'Sending…' : 'Confirm and send'}</button>
          </div>
        </div>
      )}

      {step === 'done' && receipt && (
        <div className="receipt">
          <div className="check"><CheckIcon /></div>
          <h2>{receipt.replayed ? 'Already sent earlier' : 'Money sent'}</h2>
          <p className="muted">
            {receipt.replayed ? 'This payment had already been booked; it was not booked twice.' : `Booked ${formatDateTime(receipt.tx.createdAt)}.`}
          </p>
          <p className="mono muted">Transaction {receipt.tx.id}</p>
          <div className="row" style={{ justifyContent: 'center' }}>
            <Link className="btn secondary" to={`/accounts/${from.id}`}>View statement</Link>
            <button className="btn" onClick={startOver}>New transfer</button>
          </div>
        </div>
      )}
    </>
  );
}

function TopUpForm({ account }: { account: Account }) {
  const [amountText, setAmountText] = useState('');
  const [description, setDescription] = useState('');
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [operation] = useState(() => new MoneyOperation());
  const refresh = useRefresh();

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setMessage(null);
    let amount: number;
    try {
      amount = parseAmountToCents(amountText);
    } catch (err) {
      setMessage({ tone: 'error', text: (err as Error).message });
      return;
    }
    setBusy(true);
    const body: DepositBody = { accountId: account.id, amount, currency: 'EUR', description: description.trim() || undefined };
    try {
      await operation.send<Transaction>(moneyPaths.deposit, body);
      await refresh();
      setAmountText('');
      setDescription('');
      setMessage({ tone: 'success', text: `${formatCents(amount)} added to ${account.ownerName}.` });
    } catch (err) {
      setMessage({ tone: 'error', text: describeError(err) });
    } finally {
      setBusy(false);
    }
  }

  const changed = (setter: (v: string) => void) => (e: React.ChangeEvent<HTMLInputElement>) => {
    operation.reset();
    setter(e.target.value);
  };

  return (
    <form className="form" onSubmit={submit}>
      <Alert tone="info">
        Demo only: money enters the bank from the <strong>EXTERNAL_FUNDING</strong> system account, like a cash deposit
        at the counter. In a real bank this would be an incoming payment.
      </Alert>
      <div className="field">
        <span className="label">Account</span>
        <strong>{account.ownerName}</strong>
      </div>
      <div className="field">
        <label htmlFor="topup">Amount (EUR)</label>
        <input id="topup" className="input amount-input num" inputMode="decimal" value={amountText}
          onChange={changed(setAmountText)} placeholder="0,00" />
      </div>
      <div className="field">
        <label htmlFor="topupDescription">Description</label>
        <input id="topupDescription" className="input" maxLength={255} value={description}
          onChange={changed(setDescription)} placeholder="e.g. Cash deposit" />
      </div>
      {message && <Alert tone={message.tone}>{message.text}</Alert>}
      <button className="btn block" disabled={busy}>Top up</button>
    </form>
  );
}
