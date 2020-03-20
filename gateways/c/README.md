# fjåge C gateway

## Build fjåge C library

This folder contains the files `fjage.h` and `fjage.c`. The header file includes the definition and documentation of the fjåge C APIs and the source code is provided in the `fjage.c` file.  `Makefile` is used to collate the necessary files and compile the source code and run tests.

To build fjåge C library on Linux / macOS , run:

```bash
make
```

This will generate a library (`libfjage.a`) which can be used to link against.

To build fjåge C library on Windows, run:

```bash
cl *.c /link /out:test_fjage.exe
lib *.obj /out:fjage.lib
```

This will generate a library (`fjage.lib`) which can be used to link against.

## Test fjåge C library

To run the test using fjåge C APIs, follow the two steps below:

1. Run a fjåge server in another terminal window at the project root folder as shown below:

```bash
./fjage.sh
```

2. Run the test program:

On Linux / Max OS X:

```bash
make test
```

or on Windows:

```bash
test_fjage.exe
```

## Clean

To clean all the built files on Linux / Mac OS X, run:

```bash
make clean
```

or to clean them on Windows:

```bash
del *.obj *.lib *.exe
```

## Documentation

The documentation is available at [fjåge C APIs](https://fjage.readthedocs.io/en/latest/cgw.html).
