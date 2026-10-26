package com.hmc.zenkai.feature.forms;

import com.hmc.zenkai.feature.mastery.MasteryEffects;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.world.entity.player.Player;

/**
 * Iris de las formas exclusivas del Legendario, que cambian con la maestría y con el overdrive en vez de
 * llevar un color fijo (petición del usuario, 2026-10-25, como en la película):
 * <ul>
 *   <li>Legendario ({@code ssj_green}): sin iris (blanco) hasta el 50 % de maestría; de ahí al 100 % el iris
 *       aparece poco a poco, verdoso.</li>
 *   <li>Legendario Pleno ({@code ssj_green_max}): iris verdoso, que se vuelve blanco mientras fuerza (overdrive).</li>
 * </ul>
 * Lógica pura de color, sin estado: la usa la capa de cara del cliente como override de {@code eye_rgb}.
 */
public final class LegendaryEyes {
    private LegendaryEyes() {}

    /** Blanco: «sin iris» (el iris es una capa multiplicativa sobre la esclerótica). */
    public static final int WHITE = 0xFFFFFF;
    /** Verde del iris que aparece. */
    public static final int GREEN = 0x6FD65A;
    /** Maestría (0-100) a partir de la cual el Legendario empieza a mostrar el iris. */
    public static final float IRIS_APPEARS_AT = 50f;

    /** Iris para una forma y una maestría; -1 si la forma no usa este efecto (manda su eye_rgb). */
    public static int rgb(net.minecraft.resources.ResourceLocation form, float mastery, boolean overdrive) {
        if (FormIds.SSJ_GREEN.equals(form)) {
            float t = Math.max(0f, Math.min(1f, (mastery - IRIS_APPEARS_AT) / (100f - IRIS_APPEARS_AT)));
            return mix(WHITE, GREEN, t);
        }
        if (FormIds.SSJ_GREEN_MAX.equals(form)) return overdrive ? WHITE : GREEN;
        return -1;
    }

    /** Lo mismo para un jugador (cliente o servidor); -1 si no aplica. */
    public static int rgb(Player p) {
        PlayerFormAttachment f = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        return rgb(f.getFormId(), f.activeMastery(), MasteryEffects.isLegendaryOverdrive(p));
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
