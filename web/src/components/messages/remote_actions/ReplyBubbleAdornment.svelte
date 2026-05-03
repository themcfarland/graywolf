<script>
  // ReplyBubbleAdornment -- small footer adornment rendered inside the
  // MessageBubble when reply_match.isActionReply(msg) is true.
  //
  // Renders a zap icon, the literal text "reply", and a colored badge
  // matching the inbound status (ok/error/bad_*/...).
  //
  // `statusFromText` returns the on-air wire form ("bad otp",
  // "rate-limited", "no-credential" -- see reply_match.STATUS_PREFIXES)
  // because that's what the receiver speaks. `statusVariant` switches
  // on the Go enum form ("bad_otp", "rate_limited", "no_credential").
  // We translate at the boundary so multi-token failure replies get
  // their proper red/amber colour cue instead of falling through to
  // the default badge variant.
  import { Badge, Icon } from '@chrissnell/chonky-ui';
  import { statusVariant } from '../../../lib/actions/status.js';
  import { statusFromText } from '../../../lib/remote_actions/reply_match.js';

  const WIRE_TO_ENUM = {
    'bad otp': 'bad_otp',
    'bad arg': 'bad_arg',
    'rate-limited': 'rate_limited',
    'no-credential': 'no_credential',
  };

  let { text } = $props();
  const status = $derived(statusFromText(text));
  const variant = $derived(statusVariant(WIRE_TO_ENUM[status] ?? status));
</script>

<span class="adorn">
  <Icon name="zap" size="xs" />
  <Badge variant={variant}>{status}</Badge>
  <span class="lbl">reply</span>
</span>

<style>
  .adorn { display: inline-flex; align-items: center; gap: 4px; font-size: 0.75rem; opacity: 0.85; }
  .lbl { font-style: italic; color: var(--color-text-muted); }
</style>
