package com.elfmcys.yesstevemodel.client.animation.molang;

public final class MolangExpression {
    private final String source;
    private final Node root;

    MolangExpression(String source, Node root) {
        this.source = source == null ? "" : source;
        this.root = root;
    }

    public String getSource() {
        return this.source;
    }

    MolangValue evaluate(MolangContext context) {
        return this.root == null ? MolangValue.ZERO : this.root.evaluate(context);
    }

    interface Node {
        MolangValue evaluate(MolangContext context);
    }
}