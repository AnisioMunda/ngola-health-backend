#!/usr/bin/env bash
# Restaura um backup numa base de dados NOVA ou vazia. Nunca escreve na base de origem
# (DB_NAME): para recuperar produção, restaure para uma base nova, valide e só depois aponte a
# aplicação para ela (DB_NAME no .env) — a base afectada fica intacta para investigação.
#
# Uso: ops/backup/restore.sh <pasta do backup | nome> <base de destino>
#   Pede a senha da chave privada (BACKUP_PRIVATE_KEY).
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

[[ $# -eq 2 ]] || fail "Uso: restore.sh <pasta do backup | nome> <base de destino>"
source_arg="$1"
target="$2"
valid_db_name "$target" || fail "Nome de base de destino inválido: $target"
[[ "$target" != "$DB_NAME" ]] ||
  fail "Recuso restaurar por cima da base de origem ($DB_NAME). Use uma base nova."
[[ -n "$BACKUP_PRIVATE_KEY" ]] || fail "BACKUP_PRIVATE_KEY em falta (caminho da chave privada)."
[[ -r "$BACKUP_PRIVATE_KEY" ]] || fail "Chave privada ilegível: $BACKUP_PRIVATE_KEY"

if [[ -d "$source_arg" ]]; then
  backup="$(cd "$source_arg" && pwd)"
else
  backup="$BACKUP_DIR/$source_arg"
fi
[[ -f "$backup/dump.sql.gz.enc" && -f "$backup/key.rsa" && -f "$backup/SHA256SUMS" ]] ||
  fail "Backup incompleto ou inexistente: $backup"

info "A verificar a integridade de $backup…"
while read -r expected file; do
  file="${file#\*}"
  [[ "$file" == "dump.sql.gz.enc" || "$file" == "key.rsa" ]] || fail "SHA256SUMS inesperado."
  actual="$(openssl dgst -sha256 -r "$backup/$file" | cut -d' ' -f1)"
  [[ "$actual" == "$expected" ]] || fail "Ficheiro corrompido ou alterado: $file"
done <"$backup/SHA256SUMS"

secret="$WORK_DIR/secret"
openssl pkeyutl -decrypt -inkey "$BACKUP_PRIVATE_KEY" -pkeyopt rsa_padding_mode:oaep \
  -in "$backup/key.rsa" -out "$secret" ||
  fail "Não foi possível abrir a chave do backup (chave privada ou senha erradas)."

existing="$(mysql_value information_schema "SELECT COUNT(*) FROM SCHEMATA WHERE SCHEMA_NAME = '$target'")"
created=0
if [[ "$existing" == "0" ]]; then
  mysql_cmd -e "CREATE DATABASE \`$target\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
  created=1
else
  tables="$(mysql_value information_schema "SELECT COUNT(*) FROM TABLES WHERE TABLE_SCHEMA = '$target'")"
  [[ "$tables" == "0" ]] || fail "A base $target não está vazia ($tables tabelas). Recuso misturar dados."
fi

info "A restaurar para $target…"
started=$SECONDS
if ! openssl enc -d -aes-256-cbc -pbkdf2 -iter 100000 -pass "file:$secret" \
  -in "$backup/dump.sql.gz.enc" | gunzip | mysql_cmd "$target"; then
  if [[ "$created" == "1" ]]; then
    mysql_cmd -e "DROP DATABASE \`$target\`"
  fi
  fail "O restauro falhou; a base $target foi deixada como estava antes."
fi

expected_tables="$(sed -n 's/^tables=//p' "$backup/info.txt")"
expected_migrations="$(sed -n 's/^migrations=//p' "$backup/info.txt")"
tables="$(mysql_value information_schema "SELECT COUNT(*) FROM TABLES WHERE TABLE_SCHEMA = '$target' AND TABLE_TYPE = 'BASE TABLE'")"
migrations="$(mysql_value "$target" 'SELECT COUNT(*) FROM DATABASECHANGELOG')"
[[ -z "$expected_tables" || "$tables" == "$expected_tables" ]] ||
  fail "Restauro incompleto: $tables tabelas, esperadas $expected_tables."
[[ -z "$expected_migrations" || "$migrations" == "$expected_migrations" ]] ||
  fail "Restauro incompleto: $migrations migrações, esperadas $expected_migrations."

info "Restauro concluído em $((SECONDS - started)) s: $tables tabelas, $migrations migrações."
for t in hospitals users patients episodes appointments triage_records lab_requests prescriptions audit_events; do
  printf '  %-16s %s\n' "$t" "$(mysql_value "$target" "SELECT COUNT(*) FROM \`$t\`" 2>/dev/null || echo '-')"
done
