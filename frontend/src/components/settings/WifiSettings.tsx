import { useState, useEffect } from 'react'
import type { SettingsData } from '../../types'
import { ServiceToggle, SaveRow, type SaveStatus } from './shared'

export default function WifiSettings({ settings, onSaved }: { settings: SettingsData; onSaved: (s: SettingsData) => void }) {
  const [enabled, setEnabled] = useState(settings.wifiEnabled)
  const [status, setStatus] = useState<SaveStatus>('idle')

  useEffect(() => { setEnabled(settings.wifiEnabled) }, [settings])

  const dirty = enabled !== settings.wifiEnabled

  function handleSave() {
    setStatus('saving')
    fetch('/api/settings', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ 'wifi.enabled': String(enabled) }),
    })
      .then(r => { if (!r.ok) throw new Error() })
      .then(() => { onSaved({ ...settings, wifiEnabled: enabled }); setStatus('saved') })
      .catch(() => setStatus('error'))
  }

  return (
    <div style={{ maxWidth: 480 }}>
      <ServiceToggle label="Enable WiFi QR Code" checked={enabled} onChange={v => { setEnabled(v); setStatus('idle') }} />
      <SaveRow dirty={dirty} status={status} onSave={handleSave} />
    </div>
  )
}
