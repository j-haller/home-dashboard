import type { StationboardData } from '../types'

interface Props {
  data: StationboardData | null
}

export default function StationboardTab({ data }: Props) {
  if (!data) return <p className="text-muted">Loading...</p>

  const now = new Date()

  return (
    <table className="table" id="stationboard-container">
      <thead>
        <tr>
          <th>Line</th>
          <th>Destination</th>
          <th>Min</th>
        </tr>
      </thead>
      <tbody>
        {data.connections.map((entry, i) => {
          const scheduled = new Date(entry.time)
          const delay = entry.dep_delay.slice(1)
          const actual = new Date(scheduled.getTime() + delay * 60000)
          const minutes = Math.max(0, Math.floor((actual.getTime() - now.getTime()) / 60000))
          const minDisplay = delay > 0 ? `${minutes} (+${delay})` : `${minutes}`

          return (
            <tr key={i}>
              <td>{entry.line || entry.type}</td>
              <td>{entry.terminal.name}</td>
              <td>{minDisplay}</td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}
