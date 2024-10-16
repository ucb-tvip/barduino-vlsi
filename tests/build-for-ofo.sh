#!/bin/bash

# ty abe for the reference -elam

set -ex

# $1 - name of specific binary to run on smartNIC core

make

BINARY_BUILD=${1}.riscv
BINARY_OUTPUT=${1}.ofo.riscv

# -O - output as a binary
# --change-addresses - add N to the default address (aka DRAM) - move up 0x2000_0000 to 0xa000_0000
    #-S
    #-O binary \
riscv64-unknown-elf-objcopy \
    --change-addresses 0x20000000 \
    $BINARY_BUILD \
    $BINARY_OUTPUT

# just verify that it can objdump the binary (valid bin)
riscv64-unknown-elf-objdump -S $BINARY_OUTPUT >/dev/null
