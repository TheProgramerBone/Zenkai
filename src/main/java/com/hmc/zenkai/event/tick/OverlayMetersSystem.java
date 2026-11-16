package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SkillToggles;
import com.hmc.zenkai.feature.skills.UiDodgeAnimPacket;
import com.hmc.zenkai.feature.skills.UiDodgeChoice;
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
 * Mecánicas propias de Ultra Instinct (barra de calor + esquive automático) y Ultra Ego, más la
 * exclusión mutua UI/UE. "Activo" = el interruptor (SkillToggles) O la transformación completa
 * correspondiente. Los números salen de ServerConfig (ultra_instinct.* / ultra_ego.*).
 *
 * Ultra Ego (skill de 10 niveles, la forma se desbloquea en el 5):
 *  - Niveles 1-10: PERFORACIÓN de defensa de las técnicas FÍSICAS, lineal hasta
 *    ultra_ego.max_armor_penetration en el 10 (uePenetration).
 *  - Niveles 5-10: el daño recibido llena una barra que da un BONO DE ESTADÍSTICAS (no un
 *    multiplicador de daño) cuyo techo sube con el nivel (ueStatBonus). Antes del 5 la barra no
 *    actúa: el jugador aún no tiene la forma.
 */
public final class OverlayMetersSystem {
    private OverlayMetersSystem() {}

    private static final Map<UUID, Long> DODGE_COOLDOWN = new HashMap<>();
    private static final long DODGE_COOLDOWN_TICKS = 10L;
    /** Contador de esquives por jugador: reparte la secuencia cuando no se sabe con qué golpean (UiDodgeChoice). */
    private static final Map<UUID, Integer> DODGE_NEXT = new HashMap<>();
    /** Último tick en que cada jugador animó un esquive, y separación mínima entre dos animaciones seguidas. */
    private static final Map<UUID, Long> DODGE_ANIM_TICK = new HashMap<>();
    private static final long DODGE_ANIM_GAP_TICKS = 3L;
    /** Jugadores en forma UI con la barra de calor agotada (sin esquive hasta enfriarse). */
    private static final java.util.Set<UUID> EXHAUSTED = new java.util.HashSet<>();

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

    /** Maestría (0..1) de Ultra Instinct: la MEJOR de sus dos formas, también para la técnica
     *  (entrenar la forma mejora el interruptor; sin forma entrenada es 0). */
    public static double uiMastery(Player p) {
        PlayerFormAttachment f = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        return Math.min(1.0, Math.max(f.getFormMastery(FormIds.ULTRA_INSTINCT_SIGN),
                f.getFormMastery(FormIds.ULTRA_INSTINCT)) / 100.0);
    }

    /** Maestría (0..1) de la forma Ultra Ego. */
    public static double ueMastery(Player p) {
        return Math.min(1.0, p.getData(ZenkaiDataAttachments.PLAYER_FORM.get())
                .getFormMastery(FormIds.ULTRA_EGO) / 100.0);
    }

    private static double ueMasteryFactor(Player p) {
        return 1.0 + ServerConfig.ueMasteryBonus() * ueMastery(p);
    }

    /** Nivel de Ultra Ego desde el que la forma existe y la barra de daño empieza a actuar. */
    public static final int UE_FORM_LEVEL = 5;

    /** Fracción de la defensa del objetivo que IGNORAN las técnicas físicas del jugador. En la
     *  forma Ultra Ego siempre está activa (da igual el interruptor); fuera de ella, solo con el
     *  interruptor encendido. Lineal: nivel/10 del máximo configurado. 0 sin la skill. */
    public static double uePenetration(Entity attacker) {
        if (!(attacker instanceof Player p)) return 0.0;
        int lvl = Math.min(10, SkillEffects.level(p, SkillEffects.ULTRA_EGO));
        if (lvl <= 0 || !ueActive(p, formOf(p))) return 0.0;
        // La maestría de la forma sube la perforación (tope 1.0: ignorar más que toda la defensa no existe).
        return Math.min(1.0, ServerConfig.uePenetrationMax() * lvl / 10.0 * ueMasteryFactor(p));
    }

    /** Bono ADITIVO de estadísticas de la barra de Ultra Ego, sumado en
     *  MasteryEffects.formStatFactor. 0 bajo el nivel 5 o con Ultra Ego inactivo. El techo
     *  interpola linealmente entre ultra_ego.max_stat_bonus_level5 y _level10; el bono es
     *  proporcional a lo llena que esté la barra. */
    public static double ueStatBonus(Player p) {
        int lvl = Math.min(10, SkillEffects.level(p, SkillEffects.ULTRA_EGO));
        if (lvl < UE_FORM_LEVEL || !ueActive(p, formOf(p))) return 0.0;
        double t = (lvl - UE_FORM_LEVEL) / (double) (10 - UE_FORM_LEVEL);
        double ceiling = ServerConfig.ueStatBonusLv5()
                + (ServerConfig.ueStatBonusLv10() - ServerConfig.ueStatBonusLv5()) * t;
        double fill = Math.min(1.0, PlayerStatsAttachment.get(p).getUeCharge() / 100.0);
        return ceiling * fill * ueMasteryFactor(p);
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

        // Calor: solo sube en las FORMAS Ultra Instinct. Al llegar a 100% la forma NO se pierde: el
        // jugador queda "agotado" (sin esquive, ver tryDodge) y la barra se enfría hasta
        // ultra_instinct.heat_recover_percent, momento en que el esquive vuelve.
        double heat = att.getUiHeat();
        if (isUiForm(form)) {
            if (EXHAUSTED.contains(sp.getUUID()) || heat >= 100.0) {
                EXHAUSTED.add(sp.getUUID());   // heat >= 100 tras reconectar: la marca no persiste
                heat -= ServerConfig.uiHeatCool() / 20.0;
                if (heat <= ServerConfig.uiHeatRecover()) {
                    EXHAUSTED.remove(sp.getUUID());
                    sp.displayClientMessage(Component.translatable("message.zenkai.ui_recovered"), true);
                }
            } else {
                int lvl = Math.max(1, SkillEffects.level(sp, SkillEffects.ULTRA_INSTINCT));
                double slow = Math.max(0.1, 1.0 - ServerConfig.uiHeatSlow() * (lvl - 1))
                        * (1.0 - ServerConfig.uiMasteryHeat() * uiMastery(sp));
                heat += ServerConfig.uiHeatRate() * slow / 20.0;
                if (heat >= 100.0) {
                    heat = 100.0;
                    EXHAUSTED.add(sp.getUUID());
                    sp.displayClientMessage(Component.translatable("message.zenkai.ui_overheat"), true);
                }
            }
        } else {
            EXHAUSTED.remove(sp.getUUID());
            if (heat > 0.0) heat -= ServerConfig.uiHeatCool() / 20.0;
        }
        heat = Math.max(0.0, heat);
        if (heat != att.getUiHeat()) att.setUiHeat(heat);

        double ue = att.getUeCharge();
        if (ue > 0.0) att.setUeCharge(ue - ServerConfig.ueDecay() / 20.0);
    }

    /** Esquive automático de UI. true = golpe anulado (el llamador pone el daño a 0). */
    public static boolean tryDodge(ServerPlayer sp, net.minecraft.world.damagesource.DamageSource source) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!uiActive(sp, formOf(sp))) return false;
        int lvl = SkillEffects.level(sp, SkillEffects.ULTRA_INSTINCT);
        if (lvl <= 0) return false;
        // Barra de calor al máximo: sigue transformado pero ya no esquiva hasta enfriarse.
        if (EXHAUSTED.contains(sp.getUUID()) || att.getUiHeat() >= 100.0) return false;
        long now = sp.level().getGameTime();
        Long until = DODGE_COOLDOWN.get(sp.getUUID());
        if (until != null && now < until) {
            // Golpe en ráfaga: cubierto por el mismo esquive (ni sorteo ni coste), pero cada golpe se esquiva a la vista
            // con su propio gesto. El tope evita reanimar por varios impactos del mismo tick.
            Long anim = DODGE_ANIM_TICK.get(sp.getUUID());
            if (anim == null || now - anim >= DODGE_ANIM_GAP_TICKS) playDodge(sp, source, now);
            return true;
        }
        // No siempre esquiva: probabilidad por nivel de la skill (se sortea una vez por golpe
        // suelto; las ráfagas ya quedan cubiertas por el cooldown de arriba).
        double chance = Math.min(ServerConfig.uiDodgeChanceMax(),
                ServerConfig.uiDodgeChance() + ServerConfig.uiDodgeChanceLevel() * (lvl - 1)
                        + ServerConfig.uiMasteryDodge() * uiMastery(sp));
        if (sp.getRandom().nextDouble() * 100.0 >= chance) return false;
        double factor = Math.max(0.1, 1.0 - ServerConfig.uiDodgeReduce() * (Math.min(lvl, 4) - 1));
        double cost = att.getKiPool() * ServerConfig.uiDodgeCost() / 100.0 * factor;
        if (att.getKiCurrent() < cost) return false;
        att.addKi(-cost);
        DODGE_COOLDOWN.put(sp.getUUID(), now + DODGE_COOLDOWN_TICKS);
        // Cada esquive gasta concentración: sube el calor (la HUD lo enseña al revés, como lo que QUEDA). Al llegar al 100 %
        // pasa lo de siempre: sin esquive hasta enfriarse (tick).
        double heatNow = Math.min(100.0, att.getUiHeat() + ServerConfig.uiHeatPerDodge());
        att.setUiHeat(heatNow);
        if (heatNow >= 100.0 && isUiForm(formOf(sp)) && EXHAUSTED.add(sp.getUUID())) {
            sp.displayClientMessage(Component.translatable("message.zenkai.ui_overheat"), true);
        }
        playDodge(sp, source, now);
        PlayerLifeCycle.syncIfServer(sp);
        return true;
    }

    /** El servidor elige el gesto según el golpe que llega (UiDodgeChoice) y lo manda, para que todos vean el mismo. */
    private static void playDodge(ServerPlayer sp, net.minecraft.world.damagesource.DamageSource source, long now) {
        int seq = DODGE_NEXT.merge(sp.getUUID(), 1, (old, one) -> old + 1) - 1;
        DODGE_ANIM_TICK.put(sp.getUUID(), now);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                new UiDodgeAnimPacket(sp.getId(), UiDodgeChoice.pick(sp, source, seq)));
    }

    /** Alimenta la barra de Ultra Ego con el daño recibido (fracción del cuerpo perdido). No
     *  mata ni quita nada por sí sola: solo sube la barra. */
    public static void onDamaged(ServerPlayer victim, double bodyLost, int bodyMax) {
        if (bodyMax <= 0 || bodyLost <= 0 || !ueActive(victim, formOf(victim))) return;
        if (SkillEffects.level(victim, SkillEffects.ULTRA_EGO) < UE_FORM_LEVEL) return;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(victim);
        att.setUeCharge(att.getUeCharge() + bodyLost / bodyMax * 100.0 * ServerConfig.ueCharge());
    }

    public static void forget(UUID id) {
        DODGE_COOLDOWN.remove(id); EXHAUSTED.remove(id); DODGE_NEXT.remove(id); DODGE_ANIM_TICK.remove(id);
        UiDodgeChoice.forget(id);
    }
}
