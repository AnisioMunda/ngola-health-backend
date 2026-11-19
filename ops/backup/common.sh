#!/usr/bin/env bash
# Funções partilhadas pelos scripts de backup. Não executar directamente.
#
# Configuração (variáveis de ambiente ou backend/.env):
#   DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD   ligação ao MySQL (as mesmas da aplicação)
#   BACKUP_DIR              onde ficam os backups (fora do projecto; por omissão ~/NgolaHealthBackups)
#   BACKUP_PUBLIC_KEY       chave pública RSA para cifrar (basta no servidor que faz o backup)
#   BACKUP_PRIVATE_KEY      chave privada RSA para restaurar (guardar à parte, com senha)
#   BACKUP_RETENTION_DAYS   dias a manter (por omissão 30; os 7 mais recentes nunca são apagados)
#   MYSQL_BIN_DIR           pasta com mysql/mysqldump se não estiverem no PATH
set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$BACKEND_DIR/.env}"

fail() {
  printf 'ERRO: %s\n' "$*" >&2
  exit 1
}

info() {
  printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

# Lê o .env sem o executar (só linhas CHAVE=valor) e sem sobrepor variáveis já definidas.
load_env() {
  [[ -f "$ENV_FILE" ]] || return 0
  local line key value
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ ^[[:space:]]*# || ! "$line" =~ ^[A-Za-z_][A-Za-z0-9_]*= ]] && continue
    key="${line%%=*}"
    value="${line#*=}"
    value="${value%$'\r'}"
    value="${value#\"}"
    value="${value%\"}"
    if [[ -z "${!key+x}" ]]; then
      export "$key=$value"
    fi
  done <"$ENV_FILE"
}

load_env
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-ngolahealth}"
: "${DB_USER:?DB_USER em falta (backend/.env)}"
: "${DB_PASSWORD:?DB_PASSWORD em falta (backend/.env)}"
# O .env não expande "~": faz-se aqui para os caminhos.
expand_home() {
  case "$1" in
    "~") printf '%s' "$HOME" ;;
    "~/"*) printf '%s/%s' "$HOME" "${1#\~/}" ;;
    *) printf '%s' "$1" ;;
  esac
}
BACKUP_DIR="$(expand_home "${BACKUP_DIR:-$HOME/NgolaHealthBackups}")"
BACKUP_PUBLIC_KEY="$(expand_home "${BACKUP_PUBLIC_KEY:-}")"
BACKUP_PRIVATE_KEY="$(expand_home "${BACKUP_PRIVATE_KEY:-}")"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"
BACKUP_KEEP_MIN=7

find_bin() {
  local name="$1"
  if [[ -n "${MYSQL_BIN_DIR:-}" && -x "$MYSQL_BIN_DIR/$name" ]]; then
    printf '%s' "$MYSQL_BIN_DIR/$name"
  elif command -v "$name" >/dev/null 2>&1; then
    command -v "$name"
  else
    local candidate
    for candidate in /usr/local/mysql/bin/"$name" /usr/local/mysql-*/bin/"$name"; do
      [[ -x "$candidate" ]] && { printf '%s' "$candidate"; return; }
    done
    fail "$name não encontrado. Defina MYSQL_BIN_DIR."
  fi
}

MYSQL="$(find_bin mysql)"
MYSQLDUMP="$(find_bin mysqldump)"
command -v openssl >/dev/null || fail "openssl não encontrado."

# Pasta de trabalho privada, apagada à saída. A senha da base de dados vai num ficheiro de
# opções (modo 600), nunca na linha de comandos (visível no ps).
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
MYSQL_OPTS="$WORK_DIR/mysql.cnf"
{
  printf '[client]\n'
  printf 'host=%s\nport=%s\nuser=%s\n' "$DB_HOST" "$DB_PORT" "$DB_USER"
  # Escapes do formato de opções do MySQL: barra invertida e aspas.
  escaped="${DB_PASSWORD//\\/\\\\}"
  escaped="${escaped//\"/\\\"}"
  printf 'password="%s"\n' "$escaped"
  printf 'default-character-set=utf8mb4\n'
} >"$MYSQL_OPTS"

mysql_cmd() {
  "$MYSQL" --defaults-extra-file="$MYSQL_OPTS" "$@"
}

# Valor único de uma consulta.
mysql_value() {
  mysql_cmd --batch --skip-column-names -e "$2" "$1"
}

valid_db_name() {
  [[ "$1" =~ ^[A-Za-z0-9_]{1,64}$ ]]
}
