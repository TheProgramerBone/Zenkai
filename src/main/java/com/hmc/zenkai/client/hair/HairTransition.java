package com.hmc.zenkai.client.hair;

import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormRegistry;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.race.HairResolver;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * En qué punto del paso de una forma a otra está el pelo de cada jugador (petición del usuario,
 * 2026-10-12: "que el pelo de Goku base se vaya moviendo y tornando amarillo hasta el SSJ1").
 *
 *  · Cargando una transformación (tecla mantenida): sigue el hold. Con la mitad de la barra, el
 *    pelo está a medio camino; el destino lo manda el servidor (PlayerFormAttachment.holdTargetForm).
 *  · Secuencia larga (Rage, primera vez, «siempre larga»): manda la escalada de la escena (AwakeningScene.buildup).
 *  · Al completarse, la forma ya es la de destino y el pelo está casi entero: termina solo.
 *  · Soltar a medias: vuelve a la forma actual a {@link #RATE} por tick.
 *  · Cambio sin hold (comando, Potential Unlock, destransformarse, quedarse sin ki): transición
 *    automática de ~{@code 1/RATE} ticks desde lo que se veía.
 *
 * Solo cliente y solo visual: el estado vive aquí, por jugador, y se avanza al pintar.
 */
public final class HairTransition {
    private HairTransition() {}

    /** Velocidad de la transición automática (fracción por tick): 8 ticks, 0,4 s. */
    private static final float RATE = 1f / 8f;

    private static final class State {
        ResourceLocation from, to;
        float t = 1f;
        float lastTime = Float.NaN;
        HairResolver.FormBlend last;
    }

    private static final Map<UUID, State> STATES = new WeakHashMap<>();

    public static HairResolver.FormBlend current(Player player, float partialTick) {
        PlayerFormAttachment att = player.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        ResourceLocation form = att.getFormId();
        State s = STATES.computeIfAbsent(player.getUUID(), k -> new State());
        float now = player.tickCount + partialTick;
        float dt = Float.isNaN(s.lastTime) ? 0f : Mth.clamp(now - s.lastTime, 0f, 5f);
        s.lastTime = now;
        if (s.from == null) {
            s.from = s.to = form;
            s.t = 1f;
        }

        // Secuencia larga: el color de pelo, ojos, cejas y cola sigue la ESCALADA de la escena (0 -> estallido), no el hold ni un
        // salto en el estallido. La forma mostrada sigue siendo la anterior hasta el estallido (ClientFormDelay).
        var scene = com.hmc.zenkai.client.rage.AwakeningScene.buildup(player, partialTick);
        if (scene != null && !scene.form().equals(form)) {
            s.from = form;
            s.to = scene.form();
            s.t = scene.progress();
            s.last = new HairResolver.FormBlend(s.from, s.to, s.t);
            return s.last;
        }

        ResourceLocation target = att.getHoldTargetForm();
        FormDef targetDef = target == null ? null : FormRegistry.get(target);
        int required = att.holdTicksFor(targetDef);
        if (required > 0 && !target.equals(form)) {
            // Hold en curso: manda la barra (con el recorte de la maestría, como el servidor).
            float progress = Mth.clamp((att.getHoldTicks() + partialTick) / required, 0f, 1f);
            s.from = form;
            s.to = target;
            s.t = progress;
        } else if (form.equals(s.to)) {
            s.t = Math.min(1f, s.t + dt * RATE); // llegando (o ya en reposo)
        } else if (form.equals(s.from)) {
            s.t = Math.max(0f, s.t - dt * RATE); // hold soltado a medias: de vuelta
            if (s.t <= 0f) {
                s.to = form;
                s.t = 1f;
            }
        } else {
            // Cambio sin hold: arranca desde lo que más se veía.
            s.from = s.t >= 0.5f ? s.to : s.from;
            s.to = form;
            s.t = 0f;
        }
        if (s.t >= 1f) s.from = s.to;
        s.last = new HairResolver.FormBlend(s.from, s.to, s.from.equals(s.to) ? 1f : s.t);
        return s.last;
    }

    /**
     * El último punto calculado para este jugador, sin avanzarlo (lo usan las capas pintadas de la
     * cabeza, que se dibujan en otra pasada). Si su pelo no se pinta hace rato: la forma activa, sin más.
     */
    public static HairResolver.FormBlend peek(Player player) {
        State s = STATES.get(player.getUUID());
        // Viejo (el pelo 3D no se pinta: casco, primera persona): sin transición, la forma activa.
        if (s != null && s.last != null && player.tickCount - s.lastTime < 3f) return s.last;
        return HairResolver.FormBlend.settled(player.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId());
    }
}
