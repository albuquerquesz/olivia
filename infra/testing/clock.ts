/** Domain tests advance time explicitly; network timeouts still use real time. */
export class TestClock {
  #now = 0;
  now(): number { return this.#now; }
  advance(milliseconds: number): void {
    if (!Number.isSafeInteger(milliseconds) || milliseconds < 0) throw new Error("INVALID_DURATION");
    this.#now += milliseconds;
  }
}
