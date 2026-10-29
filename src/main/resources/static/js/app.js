import { api, ApiError, MoneyOperation, NetworkError } from './api.js';
import { formatCents, parseAmountToCents, parseShareQuantity } from './money.js';

// There is no "list all accounts" endpoint (a bank would never hand every account to a browser),
// so the page remembers the ids of accounts opened or added here, in this browser only.
const STORAGE_KEY = 'ledger-core.accountIds';
const PAGE_SIZE = 10;

const state = {
  accounts: new Map(), // id -> account from the API
  instruments: [],     // from GET /instruments, with their current prices
  selectedId: null,
  page: 0,
};

const $ = (selector) => document.querySelector(selector);

function loadIds() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
  } catch {
    return [];
  }
}

function saveIds() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify([...state.accounts.keys()]));
  } catch {
    // Storage blocked (private mode): the page still works, it just forgets on reload.
  }
}

/** A short, human-readable message for a failed request. */
function describe(error) {
  if (error instanceof NetworkError) {
    return `${error.message} Submit again to retry safely: the same Idempotency-Key is reused.`;
  }
  if (error instanceof ApiError) {
    const { title, detail } = error.problem;
    return title && detail ? `${title}: ${detail}` : error.message;
  }
  return error.message;
}

function showResult(element, message, ok) {
  element.textContent = message;
  element.className = `result ${ok ? 'ok' : 'error'}`;
}

/** Gains with an explicit sign: "+12.00 EUR" / "−3.00 EUR". */
function signed(cents) {
  return cents > 0 ? `+${formatCents(cents)}` : formatCents(cents);
}

function gainClass(cents) {
  return cents > 0 ? 'in' : cents < 0 ? 'out' : '';
}

/** A table cell with text and an optional CSS class. */
function cell(text, className = '') {
  const td = document.createElement('td');
  td.textContent = text;
  td.className = className;
  return td;
}

function label(account) {
  return `${account.ownerName} (${account.id.slice(0, 8)})`;
}

// ---- rendering ---------------------------------------------------------------------------------

function renderAccountList() {
  const list = $('#account-list');
  list.replaceChildren();
  for (const account of state.accounts.values()) {
    const button = document.createElement('button');
    button.type = 'button';
    button.setAttribute('aria-current', String(account.id === state.selectedId));
    const name = document.createElement('span');
    name.textContent = account.ownerName;
    const balance = document.createElement('span');
    balance.textContent = formatCents(account.balance, account.currency);
    button.append(name, balance);
    button.addEventListener('click', () => select(account.id));
    const item = document.createElement('li');
    item.append(button);
    list.append(item);
  }
}

function renderDetail() {
  const account = state.accounts.get(state.selectedId);
  $('#no-selection').hidden = Boolean(account);
  $('#account-detail').hidden = !account;
  if (!account) {
    return;
  }
  $('#detail-owner').textContent = account.ownerName;
  $('#detail-id').textContent = account.id;
  $('#detail-balance').textContent = formatCents(account.balance, account.currency);

  // Transfer targets: every other remembered account.
  const select = $('#transfer-form select[name="toAccountId"]');
  const previous = select.value;
  select.replaceChildren();
  for (const other of state.accounts.values()) {
    if (other.id !== account.id) {
      select.append(new Option(label(other), other.id));
    }
  }
  if (select.options.length === 0) {
    select.append(new Option('Open a second account first', ''));
  }
  select.value = [...select.options].some(o => o.value === previous) ? previous : select.options[0].value;
}

async function renderStatement() {
  const id = state.selectedId;
  if (!id) {
    return;
  }
  const statement = await api.getStatement(id, state.page, PAGE_SIZE);
  const rows = $('#statement-rows');
  rows.replaceChildren();
  for (const line of statement.entries) {
    const tr = document.createElement('tr');
    const cells = [
      [new Date(line.createdAt).toLocaleString(), ''],
      [line.transactionType, ''],
      [line.description ?? '', 'desc'],
    ];
    for (const [text, className] of cells) {
      const td = document.createElement('td');
      td.textContent = text;
      td.className = className;
      tr.append(td);
    }
    const amount = document.createElement('td');
    amount.className = `num ${line.amount < 0 ? 'out' : 'in'}`;
    amount.textContent = formatCents(line.amount);
    tr.append(amount);

    const action = document.createElement('td');
    // Reversals cannot be reversed, and trades are undone by trading back, not by a reversal.
    if (line.transactionType !== 'REVERSAL' && line.transactionType !== 'TRADE') {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'secondary';
      button.textContent = 'Reverse';
      button.title = `Reverse transaction ${line.transactionId}`;
      button.addEventListener('click', () => reverse(line.transactionId, button));
      action.append(button);
    }
    tr.append(action);
    rows.append(tr);
  }
  if (statement.entries.length === 0) {
    const tr = document.createElement('tr');
    const td = document.createElement('td');
    td.colSpan = 5;
    td.className = 'muted';
    td.textContent = 'No entries yet.';
    tr.append(td);
    rows.append(tr);
  }
  const pages = Math.max(statement.totalPages, 1);
  $('#page-info').textContent = `Page ${statement.page + 1} of ${pages} · ${statement.totalEntries} entries`;
  $('#prev-page').disabled = state.page === 0;
  $('#next-page').disabled = state.page + 1 >= pages;
}

async function renderMarket() {
  state.instruments = await api.listInstruments();
  const list = $('#instrument-list');
  list.replaceChildren();
  for (const instrument of state.instruments) {
    const item = document.createElement('li');
    const name = document.createElement('span');
    name.textContent = `${instrument.symbol} · ${instrument.name}`;
    const price = document.createElement('span');
    price.className = 'price';
    price.textContent = instrument.lastPrice == null ? 'no price' : formatCents(instrument.lastPrice, instrument.currency);
    item.append(name, price);
    list.append(item);
  }
  if (state.instruments.length === 0) {
    const item = document.createElement('li');
    item.className = 'muted';
    item.textContent = 'No instruments yet.';
    list.append(item);
  }
  for (const select of document.querySelectorAll('#price-form select[name="symbol"], #trade-form select[name="symbol"]')) {
    const previous = select.value;
    select.replaceChildren(...state.instruments.map(i => new Option(i.symbol, i.symbol)));
    if (state.instruments.some(i => i.symbol === previous)) {
      select.value = previous;
    }
  }
}

async function renderPortfolio() {
  const id = state.selectedId;
  if (!id) {
    return;
  }
  const portfolio = await api.getPortfolio(id);
  const summary = $('#portfolio-summary');
  summary.replaceChildren();
  const figures = [
    ['Cash', formatCents(portfolio.cash), ''],
    ['Investments', formatCents(portfolio.marketValue), ''],
    ['Total', formatCents(portfolio.totalValue), ''],
    ['Unrealized gain/loss', signed(portfolio.unrealizedPnl), gainClass(portfolio.unrealizedPnl)],
    ['Realized gain/loss', signed(portfolio.realizedPnl), gainClass(portfolio.realizedPnl)],
  ];
  for (const [term, value, className] of figures) {
    const box = document.createElement('div');
    const dt = document.createElement('dt');
    dt.textContent = term;
    const dd = document.createElement('dd');
    dd.textContent = value;
    dd.className = className;
    box.append(dt, dd);
    summary.append(box);
  }

  const rows = $('#position-rows');
  rows.replaceChildren();
  for (const p of portfolio.positions) {
    const tr = document.createElement('tr');
    tr.append(
      cell(p.symbol),
      cell(String(p.quantity), 'num'),
      cell(formatCents(p.costBasis), 'num'),
      cell(formatCents(p.lastPrice), 'num'),
      cell(formatCents(p.marketValue), 'num'),
      cell(signed(p.unrealizedPnl), `num ${gainClass(p.unrealizedPnl)}`),
    );
    rows.append(tr);
  }
  if (portfolio.positions.length === 0) {
    const tr = document.createElement('tr');
    const td = cell('No shares held.', 'muted');
    td.colSpan = 6;
    tr.append(td);
    rows.append(tr);
  }
}

async function renderHealth() {
  const badge = $('#health');
  try {
    const health = await api.ledgerHealth();
    badge.textContent = health.healthy ? 'Books balance' : 'Books do NOT balance';
    badge.className = `badge ${health.healthy ? 'ok' : 'bad'}`;
  } catch {
    badge.textContent = 'Ledger unreachable';
    badge.className = 'badge bad';
  }
}

/** Reloads the given accounts' balances from the server, then redraws everything. */
async function refresh(...ids) {
  for (const id of ids) {
    if (id && state.accounts.has(id)) {
      state.accounts.set(id, await api.getAccount(id));
    }
  }
  renderAccountList();
  renderDetail();
  await Promise.all([renderStatement(), renderPortfolio(), renderMarket(), renderHealth()]);
}

/** The accounts whose balance a response changed: a transaction's entries, or a trade's account. */
function affectedAccounts(body) {
  return body.entries ? body.entries.map(e => e.accountId) : [body.accountId];
}

async function select(id) {
  state.selectedId = id;
  state.page = 0;
  $('#statement-result').textContent = '';
  await refresh(id);
}

// ---- actions -----------------------------------------------------------------------------------

async function addAccount(id) {
  const account = await api.getAccount(id);
  state.accounts.set(account.id, account);
  saveIds();
  await select(account.id);
}

/**
 * Wires a money-moving form to one MoneyOperation. Editing any field turns it into a different
 * operation, so the pending Idempotency-Key is dropped.
 */
function moneyForm(form, buildRequest, describeSuccess = () => 'Booked.') {
  const operation = new MoneyOperation();
  const result = form.querySelector('.result');
  form.addEventListener('input', () => operation.reset());
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const button = form.querySelector('button[type="submit"]');
    let request;
    try {
      request = buildRequest(new FormData(form));
    } catch (e) {
      showResult(result, e.message, false);
      return;
    }
    button.disabled = true;
    try {
      const { transaction, replayed } = await operation.send(request.path, request.body);
      form.reset();
      // Reload balances first, so the confirmation never appears next to an outdated balance.
      await refresh(...affectedAccounts(transaction));
      showResult(result,
        replayed ? 'Already booked earlier (replayed, nothing booked twice).' : describeSuccess(transaction), true);
    } catch (e) {
      showResult(result, describe(e), false);
    } finally {
      button.disabled = false;
    }
  });
}

async function reverse(transactionId, button) {
  if (!confirm('Reverse this transaction? This books its mirror image; the original stays in the history.')) {
    return;
  }
  const result = $('#statement-result');
  button.disabled = true;
  try {
    const { transaction } = await new MoneyOperation().send(`/transactions/${transactionId}/reversal`, undefined);
    await refresh(...affectedAccounts(transaction));
    showResult(result, 'Reversed.', true);
  } catch (e) {
    showResult(result, describe(e), false);
    button.disabled = false;
  }
}

// ---- start -------------------------------------------------------------------------------------

function wireUp() {
  $('#open-account-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.target;
    try {
      const account = await api.openAccount(new FormData(form).get('ownerName').trim());
      form.reset();
      state.accounts.set(account.id, account);
      saveIds();
      await select(account.id);
    } catch (e) {
      alert(describe(e));
    }
  });

  $('#add-account-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.target;
    try {
      await addAccount(new FormData(form).get('accountId').trim());
      form.reset();
    } catch (e) {
      alert(describe(e));
    }
  });

  moneyForm($('#deposit-form'), (data) => ({
    path: '/deposits',
    body: {
      accountId: state.selectedId,
      amount: parseAmountToCents(data.get('amount')),
      currency: 'EUR',
      description: data.get('description') || undefined,
    },
  }));

  moneyForm($('#transfer-form'), (data) => {
    const toAccountId = data.get('toAccountId');
    if (!toAccountId) {
      throw new Error('Open or add a second account to transfer to.');
    }
    return {
      path: '/transfers',
      body: {
        fromAccountId: state.selectedId,
        toAccountId,
        amount: parseAmountToCents(data.get('amount')),
        currency: 'EUR',
        description: data.get('description') || undefined,
      },
    };
  });

  moneyForm($('#trade-form'), (data) => {
    if (!data.get('symbol')) {
      throw new Error('Add an instrument in the Market panel first.');
    }
    return {
      path: `/accounts/${state.selectedId}/trades`,
      body: { side: data.get('side'), symbol: data.get('symbol'), quantity: parseShareQuantity(data.get('quantity')) },
    };
  }, (trade) => `Done: ${trade.side} ${trade.quantity} ${trade.symbol} at ${formatCents(trade.price)}, `
    + `${formatCents(trade.grossAmount)} in total.`);

  $('#add-instrument-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.target;
    const result = form.querySelector('.result');
    const data = new FormData(form);
    try {
      const instrument = await api.createInstrument(data.get('symbol').trim(), data.get('name').trim());
      form.reset();
      await renderMarket();
      showResult(result, `Added ${instrument.symbol}. Set a price before trading it.`, true);
    } catch (e) {
      showResult(result, describe(e), false);
    }
  });

  $('#price-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.target;
    const result = form.querySelector('.result');
    const data = new FormData(form);
    try {
      const price = await api.recordPrice(data.get('symbol'), parseAmountToCents(data.get('price')));
      form.querySelector('input[name="price"]').value = '';
      await refresh();
      showResult(result, `${price.symbol} is now ${formatCents(price.price)}.`, true);
    } catch (e) {
      showResult(result, describe(e), false);
    }
  });

  $('#prev-page').addEventListener('click', () => { state.page--; renderStatement(); });
  $('#next-page').addEventListener('click', () => { state.page++; renderStatement(); });
}

async function start() {
  wireUp();
  for (const id of loadIds()) {
    try {
      const account = await api.getAccount(id);
      state.accounts.set(account.id, account);
    } catch (e) {
      // The account no longer exists (e.g. the database was reset): forget it.
      if (!(e instanceof ApiError && e.status === 404)) {
        throw e;
      }
    }
  }
  saveIds();
  const first = state.accounts.keys().next();
  if (!first.done) {
    await select(first.value);
  } else {
    renderAccountList();
    renderDetail();
    await Promise.all([renderMarket(), renderHealth()]);
  }
}

start();
