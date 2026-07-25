package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.forms.KaiokenTier;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.util.BalanceUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Kaioken: quema VIDA mientras esté activo. Es lo que impide que sea gratis, y por eso
 * drena body y no ki. Se apaga solo al caer al mínimo o al perder la habilidad.
 * MODELO DE DRENAJE (v3): coste ABSOLUTO sacado de STR/WIL, con dos reductores encima.
 *     drain/s = pct/100 · powerStat · KAIOKEN_DRAIN_SCALE · skillFactor · masteryFactor
 * Por qué no es un % del pool: antes era bodyMax · pct/100, así que el drenaje escalaba
 * con el MISMO atributo que el pool (CON) y ambos se cancelaban -> subir constitución no
 * daba ni un segundo extra (medido: 5 s en x20 con CON 100 y con CON 10.000).
 * Ahora el coste lo marca tu poder y el aguante lo marca CON:
 *   - CON ≈ 1.5 · max(STR,WIL) deja la duración CONSTANTE a lo largo de la progresión;
 *   - por debajo te quemas antes; por encima compras aguante.
 * Los DOS reductores son independientes a propósito:
 *   - skillFactor  (SkillEffects.kaiokenDrainFactor): nivel de la habilidad, 1.0 -> 0.35.
 *     Se compra con TP: es la vía "teórica".
 *   - masteryFactor: maestría del ESCALÓN concreto, 1.0 -> MASTERED_DRAIN_FACTOR.
 *     Solo sube usándolo: es la vía "práctica". Ver MasteryTicker.
 * KAIOKEN_DRAIN_SCALE está calibrado para que, al dominar un escalón, el coste aterrice
 * donde estaba antes de existir la maestría (2.5 · 0.5 = 1.25), y sin maestría cueste el doble.
 * Los escalones bajos quedan sostenibles con buen CON: los compensa la regen de body, que
 * comparte acumulador con este drenaje (ver PlayerTickState#carry). No son gratis porque la regen cobra exhaustion (comida).
 */

public final class KaiokenSystem {
    private KaiokenSystem() {}

    /**
     * Calibración global del coste. Con 2.5 y ratio CON/poder = 1.5: x20 dura ~10 s sin
     * maestría y ~23 s dominado. Subirlo acorta TODOS los escalones proporcionalmente.
     * (Candidato natural a mudarse a StatsConfig junto a bodyScale/regenBody.)
     */
    private static final double KAIOKEN_DRAIN_SCALE = 2.5;

    /** Multiplicador del drenaje con el escalón dominado al 100 %. */
    private static final double MASTERED_DRAIN_FACTOR = 0.5;

    /** Suelo del poder: evita que un personaje recién creado tenga kaioken gratis. */
    private static final double MIN_POWER_STAT = 10.0;

    /** Strain base al agotarse, en ticks (5 s). Se escala por escalón y lo recorta la maestría. */
    private static final int STRAIN_BASE_TICKS = 600;

    /** Cuánto recorta el strain tener el escalón dominado al 100 %. */
    private static final double STRAIN_MASTERY_REDUCTION = 0.5;

    public static void tick(TickCtx c) {
        Player p = c.p();
        PlayerStatsAttachment att = c.att();
        PlayerFormAttachment form = c.form();
        KaiokenTier tier = form.getKaioken();
        if (!tier.isOn()) return;

        // Perdió la habilidad (respec) o el nivel ya no alcanza este escalón: se apaga.
        if (!tier.allowedAt(SkillEffects.kaiokenLevel(p))) {
            form.setKaioken(KaiokenTier.OFF);
            // syncForm y NO sync: el escalón vive en PLAYER_FORM, y sync() solo manda
            // SyncPlayerStatsPacket. Con el packet equivocado el cliente se quedaba con el
            // icono encendido y el aura roja pegada (AuraColors lee getKaioken()).
            PlayerLifeCycle.syncFormIfServer(p);
            return;
        }

        double[] carry = PlayerTickState.carry(p.getUUID());
        double perTick = drainPerTick(att, form, SkillEffects.kaiokenDrainFactor(p));

        // Reusa el acumulador de body [0] A PROPÓSITO: RegenSystem suma ahí una vez por
        // segundo, así que una buena constitución compensa la quema y el jugador aguanta
        // el kaioken. Ver PlayerTickState#carry.
        carry[0] -= perTick;
        int whole = (int) Math.floor(-carry[0]);
        if (whole > 0) {
            carry[0] += whole;
            att.addBody(-whole);
        }

        // No mata: al llegar a 1 se corta solo. Morir por kaioken sería un castigo doble
        // (ya te deja al borde y sin recursos).
        if (att.getBody() <= 1) {
            att.setBody(1);
            // STRAIN: apagarse por agotamiento bloquea reactivar y castiga stats un rato.
            // Sin esto el jugador reencendía en cuanto la regen le daba dos puntos de vida.
            form.setStrain(p.level().getGameTime() + strainTicks(form, tier));
            form.setKaioken(KaiokenTier.OFF);
            if (p instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        Component.translatable("messages.zenkai.kaioken.exhausted"), true);
                // DOS packets porque este apagado toca DOS attachments: el body clampado a 1
                // (PLAYER_STATS) y el escalón + strainUntil (PLAYER_FORM). Mandar solo el
                // primero dejaba al cliente con el kaioken activo y sin fatiga.
                PlayerLifeCycle.sync(sp);
                PlayerLifeCycle.syncForm(sp);
            }
        }
    }

    /** Puntos de body quemados por tick. Público y estático puro: vale para la GUI. */
    public static double drainPerTick(PlayerStatsAttachment att, PlayerFormAttachment form,
                                      double skillFactor) {
        KaiokenTier tier = form.getKaioken();
        if (!tier.isOn()) return 0.0;
        return tier.drainPctPerSecond() / 100.0
                * powerStat(att) * KAIOKEN_DRAIN_SCALE / 20.0
                * skillFactor * masteryFactor(form, tier) * formFactor(form);
    }

    /** 1.0 sin maestría -> MASTERED_DRAIN_FACTOR al 100 %. Lineal: el progreso se nota desde el principio. */
    public static double masteryFactor(PlayerFormAttachment form, KaiokenTier tier) {
        float m = form.getKaiokenMastery(tier);   // 0..100
        return 1.0 - (1.0 - MASTERED_DRAIN_FACTOR) * (m / 100.0);
    }

    /**
     * Sobrecoste por mantener el kaioken ENCIMA de una transformación: raíz del multiplicador
     * de la forma. Lineal sería letal (black_form da +1400 % -> x15 de coste); con raíz, ssj4
     * cuesta ~3.2x lo que en base, así que x20 queda como ráfaga de segundos mientras x2-x4
     * siguen siendo sostenibles. Dominar la forma la encarece un poco, porque da más stats.
     *
     * Usa formStatPercent() y NO totalStatPercent(): el segundo INCLUYE el % del propio
     * kaioken, así que el drenaje se retroalimentaría (x20 multiplicaría su coste por ~20).
     */
    private static double formFactor(PlayerFormAttachment form) {
        return Math.sqrt(1.0 + Math.max(0.0, form.formStatPercent()));
    }

    /**
     * Ticks de fatiga tras caer agotado. Proporcional al escalón que te tumbó (caer desde
     * x20 castiga 25 veces más que desde x2) y recortado por la maestría de ESE escalón:
     * es el segundo uso de la maestría, que así vale para algo más que el drenaje.
     */
    public static long strainTicks(PlayerFormAttachment form, KaiokenTier tier) {
        if (!tier.isOn()) return 0L;
        double mastery = form.getKaiokenMastery(tier) / 100.0;
        return Math.round(STRAIN_BASE_TICKS * (tier.drainPctPerSecond() / 2.5)
                * (1.0 - STRAIN_MASTERY_REDUCTION * mastery));
    }

    /**
     * Poder que paga el kaioken: el PICO entre fuerza y voluntad, con multiplicadores de
     * raza/estilo (misma base que usa bodyMax para CON, así los dos lados de la balanza
     * hablan el mismo idioma).
     * OJO: NO usar att.computeMeleeFinal()/computeKiPowerFinal(): esos ya llevan dentro
     * powerFraction() y statMultiplier, y statMultiplier INCLUYE el % del propio kaioken
     * -> el drenaje se retroalimentaría (x20 multiplicaría su propio coste por ~20) y
     * bajar el % de poder abarataría el kaioken.
     */
    private static double powerStat(PlayerStatsAttachment att) {
        double str = BalanceUtil.computeStat(att.getAttribute(ZenkaiAttributes.STRENGTH),
                att.getRace(), att.getStyle(), ZenkaiAttributes.STRENGTH);
        double wil = BalanceUtil.computeStat(att.getAttribute(ZenkaiAttributes.WILLPOWER),
                att.getRace(), att.getStyle(), ZenkaiAttributes.WILLPOWER);
        double total = str + wil;
        return Math.max(MIN_POWER_STAT, total);
    }
}