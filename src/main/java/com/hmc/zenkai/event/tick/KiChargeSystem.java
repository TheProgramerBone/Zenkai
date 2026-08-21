package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import net.minecraft.world.entity.player.Player;

/** Carga de ki (mantener C) y subida del % de poder por concentración. */
public final class KiChargeSystem {
    private KiChargeSystem() {}

    /** Ticks entre cada escalón de subida, tras el primer segundo cargando. */
    private static final int STEP_INTERVAL = 10; // 0.5 s
    /** Cuánto sube el % de poder por escalón. */
    private static final int STEP_AMOUNT = 10;

    public static void tick(TickCtx c) {
        Player p = c.p();
        PlayerStatsAttachment att = c.att();

        if (!att.isChargingKi()) {
            PlayerTickState.resetCharge(p.getUUID());
            return;
        }

        // Sin Meditación se puede concentrar (el % de poder sube) pero NO se recupera ki.
        double chargeMul = SkillEffects.kiChargeFactor(p);
        if (chargeMul > 0.0) {
            // Porcentual sobre el pool, igual que el regen pasivo: antes era un plano
            // getRegenEnergyPerTick() = 1.0, que ignoraba pool y config por completo.
            double perTick = att.getEnergyMax() * (CommonConfig.baseRegenEnergy() / 100.0) / 20.0;
            att.addKi(perTick * chargeMul);
        }

        // Tras 1 s cargando, el % sube de STEP_AMOUNT en STEP_AMOUNT cada STEP_INTERVAL ticks
        // hasta el techo de Ki Control. Ya no avisa por action bar: el cascarón circular del HUD
        // (KiChargeGaugeOverlay) lee isChargingKi()+powerPercent del sync de fin de tick
        // (ZenkaiTickHandlers) y se rellena en vivo, así que un mensaje de texto aparte solo
        // repetiría la misma información con más parpadeo.
        int t = PlayerTickState.bumpCharge(p.getUUID());
        if (t > 20 && (t - 20) % STEP_INTERVAL == 0) {
            att.setPowerPercent(att.getPowerPercent() + STEP_AMOUNT, SkillEffects.maxPowerPercent(p));
        }
    }
}