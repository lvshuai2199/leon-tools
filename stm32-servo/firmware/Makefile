PREFIX ?= arm-none-eabi-
CC := $(PREFIX)gcc
OBJCOPY := $(PREFIX)objcopy
SIZE := $(PREFIX)size

BUILD := build
TARGET := $(BUILD)/servo

CFLAGS := -std=c11 -Wall -Wextra -Os -mcpu=cortex-m3 -mthumb \
	-ffreestanding -fno-builtin -ffunction-sections -fdata-sections \
	-Iinclude -Isrc
LDFLAGS := -Tstm32f103c8.ld -nostartfiles -nostdlib -Wl,--gc-sections

SRCS := src/main.c src/clock.c src/uart.c src/servo.c src/cli.c
OBJS := $(SRCS:src/%.c=$(BUILD)/%.o) $(BUILD)/startup.o

all: $(TARGET).bin

$(BUILD):
	mkdir -p $(BUILD)

$(BUILD)/startup.o: startup_stm32f103xb.s | $(BUILD)
	$(CC) $(CFLAGS) -c $< -o $@

$(BUILD)/%.o: src/%.c | $(BUILD)
	$(CC) $(CFLAGS) -c $< -o $@

$(TARGET).elf: $(OBJS)
	$(CC) $(CFLAGS) $(LDFLAGS) $^ -lgcc -o $@
	$(SIZE) $@

$(TARGET).bin: $(TARGET).elf
	$(OBJCOPY) -O binary $< $@

clean:
	rm -rf $(BUILD)

.PHONY: all clean
