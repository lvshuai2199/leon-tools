.syntax unified
.cpu cortex-m3
.fpu softvfp
.thumb

.global g_pfnVectors
.global Reset_Handler
.global Default_Handler

.section .isr_vector,"a",%progbits
g_pfnVectors:
.word _estack
.word Reset_Handler
.word NMI_Handler
.word HardFault_Handler
.word MemManage_Handler
.word BusFault_Handler
.word UsageFault_Handler
.word 0
.word 0
.word 0
.word 0
.word SVC_Handler
.word DebugMon_Handler
.word 0
.word PendSV_Handler
.word SysTick_Handler
.rept 37
.word Default_Handler
.endr
.word USART1_IRQHandler

.section .text.Reset_Handler
.thumb_func
Reset_Handler:
    ldr r0, =_sidata
    ldr r1, =_sdata
    ldr r2, =_edata
    b copy_check
copy_loop:
    ldr r3, [r0], #4
    str r3, [r1], #4
copy_check:
    cmp r1, r2
    bcc copy_loop
    ldr r1, =_sbss
    ldr r2, =_ebss
    movs r3, #0
    b zero_check
zero_loop:
    str r3, [r1], #4
zero_check:
    cmp r1, r2
    bcc zero_loop
    bl SystemInit
    bl main
    b .

.thumb_func
Default_Handler:
    b Default_Handler
