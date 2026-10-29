// Small inline icons (stroke-based, 24px grid), so no icon library is needed.
import type { SVGProps } from 'react';

function Icon({ d, ...props }: SVGProps<SVGSVGElement> & { d: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round"
      strokeLinejoin="round" aria-hidden="true" {...props}>
      <path d={d} />
    </svg>
  );
}

export const HomeIcon = () => <Icon d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z" />;
export const WalletIcon = () => <Icon d="M3 7a2 2 0 0 1 2-2h13v4M3 7v10a2 2 0 0 0 2 2h15V9H5a2 2 0 0 1-2-2zm14 7h.01" />;
export const SendIcon = () => <Icon d="M22 2 11 13M22 2l-7 20-4-9-9-4z" />;
export const ChartIcon = () => <Icon d="M3 3v18h18M7 15l4-4 3 3 6-6" />;
export const MarketIcon = () => <Icon d="M4 19V9m6 10V5m6 14v-7m4 7H2" />;
export const ShieldIcon = () => <Icon d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10zm-3-10 2 2 4-4" />;
export const ArrowDownIcon = () => <Icon d="M12 5v14m-6-6 6 6 6-6" />;
export const ArrowUpIcon = () => <Icon d="M12 19V5m-6 6 6-6 6 6" />;
export const UndoIcon = () => <Icon d="M9 14 4 9l5-5M4 9h11a5 5 0 0 1 0 10h-3" />;
export const TradeIcon = () => <Icon d="M7 4v16m0 0-3-3m3 3 3-3m7-13v16m0-16-3 3m3-3 3 3" />;
export const CheckIcon = () => <Icon d="m5 12 5 5L20 7" />;
export const PlusIcon = () => <Icon d="M12 5v14M5 12h14" />;
