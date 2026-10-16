const form = document.getElementById('register-form')
const result = document.getElementById('result')
const submit = document.getElementById('submit')

function show(message, type = 'success') {
  result.hidden = false
  result.className = 'register-result ' + type
  result.textContent = message
}

form.addEventListener('submit', async (event) => {
  event.preventDefault()
  submit.disabled = true
  result.hidden = true
  const data = Object.fromEntries(new FormData(form))
  delete data.consent
  try {
    const response = await fetch('/public/v1/applications', {
      method: 'POST',
      credentials: 'omit',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(data)
    })
    const body = await response.json()
    if (!response.ok) throw new Error(body.error || '申请提交失败')
    form.reset()
    show(`申请已提交。申请编号：${body.applicationId}。请保存此编号，等待软件拥有者审核。`)
  } catch (error) {
    show('申请未提交：' + (error instanceof Error ? error.message : '网络错误'), 'error')
  } finally {
    submit.disabled = false
  }
})