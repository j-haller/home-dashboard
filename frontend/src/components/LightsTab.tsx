import { useEffect, useRef, useState } from 'react'
import type { LightGroups } from '../types'

interface Props {
  groups: LightGroups | null
  lightState: LightGroups | null
}

export default function LightsTab({ groups, lightState }: Props) {
  const [localGroups, setLocalGroups] = useState<LightGroups | null>(null)
  const pendingRef = useRef<Set<string>>(new Set())
  const brightnessTimers = useRef<Record<string, ReturnType<typeof setTimeout>>>({})

  useEffect(() => {
    if (groups) {
      setLocalGroups(groups)
      pendingRef.current.clear()
    }
  }, [groups])

  useEffect(() => {
    if (!lightState) return
    setLocalGroups(prev => {
      if (!prev) return lightState
      const updated = { ...prev }
      Object.keys(lightState).forEach(id => {
        if (!pendingRef.current.has(id)) {
          updated[id] = { ...prev[id], action: lightState[id].action }
        }
      })
      return updated
    })
  }, [lightState])

  const handleToggle = async (groupId: string, on: boolean) => {
    pendingRef.current.add(groupId)
    setLocalGroups(prev => {
      if (!prev) return prev
      return { ...prev, [groupId]: { ...prev[groupId], action: { ...prev[groupId].action, on } } }
    })
    await fetch('api/trigger-light', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ on, id: groupId }),
    })
    pendingRef.current.delete(groupId)
  }

  const handleBrightness = (groupId: string, brightness: number) => {
    setLocalGroups(prev => {
      if (!prev) return prev
      return { ...prev, [groupId]: { ...prev[groupId], action: { ...prev[groupId].action, brightness, on: true } } }
    })
    clearTimeout(brightnessTimers.current[groupId])
    brightnessTimers.current[groupId] = setTimeout(async () => {
      pendingRef.current.add(groupId)
      await fetch('api/trigger-light', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ on: true, id: groupId, brightness }),
      })
      pendingRef.current.delete(groupId)
    }, 150)
  }

  if (!localGroups) return <p className="text-muted">Loading...</p>

  return (
    <div className="row row-cols-3 g-2" id="lights-container">
      {Object.entries(localGroups).map(([id, group]) => (
        <div className="col" key={id}>
          <div className="card h-100">
            <div className="card-body py-2">
              <p className="card-title text-center mb-2">{group.name}</p>
              <div className="form-check form-switch d-flex justify-content-center mb-2">
                <input
                  className="form-check-input"
                  type="checkbox"
                  role="switch"
                  id={`switch-${id}`}
                  checked={group.action.on}
                  onChange={e => handleToggle(id, e.target.checked)}
                />
              </div>
              <input
                type="range"
                className="form-range"
                min={1}
                max={100}
                value={Math.round(group.action.brightness ?? 100)}
                onChange={e => handleBrightness(id, parseInt(e.target.value))}
              />
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}
