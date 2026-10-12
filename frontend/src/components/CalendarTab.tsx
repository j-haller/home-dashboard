import type { CalendarData } from '../types'

interface Props {
  data: CalendarData | null
}

function formatDate(dateStr: string): string {
  const [, month, day] = dateStr.split('-')
  return `${day}.${month}.`
}

function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1)
}

export default function CalendarTab({ data }: Props) {
  if (!data) return <p className="text-muted">Loading...</p>

  const wasteEntries = Object.entries(data.wasteTypes)
  const hasMrgreen = data.mrgreen.length > 0

  if (wasteEntries.length === 0 && !hasMrgreen) {
    return <p className="text-muted">No collection data available.</p>
  }

  const rowCount = Math.max(
    ...wasteEntries.map(([, dates]) => dates.length),
    hasMrgreen ? data.mrgreen.length : 0
  )

  return (
    <table className="table">
      <thead>
        <tr>
          {wasteEntries.map(([type]) => (
            <th key={type}>{capitalize(type)}</th>
          ))}
          {hasMrgreen && <th>Mr. Green</th>}
        </tr>
      </thead>
      <tbody>
        {Array.from({ length: rowCount }, (_, i) => (
          <tr key={i}>
            {wasteEntries.map(([type, dates]) => (
              <td key={type}>{dates[i] ? formatDate(dates[i]) : '—'}</td>
            ))}
            {hasMrgreen && <td>{data.mrgreen[i] ? formatDate(data.mrgreen[i]) : '—'}</td>}
          </tr>
        ))}
      </tbody>
    </table>
  )
}
