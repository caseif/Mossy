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

import net.caseif.mossy.util.OperatorType;
import net.caseif.mossy.util.exception.AssemblerException;
import net.caseif.mossy.util.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConstantFormula {

    private final int line;

    private final List<Object> values;
    private final List<Integer> sizes;
    private final List<MaskType> masks;
    private final List<OperatorType> operators;

    private ConstantFormula(List<Object> values, List<Integer> sizes, List<MaskType> masks, List<OperatorType> operators, int line) {
        this.values = values;
        this.sizes = sizes;
        this.masks = masks;
        this.operators = operators;
        this.line = line;
    }

    public static ConstantFormula fromStatementParams(List<TypedValue> paramList, int line) {
        List<Object> values = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        List<MaskType> masks = new ArrayList<>();
        List<OperatorType> operators = new ArrayList<>();

        int index = 0;

        for (int i = 1; i < paramList.size(); i++) {
            TypedValue v = paramList.get(i);
            switch (v.getType()) {
                case MATH_OPERATOR:
                    operators.add((OperatorType) v.getValue());
                    index++;
                    break;
                case OPERAND_SIZE:
                    extend(sizes, index);
                    sizes.add(index, (int) v.getValue());
                    break;
                case NUMBER_LITERAL:
                case STRING_LITERAL:
                    extend(values, index);
                    values.add(index, v.getValue());
                    break;
                case MASK:
                    extend(masks, index);
                    masks.add(index, (MaskType) v.getValue());
                    break;
                default:
                    break; // just skip
            }
        }

        return new ConstantFormula(values, sizes, masks, operators, line);
    }

    public List<Object> getValues() {
        return values;
    }

    public List<Integer> getSizes() {
        return sizes;
    }

    public List<MaskType> getMasks() {
        return masks;
    }

    public List<OperatorType> getOperators() {
        return operators;
    }

    public Pair<Integer, Integer> resolve(Map<String, NamedConstant> constants) throws AssemblerException {
        int result = 0;
        int maxSize = 0;

        for (int i = 0; i < values.size(); i++) {
            Object val = values.get(i);
            int size;
            MaskType mask = i < masks.size() ? masks.get(i) : null;

            int resolved;

            if (val instanceof Integer) {
                resolved = (int) val;
                size = sizes.get(i);
            } else if (val instanceof String) {
                if (!constants.containsKey(val)) {
                    System.out.println(constants);
                    throw new AssemblerException("Reference to undefined constant " + val + ".", line);
                }

                resolved = constants.get(val).getValue();
                size = constants.get(val).getSize();
            } else {
                throw new AssertionError("Unhandled case " + val.getClass().getName() + ".");
            }

            if (mask != null) {
                if (mask == MaskType.LOW) {
                    resolved &= 0xFF;
                } else if (mask == MaskType.HIGH) {
                    resolved >>= 8;
                }

                size = 1;
            }

            if (i == 0) {
                result = resolved;
            } else {
                switch (operators.get(i - 1)) {
                    case ADD:
                        result += resolved;
                        System.out.println("+ -> 0x" + Integer.toHexString(result));
                        break;
                    case SUBTRACT:
                        result -= resolved;
                        System.out.println("- -> 0x" + Integer.toHexString(result));
                        break;
                    default:
                        throw new AssertionError("Unhandled case " + getOperators().get(i - 1));
                }
            }

            if (size > maxSize) {
                maxSize = size;
            }
        }

        int maxVal = (int) Math.pow(2, maxSize * 8) - 1;

        if (result > maxVal) {
            throw new AssemblerException("Resolved value " + result
                    + " is too large (max value of " + maxVal + ").", line);
        }

        System.out.println("result = " + result);

        return Pair.of(result, maxSize);
    }

    private static void extend(List<?> list, int toIndex) {
        for (int i = list.size(); i < toIndex; i++) {
            list.add(null);
        }
    }

}
