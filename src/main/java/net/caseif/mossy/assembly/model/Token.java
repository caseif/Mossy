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

import static net.caseif.mossy.assembly.model.ValueType.EMPTY;
import static net.caseif.mossy.assembly.model.ValueType.NUMBER_LITERAL;
import static net.caseif.mossy.assembly.model.ValueType.MASK;
import static net.caseif.mossy.assembly.model.ValueType.MATH_OPERATOR;
import static net.caseif.mossy.assembly.model.ValueType.STRING_LITERAL;

import net.caseif.moslib.Mnemonic;
import net.caseif.mossy.util.OperatorType;

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
        COMMENT(EMPTY, ";.*$"),
        MNEMONIC(ValueType.MNEMONIC, constructMnemonicRegex(), m -> Mnemonic.valueOf(m.toUpperCase())),
        X(EMPTY, "(?:X|x)(?![A-z0-9])"),
        Y(EMPTY, "(?:Y|y)(?![A-z0-9])"),
        IDENTIFIER(STRING_LITERAL, "([A-z][A-z0-9_]*)"),
        DIRECTIVE(ValueType.DIRECTIVE, "\\.([A-z]+)", Directive::valueOfInsensitive),
        HEX_QWORD(NUMBER_LITERAL, "\\$([0-9A-Fa-f]{5,8})", PARSE_HEX),
        HEX_DWORD(NUMBER_LITERAL, "\\$([0-9A-Fa-f]{3,4})", PARSE_HEX),
        HEX_WORD(NUMBER_LITERAL, "\\$([0-9A-Fa-f]{1,2})", PARSE_HEX),
        DEC_WORD(NUMBER_LITERAL, "([0-9]{1,3})", PARSE_DEC),
        BIN_QWORD(NUMBER_LITERAL, "%([01]{32})", PARSE_BIN),
        BIN_DWORD(NUMBER_LITERAL, "%([01]{16})", PARSE_BIN),
        BIN_WORD(NUMBER_LITERAL, "%([01]{8})", PARSE_BIN),
        COLON(EMPTY, ":"),
        COMMA(EMPTY, ","),
        EQUALS(EMPTY, "="),
        GREATER_THAN(MASK, "(>)", s -> MaskType.LOW),
        LESS_THAN(MASK, "(<)", s -> MaskType.HIGH),
        POUND(EMPTY, "#"),
        PLUS(MATH_OPERATOR, "(\\+)", OperatorType::getOperatorFromChar),
        MINUS(MATH_OPERATOR, "(-)", OperatorType::getOperatorFromChar),
        LEFT_PAREN(EMPTY, "\\("),
        RIGHT_PAREN(EMPTY, "\\)");

        private final ValueType valueType;
        private final Pattern regex;
        private final Function<String, ?> valueAdapter;

        Type(ValueType valueType, String regex, Function<String, ?> valueAdapter) {
            this.valueType = valueType;
            this.regex = Pattern.compile("^" + regex);
            this.valueAdapter = valueAdapter;
        }

        Type(ValueType valueType, String regex) {
            this(valueType, regex, Function.identity());
        }

        public ValueType getValueType() {
            return valueType;
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

    public static String constructMnemonicRegex() {
        StringBuilder sb = new StringBuilder();
        sb.append("(");

        for (Mnemonic m : Mnemonic.values()) {
            sb.append(m.name()).append('|');
            sb.append(m.name().toLowerCase()).append('|');
        }

        // remove last pipe
        sb.delete(sb.length() - 1, sb.length());

        sb.append(")(?=\\s|$)");

        return sb.toString();
    }

}
