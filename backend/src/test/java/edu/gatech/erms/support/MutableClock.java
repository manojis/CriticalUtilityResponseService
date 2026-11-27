package edu.gatech.erms.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A test {@link Clock} that can be moved forward, to prove "today" is computed per call. */
public final class MutableClock extends Clock {

  private volatile Instant instant;
  private final ZoneId zone;

  public MutableClock(Instant instant, ZoneId zone) {
    this.instant = instant;
    this.zone = zone;
  }

  public static MutableClock utc(String isoInstant) {
    return new MutableClock(Instant.parse(isoInstant), ZoneOffset.UTC);
  }

  public void advance(Duration duration) {
    instant = instant.plus(duration);
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId newZone) {
    return new MutableClock(instant, newZone);
  }

  @Override
  public Instant instant() {
    return instant;
  }
}
