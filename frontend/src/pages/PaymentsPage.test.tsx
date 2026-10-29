import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { SelectionProvider } from '../lib/selection';
import { PaymentsPage } from './PaymentsPage';

const alice = { id: 'a1', ownerName: 'Alice Example', type: 'CUSTOMER', currency: 'EUR', balance: 10_000, status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' };
const bob = { id: 'b2', ownerName: 'Bob Example', type: 'CUSTOMER', currency: 'EUR', balance: 0, status: 'ACTIVE', createdAt: '2026-01-02T00:00:00Z' };

function json(body: unknown, status = 200, headers: Record<string, string> = {}) {
  return Promise.resolve(new Response(JSON.stringify(body), { status, headers }));
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <SelectionProvider>
          <PaymentsPage />
        </SelectionProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('PaymentsPage transfer', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => vi.restoreAllMocks());

  it('reviews, then sends one transfer with an Idempotency-Key and shows a receipt', async () => {
    const posts: { url: string; key: string | null; body: unknown }[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (init?.method === 'POST') {
        posts.push({ url, key: new Headers(init.headers).get('Idempotency-Key'), body: JSON.parse(String(init.body)) });
        return json({ id: 'tx-1', type: 'TRANSFER', description: 'Rent', createdAt: '2026-10-29T10:00:00Z', entries: [] }, 201,
          { 'Idempotent-Replayed': 'false' });
      }
      return json({ accounts: [alice, bob], page: 0, size: 100, totalAccounts: 2, totalPages: 1 });
    });
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByLabelText('Amount (EUR)'), '25,50');
    await user.type(screen.getByLabelText('Reference'), 'Rent');
    await user.click(screen.getByRole('button', { name: 'Review transfer' }));

    // Review step: nothing has been sent yet.
    expect(await screen.findByText('25,50 €')).toBeInTheDocument();
    expect(screen.getByText('Bob Example')).toBeInTheDocument();
    expect(posts).toHaveLength(0);

    await user.click(screen.getByRole('button', { name: 'Confirm and send' }));

    expect(await screen.findByText('Money sent')).toBeInTheDocument();
    expect(posts).toHaveLength(1);
    expect(posts[0]?.url).toBe('/transfers');
    expect(posts[0]?.key).toBeTruthy();
    expect(posts[0]?.body).toEqual({ fromAccountId: 'a1', toAccountId: 'b2', amount: 2550, currency: 'EUR', description: 'Rent' });
  });

  it('does not review an amount with more than two decimals', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      json({ accounts: [alice, bob], page: 0, size: 100, totalAccounts: 2, totalPages: 1 }));
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByLabelText('Amount (EUR)'), '1.234');
    await user.click(screen.getByRole('button', { name: 'Review transfer' }));

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/at most 2 decimals/));
    expect(screen.queryByRole('button', { name: 'Confirm and send' })).not.toBeInTheDocument();
  });
});
