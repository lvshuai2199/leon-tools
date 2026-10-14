#include "cli.h"
#include "clock.h"
#include "servo.h"
#include "stm32f103.h"
#include "uart.h"

static void led_init(void) {
    RCC->APB2ENR |= RCC_APB2ENR_IOPC;
    GPIOC->CRH &= ~(0xFu << 20);
    GPIOC->CRH |= (0x2u << 20);
    GPIOC->BSRR = (1u << 13);
}

static void led_set(int on) {
    if (on) {
        GPIOC->BSRR = (1u << (13 + 16));
    } else {
        GPIOC->BSRR = (1u << 13);
    }
}

int main(void) {
    char line[64];
    uint32_t led_at = 0u;
    int led_on = 0;

    led_init();
    uart_init();
    servo_init();

    for (;;) {
        uint32_t now = millis();
        servo_poll(now);
        if ((uint32_t)(now - led_at) >= 500u) {
            led_at = now;
            led_on = !led_on;
            led_set(led_on);
        }
        if (uart_read_line(line, (int)sizeof(line))) {
            cli_handle(line);
        }
    }
}
