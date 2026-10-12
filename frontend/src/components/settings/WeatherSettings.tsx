import { useState, useEffect } from 'react'
import type { SettingsData } from '../../types'
import { ServiceToggle, SaveRow, type SaveStatus } from './shared'
import LocationInput from './LocationInput'

export default function WeatherSettings({ settings, onSaved }: { settings: SettingsData; onSaved: (s: SettingsData) => void }) {
  const [enabled, setEnabled] = useState(settings.weatherEnabled)
  const [latitude, setLatitude] = useState(settings.latitude)
  const [longitude, setLongitude] = useState(settings.longitude)
  const [weatherLocation, setWeatherLocation] = useState(settings.weatherLocation)
  const [status, setStatus] = useState<SaveStatus>('idle')

  useEffect(() => {
    setEnabled(settings.weatherEnabled)
    setLatitude(settings.latitude)
    setLongitude(settings.longitude)
    setWeatherLocation(settings.weatherLocation)
  }, [settings])

  const dirty = enabled !== settings.weatherEnabled
    || latitude !== settings.latitude
    || longitude !== settings.longitude
    || weatherLocation !== settings.weatherLocation

  function handleSelect(label: string, lat: string, lon: string) {
    setWeatherLocation(label)
    setLatitude(lat)
    setLongitude(lon)
    setStatus('idle')
  }

  function handleSave() {
    setStatus('saving')
    fetch('/api/settings', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        'weather.enabled': String(enabled),
        'weather.location': weatherLocation,
        latitude,
        longitude,
      }),
    })
      .then(r => { if (!r.ok) throw new Error() })
      .then(() => { onSaved({ ...settings, weatherEnabled: enabled, weatherLocation, latitude, longitude }); setStatus('saved') })
      .catch(() => setStatus('error'))
  }

  return (
    <div style={{ maxWidth: 480 }}>
      <ServiceToggle label="Enable Weather" checked={enabled} onChange={v => { setEnabled(v); setStatus('idle') }} />
      <div className="mb-3">
        <label className="form-label fw-semibold">Location</label>
        <LocationInput initialValue={weatherLocation} onSelect={handleSelect} />
        <div className="form-text">
          Current coordinates: {latitude}, {longitude}
        </div>
      </div>
      <SaveRow dirty={dirty} status={status} onSave={handleSave} />
    </div>
  )
}
