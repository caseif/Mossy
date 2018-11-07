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

import static net.caseif.mossy.assembly.model.Token.Type.GREATER_THAN;
import static net.caseif.mossy.assembly.model.Token.Type.LESS_THAN;
import static net.caseif.mossy.assembly.model.TypedValue.of;
import static net.caseif.mossy.assembly.model.Token.Type.BIN_DWORD;
import static net.caseif.mossy.assembly.model.Token.Type.BIN_QWORD;
import static net.caseif.mossy.assembly.model.Token.Type.BIN_WORD;
import static net.caseif.mossy.assembly.model.Token.Type.COLON;
import static net.caseif.mossy.assembly.model.Token.Type.COMMA;
import static net.caseif.mossy.assembly.model.Token.Type.COMMENT;
import static net.caseif.mossy.assembly.model.Token.Type.DEC_WORD;
import static net.caseif.mossy.assembly.model.Token.Type.DIRECTIVE;
import static net.caseif.mossy.assembly.model.Token.Type.EQUALS;
import static net.caseif.mossy.assembly.model.Token.Type.HEX_DWORD;
import static net.caseif.mossy.assembly.model.Token.Type.HEX_QWORD;
import static net.caseif.mossy.assembly.model.Token.Type.HEX_WORD;
import static net.caseif.mossy.assembly.model.Token.Type.IDENTIFIER;
import static net.caseif.mossy.assembly.model.Token.Type.LEFT_PAREN;
import static net.caseif.mossy.assembly.model.Token.Type.MINUS;
import static net.caseif.mossy.assembly.model.Token.Type.MNEMONIC;
import static net.caseif.mossy.assembly.model.Token.Type.PLUS;
import static net.caseif.mossy.assembly.model.Token.Type.POUND;
import static net.caseif.mossy.assembly.model.Token.Type.RIGHT_PAREN;
import static net.caseif.mossy.assembly.model.Token.Type.X;
import static net.caseif.mossy.assembly.model.Token.Type.Y;
import static net.caseif.mossy.assembly.model.ValueType.ADDR_MODE;
import static net.caseif.mossy.assembly.model.ValueType.MODIFIER_IMM;
import static net.caseif.mossy.assembly.model.ValueType.MODIFIER_MASK_HI;
import static net.caseif.mossy.assembly.model.ValueType.MODIFIER_MASK_LO;
import static net.caseif.mossy.assembly.model.ValueType.OPERAND_SIZE;

import com.google.common.collect.ImmutableList;
import net.caseif.moslib.AddressingMode;
import net.caseif.mossy.assembly.model.Expression;
import net.caseif.mossy.assembly.model.ExpressionPart;
import net.caseif.mossy.assembly.model.TypedValue;
import net.caseif.mossy.assembly.model.Statement;
import net.caseif.mossy.assembly.model.Token;
import net.caseif.mossy.assembly.model.ValueType;
import net.caseif.mossy.util.exception.ParserException;
import net.caseif.mossy.util.tuple.Pair;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

public class AssemblyParser {

    private static final Map<Expression.TypeWithMetadata, Set<ImmutableList<ExpressionPart>>> EXPRESSION_SYNTAXES = new LinkedHashMap<>();
    private static final Map<Statement.Type, Set<ImmutableList<Expression.Type>>> STATEMENT_SYNTAXES = new LinkedHashMap<>();

    static {
        addExpressionSyntax(Expression.Type.COMMENT,                        COMMENT);

        addExpressionSyntax(Expression.Type.MNEMONIC,                       MNEMONIC);

        addExpressionSyntax(Expression.Type.LABEL_DEF,                      IDENTIFIER, COLON);

        addExpressionSyntax(Expression.Type.NAMED_CONSTANT_DEF,             IDENTIFIER, EQUALS, Expression.Type.CONSTANT);

        addExpressionSyntax(Expression.Type.DIRECTIVE,                      DIRECTIVE);

        addExpressionSyntax(Expression.Type.QWORD, of(OPERAND_SIZE, 4),     HEX_QWORD);
        addExpressionSyntax(Expression.Type.QWORD, of(OPERAND_SIZE, 4),     BIN_QWORD);

        addExpressionSyntax(Expression.Type.DWORD, of(OPERAND_SIZE, 2),     HEX_DWORD);
        addExpressionSyntax(Expression.Type.DWORD, of(OPERAND_SIZE, 2),     BIN_DWORD);

        addExpressionSyntax(Expression.Type.WORD,  of(OPERAND_SIZE, 1),     HEX_WORD);
        addExpressionSyntax(Expression.Type.WORD,  of(OPERAND_SIZE, 1),     DEC_WORD);
        addExpressionSyntax(Expression.Type.WORD,  of(OPERAND_SIZE, 1),     BIN_WORD);

        addExpressionSyntax(Expression.Type.WORD,  of(OPERAND_SIZE, 1),     Expression.Type.MASK, Expression.Type.DWORD);

        addExpressionSyntax(Expression.Type.MASK, of(MODIFIER_MASK_HI, 1),  GREATER_THAN);
        addExpressionSyntax(Expression.Type.MASK, of(MODIFIER_MASK_LO, 1),  LESS_THAN);

        addExpressionSyntax(Expression.Type.TARGET, of(ADDR_MODE, AddressingMode.ABX),  Expression.Type.DWORD, COMMA, X);
        addExpressionSyntax(Expression.Type.TARGET, of(ADDR_MODE, AddressingMode.ABY),  Expression.Type.DWORD, COMMA, Y);
        addExpressionSyntax(Expression.Type.TARGET, of(ADDR_MODE, AddressingMode.ZPX),  Expression.Type.WORD, COMMA, X);
        addExpressionSyntax(Expression.Type.TARGET, of(ADDR_MODE, AddressingMode.ZPY),  Expression.Type.WORD, COMMA, Y);
        addExpressionSyntax(Expression.Type.TARGET, of(ADDR_MODE, AddressingMode.ABS),  Expression.Type.DWORD);
        addExpressionSyntax(Expression.Type.TARGET, of(ADDR_MODE, AddressingMode.ZRP),  Expression.Type.WORD);
        addExpressionSyntax(Expression.Type.TARGET, of(ADDR_MODE, AddressingMode.IND),  LEFT_PAREN, Expression.Type.DWORD, RIGHT_PAREN);
        addExpressionSyntax(Expression.Type.TARGET, of(ADDR_MODE, AddressingMode.IZX),  LEFT_PAREN, Expression.Type.WORD, COMMA, X, RIGHT_PAREN);
        addExpressionSyntax(Expression.Type.TARGET, of(ADDR_MODE, AddressingMode.IZY),  LEFT_PAREN, Expression.Type.WORD, RIGHT_PAREN, COMMA, Y);

        addExpressionSyntax(Expression.Type.NUMBER, of(OPERAND_SIZE, 4),    Expression.Type.QWORD);
        addExpressionSyntax(Expression.Type.NUMBER, of(OPERAND_SIZE, 2),    Expression.Type.DWORD);
        addExpressionSyntax(Expression.Type.NUMBER, of(OPERAND_SIZE, 1),    Expression.Type.WORD);

        addExpressionSyntax(Expression.Type.ARITHMETIC_OPERATOR,            PLUS);
        addExpressionSyntax(Expression.Type.ARITHMETIC_OPERATOR,            MINUS);

        addExpressionSyntax(Expression.Type.CONSTANT,                       IDENTIFIER, Expression.Type.ARITHMETIC_OPERATOR, Expression.Type.CONSTANT);
        addExpressionSyntax(Expression.Type.CONSTANT,                       Expression.Type.NUMBER, Expression.Type.ARITHMETIC_OPERATOR, Expression.Type.CONSTANT);
        addExpressionSyntax(Expression.Type.CONSTANT,                       IDENTIFIER);
        addExpressionSyntax(Expression.Type.CONSTANT,                       Expression.Type.NUMBER);
        addExpressionSyntax(Expression.Type.CONSTANT,                       Expression.Type.MASK, Expression.Type.CONSTANT);

        //addExpressionSyntax(Expression.Type.IMM_VALUE,                      POUND, Expression.Type.WORD);
        addExpressionSyntax(Expression.Type.IMM_VALUE, of(MODIFIER_IMM, 1), POUND, Expression.Type.CONSTANT);

        addStatementSyntax(Statement.Type.COMMENT,                          Expression.Type.COMMENT);
        addStatementSyntax(Statement.Type.LABEL_DEF,                        Expression.Type.LABEL_DEF);
        addStatementSyntax(Statement.Type.NAMED_CONSTANT_DEF,               Expression.Type.NAMED_CONSTANT_DEF);
        addStatementSyntax(Statement.Type.DIRECTIVE,                        Expression.Type.DIRECTIVE, Expression.Type.CONSTANT);
        addStatementSyntax(Statement.Type.DIRECTIVE,                        Expression.Type.DIRECTIVE);
        addStatementSyntax(Statement.Type.INSTRUCTION,                      Expression.Type.MNEMONIC, Expression.Type.IMM_VALUE);
        addStatementSyntax(Statement.Type.INSTRUCTION,                      Expression.Type.MNEMONIC, Expression.Type.TARGET);
        addStatementSyntax(Statement.Type.INSTRUCTION,                      Expression.Type.MNEMONIC, Expression.Type.CONSTANT);
        addStatementSyntax(Statement.Type.INSTRUCTION,                      Expression.Type.MNEMONIC);
    }

    private static void addExpressionSyntax(Expression.Type expr, @Nullable TypedValue metadata, ExpressionPart... pattern) {
        EXPRESSION_SYNTAXES.computeIfAbsent(
                Expression.TypeWithMetadata.of(expr, metadata),
                k -> new LinkedHashSet<>()
        ).add(ImmutableList.copyOf(pattern));
    }

    private static void addExpressionSyntax(Expression.Type expr, ExpressionPart... pattern) {
        EXPRESSION_SYNTAXES.computeIfAbsent(
                Expression.TypeWithMetadata.of(expr),
                k -> new LinkedHashSet<>()
        ).add(ImmutableList.copyOf(pattern));
    }

    private static void addStatementSyntax(Statement.Type stmt, Expression.Type... pattern) {
        STATEMENT_SYNTAXES.computeIfAbsent(
                stmt,
                k -> new LinkedHashSet<>()
        ).add(ImmutableList.copyOf(pattern));
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
        System.out.println("next token: " + curTokens.get(0).getType());
        if (curTokens.size() > 1) {
            System.out.println("next next token: " + curTokens.get(1).getType());
        }

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
        System.out.println("Trying statement " + goal + " with pattern " + pattern);

        // track the token count so we can return it to the caller
        int tokenCount = 0;

        int line = -1;

        // values obtained from the expressions constituating the statement
        List<TypedValue> values = new ArrayList<>();

        for (Expression.Type nextExpr : pattern) {
            Optional<Pair<Expression, Integer>> res = matchExpression(curTokens, nextExpr);

            // if empty, this pattern doesn't work
            if (!res.isPresent()) {
                return Optional.empty();
            }

            // add the values from the expression we found
            if (res.get().first().getValues() != null) {
                values.addAll(res.get().first().getValues());
            }

            // add the metadata value too since we need it later
            if (res.get().first().getType().getMetadata() != null) {
                values.addAll(res.get().first().getType().getMetadata());
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

        return Optional.of(Pair.of(goal.constructStatement(line, values), tokenCount));
    }

    // matches a token list against a specific expression

    private static Optional<Pair<Expression, Integer>> matchExpression(List<Token> curTokens, Expression.Type goal) {
        // we have to do it this way since the map stores types _with metadata_ as keys
        for (Map.Entry<Expression.TypeWithMetadata, Set<ImmutableList<ExpressionPart>>> e : EXPRESSION_SYNTAXES.entrySet()) {
            if (e.getKey().getType() != goal) {
                // skip since it's the wrong type
                continue;
            }

            for (ImmutableList<ExpressionPart> pattern : e.getValue()) {
                Optional<Pair<Expression, Integer>> res = matchExpressionWithPattern(curTokens, e.getKey(), pattern);

                if (res.isPresent()) {
                    if (goal == Expression.Type.ARITHMETIC_OPERATOR) {
                        System.out.println("GOAL: " + res.get().first().getValues());
                    }
                    return res;
                }
            }
        }

        // we didn't find any valid expressions
        return Optional.empty();
    }
    // matches a token list against a specific expression AND pattern

    private static Optional<Pair<Expression, Integer>> matchExpressionWithPattern(List<Token> curTokens,
            Expression.TypeWithMetadata goal, List<ExpressionPart> pattern) {
        System.out.println("  Trying expression " + goal.getType() + "(" + goal.getMetadata() + ") with pattern " + pattern);

        // track the token count so we can return it to the caller
        int tokenCount = 0;

        List<TypedValue> values = new ArrayList<>(goal.getMetadata());

        int line = -1;

        if (curTokens.size() < pattern.size()) {
            System.out.println("Too small, failing");

            // not enough tokens to fulfill the pattern so just fail
            return Optional.empty();
        }

        for (ExpressionPart nextPart : pattern) {
            if (nextPart instanceof Token.Type) {
                // if the next token isn't what we expect, then the pattern fails
                if (curTokens.get(0).getType() != nextPart) {
                    System.out.println("    Failed on token " + curTokens.get(0).getType());
                    return Optional.empty();
                }

                System.out.println("    Matched token " + nextPart + "(" + curTokens.get(0).getValue().orElse(null) + ")");

                // set the value, if applicable
                if (curTokens.get(0).getType().getValueType() != ValueType.EMPTY) {
                    values.add(TypedValue.of(curTokens.get(0).getType().getValueType(), curTokens.get(0).getValue().get()));
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
                Optional<Pair<Expression, Integer>> res = matchExpression(curTokens, (Expression.Type) nextPart);

                // if we can't match the expression, the pattern fails
                if (!res.isPresent()) {
                    return Optional.empty();
                }

                // set the value, if applicable
                values.addAll(res.get().first().getValues());

                // append the child's metadata
                values.addAll(res.get().first().getType().getMetadata());

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

        System.out.println("    Line " + line + ": Matched expression " + goal.getType().name() + "(md:" + goal.getMetadata() + ")(v:" + values + ")");

        return Optional.of(Pair.of(new Expression(Expression.TypeWithMetadata.of(goal.getType()), values, line), tokenCount));
    }
}
