#!/bin/bash
set -euo pipefail
PASS=$(sudo grep '^MYSQL_ROOT_PASSWORD=' /opt/leonpro/mysql/.env | cut -d= -f2- | tr -d '\r')
for db in leonpro_db_dev leonpro_db_prod; do
  sudo docker exec -i mysql8 mysql -uroot -p"${PASS}" --default-character-set=utf8mb4 "$db" < /tmp/ensure_youbo_pe_config.sql
done
sudo docker exec mysql8 mysql -uroot -p"${PASS}" --default-character-set=utf8mb4 -N -e "SELECT id, company, name, encrypt_type, encrypt_suffix FROM leonpro_db_dev.reg_code_config ORDER BY sort_order, id;"
