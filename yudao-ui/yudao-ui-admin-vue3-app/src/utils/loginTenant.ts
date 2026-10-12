/** Unknown organizations must never fall through to a headerless login. */
export async function resolveLoginTenant(
  name: string,
  lookup: (name: string) => Promise<unknown>
): Promise<number> {
  const normalized = name.trim()
  if (!normalized) throw new Error('请输入机构名称')
  const id = await lookup(normalized)
  if (typeof id !== 'number' || !Number.isSafeInteger(id) || id <= 0) {
    throw new Error('未找到该机构，请检查机构名称后重试')
  }
  return id
}
