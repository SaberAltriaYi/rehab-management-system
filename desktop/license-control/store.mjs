// Owner licensing *control-plane* SQLite only. Do not share this database with
// local studio services, patient data or the Tauri launcher.
import { DatabaseSync } from 'node:sqlite'
import { createHash, randomUUID } from 'node:crypto'
import { existsSync, lstatSync, mkdirSync, chmodSync } from 'node:fs'
import { dirname, resolve } from 'node:path'

const SCHEMA = `
PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS owner_identity (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  username TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  mfa_encrypted TEXT NOT NULL,
  last_mfa_step INTEGER NOT NULL DEFAULT -1,
  failure_count INTEGER NOT NULL DEFAULT 0,
  lock_until INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS owner_sessions (
  token_hash TEXT PRIMARY KEY,
  issued_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  step_up_until INTEGER NOT NULL DEFAULT 0,
  mfa_failure_count INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS signing_keys (
  kid TEXT PRIMARY KEY,
  public_pem TEXT NOT NULL,
  private_encrypted TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('ACTIVE','VERIFY_ONLY','RETIRED')),
  created_at INTEGER NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS one_active_signer
  ON signing_keys(status) WHERE status = 'ACTIVE';
CREATE TABLE IF NOT EXISTS applications (
  id TEXT PRIMARY KEY,
  tenant_uuid TEXT NOT NULL UNIQUE,
  installation_uuid TEXT NOT NULL UNIQUE,
  installation_public_pem TEXT NOT NULL,
  organization TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('PENDING','NEEDS_INFO','APPROVED','REJECTED')),
  submitted_at INTEGER NOT NULL,
  decision_at INTEGER,
  decision_reason TEXT,
  source TEXT NOT NULL DEFAULT 'installation',
  contact_name TEXT,
  contact_phone TEXT,
  contact_email TEXT,
  region TEXT,
  note TEXT
);
CREATE TABLE IF NOT EXISTS entitlements (
  tenant_uuid TEXT PRIMARY KEY,
  expires_at INTEGER NOT NULL,
  seat_limit INTEGER,
  max_installations INTEGER NOT NULL,
  modules_json TEXT NOT NULL,
  revocation_epoch INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED','REVOKED')),
  updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS installations (
  installation_uuid TEXT PRIMARY KEY,
  tenant_uuid TEXT NOT NULL REFERENCES entitlements(tenant_uuid),
  public_pem TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('ACTIVE','REPLACED')),
  added_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS renewal_challenges (
  nonce_hash TEXT PRIMARY KEY,
  tenant_uuid TEXT NOT NULL,
  installation_uuid TEXT NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS license_issues (
  lease_uuid TEXT PRIMARY KEY,
  tenant_uuid TEXT NOT NULL,
  installation_uuid TEXT NOT NULL,
  mode TEXT NOT NULL,
  kid TEXT NOT NULL,
  issued_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS audit_events (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  event_uuid TEXT NOT NULL UNIQUE,
  actor TEXT NOT NULL,
  action TEXT NOT NULL,
  subject TEXT NOT NULL,
  details_json TEXT NOT NULL,
  occurred_at INTEGER NOT NULL,
  prev_hash TEXT NOT NULL,
  chain_hash TEXT NOT NULL
);
`

export class OwnerLicenseStore {
  constructor(file) {
    if (typeof file !== 'string' || !file || file === ':memory:') {
      throw new Error('Persistent owner database path is required')
    }
    const dest = resolve(file)
    const parent = dirname(dest)
    if (!existsSync(parent)) mkdirSync(parent, { recursive: true, mode: 0o700 })
    // Never silently follow a pre-existing attacker-controlled symbolic link.
    if (lstatSync(parent).isSymbolicLink() ||
      (existsSync(dest) && lstatSync(dest).isSymbolicLink())) {
      throw new Error('Symlink paths are forbidden for owner database')
    }
    this.db = new DatabaseSync(dest)
    this.db.exec('PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;')
    this.db.exec(SCHEMA)
    if (!this.db.prepare('PRAGMA table_info(owner_sessions)').all()
      .some(column => column.name === 'step_up_until')) {
      this.db.exec('ALTER TABLE owner_sessions ADD COLUMN step_up_until INTEGER NOT NULL DEFAULT 0')
    }
    if (!this.db.prepare('PRAGMA table_info(owner_sessions)').all()
      .some(column => column.name === 'mfa_failure_count')) {
      this.db.exec('ALTER TABLE owner_sessions ADD COLUMN mfa_failure_count INTEGER NOT NULL DEFAULT 0')
    }
    const applicationColumns = this.db.prepare('PRAGMA table_info(applications)').all().map(column => column.name)
    const applicationAdditions = [
      ['source', "TEXT NOT NULL DEFAULT 'installation'"],
      ['contact_name', 'TEXT'],
      ['contact_phone', 'TEXT'],
      ['contact_email', 'TEXT'],
      ['region', 'TEXT'],
      ['note', 'TEXT'],
      ['provisioning_status', "TEXT NOT NULL DEFAULT 'NOT_CONFIGURED'"],
      ['provisioning_id', 'TEXT'],
      ['provisioning_error', 'TEXT'],
      ['main_tenant_id', 'INTEGER'],
      ['notification_status', "TEXT NOT NULL DEFAULT 'NOT_CONFIGURED'"],
      ['notification_error', 'TEXT'],
      ['notification_provider_id', 'TEXT'],
      ['notification_attempted_at', 'INTEGER'],
      ['core_sync_status', "TEXT NOT NULL DEFAULT 'NOT_REQUIRED'"],
      ['core_sync_error', 'TEXT']
    ]
    for (const [name, definition] of applicationAdditions) {
      if (!applicationColumns.includes(name)) this.db.exec(`ALTER TABLE applications ADD COLUMN ${name} ${definition}`)
    }
    if (process.platform !== 'win32') {
      for (const ext of ['', '-wal', '-shm']) {
        if (existsSync(dest + ext)) chmodSync(dest + ext, 0o600)
      }
    }
    this.dbPath = dest
    if (!this.verifyAuditChain()) throw new Error('Owner audit chain integrity failure')
  }

  transaction(fn) {
    this.db.exec('BEGIN IMMEDIATE')
    try {
      const result = fn()
      this.db.exec('COMMIT')
      return result
    } catch (error) {
      this.db.exec('ROLLBACK')
      throw error
    }
  }

  audit({ actor, action, subject, details = {}, nowMs }) {
    const last = this.db.prepare('SELECT chain_hash FROM audit_events ORDER BY seq DESC LIMIT 1').get()
    const prev = last?.chain_hash ?? '0'.repeat(64)
    const record = { eventId: randomUUID(), actor, action, subject, details, nowMs }
    const serialized = JSON.stringify(record)
    const chainHash = createHash('sha256').update(prev).update(serialized).digest('hex')
    this.db.prepare(`INSERT INTO audit_events(event_uuid,actor,action,subject,details_json,occurred_at,prev_hash,chain_hash)
       VALUES(?,?,?,?,?,?,?,?)`).run(record.eventId, actor, action, subject,
      JSON.stringify(details), nowMs, prev, chainHash)
    return chainHash
  }

  verifyAuditChain() {
    const rows = this.db.prepare('SELECT * FROM audit_events ORDER BY seq ASC').all()
    let prev = '0'.repeat(64)
    for (const row of rows) {
      const record = {
        eventId: row.event_uuid, actor: row.actor, action: row.action,
        subject: row.subject, details: JSON.parse(row.details_json),
        nowMs: row.occurred_at
      }
      if (row.prev_hash !== prev ||
        createHash('sha256').update(prev).update(JSON.stringify(record))
          .digest('hex') !== row.chain_hash) return false
      prev = row.chain_hash
    }
    return true
  }

  close() { this.db.close() }
}