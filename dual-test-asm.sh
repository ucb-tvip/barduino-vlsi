#!/bin/bash
set -o pipefail
#
# run all the asm bmarks in dual-test 
for file in $PWD/generators/ofo/src/main/resources/vsrc/cores/kevin-kore/tests/asm/*.elf; do

      echo "Running test: $file"

      output=$("./dual-test.sh" "$file" 2>/dev/null | grep pass! )

          # Check if the last line is "pass!"
    if [ "$output" == "pass!" ]; then
        echo "File $file passed successfully."
    else
        echo "Error: The last line of the output for file $file is not 'pass!'."
        echo "Output was: $output"
        exit 1
    fi

done
