<script>
  // ReplyBubbleAdornment -- small footer adornment rendered inside the
  // MessageBubble when reply_match.isActionReply(msg) is true.
  //
  // Renders a zap icon, the literal text "reply", and a colored badge
  // matching the inbound status (ok/error/bad_*/...).
  import { Badge, Icon } from '@chrissnell/chonky-ui';
  import { statusVariant } from '../../../lib/actions/status.js';
  import { statusFromText } from '../../../lib/remote_actions/reply_match.js';

  let { text } = $props();
  const status = $derived(statusFromText(text));
  const variant = $derived(statusVariant(status));
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
