// Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT
import assert from 'node:assert/strict'
import test from 'node:test'
import { assertPatientAuthDenied } from './patient-auth-contract.mjs'

test('legacy phone-number login stays HTTP 403 and never returns a session', () => {
  assert.doesNotThrow(() => assertPatientAuthDenied('login', '403', { code: 403, data: null }))
})

test('anonymous binding is rejected by the existing security entry point before service invocation', () => {
  // AuthenticationEntryPointImpl uses the framework's HTTP 200 / code 401 convention.
  assert.doesNotThrow(() => assertPatientAuthDenied('bind', '200', { code: 401 }))
})

test('a validation error 404 or server failure cannot masquerade as an authentication gate', () => {
  for (const [status, code] of [[200, 400], [404, 404], [500, 500], [200, 403], [200, 0]]) {
    assert.throws(() => assertPatientAuthDenied('bind', status, { code }))
  }
})

test('successful credentials and credential fields on denial responses are always rejected', () => {
  for (const body of [{ code: 0, data: { accessToken: 'SYNTHETIC_TOKEN' } },
    { code: 401, data: { patientId: 801 } }, { code: 401, token: 'SYNTHETIC_TOKEN' },
    { code: 401, accessToken: 'SYNTHETIC_TOKEN' }, { code: 401, refreshToken: 'SYNTHETIC_TOKEN' }]) {
    assert.throws(() => assertPatientAuthDenied('bind', 200, body), error =>
      !error.message.includes('SYNTHETIC_TOKEN') && !error.message.includes('801'))
  }
})

test('unknown operations and malformed response envelopes fail closed', () => {
  for (const body of [null, [], 'invalid', {}, { code: '401' }]) {
    assert.throws(() => assertPatientAuthDenied('bind', 200, body))
  }
  assert.throws(() => assertPatientAuthDenied('unknown', 200, { code: 401 }))
})
