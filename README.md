# Mossy

An assembler for the 6502, written in Java. This project aims for feature parity with x816.

### How to Use

```
java -jar mossy.jar /path/to/input.asm [path/to/output.bin]
```

The input parameter may be a directory, in which case the laast parameter is
ignored.

If the last parameter is not provided, the output will be written to
<filename>.bin.

### Features

- Support for all addressing modes
- Label support
- Named constant support
- Full arithmetic support
- Limited masking support (`<` and `>`)
- Limited directive support (`.org`)

### Pending

- Full masking support
  - Currently cannot be used in constant definitions
  - Currently cannot be used within arithmetic expressions
- Full directive support
  - Namely `.db`/`.dw`, `.include`, and `.macro`
  - Validation of `.index` and `.mem` directives

### License

Mossy is published under the [MIT License](https://opensource.org/licenses/MIT). Use of its code and any provided assets
is permitted per the terms of the license.
