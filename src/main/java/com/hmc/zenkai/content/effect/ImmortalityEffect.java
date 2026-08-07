package com.hmc.zenkai.content.effect;

import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Inmortalidad: REGENERACIÓN MUY ALTA. Nada más.
 *
 * Antes el flag isImmortal también cancelaba la muerte en DownedDeathGuard y rellenaba el body
 * en CombatZenkaiHooks.onBodyDepleted, así que el jugador no solo se curaba rápido: no podía
 * morir de ninguna forma, ni con /kill. Esas dos ramas se han eliminado y toda la inmortalidad
 * vive aquí.
 *
 * Consecuencia buscada: un inmortal cae derribado como cualquiera, pero se levanta solo casi al
 * instante porque la regeneración le devuelve body durante el propio derribado. Sobrevive a
 * Lo que no lo mate de golpe, que es lo que un jugador entiende por inmortal, y sigue
 * siendo matable con daño suficiente en poco tiempo.
 */
public class ImmortalityEffect extends MobEffect {

    /** Fracción del body máximo curada POR SEGUNDO y por nivel de amplificador.
     *  0.15 = de 0 a full en menos de 7 s con amplificador 0. */
    private static final double BODY_FRACTION_PER_SECOND = 0.15;

    /** Corazones por segundo para quien aún no ha elegido raza (fuera del sistema Zenkai). */
    private static final float VANILLA_HEAL_PER_SECOND = 6.0F;

    /** Cada cuántos ticks corre. Las tasas de arriba son POR SEGUNDO y se escalan por este
     *  intervalo, así que tocarlo cambia la suavidad y NO la velocidad de curación. */
    private static final int TICK_INTERVAL = 10;

    public ImmortalityEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFF3AD97B);
    }

    @Override
    public boolean applyEffectTick(@NotNull LivingEntity livingEntity, int amplifier) {
        if (!(livingEntity instanceof Player player)) return true;
        if (player.level().isClientSide()) return true;

        PlayerStatsAttachment att = PlayerStatsAttachment.get(player);

        // Sin raza elegida el jugador está fuera del pipeline de Zenkai: ahí manda la vida
        // vanilla y se cura por el camino normal.
        if (!att.isRaceChosen()) {
            float heal = VANILLA_HEAL_PER_SECOND * (TICK_INTERVAL / 20.0F) * (amplifier + 1);
            player.heal(heal);
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
            return true;
        }

        int bodyMax = att.getBodyMax();

        // Solo se corta con bodyMax inválido. Con body a 0 SÍ regenera: es justo el caso del
        // derribado, y que se levante solo es la mecánica entera de este efecto.
        if (bodyMax <= 0) return true;

        int regen = (int) Math.max(1, Math.round(
                bodyMax * BODY_FRACTION_PER_SECOND * (TICK_INTERVAL / 20.0) * (amplifier + 1)));

        att.addBody(regen);   // addBody ya hace clamp a [0, bodyMax]

        // El espejo body -> corazones vive en PlayerLifeCycle.sync(). Aquí no se toca la vida
        // vanilla: era la segunda copia de la misma fórmula.
        PlayerLifeCycle.syncIfServer(player);
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // Cada TICK_INTERVAL y no cada tick: sin esto se dispara un SyncPlayerStatsPacket por
        // tick y por jugador inmortal.
        return duration % TICK_INTERVAL == 0;
    }
}