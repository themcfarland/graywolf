// web/src/lib/remote_actions/otp_timer.js
//
// Drives the "OTP <code> . next Ns" line in the drawer. Keeps a single
// fetch-and-reschedule loop per credential id; the active timer dies
// when the caller invokes the returned dispose function.
//
// Why not a setInterval over the wall clock: the server-supplied
// expires_at carries the canonical step boundary, so we sleep until
// that exact moment and refetch -- saves a round trip per second of
// countdown and tolerates clock drift between client and server.

export function secondsRemaining(expiresAtIso, now = new Date()) {
  const expiresMs = new Date(expiresAtIso).getTime();
  const diffMs = expiresMs - now.getTime();
  if (diffMs <= 0) return 0;
  return Math.ceil(diffMs / 1000);
}

// fetchAndScheduleNext drives one credential's countdown.
// fetchOTP: () => Promise<{code: string, expires_at: string}>
// onTick:   (code: string, secondsLeft: number) => void
// Returns a dispose function that cancels the next scheduled refresh.
export function fetchAndScheduleNext(fetchOTP, onTick) {
  let cancelled = false;
  let timer = null;
  let tickInterval = null;

  async function run() {
    if (cancelled) return;
    const { code, expires_at } = await fetchOTP();
    if (cancelled) return;
    const secs = secondsRemaining(expires_at);
    onTick(code, secs);
    // Per-second visual countdown.
    let left = secs;
    tickInterval = setInterval(() => {
      left -= 1;
      if (left <= 0) {
        clearInterval(tickInterval);
        tickInterval = null;
        return;
      }
      onTick(code, left);
    }, 1000);
    // Refresh just past the boundary (200 ms slack).
    timer = setTimeout(run, Math.max(200, secs * 1000 + 200));
  }

  run();

  return () => {
    cancelled = true;
    if (timer) clearTimeout(timer);
    if (tickInterval) clearInterval(tickInterval);
  };
}
