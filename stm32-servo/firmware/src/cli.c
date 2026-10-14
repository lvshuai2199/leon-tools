#include "cli.h"

#include "servo.h"
#include "uart.h"

static int put_u32(char *out, uint32_t value) {
    char tmp[10];
    int count = 0;
    int index;

    if (value == 0u) {
        out[0] = '0';
        return 1;
    }
    while (value > 0u) {
        tmp[count++] = (char)('0' + (value % 10u));
        value /= 10u;
    }
    for (index = 0; index < count; index++) {
        out[index] = tmp[count - 1 - index];
    }
    return count;
}

static void append_cdeg(char *buf, int *length, int32_t cdeg) {
    int whole;
    int frac;

    if (cdeg < 0) {
        cdeg = 0;
    }
    whole = (int)(cdeg / 100);
    frac = (int)(cdeg % 100);
    *length += put_u32(buf + *length, (uint32_t)whole);
    buf[(*length)++] = '.';
    buf[(*length)++] = (char)('0' + (frac / 10));
    buf[(*length)++] = (char)('0' + (frac % 10));
}

static void reply(const char *text) {
    uart_write(text);
}

static void reply_ok_cdeg(int32_t cdeg) {
    char buf[24];
    int length = 0;

    buf[length++] = 'O';
    buf[length++] = 'K';
    buf[length++] = ' ';
    append_cdeg(buf, &length, cdeg);
    buf[length++] = '\n';
    buf[length] = '\0';
    uart_write(buf);
}

static void reply_ang(void) {
    char buf[32];
    int length = 0;

    buf[length++] = 'A';
    buf[length++] = 'N';
    buf[length++] = 'G';
    buf[length++] = ' ';
    append_cdeg(buf, &length, servo_angle_cdeg());
    buf[length++] = ' ';
    append_cdeg(buf, &length, servo_target_cdeg());
    buf[length++] = '\n';
    buf[length] = '\0';
    uart_write(buf);
}

static char *skip_space(char *text) {
    while (*text == ' ' || *text == '\t') {
        text++;
    }
    return text;
}

static int starts_with(const char *text, const char *prefix) {
    while (*prefix != '\0') {
        char left = *text++;
        char right = *prefix++;
        if (left >= 'a' && left <= 'z') {
            left = (char)(left - 32);
        }
        if (left != right) {
            return 0;
        }
    }
    return 1;
}

static int parse_cdeg(const char **cursor, int32_t *out) {
    const char *text = skip_space(*cursor);
    int32_t whole = 0;
    int digits = 0;
    int32_t frac = 0;
    int frac_digits = 0;
    int round_digit = 0;

    if (*text < '0' || *text > '9') {
        return 0;
    }
    while (*text >= '0' && *text <= '9') {
        whole = whole * 10 + (*text - '0');
        if (whole > 1000) {
            return 0;
        }
        text++;
        digits++;
    }
    if (digits == 0) {
        return 0;
    }
    if (*text == '.') {
        text++;
        if (*text < '0' || *text > '9') {
            return 0;
        }
        while (*text >= '0' && *text <= '9') {
            int digit = *text - '0';
            if (frac_digits < 2) {
                frac = frac * 10 + digit;
                frac_digits++;
            } else if (frac_digits == 2) {
                round_digit = digit;
                frac_digits++;
            }
            text++;
        }
        while (frac_digits < 2) {
            frac *= 10;
            frac_digits++;
        }
        if (round_digit >= 5) {
            frac += 1;
        }
    }
    if (*text != '\0' && *text != ' ' && *text != '\t') {
        return 0;
    }
    *out = whole * 100 + frac;
    *cursor = text;
    return 1;
}

static int parse_u32(const char **cursor, uint32_t *out) {
    const char *text = skip_space(*cursor);
    uint32_t value = 0;
    int digits = 0;

    while (*text >= '0' && *text <= '9') {
        value = value * 10u + (uint32_t)(*text - '0');
        if (value > 100000u) {
            return 0;
        }
        text++;
        digits++;
    }
    if (digits == 0 || (*text != '\0' && *text != ' ' && *text != '\t')) {
        return 0;
    }
    *out = value;
    *cursor = text;
    return 1;
}

static int rest_empty(const char *text) {
    text = skip_space(text);
    return *text == '\0';
}

void cli_handle(char *line) {
    const char *cursor;
    int32_t cdeg;
    uint32_t speed;
    uint32_t min_us;
    uint32_t max_us;
    char buf[32];
    int length;

    line = skip_space(line);
    if (*line == '\0') {
        return;
    }
    if (starts_with(line, "PING") && rest_empty(line + 4)) {
        reply("PONG\n");
        return;
    }
    if (starts_with(line, "GET") && rest_empty(line + 3)) {
        reply_ang();
        return;
    }
    if (starts_with(line, "STOP") && rest_empty(line + 4)) {
        servo_stop();
        reply_ok_cdeg(servo_angle_cdeg());
        return;
    }
    if (starts_with(line, "CFG?") && rest_empty(line + 4)) {
        length = 0;
        buf[length++] = 'C';
        buf[length++] = 'F';
        buf[length++] = 'G';
        buf[length++] = ' ';
        length += put_u32(buf + length, servo_min_us());
        buf[length++] = ' ';
        length += put_u32(buf + length, servo_max_us());
        buf[length++] = '\n';
        buf[length] = '\0';
        uart_write(buf);
        return;
    }
    if (starts_with(line, "CFG") && !starts_with(line, "CFG?")) {
        cursor = line + 3;
        if (!parse_u32(&cursor, &min_us) || !parse_u32(&cursor, &max_us) || !rest_empty(cursor)) {
            reply("ERR SYNTAX\n");
            return;
        }
        if (min_us > 65535u || max_us > 65535u || servo_set_pulse_range((uint16_t)min_us, (uint16_t)max_us) != 0) {
            reply("ERR RANGE\n");
            return;
        }
        reply("OK\n");
        return;
    }
    if (starts_with(line, "SET")) {
        cursor = line + 3;
        speed = 0u;
        if (!parse_cdeg(&cursor, &cdeg)) {
            reply("ERR SYNTAX\n");
            return;
        }
        cursor = skip_space(cursor);
        if (*cursor != '\0') {
            if (!parse_u32(&cursor, &speed) || !rest_empty(cursor)) {
                reply("ERR SYNTAX\n");
                return;
            }
        }
        if (servo_set_angle(cdeg, speed) != 0) {
            reply("ERR RANGE\n");
            return;
        }
        reply_ok_cdeg(cdeg);
        return;
    }
    reply("ERR SYNTAX\n");
}
