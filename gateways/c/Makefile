default: 
	cmake -S . -B build
	cmake --build build 

test:
	cmake -S . -B build -DBUILD_TESTING=ON
	cmake --build build

runtest:
	./test_fjage $(DEVICE)

clean:
	rm -fr libfjage.a libfjage.dylib libfjage.so test_fjage build

.PHONY: all clean
