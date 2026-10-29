import { useEffect, useState } from 'react';
import { MoneyOperation, describeError, moneyPaths, type Trade, type TradeBody } from '../api/client';
import { useInstruments, usePortfolio, usePriceHistory, useRefresh } from '../api/hooks';
import type { TradeSide } from '../api/types';
import { AllocationChart, PriceChart } from '../components/charts';
import { Alert, Amount, Card, Empty, Loading, Segmented, Stat } from '../components/ui';
import { formatCents, formatSignedCents, parseShareQuantity } from '../lib/money';
import { useSelection } from '../lib/selection';

export function InvestmentsPage() {
  const { selected } = useSelection();
  const portfolio = usePortfolio(selected?.id);
  const instruments = useInstruments();
  const [symbol, setSymbol] = useState<string>('');

  useEffect(() => {
    if (!symbol && instruments.data?.[0]) setSymbol(instruments.data[0].symbol);
  }, [instruments.data, symbol]);

  if (!selected) {
    return <Card><Empty>Open an account first.</Empty></Card>;
  }
  const p = portfolio.data;

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Investments</h1>
          <p>Shares bought with {selected.ownerName}'s cash, valued at the latest market prices.</p>
        </div>
      </div>

      <div className="grid grid-4">
        <Stat label="Total value" value={formatCents(p?.totalValue ?? selected.balance)} sub="Cash + investments" />
        <Stat label="Investments" value={formatCents(p?.marketValue ?? 0)} sub={p ? `Cost ${formatCents(p.costBasis)}` : undefined} />
        <Stat label="Unrealized gain/loss" value={formatSignedCents(p?.unrealizedPnl ?? 0)}
          tone={(p?.unrealizedPnl ?? 0) >= 0 ? 'positive' : 'negative'} sub="If everything were sold now" />
        <Stat label="Realized gain/loss" value={formatSignedCents(p?.realizedPnl ?? 0)}
          tone={(p?.realizedPnl ?? 0) >= 0 ? 'positive' : 'negative'} sub="From sales so far" />
      </div>

      <div className="grid grid-3">
        <Card title="Positions">
          {!p ? <Loading /> : p.positions.length === 0 ? <Empty>No shares held yet. Place an order to start investing.</Empty> : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr><th>Instrument</th><th className="right">Shares</th><th className="right hide-mobile">Avg. cost</th>
                    <th className="right">Price</th><th className="right">Value</th><th className="right">Gain/loss</th></tr>
                </thead>
                <tbody>
                  {p.positions.map((pos) => (
                    <tr key={pos.symbol} className="clickable" onClick={() => setSymbol(pos.symbol)}>
                      <td><strong>{pos.symbol}</strong><div className="muted small">{pos.name}</div></td>
                      <td className="right num">{pos.quantity}</td>
                      <td className="right num hide-mobile">{formatCents(Math.round(pos.costBasis / pos.quantity))}</td>
                      <td className="right num">{formatCents(pos.lastPrice)}</td>
                      <td className="right num" style={{ fontWeight: 650 }}>{formatCents(pos.marketValue)}</td>
                      <td className="right"><Amount cents={pos.unrealizedPnl} signed /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
        <Card title="Allocation">
          {!p ? <Loading /> : (
            <AllocationChart slices={[
              { name: 'Cash', value: p.cash },
              ...p.positions.map((pos) => ({ name: pos.symbol, value: pos.marketValue })),
            ].filter((s) => s.value > 0)} />
          )}
        </Card>
      </div>

      <div className="grid grid-3">
        <InstrumentChart symbol={symbol} onSymbol={setSymbol} />
        <Card title="Place an order">
          <OrderTicket accountId={selected.id} cash={selected.balance} symbol={symbol} onSymbol={setSymbol}
            held={p?.positions.find((x) => x.symbol === symbol)?.quantity ?? 0} />
        </Card>
      </div>
    </>
  );
}

function InstrumentChart({ symbol, onSymbol }: { symbol: string; onSymbol: (s: string) => void }) {
  const instruments = useInstruments();
  const prices = usePriceHistory(symbol || undefined, 130);
  const first = prices.data?.[0]?.price;
  const last = prices.data?.at(-1)?.price;
  const change = first && last ? ((last - first) * 100) / first : null;

  return (
    <Card title={
      <div className="row">
        <select className="input" style={{ width: 'auto' }} aria-label="Instrument" value={symbol} onChange={(e) => onSymbol(e.target.value)}>
          {(instruments.data ?? []).map((i) => <option key={i.symbol} value={i.symbol}>{i.symbol} · {i.name}</option>)}
        </select>
        {last !== undefined && <strong className="num">{formatCents(last)}</strong>}
        {change !== null && <span className={`num small ${change >= 0 ? 'positive' : 'negative'}`}>
          {change >= 0 ? '+' : '−'}{Math.abs(change).toFixed(2)} % (period)</span>}
      </div>}>
      {!symbol ? <Empty>No instruments yet. Add some under Market.</Empty>
        : prices.data && prices.data.length > 0 ? <PriceChart prices={prices.data} /> : <Loading />}
    </Card>
  );
}

function OrderTicket({ accountId, cash, symbol, held, onSymbol }:
  { accountId: string; cash: number; symbol: string; held: number; onSymbol: (s: string) => void }) {
  const instruments = useInstruments();
  const [side, setSide] = useState<TradeSide>('BUY');
  const [quantityText, setQuantityText] = useState('');
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [operation] = useState(() => new MoneyOperation());
  const refresh = useRefresh();
  const instrument = instruments.data?.find((i) => i.symbol === symbol);

  let quantity: number | null = null;
  try {
    quantity = quantityText.trim() ? parseShareQuantity(quantityText) : null;
  } catch {
    quantity = null;
  }
  const estimate = quantity !== null && instrument?.lastPrice ? quantity * instrument.lastPrice : null;

  function changed<T>(setter: (v: T) => void) {
    return (v: T) => {
      operation.reset();
      setMessage(null);
      setter(v);
    };
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    let q: number;
    try {
      q = parseShareQuantity(quantityText);
    } catch (err) {
      setMessage({ tone: 'error', text: (err as Error).message });
      return;
    }
    setBusy(true);
    const body: TradeBody = { side, symbol, quantity: q };
    try {
      const { result } = await operation.send<Trade>(moneyPaths.trade(accountId), body);
      await refresh();
      setQuantityText('');
      setMessage({ tone: 'success', text: `${result.side === 'BUY' ? 'Bought' : 'Sold'} ${result.quantity} ${result.symbol} at `
        + `${formatCents(result.price)}: ${formatCents(result.grossAmount)} in total.` });
    } catch (err) {
      setMessage({ tone: 'error', text: describeError(err) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <Segmented label="Order side" value={side} onChange={changed(setSide)}
        options={[{ value: 'BUY', label: 'Buy' }, { value: 'SELL', label: 'Sell' }]} />
      <div className="field">
        <label htmlFor="orderSymbol">Instrument</label>
        <select id="orderSymbol" className="input" value={symbol} onChange={(e) => changed(onSymbol)(e.target.value)}>
          {(instruments.data ?? []).map((i) => <option key={i.symbol} value={i.symbol}>{i.symbol} · {i.name}</option>)}
        </select>
        <span className="hint">
          {instrument?.lastPrice ? <>Price {formatCents(instrument.lastPrice)} · you hold {held}</> : 'No market price yet'}
        </span>
      </div>
      <div className="field">
        <label htmlFor="quantity">Shares</label>
        <input id="quantity" className="input num" inputMode="numeric" value={quantityText}
          onChange={(e) => changed(setQuantityText)(e.target.value)} placeholder="e.g. 10" />
      </div>
      <dl className="summary-list">
        <dt>Estimated total</dt><dd>{estimate !== null ? formatCents(estimate) : '—'}</dd>
        <dt>{side === 'BUY' ? 'Cash available' : 'Shares held'}</dt><dd>{side === 'BUY' ? formatCents(cash) : held}</dd>
      </dl>
      {message && <Alert tone={message.tone}>{message.text}</Alert>}
      <button className="btn block" disabled={busy || !symbol}>
        {side === 'BUY' ? 'Buy' : 'Sell'} at the current price
      </button>
      <span className="hint muted small">Orders execute immediately at the latest price; the bank sets the price, not the order.</span>
    </form>
  );
}
