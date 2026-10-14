#include "servo.h"

#include "clock.h"
#include "stm32f103.h"

#define ANGLE_MAX_CDEG 18000
#define SERVO_PERIOD_US 20000
#define SERVO_TICK_MS 20u

static int32_t current_cdeg = 9000;
static int32_t target_cdeg = 9000;
static uint32_t speed_dps;
static uint16_t min_us = 500;
static uint16_t max_us = 2500;
static uint32_t last_ms;

static void apply_pulse(void) {
    uint32_t span = (uint32_t)(max_us - min_us);
    uint32_t pulse = (uint32_t)min_us + ((uint32_t)current_cdeg * span) / ANGLE_MAX_CDEG;
    TIM2->CCR1 = pulse;
}

static void step_once(void) {
    int32_t step;

    if (current_cdeg == target_cdeg) {
        return;
    }
    if (speed_dps == 0u) {
        current_cdeg = target_cdeg;
    } else {
        step = (int32_t)(speed_dps * 2u);
        if (step < 1) {
            step = 1;
        }
        if (target_cdeg > current_cdeg) {
            current_cdeg += step;
            if (current_cdeg > target_cdeg) {
                current_cdeg = target_cdeg;
            }
        } else {
            current_cdeg -= step;
            if (current_cdeg < target_cdeg) {
                current_cdeg = target_cdeg;
            }
        }
    }
    apply_pulse();
}

void servo_init(void) {
    uint32_t timclk = timer_clock_hz();
    uint32_t psc = (timclk / 1000000u) - 1u;

    RCC->APB2ENR |= RCC_APB2ENR_IOPA;
    RCC->APB1ENR |= RCC_APB1ENR_TIM2;

    GPIOA->CRL &= ~0xFu;
    GPIOA->CRL |= 0xBu;

    TIM2->PSC = psc;
    TIM2->ARR = SERVO_PERIOD_US - 1u;
    TIM2->CCMR1 = TIM_CCMR1_OC1M_PWM1 | TIM_CCMR1_OC1PE;
    TIM2->CCER = TIM_CCER_CC1E;
    TIM2->CR1 = TIM_CR1_ARPE;
    apply_pulse();
    TIM2->EGR = TIM_EGR_UG;
    TIM2->CR1 |= TIM_CR1_CEN;
    last_ms = millis();
}

void servo_poll(uint32_t now_ms) {
    uint32_t elapsed = now_ms - last_ms;
    uint32_t steps;

    if (elapsed < SERVO_TICK_MS) {
        return;
    }
    steps = elapsed / SERVO_TICK_MS;
    if (steps > 50u) {
        steps = 50u;
    }
    last_ms += steps * SERVO_TICK_MS;
    while (steps-- > 0u) {
        step_once();
    }
}

int servo_set_angle(int32_t cdeg, uint32_t speed) {
    if (cdeg < 0 || cdeg > ANGLE_MAX_CDEG || speed > 360u) {
        return -1;
    }
    target_cdeg = cdeg;
    speed_dps = speed;
    if (speed == 0u) {
        current_cdeg = target_cdeg;
        apply_pulse();
    }
    return 0;
}

int servo_set_pulse_range(uint16_t next_min, uint16_t next_max) {
    if (next_min < 100u || next_max > 3000u || next_max <= next_min || (uint16_t)(next_max - next_min) < 100u) {
        return -1;
    }
    min_us = next_min;
    max_us = next_max;
    apply_pulse();
    return 0;
}

void servo_stop(void) {
    target_cdeg = current_cdeg;
    speed_dps = 0u;
}

int32_t servo_angle_cdeg(void) {
    return current_cdeg;
}

int32_t servo_target_cdeg(void) {
    return target_cdeg;
}

uint16_t servo_min_us(void) {
    return min_us;
}

uint16_t servo_max_us(void) {
    return max_us;
}
