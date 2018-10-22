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

import static com.google.common.base.Preconditions.checkState;

import net.caseif.moslib.AddressingMode;
import net.caseif.moslib.Mnemonic;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
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
                this.ctor = clazz.getDeclaredConstructor(Statement.class, Object[].class, int.class);
            } catch (NoSuchMethodException ex) {
                throw new AssertionError(String.format(
                        "Supplied class %s for type %s does not have an appropriate constructor.",
                        clazz.getName(),
                        name()
                ));
            }
        }

        public Statement constructStatement(int line, Object... values) {
            try {
                return ctor.newInstance(null, (Object[]) values, line);
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

        InstructionStatement(Object[] values, int line) {
            super(Type.INSTRUCTION, line);

            this.mnemonic = (Mnemonic) values[0];

            System.out.println(Arrays.toString(values));

            if (values.length == 3) {
                operand = (int) values[1];

                if (values[2] instanceof AddressingMode) {
                    addrMode = (AddressingMode) values[2];

                    operandLength = 0;
                    constantRef = null;
                } else {
                    operandLength = (int) values[2];

                    addrMode = AddressingMode.IMM;
                    constantRef = null;
                }
            } else if (values.length == 2) {
                constantRef = (String) values[1];

                addrMode = mnemonic.getType() == Mnemonic.Type.BRANCH ? AddressingMode.REL : AddressingMode.ABS;

                operand = 0;
                operandLength = 0;
            } else {
                addrMode = AddressingMode.IMP;

                operand = 0;
                operandLength = 0;
                constantRef = null;
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

        LabelDefinitionStatement(Object[] values, int line) {
            super(Type.LABEL_DEF, line);

            this.name = (String) values[0];
        }

        public String getName() {
            return name;
        }

    }

    public class ConstantDefinitionStatement extends Statement {

        private final String name;
        private final int value;
        private final int size;

        ConstantDefinitionStatement(Object[] values, int line) {
            super(Type.NAMED_CONSTANT_DEF, line);

            this.name = (String) values[0];
            this.value = (int) values[1];
            this.size = (int) values[2];
        }

        public String getName() {
            return name;
        }

        public int getValue() {
            return value;
        }

        public int getSize() {
            return size;
        }

    }

    public class DirectiveStatement extends Statement {

        private final Directive type;
        private final Object param;

        DirectiveStatement(Object[] values, int line) {
            super(Type.DIRECTIVE, line);

            this.type = (Directive) values[0];

            if (values.length > 1) {
                this.param = values[1];
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

        CommentStatement(Object[] values, int line) {
            super(Type.COMMENT, line);
        }

    }

}
