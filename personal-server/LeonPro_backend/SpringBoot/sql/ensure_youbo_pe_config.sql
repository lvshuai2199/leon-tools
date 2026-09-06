INSERT INTO reg_code_config (id, company, name, component_name, encrypt_type, encrypt_suffix, sort_order)
VALUES ('rcc_youbo_pe', '友博', 'PE插件', 'pe', 'SHA-256', 'youbo_leon', 4)
ON DUPLICATE KEY UPDATE
  company = VALUES(company),
  name = VALUES(name),
  component_name = VALUES(component_name),
  encrypt_type = VALUES(encrypt_type),
  encrypt_suffix = VALUES(encrypt_suffix),
  sort_order = VALUES(sort_order);
