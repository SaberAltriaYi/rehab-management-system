// Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.
// Identity generation only: never contains credentials or clinical data.
export function createAuthSessionScope() {
  let version = 0
  const listeners = new Set<() => void>()
  return {
    get version() {
      return version
    },
    reset() {
      version++
      listeners.forEach((listener) => {
        try {
          listener()
        } catch {
          /* notify remaining subscribers */
        }
      })
    },
    subscribe(listener: () => void) {
      listeners.add(listener)
      return () => {
        listeners.delete(listener)
      }
    }
  }
}
export const authSessionScope = createAuthSessionScope()
