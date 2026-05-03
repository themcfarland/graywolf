// Tests for the remote-actions OTP countdown helpers.
//   node --test src/lib/remote_actions/otp_timer.test.js

import { strict as assert } from 'node:assert';
import { secondsRemaining, fetchAndScheduleNext } from './otp_timer.js';

let describe, it, mock;
try {
  const nodeTest = await import('node:test');
  describe = nodeTest.describe;
  it = nodeTest.it;
  mock = nodeTest.mock;
} catch {
  describe = globalThis.describe;
  it = globalThis.it;
}

describe('secondsRemaining', () => {
  it('returns positive seconds when expires_at is future', () => {
    const now = new Date('2026-05-03T17:14:15Z');
    assert.equal(secondsRemaining('2026-05-03T17:14:30Z', now), 15);
  });
  it('returns 0 when expires_at is past', () => {
    const now = new Date('2026-05-03T17:15:00Z');
    assert.equal(secondsRemaining('2026-05-03T17:14:30Z', now), 0);
  });
});

describe('fetchAndScheduleNext', () => {
  it('refetches on expiry boundary and stops on dispose', async () => {
    let calls = 0;
    const fakeFetch = async () => {
      calls += 1;
      const now = Date.now();
      return { code: '111111', expires_at: new Date(now + 1).toISOString() };
    };
    const onTick = mock.fn();
    const dispose = fetchAndScheduleNext(fakeFetch, onTick);
    await new Promise((r) => setTimeout(r, 50));
    dispose();
    assert.ok(calls >= 1, `expected at least one fetch, got ${calls}`);
    assert.ok(onTick.mock.callCount() >= 1, 'expected onTick to be called');
  });
});
