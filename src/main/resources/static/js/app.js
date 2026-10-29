import { api, ApiError, MoneyOperation, NetworkError } from './api.js';
import { formatCents, parseAmountToCents } from './money.js';

// There is no "list all accounts" endpoint (a bank would never hand every account to a browser),
// so the page remembers the ids of accounts opened or added here, in this browser only.
const STORAGE_KEY = 'ledger-core.accountIds';
const PAGE_SIZE = 10;

const state = {
  accounts: new Map(), // id -> account from the API
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
    if (line.transactionType !== 'REVERSAL') {
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
  await Promise.all([renderStatement(), renderHealth()]);
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
function moneyForm(form, buildRequest) {
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
      await refresh(...transaction.entries.map(e => e.accountId));
      showResult(result, replayed ? 'Already booked earlier (replayed, nothing booked twice).' : 'Booked.', true);
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
    await refresh(...transaction.entries.map(e => e.accountId));
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
    await renderHealth();
  }
}

start();
