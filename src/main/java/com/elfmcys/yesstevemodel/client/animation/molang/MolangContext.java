package com.elfmcys.yesstevemodel.client.animation.molang;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.animation.PlayerStateSnapshot;

import java.util.Locale;

public final class MolangContext {
    private final PlayerStateSnapshot snapshot;
    private final String modelId;
    private final String controllerName;
    private final MolangBindings bindings;
    private final boolean allAnimationsFinished;
    private final boolean anyAnimationFinished;

    private MolangContext(PlayerStateSnapshot snapshot, String modelId, String controllerName, MolangBindings bindings,
                          boolean allAnimationsFinished, boolean anyAnimationFinished) {
        this.snapshot = snapshot;
        this.modelId = modelId == null ? "" : modelId;
        this.controllerName = controllerName == null ? "" : controllerName;
        this.bindings = bindings == null ? MolangBindings.EMPTY : bindings;
        this.allAnimationsFinished = allAnimationsFinished;
        this.anyAnimationFinished = anyAnimationFinished;
    }

    public static MolangContext controller(PlayerStateSnapshot snapshot, String modelId, String controllerName) {
        return controller(snapshot, modelId, controllerName, MolangBindings.EMPTY, false, false);
    }

    public static MolangContext controller(PlayerStateSnapshot snapshot, String modelId, String controllerName,
                                           MolangBindings bindings, boolean allAnimationsFinished,
                                           boolean anyAnimationFinished) {
        return new MolangContext(snapshot, modelId, controllerName, bindings, allAnimationsFinished, anyAnimationFinished);
    }

    public MolangValue resolveIdentifier(String identifier) {
        String key = identifier == null ? "" : identifier.toLowerCase(Locale.ROOT);
        if (key.startsWith("ctrl.")) {
            return MolangValue.of(ctrlValue(key.substring("ctrl.".length())));
        }
        if (key.startsWith("query.")) {
            return MolangValue.of(queryValue(key.substring("query.".length())));
        }
        if (key.startsWith("variable.")) {
            String name = key.substring("variable.".length());
            if (!this.bindings.hasVariable(name)) {
                debugUnknown("variable", name);
            }
            return MolangValue.of(this.bindings.variable(name));
        }
        if (key.startsWith("temp.")) {
            String name = key.substring("temp.".length());
            if (!this.bindings.hasTemp(name)) {
                debugUnknown("temp", name);
            }
            return MolangValue.of(this.bindings.temp(name));
        }
        debugUnknown("identifier", key);
        return MolangValue.ZERO;
    }

    private double ctrlValue(String key) {
        if (this.snapshot == null) {
            return 0.0D;
        }
        return switch (key) {
            case "idle" -> this.snapshot.isMoving() ? 0.0D : 1.0D;
            case "walk", "moving" -> this.snapshot.isMoving() && !this.snapshot.sprinting && !this.snapshot.sneaking ? 1.0D : 0.0D;
            case "run", "sprint", "sprinting" -> this.snapshot.sprinting && this.snapshot.isMoving() ? 1.0D : 0.0D;
            case "sneak", "sneaking" -> this.snapshot.sneaking ? 1.0D : 0.0D;
            case "use", "using_item" -> this.snapshot.usingItem ? 1.0D : 0.0D;
            case "swing", "swinging" -> this.snapshot.swingInProgress ? 1.0D : 0.0D;
            case "hold_mainhand" -> this.snapshot.mainhandEmpty ? 0.0D : 1.0D;
            case "hold_offhand" -> this.snapshot.offhandEmpty ? 0.0D : 1.0D;
            default -> {
                debugUnknown("ctrl", key);
                yield 0.0D;
            }
        };
    }

    private double queryValue(String key) {
        if (this.snapshot == null) {
            return 0.0D;
        }
        return switch (key) {
            case "life_time" -> this.snapshot.ageInTicks / 20.0D;
            case "modified_move_speed" -> Math.abs(this.snapshot.limbSwingAmount);
            case "is_sneaking" -> this.snapshot.sneaking ? 1.0D : 0.0D;
            case "is_sprinting" -> this.snapshot.sprinting ? 1.0D : 0.0D;
            case "is_using_item" -> this.snapshot.usingItem ? 1.0D : 0.0D;
            case "all_animations_finished" -> this.allAnimationsFinished ? 1.0D : 0.0D;
            case "any_animation_finished" -> this.anyAnimationFinished ? 1.0D : 0.0D;
            default -> {
                debugUnknown("query", key);
                yield 0.0D;
            }
        };
    }

    private void debugUnknown(String kind, String name) {
        if (!Boolean.getBoolean("yes_steve_model.debugAnimationState")) {
            return;
        }
        YesSteveModel.LOGGER.info("[DEBUG-animation-state] model={} controller={} unknown {}={} default=0", this.modelId,
                this.controllerName, kind, name);
    }
}