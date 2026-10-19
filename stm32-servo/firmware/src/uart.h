#pragma once

void uart_init(void);
int uart_read_line(char *buf, int cap);
void uart_write(const char *text);
