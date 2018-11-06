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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import net.caseif.moslib.AddressingMode;
import net.caseif.moslib.Mnemonic;
import net.caseif.mossy.util.OperatorType;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public abstract class Statement {

    private final Type type;
    private final int line;

    Statement(Type type, int line) {
        this.type = type;
        this.line = line;
    }

    public Type getType() {
        return type;
    }

    public int getLine() {
        return line;
    }

    public enum Type {
        INSTRUCTION(InstructionStatement.class),
        LABEL_DEF(LabelDefinitionStatement.class),
        NAMED_CONSTANT_DEF(ConstantDefinitionStatement.class),
        DIRECTIVE(DirectiveStatement.class),
        COMMENT(CommentStatement.class);

        private final Constructor<? extends Statement> ctor;

        Type(Class<? extends Statement> clazz) {
            try {
                this.ctor = clazz.getDeclaredConstructor(Statement.class, List.class, int.class);
            } catch (NoSuchMethodException ex) {
                throw new AssertionError(String.format(
                        "Supplied class %s for type %s does not have an appropriate constructor.",
                        clazz.getName(),
                        name()
                ));
            }
        }

        public Statement constructStatement(int line, List<TypedValue> values) {
            try {
                return ctor.newInstance(null, values, line);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getValue(List<TypedValue> values, ValueType type) {
        return (T) values.stream().filter(v -> v.getType() == type).map(TypedValue::getValue).findFirst().get();
    }

    private static boolean hasValue(List<TypedValue> values, ValueType type) {
        return values.stream().anyMatch(v -> v.getType() == type);
    }


    public class InstructionStatement extends Statement {

        private final Mnemonic mnemonic;
        private final AddressingMode addrMode;
        private final int operand;
        private final int operandLength;
        private final String constantRef;

        InstructionStatement(List<TypedValue> values, int line) {
            super(Type.INSTRUCTION, line);

            // value format is [mnemonic, (imm_value | const_ref | target)]
            // metadata format is [void, void, (imm_length | void | (addr_mode, operand_length))]

            checkArgument(hasValue(values, ValueType.MNEMONIC));

            this.mnemonic = getValue(values, ValueType.MNEMONIC);

            if (values.size() == 1) {
                // no opperand, just the bare mnemonic

                addrMode = AddressingMode.IMP;

                operand = 0;
                operandLength = 0;
                constantRef = null;
            } else {
                // operand was supplied

                // operand is a constant ref
                if (hasValue(values, ValueType.STRING_LITERAL)) {
                    // operand is a constant reference

                    constantRef = getValue(values, ValueType.STRING_LITERAL);

                    addrMode = mnemonic.getType() == Mnemonic.Type.BRANCH ? AddressingMode.REL : AddressingMode.ABS;

                    operand = 0;
                    operandLength = 0;

                } else if (hasValue(values, ValueType.IMM_LITERAL)) {
                    operand = getValue(values, ValueType.IMM_LITERAL);

                    if (hasValue(values, ValueType.ADDR_MODE)) {
                        // operand is a target

                        addrMode = getValue(values, ValueType.ADDR_MODE);

                        operandLength = 0;
                        constantRef = null;
                    } else {
                        // operand is an immediate value

                        operandLength = getValue(values, ValueType.OPERAND_SIZE);

                        addrMode = AddressingMode.IMM;
                        constantRef = null;
                    }
                } else {
                    throw new AssertionError(String.format("Unhandled case (%d values)", values.size()));
                }
            }
        }

        public Mnemonic getMnemonic() {
            return mnemonic;
        }

        public AddressingMode getAddressingMode() {
            return addrMode;
        }

        public int getOperand() {
            checkState(addrMode != AddressingMode.IMP, "Cannot get operand for implicit instruction.");

            return operand;
        }

        public int getOperandLength() {
            checkState(addrMode == AddressingMode.IMM, "Cannot get operand length for non-immediate instruction.");

            return operandLength;
        }

        public Optional<String> getConstantRef() {
            return Optional.ofNullable(constantRef);
        }

    }

    public class LabelDefinitionStatement extends Statement {

        private final String name;

        LabelDefinitionStatement(List<TypedValue> values, int line) {
            super(Type.LABEL_DEF, line);

            checkArgument(hasValue(values, ValueType.STRING_LITERAL));

            this.name = getValue(values, ValueType.STRING_LITERAL);
        }

        public String getName() {
            return name;
        }

    }

    public class ConstantDefinitionStatement extends Statement {

        private final String name;
        private final List<Object> values;
        private final List<Integer> sizes;
        private final List<OperatorType> operators;

        ConstantDefinitionStatement(List<TypedValue> values, int line) {
            super(Type.NAMED_CONSTANT_DEF, line);

            this.name = getValue(values, ValueType.STRING_LITERAL);

            this.values = new ArrayList<>();
            this.sizes = new ArrayList<>();
            this.operators = new ArrayList<>();

            for (int i = 1; i < values.size(); i++) {
                TypedValue v = values.get(i);
                switch (v.getType()) {
                    case MATH_OPERATOR:
                        this.operators.add((OperatorType) v.getValue());
                        break;
                    case OPERAND_SIZE:
                        this.sizes.add((int) v.getValue());
                        break;
                    case IMM_LITERAL:
                    case STRING_LITERAL:
                        this.values.add(v.getValue());
                        break;
                    default:
                        throw new AssertionError("Unhandled case " + v.getType().name());
                }
            }
        }

        public String getName() {
            return name;
        }

        public List<Object> getValues() {
            return values;
        }

        public List<Integer> getSizes() {
            return sizes;
        }

        public List<OperatorType> getOperators() {
            return operators;
        }

    }

    public class DirectiveStatement extends Statement {

        private final Directive type;
        private final Object param;

        DirectiveStatement(List<TypedValue> values, int line) {
            super(Type.DIRECTIVE, line);

            checkArgument(hasValue(values, ValueType.DIRECTIVE));

            this.type = getValue(values, ValueType.DIRECTIVE);

            if (values.size() > 1) {
                if (hasValue(values, ValueType.IMM_LITERAL)) {
                    param = getValue(values, ValueType.IMM_LITERAL);
                } else if (hasValue(values, ValueType.STRING_LITERAL)) {
                    param = getValue(values, ValueType.STRING_LITERAL);
                } else {
                    throw new AssertionError("Cannot find valid parameter for directive statement");
                }
            } else {
                this.param = null;
            }
        }

        public Directive getDirective() {
            return type;
        }

        public Optional<Object> getParam() {
            return Optional.ofNullable(param);
        }

    }

    public class CommentStatement extends Statement {

        CommentStatement(List<TypedValue> values, int line) {
            super(Type.COMMENT, line);
        }

    }

}
