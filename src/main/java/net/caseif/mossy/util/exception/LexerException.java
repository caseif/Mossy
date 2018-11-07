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

package net.caseif.mossy.util.exception;

public class LexerException extends InvalidAssemblyException {

    public LexerException(String line, int lineNum, int col, Throwable cause) {
        super(String.format("Failed to lex assembly near %d,%d.\n\n%s", lineNum, col, printProblemLine(line, col)),
                cause, lineNum);
    }

    public LexerException(String line, int lineNum, int col) {
        this(line, lineNum, col, null);
    }

    private static String printProblemLine(String line, int col) {
        StringBuilder sb = new StringBuilder();

        sb.append("    ").append(line).append('\n');

        sb.append("    ");

        for (int i = 0; i <= col; i++) {
            sb.append("~");
        }

        sb.append("^");

        for (int i = col + 2; i < line.length(); i++) {
            sb.append("~");
        }

        return sb.toString();
    }
}
