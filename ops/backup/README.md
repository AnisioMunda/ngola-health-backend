# Backup e restauro da base de dados

Backups diários, cifrados e com ensaio de restauro. Só usam ferramentas que já existem num
servidor MySQL: `mysqldump`, `mysql`, `gzip` e `openssl` (macOS e Linux).

| Script | Para quê |
|---|---|
| `keygen.sh <pasta>` | Gera o par de chaves (uma vez) |
| `backup.sh` | Faz um backup cifrado e aplica a retenção |
| `restore.sh <backup> <base nova>` | Restaura para uma base nova ou vazia |
| `restore-check.sh [backup]` | Ensaio: restaura numa base temporária, valida e apaga |

A configuração vem do `backend/.env` (as mesmas `DB_*` da aplicação) e das variáveis `BACKUP_*`
(ver `.env.example`).

## Como fica guardado

Cada backup é uma pasta `<BACKUP_DIR>/ngolahealth-AAAAMMDDTHHMMSSZ/`:

- `dump.sql.gz.enc`: `mysqldump --single-transaction` (cópia consistente, sem parar a
  aplicação), comprimido e cifrado com AES-256 com uma chave aleatória só deste backup;
- `key.rsa`: essa chave, cifrada com a chave **pública** RSA-4096 (OAEP);
- `SHA256SUMS`: detecta ficheiros corrompidos antes de restaurar;
- `info.txt`: base, data, versão do MySQL, número de tabelas e de migrações (sem dados).

O servidor só precisa da chave pública: quem o comprometer não consegue ler os backups. A chave
privada só é usada para restaurar.

## 1. Preparar (uma vez)

```bash
ops/backup/keygen.sh ~/NgolaHealthKeys      # pede uma senha para a chave privada
```

- Guarde `backup.pem` (privada) **fora do servidor** (cofre de senhas, pen cifrada) e com uma
  segunda cópia. **Sem ela e sem a senha, nenhum backup pode ser recuperado.**
- No `backend/.env` defina `BACKUP_PUBLIC_KEY=~/NgolaHealthKeys/backup.pub.pem` (caminho
  absoluto) e, se quiser, `BACKUP_DIR`.
- O utilizador MySQL precisa de `SELECT`, `SHOW VIEW` e `TRIGGER` na base (para o backup) e de `CREATE` e `DROP` em bases novas (para restaurar).

## 2. Backup diário

Manual: `ops/backup/backup.sh`

Agendado:
- **Linux (cron)**: `30 2 * * * /caminho/backend/ops/backup/backup.sh >> /var/log/ngolahealth-backup.log 2>&1`
- **macOS (launchd)**: ver `com.ngolahealth.backup.plist.example`.

São mantidos 30 dias (`BACKUP_RETENTION_DAYS`); os 7 mais recentes nunca são apagados, mesmo
que o agendamento pare durante semanas.

**Cópia fora do servidor:** uma cópia no mesmo disco não protege de avaria, roubo ou incêndio.
Copie a pasta de backups diariamente para outro local (outro servidor, disco externo rodado,
armazenamento na nuvem). Os ficheiros já vão cifrados.

## 3. Ensaio de restauro (mensal)

```bash
BACKUP_PRIVATE_KEY=~/NgolaHealthKeys/backup.pem ops/backup/restore-check.sh
```

Restaura o backup mais recente em `ngolahealth_restore_check`, confirma o número de tabelas e
de migrações, mostra a contagem das tabelas principais e apaga a base temporária. Um backup que
nunca foi restaurado não é um backup. Repita também depois de mudar a versão do MySQL ou as
chaves.

## 4. Recuperar após um incidente

1. **Não restaure por cima da base afectada**: o script recusa-o. Fica intacta para
   investigação.
2. Restaure para uma base nova:
   ```bash
   BACKUP_PRIVATE_KEY=/caminho/backup.pem ops/backup/restore.sh ngolahealth-20261119T023000Z ngolahealth_20261119
   ```
3. Arranque a aplicação contra ela (`DB_NAME=ngolahealth_20261119` no `.env`) e valide com os
   responsáveis: últimos registos, utilizadores e hospitais.
4. Só então a deixe em produção. Registe no relatório do incidente o backup usado e a janela de
   dados perdida.

## Objectivos

- **RPO** (dados que se podem perder): até 24 horas, com um backup diário. Para reduzir, aumente
  a frequência do agendamento ou, no futuro, active os binlogs do MySQL para recuperação até ao
  minuto.
- **RTO** (tempo para voltar a funcionar): restauro medido em ~1 s com os dados de demonstração;
  medir de novo com os dados reais em cada ensaio mensal.
