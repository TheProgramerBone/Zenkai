package com.hmc.zenkai.feature.action;

import com.hmc.zenkai.feature.aura.TurboServerState;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.technique.*;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * EMBUDO ÚNICO de acciones. Nadie llama a los ejecutores salvo esta clase.
 *   Input (packet) → ActionResolver → ActionRules → ActionState → ejecutor
 * Lo que hace en cada petición, SIEMPRE en este orden:
 *   1. Construir el contexto y pedirle el veredicto a ActionRules (guardas).
 *   2. Consultar la Matriz A: ¿puede interrumpir lo que hay en curso?
 *   3. Cancelar la acción en curso y los estados sostenidos que toque (Matriz B).
 *   4. Escribir el ActionState nuevo (que se sincroniza solo).
 *   5. Ejecutar.
 * Puntos de entrada TIPADOS en vez de un ActionRequest genérico: cada acción necesita datos
 * distintos y una bolsa de payload sin tipo obligaría a un switch gigante aquí dentro. La
 * propiedad que importa —una sola ruta hacia los ejecutores— se conserva igual.
 * La prioridad de PAL no aparece por ningún lado, y es el objetivo: PAL representa lo que
 * este archivo decidió, nunca al revés.
 */
public final class ActionResolver {

    private ActionResolver() {}

    // ── Técnicas físicas ─────────────────────────────────────────────────────

    public static ActionResult firePhysical(ServerPlayer sp, PhysicalTechnique t) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);

        ActionContext ctx = ServerActionContext.of(sp);
        int cost = PhysicalCombatServer.staminaCost(att, t);

        ActionReject verdict = ActionRules.canFirePhysical(
                ctx, t.enabled(), att.techniques().isUnlocked(t),
                PhysicalCombatServer.isReady(sp, t), cost, att.getStamina());
        if (!verdict.ok()) return reject(sp, ActionType.PHYSICAL, verdict, t.ordinal());

        ActionState cur = ActionStateServer.get(sp);
        if (!ActionRules.canInterrupt(cur.type(), cur.phase(), ActionType.PHYSICAL)) {
            return reject(sp, ActionType.PHYSICAL, ActionReject.BUSY, t.ordinal());
        }

        cancelCurrent(sp, cur);
        cancelSustained(sp, att, ActionType.PHYSICAL);

        // Solo los movimientos CON DURACIÓN ocupan el estado. Heavy blow y kiai se resuelven
        // en el mismo tick: registrarlos dejaría un estado que nadie limpia.
        PhysicalCombatServer.execute(sp, t, cost);

        long now = sp.level().getGameTime();
        if (PhysicalCombatServer.isBusy(sp.getUUID())) {
            // Movimiento CON DURACIÓN (dash, barrage): lo limpia PhysicalCombatServer.onPlayerTick
            // cuando se agotan sus ticks de gameplay.
            ActionState st = new ActionState(ActionType.PHYSICAL, ActionPhase.ACTIVE,
                    now, t.ordinal(), t.animTicks());
            ActionStateServer.set(sp, st);
            return ActionResult.ok(st);
        }

        // Movimiento INSTANTÁNEO (heavy blow, kiai): el daño ya está resuelto, pero sin estado
        // los observadores no verían nada. Se registra con su duración de animación y lo limpia
        // solo ActionStateServer. La Matriz A deja pasar sobre INSTANT, así que no bloquea.
        ActionState st = new ActionState(ActionType.PHYSICAL, ActionPhase.INSTANT,
                now, t.ordinal(), t.animTicks());
        ActionStateServer.set(sp, st);
        return ActionResult.ok(st);
    }

    // ── Técnicas de ki ───────────────────────────────────────────────────────

    public static ActionResult startKiCharge(ServerPlayer sp, int slot) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);

        KiTechnique tech = att.techniques().slot(slot);
        if (tech == null) return ActionResult.fail(ActionReject.DISABLED);

        ActionContext ctx = ServerActionContext.of(sp);
        ActionReject verdict = ActionRules.canStartKiCharge(
                ctx, tech.type().enabled(), att.techniques().isUnlocked(tech.type()),
                KiCombatServer.isReady(sp, slot));
        if (!verdict.ok()) return reject(sp, ActionType.KI_TECHNIQUE, verdict, slot);

        ActionState cur = ActionStateServer.get(sp);
        if (!ActionRules.canInterrupt(cur.type(), cur.phase(), ActionType.KI_TECHNIQUE)) {
            return reject(sp, ActionType.KI_TECHNIQUE, ActionReject.BUSY, slot);
        }

        // Cambiar de técnica pasa por aquí: cancelCurrent apaga la anterior y el startTick
        // nuevo es lo que impide heredar progreso. Un láser al 100% no se convierte en un
        // Kamehameha instantáneo porque la instancia es otra.
        cancelCurrent(sp, cur);
        cancelSustained(sp, att, ActionType.KI_TECHNIQUE);

        long now = sp.level().getGameTime();
        // visual = 0 marca técnica DEFENSIVA: los animSet reales empiezan en 1, así que el 0
        // queda libre como centinela. El cliente no recibe el KiTechniqueType, y sincronizarlo
        // solo para esto no compensa.
        int visual = tech.type().defensive() ? 0 : tech.animSet();
        ActionState st = new ActionState(ActionType.KI_TECHNIQUE, ActionPhase.CHARGING, now, slot, visual);
        ActionStateServer.set(sp, st);
        KiChargeServer.begin(sp, tech);   // sonido + difusión de la bola
        return ActionResult.ok(st);
    }

    /** Soltar sin disparar (cambio de casilla, salir de combate, manos ocupadas...).
     *  SOLO cancela una CARGA. Si lo que hay ya es RELEASING, el disparo salió y ese estado
     *  ES la representación del disparo: borrarlo aquí mata la animación de release para el
     *  tirador y para todos los observadores. Y pasa siempre, no a veces: el cliente manda
     *  KiFirePacket y, en el mismo tick, KiChargeStartPacket(slot,false) desde
     *  CombatModeClientState.cancelCharge(). */
    public static void cancelKiCharge(ServerPlayer sp) {
        ActionState cur = ActionStateServer.get(sp);
        if (cur.chargingSlot() < 0) return;   // NONE, RELEASING u otra acción: no es una carga
        KiChargeServer.end(sp);
        ActionStateServer.clear(sp);
    }

    public static ActionResult releaseKi(ServerPlayer sp, int slot) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);

        ActionState cur = ActionStateServer.get(sp);
        long now = sp.level().getGameTime();

        // La bola se apaga pase lo que pase, incluso si el disparo se rechaza.
        // El ActionState NO se limpia aquí: si el disparo sale, lo sustituye RELEASING sin
        // pasar por NONE. Un NONE intermedio hace que el cliente ejecute stopKi() y, si los
        // dos paquetes caen en frames distintos, se ve el corte a mitad del disparo.
        KiChargeServer.end(sp);

        KiTechnique tech = att.techniques().slot(slot);
        if (tech == null) { abortKi(sp, cur); return ActionResult.fail(ActionReject.DISABLED); }
        KiTechniqueType type = tech.type();

        double castF = com.hmc.zenkai.feature.mastery.MasteryEffects
                .techCastFactor(att, type.name());
        int reqCharge = Math.max(1, (int) Math.round(
                KiCombatServer.chargeTicksFor(type, tech.size()) * castF));
        int maxTicks = KiCombatServer.maxChargeTicks(reqCharge);

        // CARGA AUTORITATIVA: del ActionState, no del cliente. cur.elapsed() ya está escrito
        // en el instante exacto en que arrancó la carga, sin el retraso de un tick que tenía
        // la versión derivada del paso 1.
        int realTicks = ActionRules.authoritativeChargeTicks(cur.elapsed(now), maxTicks);
        double rawRatio = KiCombatServer.chargeRatio(realTicks, reqCharge);
        double ratio = type.defensive() ? 1.0 : rawRatio;

        TechniqueEffect effect = tech.effect();
        int cost = (int) Math.max(1, Math.ceil(
                KiCombatServer.computeCost(att, type, tech.size(), effect)
                        * KiCombatServer.chargeCostFactor(ratio) * att.powerFraction()));

        // El contexto se construye con la instantánea de ANTES de limpiar: si no, chargingSlot
        // sería -1 y release daría NO_CHARGE.
        ActionContext ctx = ServerActionContext.snapshot(sp, cur, now);
        ActionReject verdict = ActionRules.canReleaseKi(
                ctx, slot, type.enabled(), att.techniques().isUnlocked(type),
                KiCombatServer.isReady(sp, slot), ratio, KiTechniqueType.MIN_CHARGE,
                att.getEnergy(), cost);
        if (!verdict.ok()) {
            abortKi(sp, cur);
            return reject(sp, ActionType.KI_TECHNIQUE, verdict, slot);
        }

        KiFirePacket.execute(sp, att, tech, slot, ratio, rawRatio, cost, effect);

        // Estado de disparo: es lo que distingue "soltó y salió" de "canceló" para los
        // observadores. Se limpia solo en ActionStateServer tras RELEASE_TICKS.
        ActionState fired = new ActionState(ActionType.KI_TECHNIQUE, ActionPhase.RELEASING,
                sp.level().getGameTime(), slot,
                type.animOverride() != null
                        ? type.animOverride().encode() : tech.animSet());
        ActionStateServer.set(sp, fired);
        return ActionResult.ok(fired);
    }

    // ── Defensa ──────────────────────────────────────────────────────────────

    public static ActionResult setBlocking(ServerPlayer sp, boolean blocking) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);

        ActionState cur = ActionStateServer.get(sp);

        if (!blocking) {
            // Salir SIEMPRE funciona: si no, el -60% de velocidad se quedaría pegado.
            if (cur.type() == ActionType.BLOCK) ActionStateServer.clear(sp);
            KiCombatServer.applyBlocking(sp, false);
            return ActionResult.ok();
        }

        ActionContext ctx = ServerActionContext.of(sp);
        if (!ctx.raceChosen() || !ctx.combatMode() || !ctx.handsFree() || ctx.downed()) {
            return reject(sp, ActionType.BLOCK, ActionReject.NOT_COMBAT_MODE, -1);
        }
        if (cur.type() == ActionType.BLOCK) return ActionResult.ok(cur); // ya defendía

        if (!ActionRules.canInterrupt(cur.type(), cur.phase(), ActionType.BLOCK)) {
            return reject(sp, ActionType.BLOCK, ActionReject.BUSY, -1);
        }

        cancelCurrent(sp, cur);
        cancelSustained(sp, att, ActionType.BLOCK);

        ActionState st = new ActionState(ActionType.BLOCK, ActionPhase.ACTIVE,
                sp.level().getGameTime(), -1,-1);
        ActionStateServer.set(sp, st);
        KiCombatServer.applyBlocking(sp, true);
        return ActionResult.ok(st);
    }

    // ── Transformación ───────────────────────────────────────────────────────

    public static ActionResult setTransformHeld(ServerPlayer sp, boolean held) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);

        ActionState cur = ActionStateServer.get(sp);

        if (!held) {
            if (cur.type() == ActionType.TRANSFORM) ActionStateServer.clear(sp);
            sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).setTransformHeld(false);
            return ActionResult.ok();
        }

        if (att.flags().isDowned()) return reject(sp, ActionType.TRANSFORM, ActionReject.DOWNED, -1);
        if (cur.type() == ActionType.TRANSFORM) return ActionResult.ok(cur);
        if (!ActionRules.canInterrupt(cur.type(), cur.phase(), ActionType.TRANSFORM)) {
            return reject(sp, ActionType.TRANSFORM, ActionReject.BUSY, -1);
        }

        cancelCurrent(sp, cur);
        cancelSustained(sp, att, ActionType.TRANSFORM); // aquí SÍ se apaga el turbo

        ActionState st = new ActionState(ActionType.TRANSFORM, ActionPhase.HOLDING,
                sp.level().getGameTime(), -1,-1);
        ActionStateServer.set(sp, st);
        sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).setTransformHeld(true);
        return ActionResult.ok(st);
    }

    // ── Interno ──────────────────────────────────────────────────────────────

    /** Apaga la mecánica de la acción que se está interrumpiendo. No toca ActionState: de eso
     *  se encarga quien la sustituye (o clear(), si no hay sustituta). */
    private static void cancelCurrent(ServerPlayer sp, ActionState cur) {
        switch (cur.type()) {
            case KI_TECHNIQUE -> KiChargeServer.end(sp);
            case PHYSICAL     -> PhysicalCombatServer.cancelActive(sp);
            case BLOCK        -> KiCombatServer.applyBlocking(sp, false);
            case TRANSFORM    -> sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get())
                    .setTransformHeld(false);
            default           -> { }
        }
    }

    /** Matriz B. */
    private static void cancelSustained(ServerPlayer sp, PlayerStatsAttachment att, ActionType in) {
        if (ActionRules.cancelsChargingKi(in)) att.setChargingKi(false);
        if (ActionRules.cancelsTurbo(in))      TurboServerState.set(sp, false);
    }

    private static ActionResult reject(ServerPlayer sp, ActionType type,
                                       ActionReject reason, int payload) {
        PacketDistributor.sendToPlayer(sp, ActionRejectPacket.of(type, reason, payload));
        return ActionResult.fail(reason);
    }

    /** Salida fallida de releaseKi: la carga se cae y no hay estado que la sustituya.
     *  Único sitio que limpia el estado de ki en el camino de disparo. */
    private static void abortKi(ServerPlayer sp, ActionState cur) {
        if (cur.type() == ActionType.KI_TECHNIQUE) ActionStateServer.clear(sp);
    }
}