// Money in the browser follows the same rule as on the server: whole cents in an integer,
// never floating point. "0.1 + 0.2" is 0.30000000000000004 in JavaScript, so a typed amount
// like "19.99" must never pass through a float on its way to the API.

/**
 * Parses an amount typed by a person ("25", "25.5", "25.50", or "25,50" with a German decimal
 * comma) into a whole number of cents, using only string and integer operations.
 * Throws an Error with a readable message if the input is not a valid positive amount.
 */
export function parseAmountToCents(input) {
  const text = String(input ?? '').trim();
  const match = /^(\d+)(?:[.,](\d{1,2}))?$/.exec(text);
  if (!match) {
    throw new Error('Enter an amount like 25 or 25.50 (at most 2 decimals, no thousands separators).');
  }
  const units = match[1];
  const fraction = (match[2] ?? '').padEnd(2, '0'); // "5" -> "50", "" -> "00"
  const cents = BigInt(units) * 100n + BigInt(fraction);
  if (cents <= 0n) {
    throw new Error('The amount must be greater than 0.');
  }
  if (cents > BigInt(Number.MAX_SAFE_INTEGER)) {
    throw new Error('The amount is too large.');
  }
  return Number(cents);
}

/**
 * Formats cents for display, e.g. 123456 -> "1,234.56 EUR" and -2500 -> "−25.00 EUR".
 * Integer arithmetic only: the cents are split off with % and never divided into a float.
 */
export function formatCents(cents, currency = 'EUR') {
  if (!Number.isSafeInteger(cents)) {
    throw new Error(`Not a whole number of cents: ${cents}`);
  }
  const sign = cents < 0 ? '−' : ''; // a real minus sign, easier to read than a hyphen
  const abs = Math.abs(cents);
  const units = Math.trunc(abs / 100).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  const fraction = String(abs % 100).padStart(2, '0');
  return `${sign}${units}.${fraction} ${currency}`;
}

/** Parses a number of whole shares typed by a person ("10"). Throws for anything else. */
export function parseShareQuantity(input) {
  const text = String(input ?? '').trim();
  if (!/^\d+$/.test(text)) {
    throw new Error('Enter a whole number of shares, like 10.');
  }
  const quantity = BigInt(text);
  if (quantity <= 0n) {
    throw new Error('The quantity must be at least 1.');
  }
  if (quantity > BigInt(Number.MAX_SAFE_INTEGER)) {
    throw new Error('The quantity is too large.');
  }
  return Number(quantity);
}
