CFLAGS=-std=c99 -Wall -O2

all: libfjage.a

libfjage.a: fjage.o jsmn.o
	$(AR) rc $@ $^

%.o: %.c fjage.h jsmn.h
	$(CC) -c $(CFLAGS) $< -o $@

test: libfjage.a
	$(CC) -o test_fjage test_fjage.c libfjage.a
	./test_fjage

clean:
	rm -f libfjage.a *.o test_fjage

.PHONY: all clean
