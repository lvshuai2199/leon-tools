#!/bin/bash
set -euo pipefail
PASS=$(sudo grep '^MYSQL_ROOT_PASSWORD=' /opt/leonpro/mysql/.env | cut -d= -f2- | tr -d '\r')
for db in leonpro_db_dev leonpro_db_prod; do
  sudo docker exec -i mysql8 mysql -uroot -p"${PASS}" --default-character-set=utf8mb4 "$db" < /tmp/group_regcode_menus.sql
done
sudo docker exec mysql8 mysql -uroot -p"${PASS}" --default-character-set=utf8mb4 -N -e "SELECT id, menu_name, parent_id, menu_url FROM leonpro_db_dev.sys_menus WHERE id LIKE 'menu_reg%' OR id='menu_registration' ORDER BY parent_id, sort_order;"
