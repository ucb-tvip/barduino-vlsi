#!/bin/bash
echo "TODO figure out how to build 151t binaries in 32bit rv32i and rocket in 64bit with one makefile"

# ty abe for the reference -elam

set -ex

RDIR=$(git rev-parse --show-toplevel)

# move to top-level
cd $RDIR

DRIVER_BIN=rocket_ofo_driver
#DRIVER_BIN=accum
OFO_BIN=write_a_lot

pushd tests
make clean

# build tests
make
# should create 2 files - OFO_BIN.riscv and DRIVER_BIN.rocket.riscv
./build-for-ofo.sh ${OFO_BIN}
popd

SOC1_BIN=$PWD/tests/${DRIVER_BIN}.riscv
#SOC1_BIN=$PWD/tests/nic-loopback.riscv
SOC2_BIN=$PWD/generators/ofo/src/main/resources/vsrc/cores/kevin-kore/tests/asm/add.elf
#SOC2_BIN=$PWD/tests/${OFO_BIN}.ofo.riscv

CFG=OFORocketConfig

pushd sims/vcs
rm -rf uartpty*
rm -rf ${CFG}.out*
#make clean
# payload should load the 2nd binary (provided it has the right addresses)
# # TODO undersatnd with soc1-msip is 
# # +uartlog=${CFG}.out +write-soc1-msip +link_lat_a2s=10 +link_lat_s2a=10" 
#
    # USE_VPD=1\
    ## +link_lat_a2s=10 +link_lat_s2a=10" \
make CONFIG=${CFG} \
    BINARY=${SOC1_BIN} \
    EXTRA_SIM_FLAGS="+use-loadmem-hack +payload=${SOC2_BIN}" \
    run-binary-debug
rm -rf uartpty*
