const dateTime = new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' });
const dateOnly = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short' });
const monthName = new Intl.DateTimeFormat('en-GB', { month: 'short', year: '2-digit', timeZone: 'UTC' });

export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso));
}

/** "2026-10-29" -> "29 Oct" (a business day from the API; no time zone conversion). */
export function formatDay(isoDate: string): string {
  const [y, m, d] = isoDate.split('-').map(Number);
  return dateOnly.format(new Date(y ?? 1970, (m ?? 1) - 1, d ?? 1));
}

/** "2026-10" -> "Oct 26". */
export function formatMonth(yearMonth: string): string {
  const [y, m] = yearMonth.split('-').map(Number);
  return monthName.format(new Date(Date.UTC(y ?? 1970, (m ?? 1) - 1, 1)));
}

export function shortId(id: string): string {
  return id.slice(0, 8);
}
