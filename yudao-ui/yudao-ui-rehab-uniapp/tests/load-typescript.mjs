import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

// Execute actual source with its imports replaced by explicit test doubles.
// Never contact a live API; no source-text assertions stand in for behavior.
export function loadTypescript(relativePath, dependencies, globals = {}) {
  const source = readFileSync(new URL(relativePath, import.meta.url), 'utf8')
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
  })
  const result = { exports: {} }
  const evaluate = vm.runInNewContext(`(function(require, module, exports) { ${outputText}\n })`, globals)
  evaluate((name) => {
    if (!Object.hasOwn(dependencies, name)) throw new Error(`Unexpected dependency: ${name}`)
    return dependencies[name]
  }, result, result.exports)
  return result.exports
}
