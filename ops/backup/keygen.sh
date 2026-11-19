#!/usr/bin/env bash
# Gera o par de chaves RSA-4096 dos backups.
#   backup.pem      chave PRIVADA, cifrada com uma senha pedida aqui. Guardar FORA do servidor
#                   (cofre, pen cifrada) e com cópia: sem ela os backups não se recuperam.
#   backup.pub.pem  chave pública: é a única que o servidor precisa para fazer backups.
# Uso: ops/backup/keygen.sh <pasta de destino>
set -Eeuo pipefail
umask 077

dest="${1:?Uso: keygen.sh <pasta de destino>}"
mkdir -p "$dest"
private="$dest/backup.pem"
public="$dest/backup.pub.pem"
[[ -e "$private" || -e "$public" ]] && {
  printf 'ERRO: já existem chaves em %s; não as substituo.\n' "$dest" >&2
  exit 1
}

cipher=(-aes256)
if [[ "${NO_PASSPHRASE:-}" == "1" ]]; then
  # Só para testes automáticos.
  cipher=()
fi
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 ${cipher[@]+"${cipher[@]}"} -out "$private"
openssl pkey -in "$private" -pubout -out "$public"
chmod 600 "$private"
chmod 644 "$public"
printf 'Chave privada: %s (guarde-a fora do servidor, com cópia)\n' "$private"
printf 'Chave pública: %s (defina BACKUP_PUBLIC_KEY com este caminho)\n' "$public"
