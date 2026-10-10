#!/usr/bin/env node
// Static deployment-template audit and production NO-GO indicator.
// No outbound network, credential creation, server binding or patient access.
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = dirname(fileURLToPath(import.meta.url))
const read = file => readFileSync(join(HERE, file), 'utf8')

export function reviewOwnerCloudTemplates({ nginx, unit, acl }) {
  const failures = []
  const nginxMust = [
    'server_name __ACTIVATION_FQDN__', 'server_name __OWNER_FQDN__',
    'location ^~ /license/v1/', 'location ^~ /owner/',
    'ssl_verify_client on', 'ssl_client_certificate',
    'include /etc/nginx/snippets/rehab-owner-admin-acl.conf',
    'proxy_pass https://127.0.0.1:9444', 'proxy_ssl_verify on',
    'proxy_ssl_trusted_certificate', 'proxy_ssl_name owner-upstream.internal',
    'limit_req zone=rehab_activation_ip', 'limit_req zone=rehab_owner_ip',
    'client_max_body_size 16k', 'location / { return 404; }'
  ]
  if (nginxMust.some(item => !nginx.includes(item)) ||
      /\b(?:funnel|proxy_ssl_verify\s+off|listen\s+80|allow\s+all)\b/i.test(nginx)) {
    failures.push('OWNER_CLOUD_NGINX_NOT_FAIL_CLOSED')
  }
  if (!/allow 127\.0\.0\.1\/32;\s*deny all;/m.test(acl) ||
      /allow\s+0\.0\.0\.0\/0/.test(acl)) {
    failures.push('OWNER_CLOUD_ADMIN_ACL_NOT_DEFAULT_DENY')
  }
  const unitMust = [
    'User=rehab-owner', 'Environment=REHAB_OWNER_REQUIRE_CREDENTIALS=1',
    'LoadCredentialEncrypted=owner-vault-key:', 'LoadCredentialEncrypted=owner-tls-key:',
    'LoadCredential=owner-tls-cert:', 'StateDirectoryMode=0700',
    'NoNewPrivileges=true', 'ProtectSystem=strict', 'IPAddressDeny=any',
    'IPAddressAllow=localhost', 'UMask=0077', 'CapabilityBoundingSet='
  ]
  if (unitMust.some(item => !unit.includes(item)) ||
      /Environment=REHAB_OWNER_(?:VAULT_KEY_B64|TLS_KEY)=/.test(unit)) {
    failures.push('OWNER_CLOUD_SYSTEMD_SECRET_OR_SANDBOX_WEAK')
  }
  return { templatesValid: failures.length === 0,
    productionReady: false, blockers: failures.concat([
      'CLOUD_ACCOUNT_AND_REGION_NOT_APPROVED',
      'REAL_DNS_TLS_AND_FIREWALL_NOT_DEPLOYED',
      'REAL_SYSTEMD_ENCRYPTED_CREDENTIALS_NOT_CREATED',
      'KMS_HSM_AND_KEY_RECOVERY_NOT_ATTESTED',
      'OWNER_IDENTITY_MFA_AND_BACKUP_NOT_PROVISIONED',
      'CLOUD_E2E_AND_RELEASE_GATES_NOT_SIGNED_OFF'
    ]) }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const result = reviewOwnerCloudTemplates({
    nginx: read('nginx.conf.template'), unit: read('rehab-owner.service.template'),
    acl: read('rehab-owner-admin-acl.conf')
  })
  process.stdout.write(JSON.stringify(result, null, 2) + '\n')
  // Intentional NO-GO until reviewed, installed, verified and attested.
  process.exitCode = 1
}
