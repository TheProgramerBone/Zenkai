package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.weights.WeightSystem;
import net.minecraft.world.entity.player.Player;

/** Vuelo: habilitación, velocidad, coste de ki y hitbox del boost. */
public final class FlightSystem {
    private FlightSystem() {}

    /** Empujón del turbo sobre la velocidad final. Mismo valor que en tierra a propósito:
     *  el turbo debe sentirse igual corriendo que volando. Candidato a StatsConfig. */
    private static final double TURBO_SPEED_MULT = 1.35;

    public static void tick(TickCtx c, boolean turboOn) {
        Player p = c.p();
        PlayerStatsAttachment att = c.att();
        if (p.isCreative() || p.isSpectator()) return;

        var ab = p.getAbilities();
        // La habilidad Fly HABILITA el vuelo: sin ella no se vuela aunque el toggle esté activo.
        // Sobrecargado NO se despega del suelo: la carga te clava.
        boolean overloaded = WeightSystem.isOverloaded(att.getWeightLoad());
        boolean shouldFly = att.isFlyEnabled() && SkillEffects.canFly(p) && !overloaded;
        if (ab.mayfly != shouldFly) {
            ab.mayfly = shouldFly;
            if (!shouldFly) ab.flying = false;
            p.onUpdateAbilities();
        }

        boolean control  = att.flags().isFlyBoosting();   // Ctrl+W en vuelo
        boolean flyTurbo = ab.flying && control && turboOn;

        // Techo = habilidad Fly. DEX ya NO interviene: alimentaba defensa, velocidad y vuelo
        // a la vez, así que tocar el balance defensivo movía la velocidad de rebote.
        double max = Math.min(CommonConfig.flyMultiplierCap(), SkillEffects.flySpeedFactor(p));
        // Se interpola desde 1.0 (vuelo vanilla): con 0% de poder el multiplicador cae a 1.0.
        double mult = 1.0 + (max - 1.0)
                * PerformanceTier.of(control) * att.powerFraction();
        // El turbo multiplica FUERA del escalón: efecto constante y perceptible.
        if (flyTurbo) mult *= TURBO_SPEED_MULT;
        // Pesas al final, igual que en tierra.
        mult *= WeightSystem.moveFactor(att.getWeightLoad());

        float newSpeed = (float) (CommonConfig.flyBaseSpeed() * mult);
        // Player.getFlyingSpeed() DUPLICA la velocidad al esprintar, y Control ES la tecla de
        // sprint: sin compensar, el escalón medio se llevaba un x2 gratis que rompía la
        // proporción entre escalones.
        if (p.isSprinting()) newSpeed /= 2.0F;

        // setFlyingSpeed en servidor NO llega al cliente sin onUpdateAbilities(), pero llamarlo
        // cada tick sería un paquete por tick: solo cuando la velocidad cambia de verdad.
        if (Math.abs(ab.getFlyingSpeed() - newSpeed) > 1.0e-4F) {
            ab.setFlyingSpeed(newSpeed);
            p.onUpdateAbilities();
        }

        // Coste del vuelo turbo: ki por tick reducido por Fly. El drenaje base del aura lo
        // cobra TurboServerState por su cuenta, y se auto-apaga si el ki llega a 0.
        if (flyTurbo) {
            double drain = CommonConfig.flyKiDrainPerTick() * SkillEffects.flyKiDrainFactor(p);
            if (drain > 0.0) att.addKi(-drain);
        }
        if (!ab.flying) att.flags().setFlyBoosting(false);
    }

    /** Hitbox/cámara "acostado" durante el boost de vuelo. */
    public static void tickBoostHitbox(TickCtx c) {
        Player p = c.p();
        PlayerStatsAttachment att = c.att();
        boolean flyingNow = att.isFlyEnabled() && p.getAbilities().flying && !p.isSpectator();
        if (!flyingNow) att.flags().setFlyBoosting(false);

        boolean prone = att.flags().isFlyBoosting();
        if (prone != att.flags().isBoostSizeApplied()) {
            att.flags().setBoostSizeApplied(prone);
            p.refreshDimensions();
        }
    }
}