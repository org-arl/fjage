# fjåge C gateway

## Build fjåge C library

This folder contains the files `fjage.h` and `fjage.c`. The header file includes the definition and documentation of the fjåge C APIs and the source code is provided in the `fjage.c` file.  `Makefile` is used to collate the necessary files and compile the source code and run tests.

To build fjåge C library, run

```bash
make
```
This will generate a library (`libfjage.a`) which can be used to link.


## Test fjåge C library

To run the test using fjåge C APIs, follow the two steps below:

1. Run a fjåge server in another terminal window at the project root folder as shown below:

```bash
./fjage.sh
```

2. Run the Makefile

```bash
make test
```

## Clean

To clean all the built files, run

```bash
make clean
```

## Documentation

The documentation is available at [fjåge C APIs](https://fjage.readthedocs.io/en/latest/cgw.html).