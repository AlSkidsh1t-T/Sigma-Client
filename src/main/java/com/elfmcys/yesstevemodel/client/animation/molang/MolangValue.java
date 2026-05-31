package com.elfmcys.yesstevemodel.client.animation.molang;

public final class MolangValue {
    public static final MolangValue ZERO = new MolangValue(0.0D);
    public static final MolangValue ONE = new MolangValue(1.0D);

    private final double value;

    private MolangValue(double value) {
        this.value = Double.isFinite(value) ? value : 0.0D;
    }

    public static MolangValue of(double value) {
        if (value == 0.0D) {
            return ZERO;
        }
        if (value == 1.0D) {
            return ONE;
        }
        return new MolangValue(value);
    }

    public static MolangValue of(boolean value) {
        return value ? ONE : ZERO;
    }

    public double asDouble() {
        return this.value;
    }

    public boolean asBoolean() {
        return Math.abs(this.value) > 0.000001D;
    }
}