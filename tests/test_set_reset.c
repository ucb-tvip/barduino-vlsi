#include "mmio.h"

#define RESET_REG 0x4000

int main(void)
{
  reg_write32(RESET_REG, 1);
 while (!reg_read32(RESET_REG) & 0x1) {

    printf("waiting\n");
  }
    printf("waited :3\n");
}
