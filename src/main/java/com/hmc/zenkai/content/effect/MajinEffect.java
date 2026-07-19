package com.hmc.zenkai.content.effect;

import com.hmc.zenkai.core.network.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.core.network.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.core.network.feature.player.PlayerVisualAttachment;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
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
        if (att.getAlignment() != -100) {
            att.setAlignment(-100);
            PlayerLifeCycle.sync(sp);
        }

        PlayerVisualAttachment visual = PlayerVisualAttachment.get(sp);
        if (!visual.isMajinControlled()) {
            visual.setMajinControlled(true);
            // A trackers Y a sí mismo: el aura roja y el badge los ven todos.
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

    /** ¿El jugador está bajo la marca majin? (server: efecto real). */
    public static boolean isActive(Player p) {
        return p.hasEffect(ModEffects.MAJIN);
    }
}