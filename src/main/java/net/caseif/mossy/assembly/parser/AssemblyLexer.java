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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AssemblyLexer {

    private static final Pattern RE_WHITESPACE      = Pattern.compile("^\\s+");

    public static Optional<Pair<Token, Integer>> nextToken(String line, int pos, int lineNum) {
        int skipped = 0;

        String substr = line.substring(pos);

        Matcher wsMatcher = RE_WHITESPACE.matcher(substr);

        if (wsMatcher.find()) {
            skipped = wsMatcher.group(0).length();
            substr = substr.substring(skipped);
        }

        for (Token.Type token : Token.Type.values()) {
            Matcher m = token.getRegex().matcher(substr);

            if (!m.find()) {
                continue;
            }

            int len = m.group(0).length();

            String valueStr = m.groupCount() > 0 ? m.group(1) : null;

            return Optional.of(Pair.of(token.createToken(valueStr, lineNum), len + skipped));
        }

        return Optional.empty();
    }

    public static List<List<Token>> lex(InputStream input) throws IOException, LexerException {
        List<List<Token>> tokens = new ArrayList<>();

        StringBuilder lineBuilder = new StringBuilder();

        int curLine = 1;

        int b;
        while ((b = input.read()) != -1) {
            if (b == '\n') {
                List<Token> line = new ArrayList<>(tokenize(lineBuilder.toString(), curLine));

                if (!line.isEmpty()) {
                    tokens.add(line);
                }

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
