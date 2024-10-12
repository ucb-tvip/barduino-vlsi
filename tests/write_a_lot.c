int main() {
unsigned long	* addr = 0x4004;

int n_bytes = 100000;

for (int i=0; i < n_bytes; i++) {
  *addr = 0xDEADBEEF;
  addr++;
}
}
