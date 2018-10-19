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

package net.caseif.mossy.assembly.parser;

import net.caseif.mossy.assembly.model.Token;
import net.caseif.mossy.util.exception.LexerException;
import net.caseif.mossy.util.tuple.Pair;

import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AssemblyLexer {

    private static final Pattern RE_WHITESPACE      = Pattern.compile("^\\s+");
    private static final Pattern RE_MNEMONIC        = Pattern.compile("^([A-Z]{3})(?=\\s|$)");
    private static final Pattern RE_X               = Pattern.compile("^X");
    private static final Pattern RE_Y               = Pattern.compile("^Y");
    private static final Pattern RE_LABEL_DEF       = Pattern.compile("^([A-z][A-z0-9_]*):");
    private static final Pattern RE_LABEL_REF       = Pattern.compile("^([A-z][A-z0-9_]*)");
    private static final Pattern RE_DIRECTIVE       = Pattern.compile("^\\.([A-z]+)");
    private static final Pattern RE_HEX_QWORD       = Pattern.compile("^\\$([0-9A-F]{8})");
    private static final Pattern RE_HEX_DWORD       = Pattern.compile("^\\$([0-9A-F]{4})");
    private static final Pattern RE_HEX_WORD        = Pattern.compile("^\\$([0-9A-F]{2})");
    private static final Pattern RE_DEC_WORD        = Pattern.compile("^([0-9]{1,3})");
    private static final Pattern RE_BIN_QWORD       = Pattern.compile("^%([01]{32})");
    private static final Pattern RE_BIN_DWORD       = Pattern.compile("^%([01]{16})");
    private static final Pattern RE_BIN_WORD        = Pattern.compile("^%([01]{8})");
    private static final Pattern RE_COMMENT         = Pattern.compile("^;.*$");
    private static final Pattern RE_COMMA           = Pattern.compile("^,");
    private static final Pattern RE_POUND           = Pattern.compile("^#");
    private static final Pattern RE_LEFT_PAREN      = Pattern.compile("^\\(");
    private static final Pattern RE_RIGHT_PAREN     = Pattern.compile("^\\)");

    private static final ImmutableMap<Pattern, Token.Type> PATTERN_MAP = ImmutableMap.<Pattern, Token.Type>builder()
            .put(RE_COMMENT, Token.Type.COMMENT)
            .put(RE_MNEMONIC, Token.Type.MNEMONIC)
            .put(RE_X, Token.Type.X)
            .put(RE_Y, Token.Type.Y)
            .put(RE_LABEL_DEF, Token.Type.LABEL_DEF)
            .put(RE_LABEL_REF, Token.Type.LABEL_REF)
            .put(RE_DIRECTIVE, Token.Type.DIRECTIVE)
            .put(RE_HEX_QWORD, Token.Type.HEX_QWORD)
            .put(RE_HEX_DWORD, Token.Type.HEX_DWORD)
            .put(RE_HEX_WORD, Token.Type.HEX_WORD)
            .put(RE_DEC_WORD, Token.Type.DEC_WORD)
            .put(RE_BIN_QWORD, Token.Type.BIN_QWORD)
            .put(RE_BIN_DWORD, Token.Type.BIN_DWORD)
            .put(RE_BIN_WORD, Token.Type.BIN_WORD)
            .put(RE_COMMA, Token.Type.COMMA)
            .put(RE_POUND, Token.Type.POUND)
            .put(RE_LEFT_PAREN, Token.Type.LEFT_PAREN)
            .put(RE_RIGHT_PAREN, Token.Type.RIGHT_PAREN)
            .build();

    public static Optional<Pair<Token, Integer>> nextToken(String line, int pos, int lineNum) {
        int skipped = 0;

        String substr = line.substring(pos);

        Matcher wsMatcher = RE_WHITESPACE.matcher(substr);

        if (wsMatcher.find()) {
            skipped = wsMatcher.group(0).length();
            substr = substr.substring(skipped);
        }

        for (Map.Entry<Pattern, Token.Type> e : PATTERN_MAP.entrySet()) {
            Matcher m = e.getKey().matcher(substr);

            if (!m.find()) {
                continue;
            }

            int len = m.group(0).length();

            Object val;

            if (m.groupCount() > 0) {
                try {
                    val = e.getValue().adaptValue(m.group(1));
                } catch (Throwable t) {
                    throw new IllegalArgumentException(String.format("Failed to adapt value %s for token type %s.",
                            m.group(1), e.getValue().name()), t);
                }
            } else {
                val = null;
            }

            return Optional.of(Pair.of(new Token(e.getValue(), val, lineNum), len + skipped));
        }

        return Optional.empty();
    }

    public static List<Token> lex(InputStream input) throws IOException, LexerException {
        List<Token> tokens = new ArrayList<>();

        StringBuilder lineBuilder = new StringBuilder();

        int curLine = 1;

        int b;
        while ((b = input.read()) != -1) {

            if (b == '\n') {
                tokens.addAll(tokenize(lineBuilder.toString(), curLine));

                lineBuilder.setLength(0);

                curLine++;
            } else {
                lineBuilder.append((char) b);
            }
        }

        return tokens;
    }

    private static List<Token> tokenize(String line, int lineNum) throws LexerException {
        List<Token> tokens = new ArrayList<>();

        int pos = 0;

        while (pos < line.length()) {
            Optional<Pair<Token, Integer>> token;
            try {
                token = nextToken(line, pos, lineNum);
            } catch (Throwable t) {
                throw new LexerException(line, lineNum, pos, t);
            }

            if (!token.isPresent()) {
                throw new LexerException(line, lineNum, pos);
            }

            tokens.add(token.get().first());

            pos += token.get().second();
        }

        return tokens;
    }

}
