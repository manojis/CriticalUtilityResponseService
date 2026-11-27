package edu.gatech.erms.service;

/** Neutralizes caller-controlled values before they are written to the log (CWE-117). */
final class LogSafe {

  private LogSafe() {}

  /**
   * Replaces CR and LF so a value cannot forge extra log lines; {@code null} stays {@code null}.
   */
  static String of(String value) {
    return value == null ? null : value.replace("\r", "_").replace("\n", "_");
  }
}
