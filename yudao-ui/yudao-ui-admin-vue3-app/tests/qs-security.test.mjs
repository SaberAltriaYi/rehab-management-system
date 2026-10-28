import { test } from 'node:test'
import assert from 'node:assert/strict'
import qs from 'qs'

for (const encodeValuesOnly of [false, true]) {
  test(`qs comma serialization handles non-callable own toString (encodeValuesOnly=${encodeValuesOnly})`, () => {
    const input = JSON.parse('{"a":[{"toString":"synthetic"}]}')
    assert.equal(qs.stringify(input, { arrayFormat: 'comma', encodeValuesOnly }),
      'a=%5Bobject%20Object%5D')
    const emptyPrototype = Object.assign(Object.create(null), { b: 'synthetic' })
    assert.doesNotThrow(() => qs.stringify({ a: [emptyPrototype] },
      { arrayFormat: 'comma', encodeValuesOnly }))
  })
}

test('normal transport encoding retains numeric zero, negatives, Unicode and array indices', () => {
  const data = { value: [0, -2, 1.5], note: '合成', tenant: 1 }
  assert.equal(qs.stringify(data, { allowDots: true }),
    'value%5B0%5D=0&value%5B1%5D=-2&value%5B2%5D=1.5&note=%E5%90%88%E6%88%90&tenant=1')
})
