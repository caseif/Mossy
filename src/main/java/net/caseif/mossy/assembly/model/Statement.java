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
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

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
                this.ctor = clazz.getDeclaredConstructor(Statement.class, Map.class, int.class);
            } catch (NoSuchMethodException ex) {
                throw new AssertionError(String.format(
                        "Supplied class %s for type %s does not have an appropriate constructor.",
                        clazz.getName(),
                        name()
                ));
            }
        }

        public Statement constructStatement(int line, Map<ValueType, Object> values) {
            try {
                return ctor.newInstance(null, values, line);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public class InstructionStatement extends Statement {

        private final Mnemonic mnemonic;
        private final AddressingMode addrMode;
        private final int operand;
        private final int operandLength;
        private final String constantRef;

        InstructionStatement(Map<ValueType, Object> values, int line) {
            super(Type.INSTRUCTION, line);

            // value format is [mnemonic, (imm_value | const_ref | target)]
            // metadata format is [void, void, (imm_length | void | (addr_mode, operand_length))]

            checkArgument(values.containsKey(ValueType.MNEMONIC));

            this.mnemonic = (Mnemonic) values.get(ValueType.MNEMONIC);

            if (values.size() == 1) {
                // no opperand, just the bare mnemonic

                addrMode = AddressingMode.IMP;

                operand = 0;
                operandLength = 0;
                constantRef = null;
            } else {
                // operand was supplied

                // operand is a constant ref
                if (values.containsKey(ValueType.STRING_LITERAL)) {
                    // operand is a constant reference

                    constantRef = (String) values.get(ValueType.STRING_LITERAL);

                    addrMode = mnemonic.getType() == Mnemonic.Type.BRANCH ? AddressingMode.REL : AddressingMode.ABS;

                    operand = 0;
                    operandLength = 0;

                } else if (values.containsKey(ValueType.IMM_LITERAL)) {
                    operand = (int) values.get(ValueType.IMM_LITERAL);

                    if (values.containsKey(ValueType.ADDR_MODE)) {
                        // operand is a target

                        addrMode = (AddressingMode) values.get(ValueType.ADDR_MODE);

                        operandLength = 0;
                        constantRef = null;
                    } else {
                        // operand is an immediate value

                        operandLength = (int) values.get(ValueType.OPERAND_SIZE);

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

        LabelDefinitionStatement(Map<ValueType, Object> values, int line) {
            super(Type.LABEL_DEF, line);

            checkArgument(values.containsKey(ValueType.STRING_LITERAL));

            this.name = (String) values.get(ValueType.STRING_LITERAL);
        }

        public String getName() {
            return name;
        }

    }

    public class ConstantDefinitionStatement extends Statement {

        private final String name;
        private final Object[] values;
        private final int[] sizes;
        private final OperatorType[] operators;

        ConstantDefinitionStatement(Map<ValueType, Object> values, int line) {
            super(Type.NAMED_CONSTANT_DEF, line);

            this.name = (String) values.get(ValueType.STRING_LITERAL);

            int valueCount = (values.size() - 1) / 2;

            this.values = new Object[valueCount];
            this.sizes = new int[valueCount];
            this.operators = new OperatorType[valueCount - 1];

            //TODO: big ol' TODO
            /*for (int i = 0; i < valueCount; i++) {
                int offset = 1;

                this.values[i] = values[i * 3 + offset++];
                if (i != valueCount - 1) {
                    this.operators[i] = (OperatorType) values[i * 3 + offset++];
                }
                // metadata comes last
                this.sizes[i] = (int) values[i * 3 + offset];
            }*/
        }

        public String getName() {
            return name;
        }

        public Object[] getValues() {
            return values;
        }

        public int[] getSizes() {
            return sizes;
        }

        public OperatorType[] getOperators() {
            return operators;
        }

    }

    public class DirectiveStatement extends Statement {

        private final Directive type;
        private final Object param;

        DirectiveStatement(Map<ValueType, Object> values, int line) {
            super(Type.DIRECTIVE, line);

            checkArgument(values.containsKey(ValueType.DIRECTIVE));

            this.type = (Directive) values.get(ValueType.DIRECTIVE);

            if (values.size() > 1) {
                if (values.containsKey(ValueType.IMM_LITERAL)) {
                    param = values.get(ValueType.IMM_LITERAL);
                } else if (values.containsKey(ValueType.STRING_LITERAL)) {
                    param = values.get(ValueType.STRING_LITERAL);
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

        CommentStatement(Map<ValueType, Object> values, int line) {
            super(Type.COMMENT, line);
        }

    }

}
