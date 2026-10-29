import type { ReactNode } from 'react';
import { formatCents, formatSignedCents } from '../lib/money';

export function Card({ title, action, children, className = '' }:
  { title?: ReactNode; action?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={`card ${className}`}>
      {(title || action) && (
        <div className="card-head">
          {typeof title === 'string' ? <h2>{title}</h2> : title}
          {action}
        </div>
      )}
      {children}
    </section>
  );
}

export function Stat({ label, value, sub, tone }:
  { label: string; value: ReactNode; sub?: ReactNode; tone?: 'positive' | 'negative' }) {
  return (
    <div className="card">
      <div className="stat-label">{label}</div>
      <div className={`stat-value num ${tone ?? ''}`}>{value}</div>
      {sub && <div className="stat-sub muted">{sub}</div>}
    </div>
  );
}

/** An amount in cents, colored by sign when {@code signed} (gains/losses, entries). */
export function Amount({ cents, signed = false, currency = 'EUR' }: { cents: number; signed?: boolean; currency?: string }) {
  if (!signed) {
    return <span className="num">{formatCents(cents, currency)}</span>;
  }
  const tone = cents > 0 ? 'positive' : cents < 0 ? 'negative' : '';
  return <span className={`num ${tone}`}>{formatSignedCents(cents, currency)}</span>;
}

export function Badge({ tone, children }: { tone?: 'ok' | 'bad' | 'info' | 'warn'; children: ReactNode }) {
  return <span className={`badge ${tone ?? ''}`}>{children}</span>;
}

export function Alert({ tone, children }: { tone: 'error' | 'success' | 'info'; children: ReactNode }) {
  return <div className={`alert ${tone}`} role={tone === 'error' ? 'alert' : 'status'}>{children}</div>;
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>;
}

export function Loading() {
  return <Empty>Loading…</Empty>;
}

export function Modal({ title, children, onClose }: { title: string; children: ReactNode; onClose: () => void }) {
  return (
    <div className="backdrop" onClick={onClose}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <h2>{title}</h2>
        {children}
      </div>
    </div>
  );
}

export function Segmented<T extends string>({ value, options, onChange, label }:
  { value: T; options: { value: T; label: string }[]; onChange: (v: T) => void; label: string }) {
  return (
    <div className="segmented" role="group" aria-label={label}>
      {options.map((o) => (
        <button key={o.value} type="button" aria-pressed={o.value === value} onClick={() => onChange(o.value)}>
          {o.label}
        </button>
      ))}
    </div>
  );
}
