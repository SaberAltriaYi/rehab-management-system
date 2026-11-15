import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, writeFileSync, chmodSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { createHash } from 'node:crypto'
import { LocalCoreRecoveryBridge } from './core-admin-recovery-bridge.mjs'

function syntheticHelper(contents) {
  const root = mkdtempSync(join(tmpdir(), 'rehab-owner-helper-synthetic-'))
  const exe = join(root, 'helper')
  // The production bridge intentionally runs with an OS-only PATH; synthetic
  // tests must use the absolute Node interpreter rather than env node.
  const executableText = '#!' + process.execPath + '\n' + contents
  writeFileSync(exe, executableText, { mode: 0o700 })
  chmodSync(exe, 0o700)
  const sha256 = createHash('sha256').update(executableText).digest('hex')
  return { root, exe, sha256 }
}

test('single-call synthetic helper success requires valid challenge and approval', async () => {
  const fake = syntheticHelper(`
  process.stdout.write(JSON.stringify({type:'challenge',challenge:'A'.repeat(43)})+'\\n')
  process.stdin.on('data',d=>{const x=JSON.parse(d.toString());
    if(x.authorizationCode==='B'.repeat(43) && x.newPassword==='synthetic1234')
      process.stdout.write(JSON.stringify({type:'result',success:true})+'\\n',()=>process.exit(0))
    else process.exit(1)
  })
  `)
  try {
    const bridge = new LocalCoreRecoveryBridge({ executable: fake.exe, sha256: fake.sha256 })
    const out = await bridge.recover({
      newPassword:'synthetic1234',
      authorize: async challenge => {
        assert.equal(challenge, 'A'.repeat(43))
        return 'B'.repeat(43)
      }
    })
    assert.equal(out.status, 'RECOVERED')
  } finally { rmSync(fake.root,{recursive:true,force:true}) }
})

test('tampered binary is rejected before launch and cannot request Owner authorization', async () => {
  const fake = syntheticHelper('process.stdout.write("wrong")')
  try {
    const bridge = new LocalCoreRecoveryBridge({ executable: fake.exe, sha256: fake.sha256 })
    writeFileSync(fake.exe, '#!' + process.execPath + '\nprocess.exit(0)')
    let called = false
    await assert.rejects(bridge.recover({ newPassword: 'synthetic1234',
      authorize: async () => { called=true; return 'A'.repeat(43) }
    }), /RECOVERY_HELPER_UNAVAILABLE/)
    assert.equal(called, false)
  } finally { rmSync(fake.root,{recursive:true,force:true}) }
})

test('invalid challenge or missing approval cannot complete recovery', async () => {
  const fake = syntheticHelper('process.stdout.write(JSON.stringify({type:"challenge",challenge:"wrong"})+"\\n")')
  try {
    const bridge = new LocalCoreRecoveryBridge({ executable: fake.exe, sha256: fake.sha256 })
    await assert.rejects(bridge.recover({ newPassword:'synthetic1234',
      authorize: async () => 'B'.repeat(43) }), /RECOVERY_NOT_CONFIRMED/)
  } finally { rmSync(fake.root,{recursive:true,force:true}) }
})
