import { ApiError, MoneyOperation, NetworkError, api } from './client';

function respond(status: number, body: unknown, headers: Record<string, string> = {}) {
  return Promise.resolve(new Response(JSON.stringify(body), { status, headers }));
}

describe('MoneyOperation', () => {
  afterEach(() => vi.restoreAllMocks());

  it('reuses the same Idempotency-Key when retrying after a network error', async () => {
    const keys: string[] = [];
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((_url, init) => {
      keys.push(new Headers(init?.headers).get('Idempotency-Key') ?? '');
      return keys.length === 1 ? Promise.reject(new TypeError('network down')) : respond(201, { id: 'tx' });
    });
    const operation = new MoneyOperation();

    await expect(operation.send('/transfers', {})).rejects.toBeInstanceOf(NetworkError);
    await operation.send('/transfers', {});

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(keys[0]).toBeTruthy();
    expect(keys[1]).toBe(keys[0]);
  });

  it('starts a new operation after a definite answer', async () => {
    const keys: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((_url, init) => {
      keys.push(new Headers(init?.headers).get('Idempotency-Key') ?? '');
      return keys.length === 1 ? respond(422, { title: 'Insufficient funds' }) : respond(201, {});
    });
    const operation = new MoneyOperation();

    await expect(operation.send('/transfers', {})).rejects.toBeInstanceOf(ApiError);
    await operation.send('/transfers', {});

    expect(keys[1]).not.toBe(keys[0]);
  });

  it('reports a replayed response', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => respond(201, {}, { 'Idempotent-Replayed': 'true' }));

    const { replayed } = await new MoneyOperation().send('/deposits', {});

    expect(replayed).toBe(true);
  });
});

describe('api.ledgerHealth', () => {
  afterEach(() => vi.restoreAllMocks());

  it('treats 503 as an answer: the books do not balance', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      respond(503, { healthy: false, sumOfAllEntries: 0, balanceMismatches: [] }),
    );

    const health = await api.ledgerHealth();

    expect(health.healthy).toBe(false);
  });
});
