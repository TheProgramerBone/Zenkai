package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.event.ZenkaiPalAnimations;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SkillToggles;
import com.hmc.zenkai.feature.skills.UiDodgeAnimPacket;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mecánicas propias de Ultra Instinct (barra de calor + esquive automático) y Ultra Ego (barra
 * de daño acumulado que multiplica el daño de salida), más la exclusión mutua UI/UE.
 * "Activo" = el interruptor (SkillToggles) O la transformación completa correspondiente.
 * Los números salen de ServerConfig (ultra_instinct.* / ultra_ego.*).
 */
public final class OverlayMetersSystem {
    private OverlayMetersSystem() {}

    private static final Map<UUID, Long> DODGE_COOLDOWN = new HashMap<>();
    private static final long DODGE_COOLDOWN_TICKS = 10L;

    public static boolean isUiForm(ResourceLocation f) {
        return FormIds.ULTRA_INSTINCT_SIGN.equals(f) || FormIds.ULTRA_INSTINCT.equals(f);
    }

    public static boolean isUeForm(ResourceLocation f) { return FormIds.ULTRA_EGO.equals(f); }

    private static ResourceLocation formOf(Player p) {
        return p.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId();
    }

    private static boolean uiActive(Player p, ResourceLocation form) {
        return isUiForm(form) || SkillToggles.isOn(p, SkillEffects.ULTRA_INSTINCT);
    }

    private static boolean ueActive(Player p, ResourceLocation form) {
        return isUeForm(form) || SkillToggles.isOn(p, SkillEffects.ULTRA_EGO);
    }

    public static void tick(TickCtx c) {
        if (!(c.p() instanceof ServerPlayer sp)) return;
        PlayerStatsAttachment att = c.att();
        ResourceLocation form = c.form().getFormId();

        // Exclusión mutua: la transformación completa manda sobre el interruptor contrario; si
        // solo hay interruptores, se conserva UI (estado guardado con los dos = dato viejo).
        boolean uiOn = SkillToggles.isOn(sp, SkillEffects.ULTRA_INSTINCT);
        boolean ueOn = SkillToggles.isOn(sp, SkillEffects.ULTRA_EGO);
        if (isUiForm(form) && ueOn) SkillToggles.set(sp, SkillEffects.ULTRA_EGO, false);
        else if (isUeForm(form) && uiOn) SkillToggles.set(sp, SkillEffects.ULTRA_INSTINCT, false);
        else if (uiOn && ueOn) SkillToggles.set(sp, SkillEffects.ULTRA_EGO, false);

        // Calor: solo sube en las FORMAS Ultra Instinct; al llegar a 100% la forma colapsa.
        double heat = att.getUiHeat();
        if (isUiForm(form)) {
            int lvl = Math.max(1, SkillEffects.level(sp, SkillEffects.ULTRA_INSTINCT));
            double slow = Math.max(0.1, 1.0 - ServerConfig.uiHeatSlow() * (lvl - 1));
            heat += ServerConfig.uiHeatRate() * slow / 20.0;
            if (heat >= 100.0) {
                heat = 100.0;
                c.form().forceBase();
                PlayerLifeCycle.syncFormIfServer(sp);
                sp.displayClientMessage(Component.translatable("message.zenkai.ui_overheat"), true);
            }
        } else if (heat > 0.0) {
            heat -= ServerConfig.uiHeatCool() / 20.0;
        }
        if (heat != att.getUiHeat()) att.setUiHeat(heat);

        double ue = att.getUeCharge();
        if (ue > 0.0) att.setUeCharge(ue - ServerConfig.ueDecay() / 20.0);
    }

    /** Esquive automático de UI. true = golpe anulado (el llamador pone el daño a 0). */
    public static boolean tryDodge(ServerPlayer sp) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!uiActive(sp, formOf(sp))) return false;
        int lvl = SkillEffects.level(sp, SkillEffects.ULTRA_INSTINCT);
        if (lvl <= 0) return false;
        long now = sp.level().getGameTime();
        Long until = DODGE_COOLDOWN.get(sp.getUUID());
        if (until != null && now < until) return true; // golpe en ráfaga: cubierto por el mismo esquive
        double factor = Math.max(0.1, 1.0 - ServerConfig.uiDodgeReduce() * (Math.min(lvl, 4) - 1));
        double cost = att.getKiPool() * ServerConfig.uiDodgeCost() / 100.0 * factor;
        if (att.getKiCurrent() < cost) return false;
        att.addKi(-cost);
        DODGE_COOLDOWN.put(sp.getUUID(), now + DODGE_COOLDOWN_TICKS);
        // Dos clips de esquive, sorteo del servidor para que todos vean el mismo.
        int variant = sp.getRandom().nextInt(ZenkaiPalAnimations.UI_DODGE_VARIANTS);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, new UiDodgeAnimPacket(sp.getId(), variant));
        PlayerLifeCycle.syncIfServer(sp);
        return true;
    }

    /** Multiplicador de daño de SALIDA de Ultra Ego según su barra (curva cuadrática). */
    public static float scaleOutgoing(Entity attacker, float dmg) {
        if (!(attacker instanceof ServerPlayer sp)) return dmg;
        if (!ueActive(sp, formOf(sp)) || SkillEffects.level(sp, SkillEffects.ULTRA_EGO) <= 0) return dmg;
        double t = PlayerStatsAttachment.get(sp).getUeCharge() / 100.0;
        double mult = ServerConfig.ueMin() + (ServerConfig.ueMax() - ServerConfig.ueMin()) * t * t;
        return (float) (dmg * mult);
    }

    /** Alimenta la barra de Ultra Ego con el daño recibido (fracción del cuerpo perdido). No
     *  mata ni quita nada por sí sola: solo sube la barra. */
    public static void onDamaged(ServerPlayer victim, double bodyLost, int bodyMax) {
        if (bodyMax <= 0 || bodyLost <= 0 || !ueActive(victim, formOf(victim))) return;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(victim);
        att.setUeCharge(att.getUeCharge() + bodyLost / bodyMax * 100.0 * ServerConfig.ueCharge());
    }

    public static void forget(UUID id) { DODGE_COOLDOWN.remove(id); }
}
