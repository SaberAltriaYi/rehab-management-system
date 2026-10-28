const form = document.getElementById('register-form')
const result = document.getElementById('result')
const nextSteps = document.getElementById('next-steps')
const applicationId = document.getElementById('application-id')
const submit = document.getElementById('submit')

function show(message, type = 'success') {
  result.hidden = false
  result.className = 'register-result ' + type
  result.textContent = message
  nextSteps.hidden = true
}

function showSubmitted(id) {
  result.hidden = false
  result.className = 'register-result success'
  result.textContent = '申请已提交。系统已记录你的机构申请。'
  applicationId.textContent = String(id || '未返回')
  nextSteps.hidden = false
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
    showSubmitted(body.applicationId)
  } catch (error) {
    show('申请未提交：' + (error instanceof Error ? error.message : '网络错误'), 'error')
  } finally {
    submit.disabled = false
  }
})