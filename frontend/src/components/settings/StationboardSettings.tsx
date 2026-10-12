import { useState, useEffect, useRef } from 'react'
import type { SettingsData } from '../../types'
import { ServiceToggle, SaveRow, type SaveStatus } from './shared'

function StationInput({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const [suggestions, setSuggestions] = useState<{ label: string }[]>([])
  const [open, setOpen] = useState(false)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onClickOutside)
    return () => document.removeEventListener('mousedown', onClickOutside)
  }, [])

  function handleInput(term: string) {
    onChange(term)
    if (debounceRef.current) clearTimeout(debounceRef.current)
    if (term.length < 2) { setSuggestions([]); setOpen(false); return }
    debounceRef.current = setTimeout(() => {
      fetch(`/api/station-completion?term=${encodeURIComponent(term)}`)
        .then(r => r.json())
        .then((data: { label: string }[]) => { setSuggestions(data.slice(0, 8)); setOpen(data.length > 0) })
        .catch(() => setOpen(false))
    }, 300)
  }

  function handleSelect(label: string) {
    onChange(label)
    setSuggestions([])
    setOpen(false)
  }

  return (
    <div ref={containerRef} style={{ position: 'relative' }}>
      <input
        className="form-control form-control-lg"
        value={value}
        onChange={e => handleInput(e.target.value)}
        onFocus={() => suggestions.length > 0 && setOpen(true)}
        placeholder="e.g. Zurich HB"
        autoComplete="off"
      />
      {open && (
        <ul className="list-group shadow" style={{ position: 'absolute', top: '100%', left: 0, right: 0, zIndex: 1000, maxHeight: 260, overflowY: 'auto' }}>
          {suggestions.map(s => (
            <li
              key={s.label}
              className="list-group-item list-group-item-action"
              style={{ cursor: 'pointer', minHeight: 48, display: 'flex', alignItems: 'center' }}
              onMouseDown={() => handleSelect(s.label)}
            >
              {s.label}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default function StationboardSettings({ settings, onSaved }: { settings: SettingsData; onSaved: (s: SettingsData) => void }) {
  const [enabled, setEnabled] = useState(settings.stationboardEnabled)
  const [station, setStation] = useState(settings.station)
  const [status, setStatus] = useState<SaveStatus>('idle')

  useEffect(() => { setEnabled(settings.stationboardEnabled); setStation(settings.station) }, [settings])

  const dirty = enabled !== settings.stationboardEnabled || station !== settings.station

  function handleSave() {
    setStatus('saving')
    fetch('/api/settings', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ 'stationboard.enabled': String(enabled), station }),
    })
      .then(r => { if (!r.ok) throw new Error() })
      .then(() => { onSaved({ ...settings, stationboardEnabled: enabled, station }); setStatus('saved') })
      .catch(() => setStatus('error'))
  }

  return (
    <div style={{ maxWidth: 480 }}>
      <ServiceToggle label="Enable Stationboard" checked={enabled} onChange={v => { setEnabled(v); setStatus('idle') }} />
      <div className="mb-3">
        <label className="form-label fw-semibold">Station</label>
        <StationInput value={station} onChange={v => { setStation(v); setStatus('idle') }} />
        <div className="form-text">Swiss public transport stop name.</div>
      </div>
      <SaveRow dirty={dirty} status={status} onSave={handleSave} />
    </div>
  )
}
