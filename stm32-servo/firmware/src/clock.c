#include "clock.h"

#include "stm32f103.h"

static volatile uint32_t g_ms;
static uint32_t g_sysclk_hz = 8000000u;
static uint32_t g_timclk_hz = 8000000u;
static uint32_t g_uartclk_hz = 8000000u;

static int wait_hse(void) {
    RCC->CR |= RCC_CR_HSEON;
    for (uint32_t i = 0; i < 1000000u; i++) {
        if ((RCC->CR & RCC_CR_HSERDY) != 0u) {
            return 1;
        }
    }
    return 0;
}

void SystemInit(void) {
    if (wait_hse()) {
        FLASH->ACR = (1u << 4) | 2u;
        RCC->CFGR = RCC_CFGR_PPRE1_DIV2 | RCC_CFGR_PLLSRC_HSE | RCC_CFGR_PLLMUL9;
        RCC->CR |= RCC_CR_PLLON;
        while ((RCC->CR & RCC_CR_PLLRDY) == 0u) {
        }
        RCC->CFGR = (RCC->CFGR & ~3u) | RCC_CFGR_SW_PLL;
        while ((RCC->CFGR & RCC_CFGR_SWS_PLL) != RCC_CFGR_SWS_PLL) {
        }
        g_sysclk_hz = 72000000u;
        g_timclk_hz = 72000000u;
        g_uartclk_hz = 72000000u;
    }

    SYST_RVR = (g_sysclk_hz / 1000u) - 1u;
    SYST_CVR = 0u;
    SYST_CSR = SYST_CSR_ENABLE | SYST_CSR_TICKINT | SYST_CSR_CLKSOURCE;
}

uint32_t millis(void) {
    return g_ms;
}

uint32_t system_clock_hz(void) {
    return g_sysclk_hz;
}

uint32_t timer_clock_hz(void) {
    return g_timclk_hz;
}

uint32_t uart_clock_hz(void) {
    return g_uartclk_hz;
}

void SysTick_Handler(void) {
    g_ms++;
}

void NMI_Handler(void) {
    for (;;) {
    }
}

void HardFault_Handler(void) {
    for (;;) {
    }
}

void MemManage_Handler(void) {
    for (;;) {
    }
}

void BusFault_Handler(void) {
    for (;;) {
    }
}

void UsageFault_Handler(void) {
    for (;;) {
    }
}

void SVC_Handler(void) {
    for (;;) {
    }
}

void DebugMon_Handler(void) {
    for (;;) {
    }
}

void PendSV_Handler(void) {
    for (;;) {
    }
}
