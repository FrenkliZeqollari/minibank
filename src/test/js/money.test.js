// Run with: node --test "src/test/js/**/*.test.js"
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { formatCents, parseAmountToCents } from '../../main/resources/static/js/money.js';

test('parses whole and decimal amounts into cents', () => {
  assert.equal(parseAmountToCents('25'), 2500);
  assert.equal(parseAmountToCents('25.5'), 2550);
  assert.equal(parseAmountToCents('25.50'), 2550);
  assert.equal(parseAmountToCents('0.01'), 1);
  assert.equal(parseAmountToCents('  7.00 '), 700);
});

test('accepts a German decimal comma', () => {
  assert.equal(parseAmountToCents('25,50'), 2550);
});

test('is exact where floating point is not', () => {
  // 19.99 * 100 is 1998.9999999999998 in floating point; a naive Math.floor would give 1998.
  assert.equal(parseAmountToCents('19.99'), 1999);
  assert.equal(parseAmountToCents('0.29'), 29);
  assert.equal(parseAmountToCents('1234567.89'), 123456789);
});

test('rejects anything that is not a positive amount with at most 2 decimals', () => {
  for (const bad of ['', '   ', 'abc', '-5', '+5', '0', '0.00', '1.234', '1,234.56', '1.', '.5', '1e3', '12 34']) {
    assert.throws(() => parseAmountToCents(bad), Error, `should reject "${bad}"`);
  }
});

test('rejects amounts that cannot be represented exactly', () => {
  assert.throws(() => parseAmountToCents('99999999999999999999'), /too large/);
});

test('formats cents for display', () => {
  assert.equal(formatCents(0), '0.00 EUR');
  assert.equal(formatCents(5), '0.05 EUR');
  assert.equal(formatCents(2500), '25.00 EUR');
  assert.equal(formatCents(-2500), '−25.00 EUR');
  assert.equal(formatCents(123456789), '1,234,567.89 EUR');
});

test('refuses to format a non-integer', () => {
  assert.throws(() => formatCents(25.5));
});
