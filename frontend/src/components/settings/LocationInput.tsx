import { useState, useEffect, useRef } from 'react'

interface Suggestion {
  label: string
}

interface Props {
  initialValue?: string
  onSelect: (label: string, lat: string, lon: string) => void
}

export default function LocationInput({ initialValue = '', onSelect }: Props) {
  const [query, setQuery] = useState(initialValue)
  const [suggestions, setSuggestions] = useState<Suggestion[]>([])
  const [open, setOpen] = useState(false)
  const [resolving, setResolving] = useState(false)
  const [error, setError] = useState('')
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
    setQuery(term)
    setError('')
    if (debounceRef.current) clearTimeout(debounceRef.current)
    if (term.length < 2) { setSuggestions([]); setOpen(false); return }
    debounceRef.current = setTimeout(() => {
      fetch(`/api/town-completion?term=${encodeURIComponent(term)}`)
        .then(r => r.json())
        .then((data: { results: Suggestion[] }) => {
          const results = data.results ?? []
          setSuggestions(results.slice(0, 8))
          setOpen(results.length > 0)
        })
        .catch(() => setOpen(false))
    }, 300)
  }

  function handleSelect(label: string) {
    setQuery(label)
    setSuggestions([])
    setOpen(false)
    setResolving(true)
    setError('')
    fetch(`/api/station-location?stop=${encodeURIComponent(label)}`)
      .then(r => r.json())
      .then((data: { lat: number; lon: number }) => {
        if (data.lat == null || data.lon == null) throw new Error('No coordinates')
        onSelect(label, String(data.lat), String(data.lon))
      })
      .catch(() => setError('Could not resolve coordinates for this location.'))
      .finally(() => setResolving(false))
  }

  return (
    <div ref={containerRef} style={{ position: 'relative' }}>
      <div className="input-group">
        <input
          className="form-control form-control-lg"
          value={query}
          onChange={e => handleInput(e.target.value)}
          onFocus={() => suggestions.length > 0 && setOpen(true)}
          placeholder="Search for a city…"
          autoComplete="off"
          disabled={resolving}
        />
        {resolving && (
          <span className="input-group-text">
            <span className="spinner-border spinner-border-sm" role="status" />
          </span>
        )}
      </div>
      {error && <div className="text-danger mt-1" style={{ fontSize: '0.85rem' }}>{error}</div>}
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
