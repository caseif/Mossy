package net.caseif.mossy.util;

import static com.google.common.base.Preconditions.checkArgument;

public enum OperatorType {

    ADD('+'),
    SUBTRACT('-'),
    MULTIPLY('*'),
    DIVIDE('/');

    char c;

    OperatorType(char c) {
        this.c = c;
    }

    public static OperatorType getOperatorFromChar(char c) {
        for (OperatorType ot : values()) {
            if (ot.c == c) {
                return ot;
            }
        }
        throw new IllegalArgumentException("No operator for character '" + c + "'");
    }

    public static OperatorType getOperatorFromChar(String str) {
        checkArgument(str.length() == 1, "String length must be 1");
        return getOperatorFromChar(str.charAt(0));
    }

}
