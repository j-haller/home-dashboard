import { useState, useEffect } from 'react'
import { useSse } from './hooks/useSse'
import StationboardTab from './components/StationboardTab'
import LightsTab from './components/LightsTab'
import CalendarTab from './components/CalendarTab'
import WifiTab from './components/WifiTab'
import WeatherTab from './components/WeatherTab'
import StationboardSettings from './components/settings/StationboardSettings'
import LightsSettings from './components/settings/LightsSettings'
import WeatherSettings from './components/settings/WeatherSettings'
import CalendarSettings from './components/settings/CalendarSettings'
import WifiSettings from './components/settings/WifiSettings'
import type { CalendarData, LightGroups, SettingsData, StationboardData, WifiData, WeatherData } from './types'

type EnabledKey = 'stationboardEnabled' | 'lightsEnabled' | 'weatherEnabled' | 'calendarEnabled' | 'wifiEnabled'

const SERVICES: Array<{ id: string; label: string; enabledKey: EnabledKey }> = [
  { id: 'stationboard', label: 'Stationboard', enabledKey: 'stationboardEnabled' },
  { id: 'lights',       label: 'Lights',       enabledKey: 'lightsEnabled' },
  { id: 'weather',      label: 'Weather',      enabledKey: 'weatherEnabled' },
  { id: 'calendar',     label: 'Calendar',     enabledKey: 'calendarEnabled' },
  { id: 'wifi',         label: 'WiFi',         enabledKey: 'wifiEnabled' },
]

export default function App() {
  const [darkMode, setDarkMode] = useState(() => localStorage.getItem('theme') === 'dark')
  useEffect(() => {
    document.documentElement.setAttribute('data-bs-theme', darkMode ? 'dark' : 'light')
    localStorage.setItem('theme', darkMode ? 'dark' : 'light')
  }, [darkMode])

  const [settings, setSettings] = useState<SettingsData | null>(null)
  useEffect(() => {
    fetch('/api/settings').then(r => r.json()).then(setSettings).catch(() => {})
  }, [])

  const [settingsMode, setSettingsMode] = useState(false)
  const [activeTab, setActiveTab] = useState(SERVICES[0].id)

  const [stationboard, setStationboard] = useState<StationboardData | null>(null)
  const [lightGroups, setLightGroups] = useState<LightGroups | null>(null)
  const [lightState, setLightState] = useState<LightGroups | null>(null)
  const [calendar, setCalendar] = useState<CalendarData | null>(null)
  const [wifi, setWifi] = useState<WifiData | null>(null)
  const [weather, setWeather] = useState<WeatherData | null>(null)

  useSse('/sse', {
    stationboard: data => setStationboard(data as StationboardData),
    groups:       data => setLightGroups(data as LightGroups),
    light:        data => setLightState(data as LightGroups),
    calendar:     data => setCalendar(data as CalendarData),
    wifi:         data => setWifi(data as WifiData),
    weather:      data => setWeather(data as WeatherData),
  })

  function isEnabled(svc: typeof SERVICES[number]) {
    return settings?.[svc.enabledKey] ?? true
  }

  function toggleSettingsMode() {
    if (settingsMode) {
      const currentSvc = SERVICES.find(s => s.id === activeTab)
      if (currentSvc && !isEnabled(currentSvc)) {
        const first = SERVICES.find(s => isEnabled(s))
        setActiveTab(first?.id ?? SERVICES[0].id)
      }
    }
    setSettingsMode(m => !m)
  }

  const visibleServices = settingsMode ? SERVICES : SERVICES.filter(s => isEnabled(s))

  function getContent(id: string) {
    if (settingsMode) {
      if (!settings) return <p className="text-muted">Loading…</p>
      switch (id) {
        case 'stationboard': return <StationboardSettings settings={settings} onSaved={setSettings} />
        case 'lights':       return <LightsSettings       settings={settings} onSaved={setSettings} />
        case 'weather':      return <WeatherSettings      settings={settings} onSaved={setSettings} />
        case 'calendar':     return <CalendarSettings     settings={settings} onSaved={setSettings} />
        case 'wifi':         return <WifiSettings         settings={settings} onSaved={setSettings} />
      }
    }
    switch (id) {
      case 'stationboard': return <StationboardTab data={stationboard} />
      case 'lights':       return <LightsTab groups={lightGroups} lightState={lightState} />
      case 'weather':      return <WeatherTab data={weather} />
      case 'calendar':     return <CalendarTab data={calendar} />
      case 'wifi':         return <WifiTab data={wifi} />
    }
  }

  return (
    <div className="container-fluid py-2">
      <ul className="nav nav-tabs mb-0 sticky-top" id="mainTabs" role="tablist" style={{ fontSize: '0.85em' }}>
        {visibleServices.map(svc => (
          <li key={svc.id} className="nav-item" role="presentation">
            <button
              className={`nav-link ${activeTab === svc.id ? 'active' : ''}`}
              type="button"
              role="tab"
              onClick={() => setActiveTab(svc.id)}
            >
              {svc.label}
            </button>
          </li>
        ))}
        <li className="nav-item ms-auto d-flex align-items-center gap-1">
          <button
            className="btn btn-sm d-flex align-items-center justify-content-center border-0"
            style={{ width: '1.6rem', height: '1.6rem', backgroundColor: settingsMode ? 'var(--bs-secondary-bg)' : undefined }}
            onClick={toggleSettingsMode}
            title="Settings"
          >
            ⚙
          </button>
          <button
            className="btn btn-sm d-flex align-items-center justify-content-center border-0"
            style={{ width: '1.6rem', height: '1.6rem' }}
            onClick={() => setDarkMode(d => !d)}
            title={darkMode ? 'Switch to light mode' : 'Switch to dark mode'}
          >
            {darkMode ? '☀' : '☾'}
          </button>
        </li>
      </ul>
      <div className="tab-content pt-3">
        {visibleServices.map(svc => (
          <div
            key={svc.id + (settingsMode ? '-settings' : '-data')}
            className={`tab-pane fade ${activeTab === svc.id ? 'show active' : ''}`}
            id={svc.id}
            role="tabpanel"
          >
            {getContent(svc.id)}
          </div>
        ))}
      </div>
    </div>
  )
}
