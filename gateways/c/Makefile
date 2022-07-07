DEBUG ?= 0
ifeq ($(DEBUG), 1)
    CFLAGS += -DDEBUG
endif

default:
	cmake -DCMAKE_C_FLAGS="$(CFLAGS)" -S . -B build
	cmake --build build

test:
	cmake -DCMAKE_C_FLAGS="$(CFLAGS)" -S . -B build -DBUILD_TESTING=ON
	cmake --build build

runtest:
	./test_fjage $(DEVICE)

clean:
	rm -fr libfjage.a libfjage.dylib libfjage.so test_fjage build

.PHONY: all clean
