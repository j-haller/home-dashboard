export type SaveStatus = 'idle' | 'saving' | 'saved' | 'error'

export function ServiceToggle({ label, checked, onChange }: {
  label: string
  checked: boolean
  onChange: (v: boolean) => void
}) {
  return (
    <div className="form-check form-switch mb-4" style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', minHeight: 44 }}>
      <input
        className="form-check-input"
        type="checkbox"
        role="switch"
        checked={checked}
        style={{ width: '3em', height: '1.5em', cursor: 'pointer', flexShrink: 0 }}
        onChange={e => onChange(e.target.checked)}
      />
      <span className="fw-semibold" style={{ fontSize: '1.05rem', cursor: 'pointer' }} onClick={() => onChange(!checked)}>
        {label}
      </span>
    </div>
  )
}

export function SaveRow({ dirty, status, onSave }: {
  dirty: boolean
  status: SaveStatus
  onSave: () => void
}) {
  return (
    <div className="d-flex align-items-center gap-3 mt-4">
      <button
        className="btn btn-primary btn-lg px-4"
        onClick={onSave}
        disabled={!dirty || status === 'saving'}
      >
        {status === 'saving' ? 'Saving…' : 'Save'}
      </button>
      {status === 'saved' && <span className="text-success">Saved.</span>}
      {status === 'error' && <span className="text-danger">Save failed.</span>}
    </div>
  )
}
