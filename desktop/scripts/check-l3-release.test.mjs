import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { createHash } from 'node:crypto'
import { evaluateL3Release } from './check-l3-release.mjs'

function fixture(callback) {
  const root = mkdtempSync(join(tmpdir(), 'rehab-l3-gate-'))
  const files = {
    'desktop/launcher/src-tauri/resources/license/trusted-issuers.json':
      JSON.stringify({ ownerBaseUrl: 'https://licenses.publisher.test/',
        issuers: [{ kid: 'owner-v1', publicKeyBase64url: Buffer.alloc(32, 7).toString('base64url') }] }),
    'desktop/launcher/src-tauri/Cargo.toml': '[features]\nlicense-required = []\n',
    '.github/workflows/desktop-release.yml': [
      'run: pnpm tauri build --bundles nsis --features license-required',
      'run: pnpm tauri build --bundles dmg --features license-required',
      'Require Windows code-signing credentials',
      'Get-AuthenticodeSignature',
      "signature.Status -ne 'Valid'",
      'Require Apple signing and notarization credentials',
      'codesign --verify --deep --strict',
      'xcrun notarytool submit',
      "'.status == \"Accepted\"'",
      'xcrun stapler staple',
      'xcrun stapler validate',
      'spctl -a -t open',
      'notarization=accepted-and-stapled'
    ].join('\n'),
    'sql/mysql/rehab-tenant-license-v1.sql': 'CREATE TABLE rehab_tenant_license_binding (tenant_id BIGINT PRIMARY KEY);\n'
  }
  const sqlHash = createHash('sha256')
    .update(files['sql/mysql/rehab-tenant-license-v1.sql']).digest('hex')
  files['deploy/internal/migrations.manifest'] =
    `026|${sqlHash}|sql/mysql/rehab-tenant-license-v1.sql|Synthetic only\n`
  function put(relative, text) {
    const target = join(root, relative)
    mkdirSync(dirname(target), { recursive: true })
    writeFileSync(target, text)
  }
  try {
    for (const [path, contents] of Object.entries(files)) put(path, contents)
    callback({ root, put, files })
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
}

test('fully pinned/signed hypothetical synthetic release can pass this additional gate', () => {
  fixture(({ root }) => assert.equal(evaluateL3Release(root).ready, true))
})
test('missing owner publisher root and URL keep real release blocked', () => {
  fixture(({ root, put }) => {
    put('desktop/launcher/src-tauri/resources/license/trusted-issuers.json',
      JSON.stringify({ issuers: [], ownerBaseUrl: null }))
    const result = evaluateL3Release(root)
    assert.equal(result.ready, false)
    assert.ok(result.blockers.includes('PUBLISHER_ROOTS_MISSING'))
    assert.ok(result.blockers.includes('PUBLISHER_HTTPS_ORIGIN_MISSING'))
  })
})
test('migration not integrated cannot be bypassed by adding a SQL file', () => {
  fixture(({ root, put }) => {
    put('deploy/internal/migrations.manifest', '')
    assert.ok(evaluateL3Release(root).blockers.includes('TENANT_LICENSE_SCHEMA_MIGRATION_NOT_INTEGRATED'))
  })
})
test('unsigned/unenforced packaging branches are refused', () => {
  fixture(({ root, put }) => {
    put('.github/workflows/desktop-release.yml',
      'run: pnpm tauri build --bundles nsis --features license-required\n' +
      'run: pnpm tauri build --bundles nsis\n' +
      'run: pnpm tauri build --bundles dmg --features license-required\n')
    assert.ok(evaluateL3Release(root).blockers.includes('PACKAGING_CAN_EMIT_UNLICENSED_APPLICATION'))
    assert.ok(evaluateL3Release(root).blockers.includes('UNSIGNED_OR_UNNOTARIZED_RELEASE_PATH'))
  })
})
test('signing and notarization must both verify their actual artifact', () => {
  fixture(({ root, put, files }) => {
    put('.github/workflows/desktop-release.yml',
      files['.github/workflows/desktop-release.yml'].replace('xcrun stapler validate', 'echo success'))
    assert.ok(evaluateL3Release(root).blockers.includes('UNSIGNED_OR_UNNOTARIZED_RELEASE_PATH'))
  })
})
test('tampered migration hash cannot be silently accepted', () => {
  fixture(({ root, put }) => {
    put('sql/mysql/rehab-tenant-license-v1.sql', 'tampered with synthetic SQL')
    assert.ok(evaluateL3Release(root).blockers.includes('TENANT_LICENSE_SCHEMA_HASH_MISMATCH'))
  })
})
