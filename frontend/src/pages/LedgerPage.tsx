import { Link } from 'react-router-dom';
import { useLedgerHealth } from '../api/hooks';
import { Alert, Amount, Card, Loading, Stat } from '../components/ui';

export function LedgerPage() {
  const health = useLedgerHealth();
  const h = health.data;
  return (
    <>
      <div className="page-head">
        <div>
          <h1>Ledger</h1>
          <p>The bank checks its own books: independently of the code that wrote them.</p>
        </div>
      </div>
      {!h ? <Loading /> : (
        <>
          <Alert tone={h.healthy ? 'success' : 'error'}>
            {h.healthy ? 'The books balance. Every check passed.' : 'The books do NOT balance. Someone has to look at this now.'}
          </Alert>
          <div className="grid grid-2">
            <Stat label="Sum of all entries" value={<Amount cents={Number(h.sumOfAllEntries)} />}
              sub="Must be exactly 0: money only moves between accounts" />
            <Stat label="Accounts with a wrong cached balance" value={h.balanceMismatches.length}
              sub="Must be 0: each cached balance equals the sum of its entries" />
          </div>
          {h.balanceMismatches.length > 0 && (
            <Card title="Mismatches">
              <table className="table">
                <thead><tr><th>Account</th><th className="right">Cached</th><th className="right">Sum of entries</th></tr></thead>
                <tbody>
                  {h.balanceMismatches.map((m) => (
                    <tr key={m.accountId}>
                      <td className="mono"><Link to={`/accounts/${m.accountId}`}>{m.accountId}</Link></td>
                      <td className="right"><Amount cents={m.cachedBalance} /></td>
                      <td className="right"><Amount cents={m.entriesSum} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>
          )}
          <Card title="What is checked">
            <div className="muted" style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <p style={{ margin: 0 }}><strong>Double entry.</strong> Every transaction's entries sum to 0, so the whole ledger sums to 0.
                Money enters and leaves only through system accounts.</p>
              <p style={{ margin: 0 }}><strong>Cached balances.</strong> Each account stores its balance for speed; it must always
                equal the sum of the account's entries.</p>
              <p style={{ margin: 0 }}>This page reads <code>GET /ledger/health</code>, which answers <code>503</code> when a check
                fails, so monitoring can raise an alarm.</p>
            </div>
          </Card>
        </>
      )}
    </>
  );
}
