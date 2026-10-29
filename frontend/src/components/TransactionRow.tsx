import type { StatementLine } from '../api/types';
import { formatDateTime } from '../lib/format';
import { Amount, Badge } from './ui';
import { ArrowDownIcon, ArrowUpIcon, TradeIcon, UndoIcon } from './icons';

const typeLabel: Record<StatementLine['transactionType'], string> = {
  DEPOSIT: 'Deposit',
  TRANSFER: 'Transfer',
  REVERSAL: 'Reversal',
  TRADE: 'Securities trade',
};

function icon(line: StatementLine) {
  if (line.transactionType === 'REVERSAL') return <UndoIcon />;
  if (line.transactionType === 'TRADE') return <TradeIcon />;
  return line.amount > 0 ? <ArrowDownIcon /> : <ArrowUpIcon />;
}

export const transactionTypeLabel = typeLabel;

export function TransactionRow({ line, onOpen }: { line: StatementLine; onOpen?: () => void }) {
  const content = (
    <>
      <div className={`tx-icon ${line.amount > 0 ? 'in' : 'out'}`}>{icon(line)}</div>
      <div style={{ minWidth: 0 }}>
        <div className="tx-title">{line.description || typeLabel[line.transactionType]}</div>
        <div className="muted small">
          {typeLabel[line.transactionType]} · {formatDateTime(line.createdAt)}
          {line.reversedByTransactionId && <> · <Badge>Reversed</Badge></>}
        </div>
      </div>
      <div className="tx-amount">
        <Amount cents={line.amount} signed />
      </div>
    </>
  );
  return onOpen
    ? <button type="button" className="tx tx-button" onClick={onOpen}>{content}</button>
    : <div className="tx">{content}</div>;
}
