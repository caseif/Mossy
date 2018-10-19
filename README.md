# Mossy

An assembler for the 6502, written in Java.

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

### Pending

- Most directives
  - Namely `.db`/`.dw`, `.include`, and `.macro`
- Named constants
- Arithmetic operations in constant expressions

### License

Mossy is published under the [MIT License](https://opensource.org/licenses/MIT). Use of its code and any provided assets
is permitted per the terms of the license.
