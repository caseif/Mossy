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

import static net.caseif.mossy.assembly.model.Token.Type.BIN_DWORD;
import static net.caseif.mossy.assembly.model.Token.Type.BIN_QWORD;
import static net.caseif.mossy.assembly.model.Token.Type.BIN_WORD;
import static net.caseif.mossy.assembly.model.Token.Type.COMMA;
import static net.caseif.mossy.assembly.model.Token.Type.COMMENT;
import static net.caseif.mossy.assembly.model.Token.Type.DEC_WORD;
import static net.caseif.mossy.assembly.model.Token.Type.DIRECTIVE;
import static net.caseif.mossy.assembly.model.Token.Type.HEX_DWORD;
import static net.caseif.mossy.assembly.model.Token.Type.HEX_QWORD;
import static net.caseif.mossy.assembly.model.Token.Type.HEX_WORD;
import static net.caseif.mossy.assembly.model.Token.Type.LABEL_DEF;
import static net.caseif.mossy.assembly.model.Token.Type.LABEL_REF;
import static net.caseif.mossy.assembly.model.Token.Type.LEFT_PAREN;
import static net.caseif.mossy.assembly.model.Token.Type.MNEMONIC;
import static net.caseif.mossy.assembly.model.Token.Type.POUND;
import static net.caseif.mossy.assembly.model.Token.Type.RIGHT_PAREN;
import static net.caseif.mossy.assembly.model.Token.Type.X;
import static net.caseif.mossy.assembly.model.Token.Type.Y;

import com.google.common.collect.ImmutableList;
import net.caseif.moslib.AddressingMode;
import net.caseif.mossy.assembly.model.Expression;
import net.caseif.mossy.assembly.model.ExpressionPart;
import net.caseif.mossy.assembly.model.Statement;
import net.caseif.mossy.assembly.model.Token;
import net.caseif.mossy.util.exception.ParserException;
import net.caseif.mossy.util.tuple.Pair;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class AssemblyParser {

    private static final Map<Expression.TypeWithMetadata<?>, Set<ImmutableList<ExpressionPart>>> EXPRESSION_SYNTAXES = new LinkedHashMap<>();
    private static final Map<Statement.Type, Set<ImmutableList<Expression.Type>>> STATEMENT_SYNTAXES = new LinkedHashMap<>();

    static {
        addExpressionSyntax(Expression.Type.COMMENT,                        COMMENT);

        addExpressionSyntax(Expression.Type.MNEMONIC,                       MNEMONIC);

        addExpressionSyntax(Expression.Type.LABEL_DEF,                      LABEL_DEF);

        addExpressionSyntax(Expression.Type.LABEL_REF,                      LABEL_REF);

        addExpressionSyntax(Expression.Type.DIRECTIVE,                      DIRECTIVE);

        addExpressionSyntax(Expression.Type.QWORD, 4,                       HEX_QWORD);
        addExpressionSyntax(Expression.Type.QWORD, 4,                       BIN_QWORD);

        addExpressionSyntax(Expression.Type.DWORD, 2,                       HEX_DWORD);
        addExpressionSyntax(Expression.Type.DWORD, 2,                       BIN_DWORD);

        addExpressionSyntax(Expression.Type.WORD,  1,                       HEX_WORD);
        addExpressionSyntax(Expression.Type.WORD,  1,                       DEC_WORD);
        addExpressionSyntax(Expression.Type.WORD,  1,                       BIN_WORD);

        addExpressionSyntax(Expression.Type.TARGET, AddressingMode.ABX,     Expression.Type.DWORD, COMMA, X);
        addExpressionSyntax(Expression.Type.TARGET, AddressingMode.ABY,     Expression.Type.DWORD, COMMA, Y);
        addExpressionSyntax(Expression.Type.TARGET, AddressingMode.ABS,     Expression.Type.DWORD);
        addExpressionSyntax(Expression.Type.TARGET, AddressingMode.ZPX,     Expression.Type.WORD, COMMA, X);
        addExpressionSyntax(Expression.Type.TARGET, AddressingMode.ZPY,     Expression.Type.WORD, COMMA, Y);
        addExpressionSyntax(Expression.Type.TARGET, AddressingMode.ZRP,     Expression.Type.WORD);
        addExpressionSyntax(Expression.Type.TARGET, AddressingMode.IND,     LEFT_PAREN, Expression.Type.DWORD, RIGHT_PAREN);
        addExpressionSyntax(Expression.Type.TARGET, AddressingMode.IZX,     LEFT_PAREN, Expression.Type.WORD, COMMA, X, RIGHT_PAREN);
        addExpressionSyntax(Expression.Type.TARGET, AddressingMode.IZY,     LEFT_PAREN, Expression.Type.WORD, RIGHT_PAREN, COMMA, Y);

        addExpressionSyntax(Expression.Type.NUMBER,                         Expression.Type.QWORD);
        addExpressionSyntax(Expression.Type.NUMBER,                         Expression.Type.DWORD);
        addExpressionSyntax(Expression.Type.NUMBER,                         Expression.Type.WORD);

        addExpressionSyntax(Expression.Type.IMM_VALUE,                      POUND, Expression.Type.WORD);

        addExpressionSyntax(Expression.Type.CONSTANT,                       Expression.Type.NUMBER);
        addExpressionSyntax(Expression.Type.CONSTANT,                       Expression.Type.LABEL_REF);

        addStatementSyntax(Statement.Type.COMMENT,                          Expression.Type.COMMENT);
        addStatementSyntax(Statement.Type.LABEL_DEF,                        Expression.Type.LABEL_DEF);
        addStatementSyntax(Statement.Type.DIRECTIVE,                        Expression.Type.DIRECTIVE, Expression.Type.CONSTANT);
        addStatementSyntax(Statement.Type.DIRECTIVE,                        Expression.Type.DIRECTIVE);
        addStatementSyntax(Statement.Type.INSTRUCTION,                      Expression.Type.MNEMONIC, Expression.Type.IMM_VALUE);
        addStatementSyntax(Statement.Type.INSTRUCTION,                      Expression.Type.MNEMONIC, Expression.Type.LABEL_REF);
        addStatementSyntax(Statement.Type.INSTRUCTION,                      Expression.Type.MNEMONIC, Expression.Type.TARGET);
        addStatementSyntax(Statement.Type.INSTRUCTION,                      Expression.Type.MNEMONIC);
    }

    private static void addExpressionSyntax(Expression.Type expr, Object metadata, ExpressionPart... pattern) {
        EXPRESSION_SYNTAXES.computeIfAbsent(
                Expression.TypeWithMetadata.of(expr, metadata),
                k -> new LinkedHashSet<>()).add(ImmutableList.copyOf(pattern)
        );
    }

    private static void addExpressionSyntax(Expression.Type expr, ExpressionPart... pattern) {
        addExpressionSyntax(expr, null, pattern);
    }

    private static void addStatementSyntax(Statement.Type stmt, Expression.Type... pattern) {
        STATEMENT_SYNTAXES.computeIfAbsent(stmt, k -> new LinkedHashSet<>()).add(ImmutableList.copyOf(pattern));
    }

    public static List<Statement> parse(List<Token> tokens) throws ParserException {
        List<Statement> stmts = new ArrayList<>();

        while (tokens.size() > 0) {
            // try to match the next available statement
            Pair<Statement, Integer> res = matchNextStatement(tokens);

            // add it to the list
            stmts.add(res.first());

            // shift the head of the token list past the ones we've already parsed
            tokens = tokens.subList(res.second(), tokens.size());
        }

        return stmts;
    }

    // matches whatever statement can be found next
    private static Pair<Statement, Integer> matchNextStatement(List<Token> curTokens) throws ParserException {
        for (Statement.Type goal : STATEMENT_SYNTAXES.keySet()) {
            // try to match against a specific goal
            Optional<Pair<Statement, Integer>> res = matchStatement(curTokens, goal);

            // check if we found a valid statement
            if (res.isPresent()) {
                return res.get();
            }
        }

        // no statements matched
        throw new ParserException("Failed to match any statement.", curTokens.get(0).getLine());
    }

    // matches a token list against a specific statement type
    private static Optional<Pair<Statement, Integer>> matchStatement(List<Token> curTokens, Statement.Type goal) {
        for (List<Expression.Type> pattern : STATEMENT_SYNTAXES.get(goal)) {
            // try to match against a specific pattern specified by this goal
            Optional<Pair<Statement, Integer>> res = matchStatementWithPattern(curTokens, goal, pattern);

            // check if we found a valid statement
            if (res.isPresent()) {
                return res;
            }
        }

        // we didn't find anything valid so just return empty
        return Optional.empty();
    }

    // matches a token list against a specific statement type AND pattern
    private static Optional<Pair<Statement, Integer>> matchStatementWithPattern(List<Token> curTokens,
            Statement.Type goal, List<Expression.Type> pattern) {
        // track the token count so we can return it to the caller
        int tokenCount = 0;

        int line = -1;

        // values obtained from the expressions constituating the statement
        List<Object> values = new ArrayList<>();

        for (Expression.Type nextExpr : pattern) {
            Optional<Pair<Expression<?>, Integer>> res = matchExpression(curTokens, nextExpr);

            // if empty, this pattern doesn't work
            if (!res.isPresent()) {
                return Optional.empty();
            }

            // add the values from the expression we found
            if (res.get().first().getValue() != null) {
                values.add(res.get().first().getValue());
            }

            // add the metadata value too since we need it later
            if (res.get().first().getType().getMetadata() != null) {
                values.add(res.get().first().getType().getMetadata());
            }

            // set the statement's line number if we haven't already
            if (line == -1) {
                line = res.get().first().getLine();
            }

            // update the token count
            tokenCount += res.get().second();
            // adjust the token list to account for the ones we just consumed
            curTokens = curTokens.subList(res.get().second(), curTokens.size());
        }

        System.out.println("Line " + line + ": Matched statement " + goal.name() + pattern + "(v:" + values + ")");

        return Optional.of(Pair.of(goal.constructStatement(line, values.toArray()), tokenCount));
    }

    // matches a token list against a specific expression
    private static Optional<Pair<Expression<?>, Integer>> matchExpression(List<Token> curTokens, Expression.Type goal) {
        // we have to do it this way since the map stores types _with metadata_ as keys
        for (Map.Entry<Expression.TypeWithMetadata<?>, Set<ImmutableList<ExpressionPart>>> e : EXPRESSION_SYNTAXES.entrySet()) {
            if (e.getKey().getType() != goal) {
                // skip since it's the wrong type
                continue;
            }

            for (ImmutableList<ExpressionPart> pattern : e.getValue()) {
                Optional<Pair<Expression<?>, Integer>> res = matchExpressionWithPattern(curTokens, e.getKey(), pattern);

                if (res.isPresent()) {
                    return res;
                }
            }
        }

        // we didn't find any valid expressions
        return Optional.empty();
    }

    // matches a token list against a specific expression AND pattern
    private static Optional<Pair<Expression<?>, Integer>> matchExpressionWithPattern(List<Token> curTokens,
            Expression.TypeWithMetadata<?> goal, List<ExpressionPart> pattern) {
        // track the token count so we can return it to the caller
        int tokenCount = 0;

        Object value = null;

        int line = -1;

        for (ExpressionPart nextPart : pattern) {
            if (nextPart instanceof Token.Type) {
                // if the next token isn't what we expect, then the pattern fails
                if (curTokens.get(0).getType() != nextPart) {
                    return Optional.empty();
                }

                // set the value, if applicable
                if (curTokens.get(0).getValue().isPresent()) {
                    value = curTokens.get(0).getValue().get();
                }

                // set the expression's line number if we haven't already
                if (line == -1) {
                    line = curTokens.get(0).getLine();
                }

                // increment the token count since we just consumed the head
                tokenCount++;
                // update the token list as well
                curTokens = curTokens.subList(1, curTokens.size());
            } else { // it's a recursive expression
                Optional<Pair<Expression<?>, Integer>> res = matchExpression(curTokens, (Expression.Type) nextPart);

                // if we can't match the expression, the pattern fails
                if (!res.isPresent()) {
                    return Optional.empty();
                }

                // set the value, if applicable
                if (res.get().first().getValue() != null) {
                    value = res.get().first().getValue();
                }

                // try to inherit the child's metadata
                if (goal.getMetadata() == null && res.get().first().getType().getMetadata() != null) {
                    goal  = res.get().first().getType();
                }

                // set the expression's line number if we haven't already
                if (line == -1) {
                    line = res.get().first().getLine();
                }

                // update the token count to account for however many we just consumed
                tokenCount += res.get().second();
                // update the token list as well
                curTokens = curTokens.subList(res.get().second(), curTokens.size());
            }
        }

        System.out.println("Line " + line + ": Matched expression " + goal.getType().name() + "(md:" + goal.getMetadata() + ")(v:" + value + ")");

        return Optional.of(Pair.of(new Expression<>(goal, value, line), tokenCount));
    }

}
