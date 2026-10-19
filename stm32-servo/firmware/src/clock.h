#pragma once

#include <stdint.h>

void SystemInit(void);
uint32_t millis(void);
uint32_t system_clock_hz(void);
uint32_t timer_clock_hz(void);
uint32_t uart_clock_hz(void);
