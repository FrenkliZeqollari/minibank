package dev.frenkli.ledger.demo;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A clock that normally tells the real time (UTC), but can be set to a fixed instant. Used only
 * by the demo profile, so the demo data can be booked "in the past" through the normal services.
 */
public class SettableClock extends Clock {

    private final Clock system = Clock.systemUTC();
    private volatile Instant fixed;

    /** From now on, report this instant (until {@link #reset()}). */
    public void set(Instant instant) {
        this.fixed = instant;
    }

    /** Back to the real time. */
    public void reset() {
        this.fixed = null;
    }

    @Override
    public Instant instant() {
        Instant f = fixed;
        return f != null ? f : system.instant();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        SettableClock owner = this;
        return new Clock() {
            @Override
            public Instant instant() {
                return owner.instant();
            }

            @Override
            public ZoneId getZone() {
                return zone;
            }

            @Override
            public Clock withZone(ZoneId other) {
                return owner.withZone(other);
            }
        };
    }
}
