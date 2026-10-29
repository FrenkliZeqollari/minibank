import {
  Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, Line, LineChart, Pie, PieChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { formatAxisCents, formatCents } from '../lib/money';
import { formatDay, formatMonth } from '../lib/format';

// Charts display money that the API delivers in cents; values are only scaled for axis labels.
const grid = 'var(--border)';
const axis = { stroke: 'var(--muted)', fontSize: 12, tickLine: false, axisLine: false } as const;

function ChartTooltip({ active, payload, label, labelFormatter }: {
  active?: boolean; payload?: { name?: string; value?: number; color?: string }[]; label?: string;
  labelFormatter?: (l: string) => string;
}) {
  if (!active || !payload?.length) {
    return null;
  }
  return (
    <div className="tooltip">
      {label !== undefined && <div className="muted small">{labelFormatter ? labelFormatter(label) : label}</div>}
      {payload.map((p) => (
        <div key={p.name} className="num" style={{ fontWeight: 600 }}>
          {payload.length > 1 && <span style={{ color: p.color }}>{p.name}: </span>}
          {formatCents(p.value ?? 0)}
        </div>
      ))}
    </div>
  );
}

export function BalanceChart({ points }: { points: { date: string; balance: number }[] }) {
  return (
    <div className="chart" role="img" aria-label="Balance over time">
      <ResponsiveContainer>
        <AreaChart data={points} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
          <defs>
            <linearGradient id="balanceFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="var(--chart-1)" stopOpacity={0.3} />
              <stop offset="100%" stopColor="var(--chart-1)" stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid stroke={grid} vertical={false} />
          <XAxis dataKey="date" tickFormatter={formatDay} minTickGap={36} {...axis} />
          <YAxis tickFormatter={formatAxisCents} width={78} {...axis} />
          <Tooltip content={<ChartTooltip labelFormatter={formatDay} />} />
          <Area type="monotone" dataKey="balance" name="Balance" stroke="var(--chart-1)" strokeWidth={2.2}
            fill="url(#balanceFill)" isAnimationActive={false} />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

export function MonthlyChart({ months }: { months: { month: string; moneyIn: number; moneyOut: number }[] }) {
  return (
    <>
      <div className="legend">
        <span><i className="swatch" style={{ background: 'var(--chart-2)' }} />Money in</span>
        <span><i className="swatch" style={{ background: 'var(--chart-3)' }} />Money out</span>
      </div>
      <div className="chart small" role="img" aria-label="Money in and out per month">
        <ResponsiveContainer>
          <BarChart data={months} margin={{ top: 8, right: 8, left: 0, bottom: 0 }} barGap={4}>
            <CartesianGrid stroke={grid} vertical={false} />
            <XAxis dataKey="month" tickFormatter={formatMonth} {...axis} />
            <YAxis tickFormatter={formatAxisCents} width={78} {...axis} />
            <Tooltip content={<ChartTooltip labelFormatter={formatMonth} />} cursor={{ fill: 'var(--surface-2)' }} />
            <Bar dataKey="moneyIn" name="In" fill="var(--chart-2)" radius={[6, 6, 0, 0]} isAnimationActive={false} />
            <Bar dataKey="moneyOut" name="Out" fill="var(--chart-3)" radius={[6, 6, 0, 0]} isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </>
  );
}

const sliceColors = ['var(--chart-1)', 'var(--chart-2)', 'var(--chart-3)', 'var(--chart-4)', 'var(--chart-5)'];

export function AllocationChart({ slices }: { slices: { name: string; value: number }[] }) {
  const total = slices.reduce((sum, s) => sum + s.value, 0);
  return (
    <div className="row" style={{ alignItems: 'center', gap: 20 }}>
      <div style={{ width: 170, height: 170 }} role="img" aria-label="Allocation">
        <ResponsiveContainer>
          <PieChart>
            <Pie data={slices} dataKey="value" nameKey="name" innerRadius={52} outerRadius={80} paddingAngle={2}
              stroke="none" isAnimationActive={false}>
              {slices.map((s, i) => <Cell key={s.name} fill={sliceColors[i % sliceColors.length]} />)}
            </Pie>
            <Tooltip content={<ChartTooltip />} />
          </PieChart>
        </ResponsiveContainer>
      </div>
      <div className="legend" style={{ flexDirection: 'column', gap: 8 }}>
        {slices.map((s, i) => (
          <span key={s.name}>
            <i className="swatch" style={{ background: sliceColors[i % sliceColors.length] }} />
            <strong style={{ color: 'var(--text)' }}>{s.name}</strong>
            {total > 0 && <span className="num">{((s.value * 100) / total).toFixed(1)} %</span>}
          </span>
        ))}
      </div>
    </div>
  );
}

export function PriceChart({ prices, height = 220 }: { prices: { createdAt: string; price: number }[]; height?: number }) {
  const data = prices.map((p) => ({ date: p.createdAt.slice(0, 10), price: p.price }));
  return (
    <div style={{ width: '100%', height }} role="img" aria-label="Price history">
      <ResponsiveContainer>
        <LineChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
          <CartesianGrid stroke={grid} vertical={false} />
          <XAxis dataKey="date" tickFormatter={formatDay} minTickGap={36} {...axis} />
          <YAxis tickFormatter={formatAxisCents} width={70} domain={['auto', 'auto']} {...axis} />
          <Tooltip content={<ChartTooltip labelFormatter={formatDay} />} />
          <Line type="monotone" dataKey="price" name="Price" stroke="var(--chart-1)" strokeWidth={2} dot={false}
            isAnimationActive={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

export function Sparkline({ values, positive }: { values: number[]; positive: boolean }) {
  const data = values.map((v, i) => ({ i, v }));
  return (
    <div style={{ width: 110, height: 36 }} aria-hidden="true">
      <ResponsiveContainer>
        <LineChart data={data}>
          <YAxis hide domain={['dataMin', 'dataMax']} />
          <Line type="monotone" dataKey="v" stroke={positive ? 'var(--positive)' : 'var(--negative)'} strokeWidth={1.8}
            dot={false} isAnimationActive={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
