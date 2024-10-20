#!/bin/bash
echo "TODO figure out how to build 151t binaries in 32bit rv32i and rocket in 64bit with one makefile"

# ty abe for the reference -elam

set -ex

RDIR=$(git rev-parse --show-toplevel)

# move to top-level
cd $RDIR

DRIVER_BIN=rocket_ofo_driver
OFO_BIN=median

pushd tests

# build tests
make
# should create 2 files - OFO_BIN.riscv and DRIVER_BIN.rocket.riscv
./build-for-ofo.sh ${OFO_BIN}
popd

SOC1_BIN=$PWD/tests/${DRIVER_BIN}.riscv
SOC2_BIN=${1:-$PWD/tests/${OFO_BIN}.ofo.riscv}

CFG=OFORocketConfig
out_name=$(basename $SOC2_BIN)

pushd sims/vcs
rm -rf uartpty*
rm -rf ${out_name}.out*
# payload should load the 2nd binary (provided it has the right addresses)

make CONFIG=${CFG} \
    BINARY=${SOC1_BIN} \
    EXTRA_SIM_FLAGS="+use-loadmem-hack +payload=${SOC2_BIN} +uartlog=${out_name}.out" \
    run-binary-debug
rm -rf uartpty*
