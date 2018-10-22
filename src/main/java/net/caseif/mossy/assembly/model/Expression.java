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

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

public class Expression<T> {

    private final TypeWithMetadata<T> type;
    private final List<Object> values;
    private final int line;

    public Expression(TypeWithMetadata<T> type, List<Object> values, int line) {
        this.type = type;
        this.values = values;
        this.line = line;
    }

    public TypeWithMetadata<T> getType() {
        return type;
    }

    public List<Object> getValues() {
        return values;
    }

    public int getLine() {
        return line;
    }

    public enum Type implements ExpressionPart {
        MNEMONIC,
        TARGET,
        IMM_VALUE,
        LABEL_DEF,
        NAMED_CONSTANT_DEF,
        LABEL_REF,
        DIRECTIVE,
        QWORD,
        DWORD,
        WORD,
        NUMBER,
        CONSTANT,
        COMMENT
    }

    public static class TypeWithMetadata<T> {

        public static <T> TypeWithMetadata<T> of(Type type, T metadata) {
            return new TypeWithMetadata<>(type, metadata);
        }

        public static TypeWithMetadata<Void> of(Type type) {
            return of(type, null);
        }

        private final Type type;
        private final T metadata;

        private TypeWithMetadata(Type type, @Nullable T metadata) {
            this.type = type;
            this.metadata = metadata;
        }

        public Type getType() {
            return type;
        }

        public T getMetadata() {
            return metadata;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TypeWithMetadata
                    && type == ((TypeWithMetadata) other).type
                    && Objects.equals(metadata, ((TypeWithMetadata) other).metadata);
        }

        @Override
        public int hashCode() {
            return Objects.hash(TypeWithMetadata.class, type, metadata);
        }
    }

}
