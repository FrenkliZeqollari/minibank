import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, describeError } from '../api/client';
import { useRefresh } from '../api/hooks';
import { Alert, Amount, Badge, Card, Empty, Loading, Modal } from '../components/ui';
import { formatDateTime } from '../lib/format';
import { useSelection } from '../lib/selection';
import { PlusIcon } from '../components/icons';

export function AccountsPage() {
  const { accounts, loading, select } = useSelection();
  const [opening, setOpening] = useState(false);
  const navigate = useNavigate();
  const total = accounts.reduce((sum, a) => sum + a.balance, 0);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Accounts</h1>
          <p>{accounts.length} accounts · <Amount cents={total} /> in total</p>
        </div>
        <button className="btn" onClick={() => setOpening(true)}><PlusIcon />Open account</button>
      </div>

      <Card>
        {loading ? <Loading /> : accounts.length === 0 ? <Empty>No accounts yet.</Empty> : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr><th>Owner</th><th className="hide-mobile">Account id</th><th className="hide-mobile">Opened</th>
                  <th>Status</th><th className="right">Balance</th></tr>
              </thead>
              <tbody>
                {accounts.map((a) => (
                  <tr key={a.id} className="clickable" onClick={() => { select(a.id); navigate(`/accounts/${a.id}`); }}>
                    <td style={{ fontWeight: 600 }}>{a.ownerName}</td>
                    <td className="mono muted hide-mobile">{a.id}</td>
                    <td className="muted hide-mobile">{formatDateTime(a.createdAt)}</td>
                    <td><Badge tone={a.status === 'ACTIVE' ? 'ok' : undefined}>{a.status === 'ACTIVE' ? 'Active' : 'Closed'}</Badge></td>
                    <td className="right" style={{ fontWeight: 650 }}><Amount cents={a.balance} currency={a.currency} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {opening && <OpenAccountDialog onClose={() => setOpening(false)} onOpened={(id) => { select(id); navigate(`/accounts/${id}`); }} />}
    </>
  );
}

function OpenAccountDialog({ onClose, onOpened }: { onClose: () => void; onOpened: (id: string) => void }) {
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const refresh = useRefresh();

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      const account = await api.openAccount(name.trim());
      await refresh();
      onOpened(account.id);
    } catch (err) {
      setError(describeError(err));
      setBusy(false);
    }
  }

  return (
    <Modal title="Open a new EUR account" onClose={onClose}>
      <form className="form" onSubmit={submit}>
        <div className="field">
          <label htmlFor="owner">Account holder</label>
          <input id="owner" className="input" required maxLength={255} value={name}
            onChange={(e) => setName(e.target.value)} placeholder="e.g. Max Mustermann" autoFocus />
        </div>
        {error && <Alert tone="error">{error}</Alert>}
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="btn secondary" onClick={onClose}>Cancel</button>
          <button className="btn" disabled={busy}>Open account</button>
        </div>
      </form>
    </Modal>
  );
}
