// Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.
// Read-only local-install evidence check. Never installs, activates or migrates.
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { evaluateReadiness } from './prepackage-readiness.mjs'

export function evaluateInternalReadiness({ root, revision, dirty, evidence }) {
  const permitted = evidence?.schema === 'internal-controlled-evidence/1'
    && evidence?.scope?.purpose === 'single-studio-internal'
    && evidence?.scope?.publicDistribution === false
    && evidence?.scope?.network === 'loopback'
  // Reuse every existing integrity, identity, migration and business gate.
  // A different schema prevents this internal record becoming formal approval.
  const result = evaluateReadiness({
    root, revision, dirty,
    evidence: permitted
      ? { ...evidence, schema: 'prepackage-evidence/1.0.0' }
      : null
  })
  const blockers = result.blockers.filter(item =>
    !(permitted && item === 'GATE_PENDING: code_signing'))
  if (!permitted) blockers.unshift('INTERNAL_SCOPE_REQUIRED')
  return {
    ready: blockers.length === 0,
    revision,
    purpose: 'single-studio-internal',
    formalReleaseApproved: false,
    codeSigningRequired: false,
    blockers,
    blockerCount: blockers.length
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
  let evidence = null
  try { evidence = JSON.parse(readFileSync(process.argv[2], 'utf8')) } catch { /* fail closed */ }
  let revision = null
  let dirty = true
  try {
    revision = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim()
    dirty = Boolean(execFileSync('git', ['status', '--porcelain'], { cwd: root, encoding: 'utf8' }).trim())
  } catch { /* missing source provenance cannot approve installation */ }
  const result = evaluateInternalReadiness({ root, revision, dirty, evidence })
  process.stdout.write(JSON.stringify(result, null, 2) + '\n')
  process.exitCode = result.ready ? 0 : 1
}
