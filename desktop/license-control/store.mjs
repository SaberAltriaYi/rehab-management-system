1: // Owner licensing *control-plane* SQLite only. Do not share this database with
2: // local studio services, patient data or the Tauri launcher.
3: import { DatabaseSync } from 'node:sqlite'
4: import { createHash, randomUUID } from 'node:crypto'
5: import { existsSync, lstatSync, mkdirSync, chmodSync } from 'node:fs'
6: import { dirname, resolve } from 'node:path'
7: 
8: const SCHEMA = `
9: PRAGMA foreign_keys = ON;
10: CREATE TABLE IF NOT EXISTS owner_identity (
11:   id INTEGER PRIMARY KEY CHECK (id = 1),
12:   username TEXT NOT NULL UNIQUE,
13:   password_hash TEXT NOT NULL,
14:   mfa_encrypted TEXT NOT NULL,
15:   last_mfa_step INTEGER NOT NULL DEFAULT -1,
16:   failure_count INTEGER NOT NULL DEFAULT 0,
17:   lock_until INTEGER NOT NULL DEFAULT 0
18: );
19: CREATE TABLE IF NOT EXISTS owner_sessions (
20:   token_hash TEXT PRIMARY KEY,
21:   issued_at INTEGER NOT NULL,
22:   expires_at INTEGER NOT NULL,
23:   step_up_until INTEGER NOT NULL DEFAULT 0,
24:   mfa_failure_count INTEGER NOT NULL DEFAULT 0
25: );
26: CREATE TABLE IF NOT EXISTS signing_keys (
27:   kid TEXT PRIMARY KEY,
28:   public_pem TEXT NOT NULL,
29:   private_encrypted TEXT NOT NULL,
30:   status TEXT NOT NULL CHECK (status IN ('ACTIVE','VERIFY_ONLY','RETIRED')),
31:   created_at INTEGER NOT NULL
32: );
33: CREATE UNIQUE INDEX IF NOT EXISTS one_active_signer
34:   ON signing_keys(status) WHERE status = 'ACTIVE';
35: CREATE TABLE IF NOT EXISTS applications (
36:   id TEXT PRIMARY KEY,
37:   tenant_uuid TEXT NOT NULL UNIQUE,
38:   installation_uuid TEXT NOT NULL UNIQUE,
39:   installation_public_pem TEXT NOT NULL,
40:   organization TEXT NOT NULL,
41:   status TEXT NOT NULL CHECK (status IN ('PENDING','NEEDS_INFO','APPROVED','REJECTED')),
42:   submitted_at INTEGER NOT NULL,
43:   decision_at INTEGER,
44:   decision_reason TEXT,
45:   source TEXT NOT NULL DEFAULT 'installation',
46:   contact_name TEXT,
47:   contact_phone TEXT,
48:   contact_email TEXT,
49:   region TEXT,
50:   note TEXT
51: );
52: CREATE TABLE IF NOT EXISTS entitlements (
53:   tenant_uuid TEXT PRIMARY KEY,
54:   expires_at INTEGER NOT NULL,
55:   seat_limit INTEGER,
56:   max_installations INTEGER NOT NULL,
57:   modules_json TEXT NOT NULL,
58:   revocation_epoch INTEGER NOT NULL DEFAULT 0,
59:   status TEXT NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED','REVOKED')),
60:   updated_at INTEGER NOT NULL
61: );
62: CREATE TABLE IF NOT EXISTS installations (
63:   installation_uuid TEXT PRIMARY KEY,
64:   tenant_uuid TEXT NOT NULL REFERENCES entitlements(tenant_uuid),
65:   public_pem TEXT NOT NULL,
66:   status TEXT NOT NULL CHECK (status IN ('ACTIVE','REPLACED')),
67:   added_at INTEGER NOT NULL
68: );
69: CREATE TABLE IF NOT EXISTS renewal_challenges (
70:   nonce_hash TEXT PRIMARY KEY,
71:   tenant_uuid TEXT NOT NULL,
72:   installation_uuid TEXT NOT NULL,
73:   expires_at INTEGER NOT NULL
74: );
75: CREATE TABLE IF NOT EXISTS license_issues (
76:   lease_uuid TEXT PRIMARY KEY,
77:   tenant_uuid TEXT NOT NULL,
78:   installation_uuid TEXT NOT NULL,
79:   mode TEXT NOT NULL,
80:   kid TEXT NOT NULL,
81:   issued_at INTEGER NOT NULL,
82:   expires_at INTEGER NOT NULL
83: );
84: CREATE TABLE IF NOT EXISTS audit_events (
85:   seq INTEGER PRIMARY KEY AUTOINCREMENT,
86:   event_uuid TEXT NOT NULL UNIQUE,
87:   actor TEXT NOT NULL,
88:   action TEXT NOT NULL,
89:   subject TEXT NOT NULL,
90:   details_json TEXT NOT NULL,
91:   occurred_at INTEGER NOT NULL,
92:   prev_hash TEXT NOT NULL,
93:   chain_hash TEXT NOT NULL
94: );
95: `
96: 
97: export class OwnerLicenseStore {
98:   constructor(file) {
99:     if (typeof file !== 'string' || !file || file === ':memory:') {
100:       throw new Error('Persistent owner database path is required')
101:     }
102:     const dest = resolve(file)
103:     const parent = dirname(dest)
104:     if (!existsSync(parent)) mkdirSync(parent, { recursive: true, mode: 0o700 })
105:     // Never silently follow a pre-existing attacker-controlled symbolic link.
106:     if (lstatSync(parent).isSymbolicLink() ||
107:       (existsSync(dest) && lstatSync(dest).isSymbolicLink())) {
108:       throw new Error('Symlink paths are forbidden for owner database')
109:     }
110:     this.db = new DatabaseSync(dest)
111:     this.db.exec('PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;')
112:     this.db.exec(SCHEMA)
113:     if (!this.db.prepare('PRAGMA table_info(owner_sessions)').all()
114:       .some(column => column.name === 'step_up_until')) {
115:       this.db.exec('ALTER TABLE owner_sessions ADD COLUMN step_up_until INTEGER NOT NULL DEFAULT 0')
116:     }
117:     if (!this.db.prepare('PRAGMA table_info(owner_sessions)').all()
118:       .some(column => column.name === 'mfa_failure_count')) {
119:       this.db.exec('ALTER TABLE owner_sessions ADD COLUMN mfa_failure_count INTEGER NOT NULL DEFAULT 0')
120:     }
121:     const applicationColumns = this.db.prepare('PRAGMA table_info(applications)').all().map(column => column.name)
122:     const applicationAdditions = [
123:       ['source', "TEXT NOT NULL DEFAULT 'installation'"],
124:       ['contact_name', 'TEXT'],
125:       ['contact_phone', 'TEXT'],
126:       ['contact_email', 'TEXT'],
127:       ['region', 'TEXT'],
128:       ['note', 'TEXT'],
129:       ['provisioning_status', "TEXT NOT NULL DEFAULT 'NOT_CONFIGURED'"],
130:       ['provisioning_id', 'TEXT'],
131:       ['provisioning_error', 'TEXT'],
132:       ['main_tenant_id', 'INTEGER']
133:     ]
134:     for (const [name, definition] of applicationAdditions) {
135:       if (!applicationColumns.includes(name)) this.db.exec(`ALTER TABLE applications ADD COLUMN ${name} ${definition}`)
136:     }
137:     if (process.platform !== 'win32') {
138:       for (const ext of ['', '-wal', '-shm']) {
139:         if (existsSync(dest + ext)) chmodSync(dest + ext, 0o600)
140:       }
141:     }
142:     this.dbPath = dest
143:     if (!this.verifyAuditChain()) throw new Error('Owner audit chain integrity failure')
144:   }
145: 
146:   transaction(fn) {
147:     this.db.exec('BEGIN IMMEDIATE')
148:     try {
149:       const result = fn()
150:       this.db.exec('COMMIT')
151:       return result
152:     } catch (error) {
153:       this.db.exec('ROLLBACK')
154:       throw error
155:     }
156:   }
157: 
158:   audit({ actor, action, subject, details = {}, nowMs }) {
159:     const last = this.db.prepare('SELECT chain_hash FROM audit_events ORDER BY seq DESC LIMIT 1').get()
160:     const prev = last?.chain_hash ?? '0'.repeat(64)
161:     const record = { eventId: randomUUID(), actor, action, subject, details, nowMs }
162:     const serialized = JSON.stringify(record)
163:     const chainHash = createHash('sha256').update(prev).update(serialized).digest('hex')
164:     this.db.prepare(`INSERT INTO audit_events(event_uuid,actor,action,subject,details_json,occurred_at,prev_hash,chain_hash)
165:        VALUES(?,?,?,?,?,?,?,?)`).run(record.eventId, actor, action, subject,
166:       JSON.stringify(details), nowMs, prev, chainHash)
167:     return chainHash
168:   }
169: 
170:   verifyAuditChain() {
171:     const rows = this.db.prepare('SELECT * FROM audit_events ORDER BY seq ASC').all()
172:     let prev = '0'.repeat(64)
173:     for (const row of rows) {
174:       const record = {
175:         eventId: row.event_uuid, actor: row.actor, action: row.action,
176:         subject: row.subject, details: JSON.parse(row.details_json),
177:         nowMs: row.occurred_at
178:       }
179:       if (row.prev_hash !== prev ||
180:         createHash('sha256').update(prev).update(JSON.stringify(record))
181:           .digest('hex') !== row.chain_hash) return false
182:       prev = row.chain_hash
183:     }
184:     return true
185:   }
186: 
187:   close() { this.db.close() }
188: }