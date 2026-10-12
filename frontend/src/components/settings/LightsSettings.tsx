import { useState, useEffect } from 'react'
import type { SettingsData } from '../../types'
import { ServiceToggle, SaveRow, type SaveStatus } from './shared'

export default function LightsSettings({ settings, onSaved }: { settings: SettingsData; onSaved: (s: SettingsData) => void }) {
  const [enabled, setEnabled] = useState(settings.lightsEnabled)
  const [status, setStatus] = useState<SaveStatus>('idle')

  useEffect(() => { setEnabled(settings.lightsEnabled) }, [settings])

  const dirty = enabled !== settings.lightsEnabled

  function handleSave() {
    setStatus('saving')
    fetch('/api/settings', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ 'lights.enabled': String(enabled) }),
    })
      .then(r => { if (!r.ok) throw new Error() })
      .then(() => { onSaved({ ...settings, lightsEnabled: enabled }); setStatus('saved') })
      .catch(() => setStatus('error'))
  }

  return (
    <div style={{ maxWidth: 480 }}>
      <ServiceToggle label="Enable Lights" checked={enabled} onChange={v => { setEnabled(v); setStatus('idle') }} />
      <SaveRow dirty={dirty} status={status} onSave={handleSave} />
    </div>
  )
}
