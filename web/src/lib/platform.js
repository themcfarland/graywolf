// Platform detection helpers. The presence of the GraywolfWebInterface
// JS bridge (injected by the Android Service via WebView.addJavascriptInterface)
// is the ground-truth signal that the SPA is running inside the Android
// WebView vs. a desktop browser.
//
// Used by Svelte routes/components to hide surfaces that are unsupported
// or meaningless on Android (audio device path field, output device
// selectors, PTT settings, the Updates page, etc.) so the operator UI
// matches the actual runtime capability rather than the desktop-shaped
// configstore schema.

import { getBearerToken } from './androidBridge.js';

/**
 * Returns true when running inside the Android WebView. Cheap (cached
 * by androidBridge.getBearerToken) -- safe to call in render paths.
 */
export function isAndroid() {
  return getBearerToken() !== null;
}

/**
 * Inverse helper for readability in templates that hide rather than
 * show on Android.
 *
 *   {#if isDesktop()}
 *     <Button>Detect devices</Button>
 *   {/if}
 */
export function isDesktop() {
  return !isAndroid();
}
