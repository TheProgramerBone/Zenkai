package com.hmc.zenkai.content.effect;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Marca Majin ("controlado por Babidi"). Mientras dura:
 *  - Alineamiento CLAVADO en -100 (se re-fuerza y AlignmentManager ignora kills).
 *  - Aura roja + badge (flag majinControlled en PlayerVisualAttachment, sync a trackers).
 *  - Boost de stats (StatsConfig.majinStatBonus, vía MasteryEffects.formStatFactor).
 *
 * PERSISTENTE como la inmortalidad: el flag es la fuente de verdad; si quitan el efecto
 * (leche, /effect clear), TickHandlers lo RE-APLICA mientras el flag siga puesto. La única
 * salida es MORIR (PlayerLifeCycle limpia flag + efecto en death/respawn).
 * Aplicar: /effect give <player> zenkai:majin infinite
 */
public class MajinEffect extends MobEffect {

    public MajinEffect() {
        super(MobEffectCategory.NEUTRAL, 0xD41A25);
    }

    @Override
    public boolean applyEffectTick(@NotNull LivingEntity entity, int amplifier) {
        if (!(entity instanceof ServerPlayer sp)) return true;

        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        // Exclusión mutua con el estado Legendario (config): el Legendario es irreversible y gana, así que la marca se retira.
        // Solo se baja el flag: quitar el efecto AQUÍ, dentro de su propio tick, rompería la iteración de vainilla;
        // PersistentEffectsSystem (PlayerTickEvent.Post) lo quita en cuanto ve el flag apagado.
        if (blockedByLegendary(sp)) {
            PlayerVisualAttachment v = PlayerVisualAttachment.get(sp);
            if (v.isMajinControlled()) {
                v.setMajinControlled(false);
                PlayerLifeCycle.syncVisualToTrackersAndSelf(sp);
            }
            return true;
        }
        if (att.getAlignment() != -100) {
            att.setAlignment(-100);
            PlayerLifeCycle.sync(sp);
        }

        PlayerVisualAttachment visual = PlayerVisualAttachment.get(sp);
        if (!visual.isMajinControlled()) {
            visual.setMajinControlled(true);
            // A trackers Y a sí mismo: el aura roja y el badge los ve cualquiera cerca.
            PlayerLifeCycle.syncVisualToTrackersAndSelf(sp);
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // SIEMPRE true: con "infinite" la duración se queda en -1, así que un gate tipo
        // "duration % 20 == 0" NUNCA dispararía. El cuerpo es idempotente y barato.
        return true;
    }

    /** ¿Impide el estado Legendario aplicar la marca Majin a este jugador? (majin.legendary_mutually_exclusive) */
    public static boolean blockedByLegendary(Player p) {
        return ServerConfig.majinLegendaryExclusive() && PlayerStatsAttachment.get(p).isLegendary();
    }

    /** ¿El jugador está bajo la marca majin? (server: efecto real). */
    public static boolean isActive(Player p) {
        return p.hasEffect(ModEffects.MAJIN);
    }

    /**
     * ÚNICO punto que debe usar cualquier código que active/desactive Majin fuera del propio
     * efecto (comando de debug, reset full...): fija el flag Y el {@code MobEffectInstance} EN
     * LA MISMA LLAMADA, sincrónicamente, y sincroniza.
     *
     * Por qué hace falta esto y no basta con tocar el flag y dejar que
     * {@code PersistentEffectsSystem.tick()} limpie el efecto en el siguiente tick: ese tick
     * corre en {@code PlayerTickEvent.Post}, que se dispara DESPUÉS de que vainilla ya haya
     * procesado el tick normal de efectos de la entidad ({@code LivingEntity.tickEffects()} ->
     * {@link #applyEffectTick}). Si el efecto sigue vivo cuando corre ese tick de vainilla,
     * {@link #applyEffectTick} lo detecta presente y vuelve a poner el flag en {@code true}
     * ANTES de que {@code PersistentEffectsSystem} tenga ocasión de quitarlo — así que el
     * comando "apagaba" el flag un instante (el icono llegaba a desaparecer, incluso a mandar su
     * propio sync) y el propio efecto lo reactivaba solo el mismo tick o el siguiente. Quitando
     * el {@code MobEffectInstance} aquí mismo, de forma síncrona, no queda ninguna ventana en la
     * que el efecto pueda seguir vivo para volver a reafirmar el flag.
     */
    public static void setControlled(ServerPlayer sp, boolean value) {
        if (value && blockedByLegendary(sp)) value = false;
        PlayerVisualAttachment visual = PlayerVisualAttachment.get(sp);
        visual.setMajinControlled(value);
        if (value) {
            if (!sp.hasEffect(ModEffects.MAJIN)) {
                sp.addEffect(new MobEffectInstance(ModEffects.MAJIN,
                        MobEffectInstance.INFINITE_DURATION, 0, true, false, false));
            }
        } else {
            sp.removeEffect(ModEffects.MAJIN);
        }
        PlayerLifeCycle.syncVisualToTrackersAndSelf(sp);
    }
}