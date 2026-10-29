package dev.frenkli.ledger.demo;

import dev.frenkli.ledger.domain.Account;
import dev.frenkli.ledger.domain.AccountType;
import dev.frenkli.ledger.domain.TradeSide;
import dev.frenkli.ledger.repository.AccountRepository;
import dev.frenkli.ledger.service.AccountService;
import dev.frenkli.ledger.service.InstrumentService;
import dev.frenkli.ledger.service.MoneyMovementService;
import dev.frenkli.ledger.service.ReversalService;
import dev.frenkli.ledger.service.TradeService;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fills an empty database with a small, believable bank: customers with six months of salaries,
 * rent, groceries, savings, transfers between friends, one reversed double charge, and a few
 * fictional instruments with daily prices and some trades.
 *
 * <p>Everything is booked through the normal services, so every rule of the ledger (zero-sum,
 * no overdrafts, idempotency, cached balances) applies to the demo data, too. The settable clock
 * makes each booking happen "at" its date in the past. Deterministic: the same data every time.
 *
 * <p>Runs only with the "demo" profile and only if there are no customer accounts yet.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final int HISTORY_DAYS = 180;

    private final SettableClock clock;
    private final ZoneId zone;
    private final TransactionTemplate transaction;
    private final AccountRepository accountRepository;
    private final AccountService accounts;
    private final MoneyMovementService money;
    private final ReversalService reversals;
    private final InstrumentService instruments;
    private final TradeService trades;

    private int keyCounter;

    public DemoDataSeeder(SettableClock clock, @Value("${ledger.business-zone}") ZoneId zone,
                          TransactionTemplate transaction, AccountRepository accountRepository,
                          AccountService accounts, MoneyMovementService money, ReversalService reversals,
                          InstrumentService instruments, TradeService trades) {
        this.clock = clock;
        this.zone = zone;
        this.transaction = transaction;
        this.accountRepository = accountRepository;
        this.accounts = accounts;
        this.money = money;
        this.reversals = reversals;
        this.instruments = instruments;
        this.trades = trades;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!accountRepository.findByTypeOrderByCreatedAtAscIdAsc(AccountType.CUSTOMER, PageRequest.of(0, 1)).isEmpty()) {
            log.info("Demo data: customer accounts already exist, nothing to seed");
            return;
        }
        LocalDate today = LocalDate.now(clock.withZone(zone));
        try {
            // One DB transaction: either the whole demo bank is created, or nothing is.
            transaction.executeWithoutResult(status -> seed(today));
        } finally {
            clock.reset();
        }
        log.info("Demo data: seeded {} days of history", HISTORY_DAYS);
    }

    /** A booking to make at a given moment. */
    private record Event(Instant at, Runnable action) {
    }

    private void seed(LocalDate today) {
        LocalDate start = today.minusDays(HISTORY_DAYS);
        Random random = new Random(7);
        List<Event> events = new ArrayList<>();

        // People and businesses. Opened on the first day of the history.
        at(start, LocalTime.of(9, 0));
        UUID anna = open("Anna Schmidt");
        UUID annaSavings = open("Anna Schmidt (Savings)");
        UUID lukas = open("Lukas Müller");
        UUID sophie = open("Sophie Weber");
        UUID landlord = open("Hausverwaltung Berger GmbH");
        UUID grocer = open("FrischMarkt");
        UUID utilities = open("Stadtwerke Nord");
        UUID gym = open("FitWerk Studio");

        instruments.create("ALPN", "Alpine Energy AG", "EUR");
        instruments.create("BYTE", "Bytewerk SE", "EUR");
        instruments.create("EUFD", "Euro Index Fund", "EUR");

        // Opening balances. Anna's covers a full month of spending, even if the history starts
        // just after her payday.
        events.add(event(start, 9, 30, () -> deposit(anna, 3_200_00, "Opening balance")));
        events.add(event(start, 9, 30, () -> deposit(lukas, 1_850_00, "Opening balance")));
        events.add(event(start, 9, 30, () -> deposit(sophie, 3_100_00, "Opening balance")));

        // Daily closing prices: a random walk that drifts slightly up.
        Map<String, Long> price = new HashMap<>(Map.of("ALPN", 42_00L, "BYTE", 118_50L, "EUFD", 85_20L));
        Map<String, Double> volatility = Map.of("ALPN", 0.022, "BYTE", 0.03, "EUFD", 0.009);
        // Opening prices before anything else happens, so no trade can come before a first price.
        for (String symbol : List.of("ALPN", "BYTE", "EUFD")) {
            long opening = price.get(symbol);
            events.add(event(start, 9, 15, () -> instruments.recordPrice(symbol, opening)));
        }
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            for (String symbol : List.of("ALPN", "BYTE", "EUFD")) {
                // Price changes are percentages, but the result is stored as whole cents.
                double change = 1 + 0.0004 + random.nextGaussian() * volatility.get(symbol);
                long next = Math.max(1_00, Math.round(price.get(symbol) * change));
                price.put(symbol, next);
                add(events, start, today, day, 17, 35, () -> instruments.recordPrice(symbol, next));
            }
        }

        // Monthly life.
        for (LocalDate month = start.withDayOfMonth(1); !month.isAfter(today); month = month.plusMonths(1)) {
            String name = month.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
            add(events, start, today, month.withDayOfMonth(1), 8, 0, () -> deposit(anna, 3_450_00, "Salary " + name));
            add(events, start, today, month.withDayOfMonth(1), 8, 0, () -> deposit(lukas, 2_980_00, "Salary " + name));
            add(events, start, today, month.withDayOfMonth(1), 8, 0, () -> deposit(sophie, 4_120_00, "Salary " + name));
            add(events, start, today, month.withDayOfMonth(3), 7, 15, () -> transfer(anna, landlord, 1_150_00, "Rent " + name));
            add(events, start, today, month.withDayOfMonth(3), 7, 15, () -> transfer(lukas, landlord, 890_00, "Rent " + name));
            add(events, start, today, month.withDayOfMonth(4), 6, 0, () -> transfer(anna, utilities, 89_00, "Electricity " + name));
            add(events, start, today, month.withDayOfMonth(4), 6, 5, () -> transfer(anna, gym, 39_90, "Membership " + name));
            add(events, start, today, month.withDayOfMonth(2), 9, 0, () -> transfer(anna, annaSavings, 300_00, "Savings " + name));
            // A monthly fund savings plan: 5 shares of the index fund.
            add(events, start, today, month.withDayOfMonth(5), 10, 0, () -> trade(anna, TradeSide.BUY, "EUFD", 5));
            for (int week = 0; week < 4; week++) {
                long anna_spend = 45_00 + random.nextInt(75_00);
                long sophie_spend = 30_00 + random.nextInt(60_00);
                LocalDate day = month.plusDays(6 + week * 7L);
                add(events, start, today, day, 18, 20, () -> transfer(anna, grocer, anna_spend, "Groceries"));
                add(events, start, today, day, 12, 40, () -> transfer(sophie, grocer, sophie_spend, "Groceries"));
            }
        }

        // A few one-off moments.
        add(events, start, today, today.minusDays(150), 11, 0, () -> trade(anna, TradeSide.BUY, "BYTE", 10));
        add(events, start, today, today.minusDays(120), 14, 30, () -> trade(lukas, TradeSide.BUY, "ALPN", 20));
        add(events, start, today, today.minusDays(40), 15, 10, () -> trade(anna, TradeSide.SELL, "BYTE", 4));
        add(events, start, today, today.minusDays(75), 20, 15, () -> transfer(lukas, anna, 25_00, "Pizza night"));
        add(events, start, today, today.minusDays(33), 19, 0, () -> transfer(anna, sophie, 60_00, "Concert tickets"));
        // A double charge: booked twice by mistake, the second one is reversed the next day.
        UUID[] duplicate = new UUID[1];
        add(events, start, today, today.minusDays(20), 6, 6, () -> duplicate[0] = transfer(anna, gym, 39_90, "Membership (duplicate)"));
        add(events, start, today, today.minusDays(19), 10, 30, () -> reversals.reverse(key(), duplicate[0]));

        // Book everything in the order it happened.
        events.sort(Comparator.comparing(Event::at));
        for (Event event : events) {
            clock.set(event.at());
            event.action().run();
        }
    }

    private void add(List<Event> events, LocalDate start, LocalDate today, LocalDate day, int hour, int minute,
                     Runnable action) {
        // The first day only sets things up (accounts, opening balances and prices); everything else
        // happens afterwards, so it can never run before the money it needs is there. Nothing is
        // booked in the future.
        if (day.isAfter(start) && !day.isAfter(today) && atTime(day, hour, minute).isBefore(Instant.now())) {
            events.add(event(day, hour, minute, action));
        }
    }

    private Event event(LocalDate day, int hour, int minute, Runnable action) {
        return new Event(atTime(day, hour, minute), action);
    }

    private Instant atTime(LocalDate day, int hour, int minute) {
        return ZonedDateTime.of(day, LocalTime.of(hour, minute), zone).toInstant();
    }

    private void at(LocalDate day, LocalTime time) {
        clock.set(ZonedDateTime.of(day, time, zone).toInstant());
    }

    /** Opens accounts one minute apart, so they are listed in the order they were opened here. */
    private UUID open(String owner) {
        Account account = accounts.open(owner, "EUR");
        clock.set(clock.instant().plusSeconds(60));
        return account.getId();
    }

    private void deposit(UUID account, long cents, String description) {
        money.deposit(key(), account, cents, "EUR", description);
    }

    private UUID transfer(UUID from, UUID to, long cents, String description) {
        return money.transfer(key(), from, to, cents, "EUR", description).recorded().transaction().getId();
    }

    private void trade(UUID account, TradeSide side, String symbol, long quantity) {
        trades.trade(key(), account, side, symbol, quantity);
    }

    private String key() {
        return "demo-" + (++keyCounter);
    }
}
