#!/usr/bin/env bash
# Backup cifrado da base de dados (mysqldump consistente, sem bloquear a aplicação).
#
# Cada backup é uma pasta  <BACKUP_DIR>/<DB_NAME>-<AAAAMMDDTHHMMSSZ>/  com:
#   dump.sql.gz.enc  dump comprimido e cifrado (AES-256-CBC, chave aleatória deste backup)
#   key.rsa          essa chave, cifrada com a chave pública RSA (OAEP)
#   SHA256SUMS       resumos dos dois ficheiros (detecta corrupção)
#   info.txt         base, data, versão do MySQL, última migração, tabelas (sem dados)
# Depois apaga os backups com mais de BACKUP_RETENTION_DAYS dias, mantendo sempre os 7 mais
# recentes.
#
# Uso: ops/backup/backup.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

[[ -n "$BACKUP_PUBLIC_KEY" ]] || fail "BACKUP_PUBLIC_KEY em falta (caminho da chave pública; ver keygen.sh)."
[[ -r "$BACKUP_PUBLIC_KEY" ]] || fail "Chave pública ilegível: $BACKUP_PUBLIC_KEY"
[[ "$BACKUP_RETENTION_DAYS" =~ ^[1-9][0-9]*$ ]] || fail "BACKUP_RETENTION_DAYS inválido."
valid_db_name "$DB_NAME" || fail "DB_NAME inválido."

mkdir -p "$BACKUP_DIR"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
name="$DB_NAME-$stamp"
partial="$BACKUP_DIR/.$name.partial"
final="$BACKUP_DIR/$name"
mkdir "$partial"
trap 'rm -rf "$WORK_DIR" "$partial"' EXIT

info "Backup de $DB_NAME em $DB_HOST:$DB_PORT…"
started=$SECONDS

# Chave aleatória deste backup: só existe em claro na pasta de trabalho temporária.
secret="$WORK_DIR/secret"
openssl rand -hex 32 >"$secret"
openssl pkeyutl -encrypt -pubin -inkey "$BACKUP_PUBLIC_KEY" \
  -pkeyopt rsa_padding_mode:oaep -in "$secret" -out "$partial/key.rsa"

# --single-transaction: cópia consistente sem bloquear escritas (tabelas InnoDB).
# Sem CREATE DATABASE: o restauro escolhe a base de destino.
set -o pipefail
"$MYSQLDUMP" --defaults-extra-file="$MYSQL_OPTS" \
  --single-transaction --quick --triggers --hex-blob \
  --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 \
  "$DB_NAME" |
  gzip -6 |
  openssl enc -aes-256-cbc -salt -pbkdf2 -iter 100000 -pass "file:$secret" \
    -out "$partial/dump.sql.gz.enc"

(cd "$partial" && openssl dgst -sha256 -r dump.sql.gz.enc key.rsa >SHA256SUMS)

{
  printf 'database=%s\n' "$DB_NAME"
  printf 'created_utc=%s\n' "$stamp"
  printf 'mysql_version=%s\n' "$(mysql_value "$DB_NAME" 'SELECT VERSION()')"
  printf 'tables=%s\n' "$(mysql_value "$DB_NAME" "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'")"
  printf 'migrations=%s\n' "$(mysql_value "$DB_NAME" 'SELECT COUNT(*) FROM DATABASECHANGELOG')"
  printf 'last_migration=%s\n' "$(mysql_value "$DB_NAME" 'SELECT ID FROM DATABASECHANGELOG ORDER BY ORDEREXECUTED DESC LIMIT 1')"
  printf 'encrypted_bytes=%s\n' "$(wc -c <"$partial/dump.sql.gz.enc" | tr -d ' ')"
  printf 'format=mysqldump+gzip+aes-256-cbc(pbkdf2)+rsa-oaep\n'
} >"$partial/info.txt"

mv "$partial" "$final"
trap 'rm -rf "$WORK_DIR"' EXIT
info "Backup criado: $final ($(du -sh "$final" | cut -f1), $((SECONDS - started)) s)"

# Retenção: só pastas com o nome esperado; nunca os BACKUP_KEEP_MIN mais recentes.
backups=()
while IFS= read -r dir; do
  backups+=("$dir")
done < <(find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -name "$DB_NAME-*Z" | sort -r)
cutoff="$(date -u -v-"${BACKUP_RETENTION_DAYS}"d +%Y%m%dT%H%M%SZ 2>/dev/null ||
  date -u -d "-${BACKUP_RETENTION_DAYS} days" +%Y%m%dT%H%M%SZ)"
for ((i = BACKUP_KEEP_MIN; i < ${#backups[@]}; i++)); do
  dir="${backups[$i]}"
  ts="${dir##*-}"
  if [[ "$ts" =~ ^[0-9]{8}T[0-9]{6}Z$ && "$ts" < "$cutoff" ]]; then
    rm -rf -- "$dir"
    info "Removido (mais de $BACKUP_RETENTION_DAYS dias): $dir"
  fi
done
