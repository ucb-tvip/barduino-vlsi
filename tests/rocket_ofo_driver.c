#include "mmio.h"

#define RESET_REG 0x4000

__attribute__((section(".secondary_text")))
int main(void)
{
  printf("amogus\n");
  // enable the core
  reg_write32(RESET_REG, 0);
  /*
  while(1) {
    printf("amogus\n");
  }
  */
}
