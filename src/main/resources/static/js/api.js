// Thin client for the ledger API. Same origin as the page, so no CORS setup is needed.

/** An error response from the API, carrying its RFC 7807 Problem Details. */
export class ApiError extends Error {
  constructor(status, problem) {
    super(problem?.detail || problem?.title || `HTTP ${status}`);
    this.status = status;
    this.problem = problem ?? {};
  }
}

/** The request never got an answer (network down, server unreachable). It may or may not have happened. */
export class NetworkError extends Error {}

async function request(method, path, { body, headers } = {}) {
  let response;
  try {
    response = await fetch(path, {
      method,
      headers: { ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}), ...headers },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch (e) {
    throw new NetworkError('No answer from the server. The request may or may not have been processed.');
  }
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new ApiError(response.status, data);
  }
  return { data, headers: response.headers };
}

function newIdempotencyKey() {
  if (crypto.randomUUID) {
    return crypto.randomUUID();
  }
  // Fallback for browsers that only offer randomUUID on https pages.
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
}

/**
 * One money-moving operation the user intends to do, e.g. "transfer 25.00 to Bob".
 *
 * The Idempotency-Key is created once per operation and kept until the server gives a definite
 * answer. If the network fails, sending again reuses the same key, so the server books the
 * operation at most once, even if the first attempt did arrive. Once the user changes the form,
 * it is a different operation and needs a new key: call reset().
 */
export class MoneyOperation {
  #key = null;

  get pendingKey() {
    return this.#key;
  }

  reset() {
    this.#key = null;
  }

  async send(path, body) {
    this.#key ??= newIdempotencyKey();
    try {
      const result = await request('POST', path, { body, headers: { 'Idempotency-Key': this.#key } });
      this.#key = null; // definite answer: done
      // 'transaction' is the response body: a transaction, or a trade for /trades.
      return { transaction: result.data, replayed: result.headers.get('Idempotent-Replayed') === 'true' };
    } catch (e) {
      if (!(e instanceof NetworkError)) {
        this.#key = null; // the server answered (e.g. 422): a definite "no", nothing was booked
      }
      throw e;
    }
  }
}

export const api = {
  openAccount: (ownerName) => request('POST', '/accounts', { body: { ownerName, currency: 'EUR' } }).then(r => r.data),
  getAccount: (id) => request('GET', `/accounts/${encodeURIComponent(id)}`).then(r => r.data),
  getStatement: (id, page, size) =>
    request('GET', `/accounts/${encodeURIComponent(id)}/entries?page=${page}&size=${size}`).then(r => r.data),
  ledgerHealth: () => request('GET', '/ledger/health').then(r => r.data),
  listInstruments: () => request('GET', '/instruments').then(r => r.data),
  createInstrument: (symbol, name) =>
    request('POST', '/instruments', { body: { symbol, name, currency: 'EUR' } }).then(r => r.data),
  recordPrice: (symbol, price) =>
    request('POST', `/instruments/${encodeURIComponent(symbol)}/prices`, { body: { price } }).then(r => r.data),
  getPortfolio: (id) => request('GET', `/accounts/${encodeURIComponent(id)}/portfolio`).then(r => r.data),
};
