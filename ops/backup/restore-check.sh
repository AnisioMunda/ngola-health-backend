#!/usr/bin/env bash
# Ensaio de restauro: um backup só conta se se conseguir restaurar. Restaura o backup mais
# recente (ou o indicado) numa base temporária <DB_NAME>_restore_check, valida-a e apaga-a.
# Correr pelo menos uma vez por mês e depois de mudar chaves ou versões do MySQL.
#
# Uso: ops/backup/restore-check.sh [pasta do backup | nome]
#   KEEP=1 mantém a base temporária para inspecção.
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

if [[ $# -ge 1 ]]; then
  backup="$1"
else
  backup="$(find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -name "$DB_NAME-*Z" | sort -r | head -1)"
  [[ -n "$backup" ]] || fail "Não há backups em $BACKUP_DIR."
fi
check_db="${DB_NAME}_restore_check"

exists="$(mysql_value information_schema "SELECT COUNT(*) FROM SCHEMATA WHERE SCHEMA_NAME = '$check_db'")"
[[ "$exists" == "0" ]] || fail "A base $check_db já existe (ensaio anterior?). Apague-a primeiro."

"$SCRIPT_DIR/restore.sh" "$backup" "$check_db"
if [[ "${KEEP:-}" != "1" ]]; then
  mysql_cmd -e "DROP DATABASE \`$check_db\`"
  info "Base temporária $check_db apagada."
fi
info "Ensaio de restauro OK: $backup"
