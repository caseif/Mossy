/*
 * This file is a part of Mossy.
 * Copyright (c) 2018, Max Roncace <mproncace@gmail.com>
 *
 * The MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package net.caseif.mossy.assembly.model;

import net.caseif.moslib.Mnemonic;

import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

public class Token {

    private static final Function<String, Integer> PARSE_HEX = v -> Integer.parseInt(v, 16);
    private static final Function<String, Integer> PARSE_DEC = v -> Integer.parseInt(v, 10);
    private static final Function<String, Integer> PARSE_BIN = v -> Integer.parseInt(v, 2);

    private final Type type;
    private final Object val;
    private final int line;

    public Token(Type type, @Nullable Object val, int line) {
        this.type = type;
        this.val = val;
        this.line = line;
    }

    public Type getType() {
        return type;
    }

    public Optional<?> getValue() {
        return Optional.ofNullable(val);
    }

    public int getLine() {
        return line;
    }

    public enum Type implements ExpressionPart {
        COMMENT(";.*$"),
        MNEMONIC("([A-Z]{3})(?=\\s|$)", Mnemonic::valueOf),
        X("X(?![A-z0-9])"),
        Y("Y(?![A-z0-9])"),
        IDENTIFIER("([A-z][A-z0-9_]*)"),
        DIRECTIVE("\\.([A-z]+)", Directive::valueOfInsensitive),
        HEX_QWORD("\\$([0-9A-F]{8})", PARSE_HEX),
        HEX_DWORD("\\$([0-9A-F]{4})", PARSE_HEX),
        HEX_WORD("\\$([0-9A-F]{2})", PARSE_HEX),
        DEC_WORD("([0-9]{1,3})", PARSE_DEC),
        BIN_QWORD("%([01]{32})", PARSE_BIN),
        BIN_DWORD("%([01]{16})", PARSE_BIN),
        BIN_WORD("%([01]{8})", PARSE_BIN),
        COLON(":"),
        COMMA(","),
        EQUALS("="),
        POUND("#"),
        LEFT_PAREN("\\("),
        RIGHT_PAREN("\\)");

        private final Pattern regex;
        private final Function<String, ?> valueAdapter;

        Type(String regex, Function<String, ?> valueAdapter) {
            this.regex = Pattern.compile("^" + regex);
            this.valueAdapter = valueAdapter;
        }

        Type(String regex) {
            this(regex, Function.identity());
        }

        public Pattern getRegex() {
            return regex;
        }

        public Object adaptValue(String val) {
            return valueAdapter.apply(val);
        }

        public Token createToken(String valueStr, int lineNum) {
            Object value = null;

            if (valueStr != null) {
                try {
                    value = adaptValue(valueStr);
                } catch (Throwable t) {
                    throw new IllegalArgumentException(String.format("Failed to adapt value %s for token type %s.",
                            valueStr, name()), t);
                }
            }

            return new Token(this, value, lineNum);
        }
    }

}
