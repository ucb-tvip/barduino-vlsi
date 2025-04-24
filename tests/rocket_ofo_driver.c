#include "mmio.h"

#define RESET_REG 0x4000
#define CSR_REG 0x4004

int main(void)
{
  printf("enabling the 151t core...\n");
  // enable the core
  reg_write32(RESET_REG, 0);

  int csr = reg_read32(CSR_REG);
  while(csr == 0) {
    csr = reg_read32(CSR_REG);
  }

  if (csr == 1) {
    printf("pass!\n");
  } else {
    printf("fail :( CSR == %d\n", csr);
  }
}
