<!-- web/src/routes/ptt/PttCard.svelte -->
<script>
  import { Button, Badge } from '@chrissnell/chonky-ui';

  let {
    item,
    channelName,
    methodLabel,
    onEdit,
    onDelete,
  } = $props();

  function truncatePath(p, max = 40) {
    if (!p || p.length <= max) return p || '—';
    return '...' + p.slice(-(max - 3));
  }
</script>

<div class="device-card">
  <div class="device-header">
    <span class="device-name">{channelName || `Channel ${item.channel_id}`}</span>
    <div class="device-badges">
      <Badge variant={item.method === 'none' ? 'default' : 'success'}>
        {methodLabel}
      </Badge>
    </div>
  </div>
  <div class="device-details">
    {#if item.method !== 'none'}
      <div class="detail-row">
        <span class="detail-label">Device</span>
        <span class="detail-value" title={item.device_path}>{truncatePath(item.device_path)}</span>
      </div>
    {/if}
    {#if item.method === 'cm108'}
      <div class="detail-row">
        <span class="detail-label">GPIO Pin</span>
        <span class="detail-value">GPIO {item.gpio_pin} (pin {item.gpio_pin + 10})</span>
      </div>
    {/if}
    {#if item.method === 'gpio'}
      <div class="detail-row">
        <span class="detail-label">GPIO Line</span>
        <span class="detail-value">Line {item.gpio_line ?? 0}</span>
      </div>
    {/if}
    {#if item.method === 'none'}
      <div class="detail-row">
        <span class="detail-label">Status</span>
        <span class="detail-value muted">No PTT method set</span>
      </div>
    {/if}
  </div>
  <div class="device-actions">
    <Button variant="ghost" onclick={() => onEdit(item)}>Edit</Button>
    <Button variant="danger" onclick={() => onDelete(item)}>Delete</Button>
  </div>
</div>

<style>
  /* Card styles copied verbatim from Ptt.svelte to preserve visual parity. */
  .device-card {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 14px 16px;
    background: var(--bg-surface, #fff);
    border: 1px solid var(--border-color);
    border-radius: var(--radius);
  }
  .device-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
  }
  .device-name {
    font-weight: 600;
    font-size: 14px;
  }
  .device-badges {
    display: flex;
    gap: 4px;
  }
  .device-details {
    display: flex;
    flex-direction: column;
    gap: 4px;
    font-size: 13px;
  }
  .detail-row {
    display: flex;
    justify-content: space-between;
    gap: 12px;
  }
  .detail-label {
    color: var(--text-secondary, #555);
    font-weight: 500;
  }
  .detail-value {
    color: var(--text-primary, #111);
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 12px;
  }
  .detail-value.muted {
    color: var(--text-muted);
    font-style: italic;
    font-family: inherit;
  }
  .device-actions {
    display: flex;
    gap: 6px;
    justify-content: flex-end;
  }
</style>
