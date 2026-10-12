import { useState, useEffect } from 'react'
import type { SettingsData } from '../../types'
import { ServiceToggle, SaveRow, type SaveStatus } from './shared'

export default function CalendarSettings({ settings, onSaved }: { settings: SettingsData; onSaved: (s: SettingsData) => void }) {
  const [enabled, setEnabled] = useState(settings.calendarEnabled)
  const [region, setRegion] = useState(settings.calendarRegion)
  const [area, setArea] = useState(settings.calendarArea)
  const [selectedTypes, setSelectedTypes] = useState<string[]>(settings.calendarTypes)
  const [limit, setLimit] = useState(settings.calendarLimit)
  const [mrgreenEnabled, setMrgreenEnabled] = useState(settings.calendarMrgreenEnabled)
  const [mrgreenZip, setMrgreenZip] = useState(settings.calendarMrgreenZip)
  const [mrgreenType, setMrgreenType] = useState(settings.calendarMrgreenType)
  const [status, setStatus] = useState<SaveStatus>('idle')

  const [regions, setRegions] = useState<string[]>([])
  const [areas, setAreas] = useState<string[]>([])
  const [types, setTypes] = useState<string[]>([])

  useEffect(() => {
    fetch('/api/calendar/regions')
      .then(r => r.json())
      .then((d: { result: string[] }) => setRegions(d.result ?? []))
      .catch(() => {})
  }, [])

  useEffect(() => {
    if (!region) { setAreas([]); setTypes([]); return }
    Promise.all([
      fetch(`/api/calendar/areas?region=${encodeURIComponent(region)}`).then(r => r.json()),
      fetch(`/api/calendar/types?region=${encodeURIComponent(region)}`).then(r => r.json()),
    ])
      .then(([areaData, typeData]: [{ result: { area: string }[] }, { result: string[] }]) => {
        setAreas((areaData.result ?? []).map(item => item.area))
        setTypes(typeData.result ?? [])
      })
      .catch(() => {})
  }, [region])

  useEffect(() => {
    setEnabled(settings.calendarEnabled)
    setRegion(settings.calendarRegion)
    setArea(settings.calendarArea)
    setSelectedTypes(settings.calendarTypes)
    setLimit(settings.calendarLimit)
    setMrgreenEnabled(settings.calendarMrgreenEnabled)
    setMrgreenZip(settings.calendarMrgreenZip)
    setMrgreenType(settings.calendarMrgreenType)
  }, [settings])

  const dirty = enabled !== settings.calendarEnabled
    || region !== settings.calendarRegion
    || area !== settings.calendarArea
    || JSON.stringify([...selectedTypes].sort()) !== JSON.stringify([...settings.calendarTypes].sort())
    || limit !== settings.calendarLimit
    || mrgreenEnabled !== settings.calendarMrgreenEnabled
    || mrgreenZip !== settings.calendarMrgreenZip
    || mrgreenType !== settings.calendarMrgreenType

  function toggleType(type: string) {
    setSelectedTypes(prev => prev.includes(type) ? prev.filter(t => t !== type) : [...prev, type])
    setStatus('idle')
  }

  function handleSave() {
    setStatus('saving')
    fetch('/api/settings', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        'calendar.enabled':          String(enabled),
        'calendar.region':           region,
        'calendar.area':             area,
        'calendar.types':            selectedTypes.join(','),
        'calendar.limit':            limit,
        'calendar.mrgreen.enabled':  String(mrgreenEnabled),
        'calendar.mrgreen.zip':      mrgreenZip,
        'calendar.mrgreen.type':     mrgreenType,
      }),
    })
      .then(r => { if (!r.ok) throw new Error() })
      .then(() => {
        onSaved({ ...settings, calendarEnabled: enabled, calendarRegion: region, calendarArea: area, calendarTypes: selectedTypes, calendarLimit: limit, calendarMrgreenEnabled: mrgreenEnabled, calendarMrgreenZip: mrgreenZip, calendarMrgreenType: mrgreenType })
        setStatus('saved')
      })
      .catch(() => setStatus('error'))
  }

  return (
    <div style={{ maxWidth: 480 }}>
      <ServiceToggle label="Enable Calendar" checked={enabled} onChange={v => { setEnabled(v); setStatus('idle') }} />

      <h6 className="text-muted text-uppercase mb-3" style={{ letterSpacing: '0.05em', fontSize: '0.75rem' }}>Regular Waste Collection</h6>

      <div className="mb-3">
        <label className="form-label fw-semibold">Region</label>
        <select
          className="form-select form-select-lg"
          value={region}
          onChange={e => { setRegion(e.target.value); setArea(''); setSelectedTypes([]); setStatus('idle') }}
        >
          <option value="">Select a region…</option>
          {regions.map(r => <option key={r} value={r}>{r}</option>)}
        </select>
      </div>

      <div className="mb-3">
        <label className="form-label fw-semibold">Area</label>
        <select
          className="form-select form-select-lg"
          value={area}
          onChange={e => { setArea(e.target.value); setStatus('idle') }}
          disabled={!region}
        >
          <option value="">All areas</option>
          {areas.map(a => <option key={a} value={a}>{a}</option>)}
        </select>
      </div>

      <div className="mb-3">
        <label className="form-label fw-semibold">Types</label>
        {types.length === 0
          ? <p className="text-muted mb-0" style={{ fontSize: '0.9rem' }}>Select a region to load types.</p>
          : types.map(type => (
            <div key={type} className="form-check mb-1" style={{ minHeight: 40, display: 'flex', alignItems: 'center' }}>
              <input
                className="form-check-input"
                type="checkbox"
                id={`type-${type}`}
                checked={selectedTypes.includes(type)}
                onChange={() => toggleType(type)}
                style={{ width: '1.4em', height: '1.4em', cursor: 'pointer' }}
              />
              <label className="form-check-label ms-2" htmlFor={`type-${type}`} style={{ cursor: 'pointer' }}>
                {type.charAt(0).toUpperCase() + type.slice(1)}
              </label>
            </div>
          ))
        }
      </div>

      <div className="mb-4">
        <label className="form-label fw-semibold">Limit</label>
        <input
          className="form-control form-control-lg"
          type="number"
          min="1"
          max="50"
          value={limit}
          onChange={e => { setLimit(e.target.value); setStatus('idle') }}
          style={{ maxWidth: 120 }}
        />
        <div className="form-text">Number of upcoming dates to fetch per type.</div>
      </div>

      <h6 className="text-muted text-uppercase mb-3" style={{ letterSpacing: '0.05em', fontSize: '0.75rem' }}>Mr. Green</h6>

      <ServiceToggle label="Enable Mr. Green" checked={mrgreenEnabled} onChange={v => { setMrgreenEnabled(v); setStatus('idle') }} />

      {mrgreenEnabled && (
        <>
          <div className="mb-3">
            <label className="form-label fw-semibold">ZIP Code</label>
            <input
              className="form-control form-control-lg"
              value={mrgreenZip}
              onChange={e => { setMrgreenZip(e.target.value); setStatus('idle') }}
              placeholder="e.g. 8000"
            />
          </div>
          <div className="mb-4">
            <label className="form-label fw-semibold">Pickup Schedule</label>
            <select
              className="form-select form-select-lg"
              value={mrgreenType}
              onChange={e => { setMrgreenType(e.target.value); setStatus('idle') }}
            >
              <option value="monthly">Monthly</option>
              <option value="biweekly">Biweekly</option>
            </select>
          </div>
        </>
      )}

      <SaveRow dirty={dirty} status={status} onSave={handleSave} />
    </div>
  )
}
