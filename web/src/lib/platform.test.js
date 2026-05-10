import { test, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { _resetForTests as resetBridge } from './androidBridge.js';
import { isAndroid, isDesktop } from './platform.js';

beforeEach(() => {
  resetBridge();
  delete globalThis.GraywolfWebInterface;
});
afterEach(() => {
  resetBridge();
  delete globalThis.GraywolfWebInterface;
});

test('isAndroid false when bridge absent', () => {
  assert.equal(isAndroid(), false);
  assert.equal(isDesktop(), true);
});

test('isAndroid true when bridge present', () => {
  globalThis.GraywolfWebInterface = { getBearerToken: () => 'tok' };
  assert.equal(isAndroid(), true);
  assert.equal(isDesktop(), false);
});
