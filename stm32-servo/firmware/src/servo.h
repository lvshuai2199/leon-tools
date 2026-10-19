#pragma once

#include <stdint.h>

void servo_init(void);
void servo_poll(uint32_t now_ms);
int servo_set_angle(int32_t cdeg, uint32_t speed_dps);
int servo_set_pulse_range(uint16_t min_us, uint16_t max_us);
void servo_stop(void);
int32_t servo_angle_cdeg(void);
int32_t servo_target_cdeg(void);
uint16_t servo_min_us(void);
uint16_t servo_max_us(void);
