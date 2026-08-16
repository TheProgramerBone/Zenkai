package com.hmc.zenkai.feature.action;

/** Resultado de una petición al resolver. state = el estado que quedó activo (NONE si ninguno). */
public record ActionResult(ActionReject reject, ActionState state) {
    public static ActionResult ok(ActionState st) { return new ActionResult(ActionReject.OK, st); }
    public static ActionResult ok()               { return new ActionResult(ActionReject.OK, ActionState.NONE); }
    public static ActionResult fail(ActionReject r) { return new ActionResult(r, ActionState.NONE); }
}