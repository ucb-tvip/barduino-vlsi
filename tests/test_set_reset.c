#include "mmio.h"

#define RESET_REG 0x4000

int main(void)
{
  printf("amogus\n");
  reg_write32(RESET_REG, 0);
   while (!reg_read32(RESET_REG) & 0x0) {

    printf("waiting\n");
  }
    printf("waited :3\n");
}
