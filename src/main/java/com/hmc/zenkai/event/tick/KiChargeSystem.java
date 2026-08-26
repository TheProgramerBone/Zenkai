package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.advancement.ZenkaiTriggers;
import com.hmc.zenkai.feature.forms.OverdriveTuning;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
            PlayerTickState.resetForce(p.getUUID());
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

        // Subir el % por encima de 100 SIEMPRE exige Shift sostenido, en TODO momento — no solo
        // para romper el candado la primera vez de esta subida. Cargar ki solo (sin Shift) sigue
        // funcionando normal (el regen de arriba no depende de esto), pero el % nunca sube más
        // allá de 100 sin Shift, ni siquiera si ya se había forzado antes y luego se soltó: al
        // soltar Shift por encima de 100 el % se queda quieto (no cae de golpe — eso es cosa del
        // drenaje de OverdriveSystem o de Descender), y hace falta Shift de nuevo para seguir.
        //
        // A exactamente 100 (candado aún sin romper) hace falta ADEMÁS sostener el temblor:
        // cuenta CADA tick (no cada STEP_INTERVAL, necesita medir segundos reales). Menos ticks
        // que el umbral -> sigue temblando (el HUD lee isChargingKi()+isOverdriveCharging()+
        // powerPercent==100 para el efecto, aquí solo se decide SI se le deja subir). Al llegar
        // al umbral, la primera vez de todas también otorga el logro oculto y marca el flag que
        // abarata las siguientes veces.
        int cur = att.getPowerPercent();
        boolean forcing = att.isOverdriveCharging();
        boolean stepAllowed = false; // ¿se permite subir el % este tick, ya en/sobre 100?

        if (cur >= 100) {
            if (cur == 100) {
                if (forcing) {
                    int need = OverdriveTuning.breakthroughTicksNeeded(att.hasBrokenOverdriveOnce());
                    int held = PlayerTickState.bumpForce(p.getUUID());
                    stepAllowed = held >= need;
                    if (stepAllowed && !att.hasBrokenOverdriveOnce()) {
                        att.setHasBrokenOverdriveOnce(true);
                        if (p instanceof ServerPlayer sp) {
                            ZenkaiTriggers.MILESTONE.get().trigger(sp, ZenkaiTriggers.Kinds.OVERDRIVE);
                            // Cristal roto: SOLO la primera vez de todas, el resto de rupturas no
                            // lo repiten (rompes el límite una vez, no un candado nuevo cada vez).
                            // ⚠ API a verificar: SoundEvents.GLASS_BREAK en 1.21.1.
                            sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                                    SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
                        }
                    }
                } else {
                    PlayerTickState.resetForce(p.getUUID());
                }
            } else {
                // Ya roto en una subida anterior (cur > 100): SIEMPRE hace falta Shift sostenido
                // para seguir, sin excepción — el candado no se queda abierto solo.
                PlayerTickState.resetForce(p.getUUID());
                stepAllowed = forcing;
            }

            // Cadencia PROPIA una vez en esta banda: más lenta y en pasos más pequeños que la
            // subida 0-100 (OVERDRIVE_STEP_AMOUNT/INTERVAL en OverdriveTuning) — el jugador debe
            // poder tantear con cuánto % extra aguanta cómodo, no pegar un salto grande de golpe.
            PlayerTickState.resetCharge(p.getUUID()); // no gastar la cadencia normal aquí
            if (stepAllowed) {
                int held = PlayerTickState.bumpOverdriveStep(p.getUUID());
                if (held >= OverdriveTuning.OVERDRIVE_STEP_INTERVAL_TICKS) {
                    PlayerTickState.resetOverdriveStep(p.getUUID());
                    int dynCeiling = (int) Math.round(Math.max(SkillEffects.maxPowerPercent(p),
                            OverdriveSystem.ceilingFor(c.form().activeDef())));
                    att.setPowerPercent(cur + OverdriveTuning.OVERDRIVE_STEP_AMOUNT, dynCeiling);
                }
            } else {
                // Sin Shift ahora mismo (o aún temblando sin romper el candado): no se acumula
                // cadencia de subida — soltar Shift a medio escalón obliga a esperar el segundo
                // entero de nuevo al retomarlo, no a reanudar desde donde se quedó.
                PlayerTickState.resetOverdriveStep(p.getUUID());
            }
        } else {
            PlayerTickState.resetOverdriveStep(p.getUUID());
            // Tras 1 s cargando, el % sube de STEP_AMOUNT en STEP_AMOUNT cada STEP_INTERVAL
            // ticks, tope 100 (candado cerrado y ya en 100: no sube, tampoco baja — eso es cosa
            // del drenaje de OverdriveSystem o de Descender). Ya no avisa por action bar: el
            // cascarón circular del HUD (KiChargeGaugeOverlay) lee isChargingKi()+powerPercent
            // del sync de fin de tick (ZenkaiTickHandlers) y se rellena en vivo, así que un
            // mensaje de texto aparte solo repetiría la misma información con más parpadeo.
            int t = PlayerTickState.bumpCharge(p.getUUID());
            if (t > 20 && (t - 20) % STEP_INTERVAL == 0 && cur < 100) {
                att.setPowerPercent(cur + STEP_AMOUNT, 100);
            }
        }
    }
}