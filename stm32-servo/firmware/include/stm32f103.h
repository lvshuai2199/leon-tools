#pragma once

#include <stdint.h>

#define PERIPH_BASE      0x40000000UL
#define APB1PERIPH_BASE  PERIPH_BASE
#define APB2PERIPH_BASE  (PERIPH_BASE + 0x10000UL)
#define AHBPERIPH_BASE   (PERIPH_BASE + 0x20000UL)

#define TIM2_BASE        (APB1PERIPH_BASE + 0x0000UL)
#define GPIOA_BASE       (APB2PERIPH_BASE + 0x0800UL)
#define GPIOC_BASE       (APB2PERIPH_BASE + 0x1000UL)
#define USART1_BASE      (APB2PERIPH_BASE + 0x3800UL)
#define RCC_BASE         (AHBPERIPH_BASE + 0x1000UL)
#define FLASH_R_BASE     (AHBPERIPH_BASE + 0x2000UL)

#define RCC_CR_HSEON     (1u << 16)
#define RCC_CR_HSERDY    (1u << 17)
#define RCC_CR_PLLON     (1u << 24)
#define RCC_CR_PLLRDY    (1u << 25)

#define RCC_CFGR_SW_PLL  2u
#define RCC_CFGR_SWS_PLL (2u << 2)
#define RCC_CFGR_PPRE1_DIV2 (4u << 8)
#define RCC_CFGR_PLLSRC_HSE (1u << 16)
#define RCC_CFGR_PLLMUL9 (7u << 18)

#define RCC_APB2ENR_IOPA (1u << 2)
#define RCC_APB2ENR_IOPC (1u << 4)
#define RCC_APB2ENR_USART1 (1u << 14)
#define RCC_APB1ENR_TIM2 (1u << 0)

#define USART_SR_RXNE    (1u << 5)
#define USART_SR_TXE     (1u << 7)
#define USART_CR1_RE     (1u << 2)
#define USART_CR1_TE     (1u << 3)
#define USART_CR1_RXNEIE (1u << 5)
#define USART_CR1_UE     (1u << 13)

#define TIM_CR1_CEN      (1u << 0)
#define TIM_CR1_ARPE     (1u << 7)
#define TIM_EGR_UG       (1u << 0)
#define TIM_CCER_CC1E    (1u << 0)
#define TIM_CCMR1_OC1PE  (1u << 3)
#define TIM_CCMR1_OC1M_PWM1 (6u << 4)

typedef struct {
    volatile uint32_t CR;
    volatile uint32_t CFGR;
    volatile uint32_t CIR;
    volatile uint32_t APB2RSTR;
    volatile uint32_t APB1RSTR;
    volatile uint32_t AHBENR;
    volatile uint32_t APB2ENR;
    volatile uint32_t APB1ENR;
    volatile uint32_t BDCR;
    volatile uint32_t CSR;
} RCC_TypeDef;

typedef struct {
    volatile uint32_t ACR;
    volatile uint32_t KEYR;
    volatile uint32_t OPTKEYR;
    volatile uint32_t SR;
    volatile uint32_t CR;
    volatile uint32_t AR;
    volatile uint32_t RESERVED;
    volatile uint32_t OBR;
    volatile uint32_t WRPR;
} FLASH_TypeDef;

typedef struct {
    volatile uint32_t CRL;
    volatile uint32_t CRH;
    volatile uint32_t IDR;
    volatile uint32_t ODR;
    volatile uint32_t BSRR;
    volatile uint32_t BRR;
    volatile uint32_t LCKR;
} GPIO_TypeDef;

typedef struct {
    volatile uint32_t CR1;
    volatile uint32_t CR2;
    volatile uint32_t SMCR;
    volatile uint32_t DIER;
    volatile uint32_t SR;
    volatile uint32_t EGR;
    volatile uint32_t CCMR1;
    volatile uint32_t CCMR2;
    volatile uint32_t CCER;
    volatile uint32_t CNT;
    volatile uint32_t PSC;
    volatile uint32_t ARR;
    volatile uint32_t RESERVED0;
    volatile uint32_t CCR1;
    volatile uint32_t CCR2;
    volatile uint32_t CCR3;
    volatile uint32_t CCR4;
    volatile uint32_t RESERVED1;
    volatile uint32_t DCR;
    volatile uint32_t DMAR;
} TIM_TypeDef;

typedef struct {
    volatile uint32_t SR;
    volatile uint32_t DR;
    volatile uint32_t BRR;
    volatile uint32_t CR1;
    volatile uint32_t CR2;
    volatile uint32_t CR3;
    volatile uint32_t GTPR;
} USART_TypeDef;

#define RCC    ((RCC_TypeDef *)RCC_BASE)
#define FLASH  ((FLASH_TypeDef *)FLASH_R_BASE)
#define GPIOA  ((GPIO_TypeDef *)GPIOA_BASE)
#define GPIOC  ((GPIO_TypeDef *)GPIOC_BASE)
#define TIM2   ((TIM_TypeDef *)TIM2_BASE)
#define USART1 ((USART_TypeDef *)USART1_BASE)

#define NVIC_ISER1 (*(volatile uint32_t *)0xE000E104)
#define USART1_IRQ_BIT (1u << (37 - 32))

#define SYST_CSR (*(volatile uint32_t *)0xE000E010)
#define SYST_RVR (*(volatile uint32_t *)0xE000E014)
#define SYST_CVR (*(volatile uint32_t *)0xE000E018)
#define SYST_CSR_ENABLE (1u << 0)
#define SYST_CSR_TICKINT (1u << 1)
#define SYST_CSR_CLKSOURCE (1u << 2)
