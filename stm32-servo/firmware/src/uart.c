#include "uart.h"

#include "clock.h"
#include "stm32f103.h"

#define RB_SIZE 128u

static volatile uint8_t rb[RB_SIZE];
static volatile uint16_t rb_head;
static volatile uint16_t rb_tail;

static uint16_t usart_brr(uint32_t clk, uint32_t baud) {
    uint32_t div = (clk + (baud / 2u)) / baud;
    uint32_t mantissa = div / 16u;
    uint32_t fraction = div % 16u;
    return (uint16_t)((mantissa << 4) | fraction);
}

void uart_init(void) {
    RCC->APB2ENR |= RCC_APB2ENR_IOPA | RCC_APB2ENR_USART1;

    GPIOA->CRH &= ~(0xFFu << 4);
    GPIOA->CRH |= (0xBu << 4);
    GPIOA->CRH |= (0x4u << 8);

    USART1->BRR = usart_brr(uart_clock_hz(), 115200u);
    USART1->CR1 = USART_CR1_UE | USART_CR1_TE | USART_CR1_RE | USART_CR1_RXNEIE;
    NVIC_ISER1 = USART1_IRQ_BIT;
}

void USART1_IRQHandler(void) {
    if ((USART1->SR & USART_SR_RXNE) == 0u) {
        return;
    }
    uint8_t byte = (uint8_t)USART1->DR;
    uint16_t next = (uint16_t)((rb_head + 1u) % RB_SIZE);
    if (next == rb_tail) {
        return;
    }
    rb[rb_head] = byte;
    rb_head = next;
}

int uart_read_line(char *buf, int cap) {
    static char line[64];
    static int length = 0;

    while (rb_tail != rb_head) {
        uint8_t byte = rb[rb_tail];
        rb_tail = (uint16_t)((rb_tail + 1u) % RB_SIZE);
        if (byte == '\r') {
            continue;
        }
        if (byte == '\n') {
            int copy = length;
            if (copy >= cap) {
                copy = cap - 1;
            }
            for (int i = 0; i < copy; i++) {
                buf[i] = line[i];
            }
            buf[copy] = '\0';
            length = 0;
            return 1;
        }
        if (length < (int)sizeof(line) - 1) {
            line[length++] = (char)byte;
        }
    }
    return 0;
}

void uart_write(const char *text) {
    while (*text != '\0') {
        while ((USART1->SR & USART_SR_TXE) == 0u) {
        }
        USART1->DR = (uint8_t)*text++;
    }
}
