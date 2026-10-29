import { formatCents, formatSignedCents, parseAmountToCents, parseShareQuantity } from './money';

describe('parseAmountToCents', () => {
  it('parses whole and decimal amounts', () => {
    expect(parseAmountToCents('25')).toBe(2500);
    expect(parseAmountToCents('25.5')).toBe(2550);
    expect(parseAmountToCents('25.50')).toBe(2550);
    expect(parseAmountToCents('0.01')).toBe(1);
    expect(parseAmountToCents('  7.00 ')).toBe(700);
  });

  it('accepts a German decimal comma', () => {
    expect(parseAmountToCents('25,50')).toBe(2550);
  });

  it('is exact where floating point is not', () => {
    // 19.99 * 100 is 1998.9999999999998 in floating point.
    expect(parseAmountToCents('19.99')).toBe(1999);
    expect(parseAmountToCents('0.29')).toBe(29);
    expect(parseAmountToCents('1234567.89')).toBe(123456789);
  });

  it.each(['', '   ', 'abc', '-5', '+5', '0', '0.00', '1.234', '1,234.56', '1.', '.5', '1e3', '12 34'])(
    'rejects "%s"',
    (bad) => {
      expect(() => parseAmountToCents(bad)).toThrow();
    },
  );

  it('rejects amounts that cannot be represented exactly', () => {
    expect(() => parseAmountToCents('99999999999999999999')).toThrow(/too large/);
  });
});

describe('parseShareQuantity', () => {
  it('parses whole numbers', () => {
    expect(parseShareQuantity('10')).toBe(10);
    expect(parseShareQuantity(' 1 ')).toBe(1);
  });

  it.each(['', '0', '-1', '1.5', '1,5', 'ten', '1e2'])('rejects "%s"', (bad) => {
    expect(() => parseShareQuantity(bad)).toThrow();
  });
});

describe('formatCents', () => {
  it('formats German style', () => {
    expect(formatCents(0)).toBe('0,00 €');
    expect(formatCents(5)).toBe('0,05 €');
    expect(formatCents(2500)).toBe('25,00 €');
    expect(formatCents(-2500)).toBe('−25,00 €');
    expect(formatCents(123456789)).toBe('1.234.567,89 €');
  });

  it('shows the sign of gains', () => {
    expect(formatSignedCents(1200)).toBe('+12,00 €');
    expect(formatSignedCents(-300)).toBe('−3,00 €');
    expect(formatSignedCents(0)).toBe('0,00 €');
  });

  it('refuses a non-integer', () => {
    expect(() => formatCents(25.5)).toThrow();
  });
});
