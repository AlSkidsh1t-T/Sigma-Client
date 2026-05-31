package com.elfmcys.yesstevemodel.client.animation.molang;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class MolangBindings {
    public static final MolangBindings EMPTY = new MolangBindings(Collections.emptyMap(), Collections.emptyMap());

    private final Map<String, Double> variables;
    private final Map<String, Double> temps;

    public MolangBindings(Map<String, Double> variables, Map<String, Double> temps) {
        this.variables = new HashMap<>(variables == null ? Collections.emptyMap() : variables);
        this.temps = new HashMap<>(temps == null ? Collections.emptyMap() : temps);
    }

    public double variable(String name) {
        return this.variables.getOrDefault(name, 0.0D);
    }

    public double temp(String name) {
        return this.temps.getOrDefault(name, 0.0D);
    }

    public boolean hasVariable(String name) {
        return this.variables.containsKey(name);
    }

    public boolean hasTemp(String name) {
        return this.temps.containsKey(name);
    }
}