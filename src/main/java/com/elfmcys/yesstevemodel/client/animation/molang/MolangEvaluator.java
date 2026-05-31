package com.elfmcys.yesstevemodel.client.animation.molang;

public final class MolangEvaluator {
    private MolangEvaluator() {
    }

    public static MolangValue evaluate(MolangExpression expression, MolangContext context) {
        if (expression == null) {
            return MolangValue.ZERO;
        }
        try {
            return expression.evaluate(context);
        } catch (RuntimeException exception) {
            return MolangValue.ZERO;
        }
    }
}