# Mossy

An assembler for the 6502, written in Java. This project aims for compatibility with x816.

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
- Masking support (`<` and `>`)
- Some directive support (`.org`, `.db`, `.dw`)
  - `.index` and `.mem` (as specified by x816) will parse, but are ignored

#### Pending

- More directive support
  - `.include` support
  - `.macro` support
  - Validation of `.index` and `.mem` directives
- Tentatively: performance optimization (the parser is _super_ inefficient)

### Why?

Mossy was originally a rudimentary assembler built into
[jNES](https://github.com/caseif/jNES) until I realized it was quickly growing
out of the project's scope, at which point it was split into a separate,
fully-featured project.

As for why I choose to develop it, I just think this stuff is a lot of fun, and
I want to gain more experience working with it.

### License

Mossy is published under the [MIT License](https://opensource.org/licenses/MIT). Use of its code and any provided assets
is permitted per the terms of the license.
