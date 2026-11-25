package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.forms.OozaruConditions;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Orquesta el PRIMER tramo del ritual de Oozaru: Base -> Oozaru. NO fuerza el formId
 * directamente (eso rompería el gui de transformación, ver abajo) — cada tick vuelca la
 * condición (OozaruConditions: raza + cola + luna llena + MIRAR hacia arriba) en
 * PlayerFormAttachment.oozaruForced, y es serverTick()/tickOozaruLadder() quien de verdad
 * hace avanzar el hold usando el mismo holdTicks/transforming que cualquier otra
 * transformación — así el HUD (TransformGaugeOverlay) enseña el mismo gui de "cargando" sin
 * que el jugador toque una tecla, exactamente lo pedido ("sale el gui de transformación").
 * La mirada es la "salida de emergencia": un saiyan que no quiera transformarse solo tiene
 * que no mirar hacia arriba (ver OozaruConditions.lookingAtMoon).
 *
 * Los tramos 2 y 3 (Oozaru -> Super Oozaru -> SSJ4) SÍ son un hold normal de la tecla H —
 * ver PlayerFormAttachment.resolveNextForm/canAdvance/tickFormLadder, no hace falta nada aquí.
 *
 * UNA VEZ TRANSFORMADO, la luna deja de importar por completo: es la condición de ENTRADA,
 * no de permanencia (a diferencia de una forma normal, que sí se cae sola al agotar su ki —
 * FormSystem.tick). Oozaru/Super Oozaru solo se abandonan por voluntad propia, con el toque
 * de detransformar de siempre (TransformHoldPacket.DETRANSFORM -> forceBase()). Que salga el
 * sol, se nuble el cielo o el jugador entre bajo techo NO lo revierte: el disfrute de la
 * forma no debería depender de mantener la vista en el cielo mientras se pelea con ella.
 *
 * LUNA FALSA (técnica de Tori-Bot, feature/forms/FalseMoon): una esfera flotante que hace de luna llena; ver
 * OozaruConditions.canTrigger.
 */
public final class OozaruSystem {
    private OozaruSystem() {}

    public static void tick(TickCtx c) {
        Player p = c.p();
        PlayerStatsAttachment stats = c.att();
        if (stats.getRace() != Race.SAIYAN) return;

        PlayerFormAttachment form = c.form();
        ResourceLocation current = form.getFormId();

        // Ya transformado: no hay nada que comprobar (ver el javadoc de la clase). oozaruForced
        // solo importa para EMPEZAR el primer tramo desde Base, así que se apaga aquí en vez de
        // dejarlo pegado a lo que fuera antes.
        if (FormIds.OOZARU.equals(current) || FormIds.SUPER_OOZARU.equals(current)) {
            form.setOozaruForced(false);
            return;
        }

        boolean shouldEnter = FormIds.BASE.equals(current) && OozaruConditions.canTrigger(p);
        form.setOozaruForced(shouldEnter);
    }
}
