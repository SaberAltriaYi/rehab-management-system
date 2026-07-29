#!/usr/bin/env sh

resolve_backup_key() {
  configured_key=${BACKUP_KEY_FILE:-}
  if [ -z "$configured_key" ] && [ -f "$ENV_FILE" ]; then
    configured_key=$(awk -F= '$1 == "BACKUP_KEY_FILE" {sub(/^[^=]*=/, ""); print; exit}' "$ENV_FILE")
  fi
  configured_key=${configured_key:-deploy/internal/secrets/backup.key}
  case "$configured_key" in
    /*) BACKUP_KEY_PATH="$configured_key" ;;
    *) BACKUP_KEY_PATH="$PROJECT_DIR/$configured_key" ;;
  esac
  export BACKUP_KEY_PATH
}

encrypt_backup_file() {
  source_file=$1
  encrypted_file=$2
  openssl enc -aes-256-cbc -salt -pbkdf2 -iter 600000 -md sha256 \
    -pass "file:$BACKUP_KEY_PATH" \
    -in "$source_file" -out "$encrypted_file"
}

decrypt_backup_file() {
  encrypted_file=$1
  openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -md sha256 \
    -pass "file:$BACKUP_KEY_PATH" \
    -in "$encrypted_file"
}

verify_backup_signature() {
  backup_dir=$1
  public_key_file=$2
  openssl x509 -in "$SCRIPT_DIR/certs/ca.crt" -pubkey -noout > "$public_key_file"
  openssl dgst -sha256 -verify "$public_key_file" \
    -signature "$backup_dir/SHA256SUMS.sig" "$backup_dir/SHA256SUMS" >/dev/null
}
