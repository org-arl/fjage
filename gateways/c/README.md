# fjåge C gateway

This is a fjåge gateway implementation in C.

## Building

Prerequisites: [CMake](https://cmake.org/) ≥ 3.1

For building on Linux / macOS :

A Makefile is provided which will invoke CMake to build a static (`libfjage.a`) and dynamic(`libfjage.so/libfjage.dylib`) library for your platform.

```bash
make
```

For building on Windows:

- Ensure [Visual Studio Code](https://code.visualstudio.com/) or a similar IDE is installed and the CMake toolchain is set up.
- open this directory in the IDE. It should automatically identify the CMake target and provide a option to build it, generating the static (`fjage.lib`) and dynamic (`fjage.dll`) libaries.

### Cleaning

```bash
make clean
```

### Debugging

A debug build is also available.

```bash
make DEBUG=1 default
```

Current the debug build adds the following features :

- adds a handler for SIGSEV (segmentation fault) to print a stack trace when the applications crashes (Linux / macOS).

## Usage

`fjage.h` header file includes the definition and documentation of the fjåge C APIs that can be used.

## Testing

A test suite is available in the `test` directory. To run the test suite, follow the two steps below:

1. Run a fjåge server in another terminal window at the project root folder as shown below:

    ```bash
    # cd to the project root
    > cd ../..
    > ./fjage.sh
    ```

2. Run the test program:

    On Linux / Max OS X:

    ```bash
    > make test runtest
    ```

    or on Windows:

    ```bash
    > test_fjage.exe
    ```

## Documentation

The documentation is available at [fjåge C APIs](https://fjage.readthedocs.io/en/latest/cgw.html).
