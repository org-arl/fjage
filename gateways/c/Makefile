CFLAGS=-std=c99 -Wall -O2 -D_DEFAULT_SOURCE

all: libfjage.a

libfjage.a: fjage.o jsmn.o encode.o decode.o
	$(AR) rc $@ $^

%.o: %.c fjage.h jsmn.h b64.h
	$(CC) $(CFLAGS) -c $< -o $@

test_fjage: test_fjage.o libfjage.a
	$(CC) -o test_fjage test_fjage.o libfjage.a -lpthread

test: test_fjage
	./test_fjage $(DEVICE)

clean:
	rm -f libfjage.a *.o test_fjage

.PHONY: all clean
