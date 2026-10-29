import { useState } from 'react';
import { api, describeError } from '../api/client';
import { useInstruments, usePriceHistory, useRefresh } from '../api/hooks';
import type { Instrument } from '../api/types';
import { Sparkline } from '../components/charts';
import { Alert, Card, Empty, Loading } from '../components/ui';
import { formatDateTime } from '../lib/format';
import { formatCents, parseAmountToCents } from '../lib/money';

export function MarketPage() {
  const instruments = useInstruments();
  return (
    <>
      <div className="page-head">
        <div>
          <h1>Market</h1>
          <p>Instruments customers can trade. Prices are entered by hand here, standing in for a market data feed.</p>
        </div>
      </div>
      <div className="grid grid-3">
        <Card title="Instruments">
          {!instruments.data ? <Loading /> : instruments.data.length === 0 ? <Empty>No instruments yet.</Empty> : (
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>Instrument</th><th className="hide-mobile">Last 30 prices</th><th className="right">Price</th>
                  <th className="right hide-mobile">As of</th></tr></thead>
                <tbody>{instruments.data.map((i) => <InstrumentRow key={i.symbol} instrument={i} />)}</tbody>
              </table>
            </div>
          )}
        </Card>
        <div className="grid" style={{ alignContent: 'start' }}>
          <Card title="Set a price"><PriceForm instruments={instruments.data ?? []} /></Card>
          <Card title="Add an instrument"><AddInstrumentForm /></Card>
        </div>
      </div>
    </>
  );
}

function InstrumentRow({ instrument }: { instrument: Instrument }) {
  const prices = usePriceHistory(instrument.symbol, 30);
  const values = prices.data?.map((p) => p.price) ?? [];
  const up = values.length > 1 ? (values.at(-1) ?? 0) >= (values[0] ?? 0) : true;
  return (
    <tr>
      <td><strong>{instrument.symbol}</strong><div className="muted small">{instrument.name}</div></td>
      <td className="hide-mobile">{values.length > 1 ? <Sparkline values={values} positive={up} /> : <span className="muted">—</span>}</td>
      <td className="right num" style={{ fontWeight: 650 }}>{instrument.lastPrice != null ? formatCents(instrument.lastPrice) : '—'}</td>
      <td className="right muted small hide-mobile">{instrument.priceAsOf ? formatDateTime(instrument.priceAsOf) : 'no price'}</td>
    </tr>
  );
}

function PriceForm({ instruments }: { instruments: Instrument[] }) {
  const [symbol, setSymbol] = useState('');
  const [priceText, setPriceText] = useState('');
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);
  const refresh = useRefresh();
  const chosen = symbol || instruments[0]?.symbol || '';

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    try {
      const price = await api.recordPrice(chosen, parseAmountToCents(priceText));
      setPriceText('');
      await refresh();
      setMessage({ tone: 'success', text: `${price.symbol} is now ${formatCents(price.price)}.` });
    } catch (err) {
      setMessage({ tone: 'error', text: describeError(err) });
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <div className="field">
        <label htmlFor="priceSymbol">Instrument</label>
        <select id="priceSymbol" className="input" value={chosen} onChange={(e) => setSymbol(e.target.value)}>
          {instruments.map((i) => <option key={i.symbol} value={i.symbol}>{i.symbol} · {i.name}</option>)}
        </select>
      </div>
      <div className="field">
        <label htmlFor="price">New price per share (EUR)</label>
        <input id="price" className="input num" inputMode="decimal" value={priceText} onChange={(e) => setPriceText(e.target.value)}
          placeholder="e.g. 12,34" />
      </div>
      {message && <Alert tone={message.tone}>{message.text}</Alert>}
      <button className="btn secondary" disabled={!chosen}>Set price</button>
    </form>
  );
}

function AddInstrumentForm() {
  const [symbol, setSymbol] = useState('');
  const [name, setName] = useState('');
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);
  const refresh = useRefresh();

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    try {
      const created = await api.createInstrument(symbol.trim().toUpperCase(), name.trim());
      setSymbol('');
      setName('');
      await refresh();
      setMessage({ tone: 'success', text: `Added ${created.symbol}. Set a price before it can be traded.` });
    } catch (err) {
      setMessage({ tone: 'error', text: describeError(err) });
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <div className="field">
        <label htmlFor="symbol">Symbol</label>
        <input id="symbol" className="input" required maxLength={12} value={symbol} onChange={(e) => setSymbol(e.target.value)}
          placeholder="e.g. ACME" />
      </div>
      <div className="field">
        <label htmlFor="instrumentName">Name</label>
        <input id="instrumentName" className="input" required maxLength={255} value={name} onChange={(e) => setName(e.target.value)}
          placeholder="e.g. ACME Corp." />
      </div>
      {message && <Alert tone={message.tone}>{message.text}</Alert>}
      <button className="btn secondary">Add instrument</button>
    </form>
  );
}
