package net.caseif.mossy.assembly.model;

public class TypedValue {

    public static TypedValue of(ValueType type, Object value) {
        return new TypedValue(type, value);
    }

    private final ValueType type;
    private final Object value;

    private TypedValue(ValueType type, Object value) {
        this.type = type;
        this.value = value;
    }

    public ValueType getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "TypedValue[type=" + type + ", value=" + value + "]";
    }
}
