package com.hmc.zenkai.content.entity.ai;

import com.hmc.zenkai.content.entity.ZenkaiDefaultMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

/**
 * Bloqueo de HÁBITO + REACTIVO. Hábito: cada cierto tiempo, si hay objetivo y el mob no está a
 * mitad de su propio wind-up (isWindingUp()), entra en guardia unos segundos con una
 * probabilidad baja. REACTIVO (añadido después): además, SIEMPRE entra en guardia si detecta que
 * su objetivo actual está en pleno wind-up de un ataque real (CombatAiUtil.isWindingUp — otro mob
 * vía AttackTelegraph, un jugador vía ActionStateServer), sin esperar el dado ni el cooldown del
 * hábito — pedido explícito: con el resto de la IA volviéndose situacional, tenía sentido que
 * también "viera" cuándo cubrirse, no solo cuándo atacar. Ninguna de las dos vías es predicción
 * fina (no lee daño/dirección concretos), solo "¿está a punto de pegarme?".
 *
 * LÍMITE REAL de la vía reactiva contra un JUGADOR (no adivinado, se sigue de ActionPhase): un
 * puñetazo/golpe vainilla NO pasa por ActionState en absoluto (no es una acción Zenkai), así que
 * nunca dispara la reacción — solo lo hace cargar un KI_TECHNIQUE (fase CHARGING/OVERCHARGING) o
 * usar DASH_PUNCH/BARRAGE (las únicas físicas con fase ACTIVA sostenida; HEAVY_BLOW/KIAI van
 * directas a INSTANT, sin ventana server-side que reaccionar). La vía de HÁBITO es la única que
 * cubre "el jugador me está pegando con melee normal" — probabilística, no garantizada.
 *
 * SIN FLAGS (EnumSet vacío): no compite por MOVE/LOOK con KiAttackGoal/PhysicalAttackGoal/
 * MeleeAttackGoal, así que corre en paralelo sin desalojar ni ser desalojado por ellos — la
 * exclusión mutua real (no bloquear a mitad de un wind-up, no empezar un wind-up mientras se
 * bloquea) se hace a mano comprobando el estado del otro lado, no con el sistema de flags.
 */
public class BlockHabitGoal<T extends ZenkaiDefaultMob & BlockingMob> extends Goal {

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-ShadowAI");

    private final T mob;

    private long readyAt = 0;
    private int ticksLeft = 0;

    private static final int BLOCK_DURATION_TICKS = 40;  // 2 s bloqueando
    private static final int BLOCK_COOLDOWN_TICKS = 60;  // 3 s antes de poder volver a intentarlo (solo hábito)
    private static final int CHECK_INTERVAL_TICKS = 20;  // el dado del hábito solo se tira 1 vez por segundo
    private static final int CHECK_CHANCE = 3;           // 1 entre 3 en cada tirada

    public BlockHabitGoal(T mob) {
        this.mob = mob;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        LivingEntity tgt = mob.getTarget();
        if (tgt == null || !tgt.isAlive()) return false;
        if (mob.isWindingUp()) return false; // no empezar a bloquear a mitad del propio ataque

        // REACTIVO: gana siempre, sin dado ni cooldown del hábito — un mob no debería "no poder
        // cubrirse todavía" solo porque acaba de intentarlo hace poco. Ver el javadoc de clase
        // para el límite real de esta vía contra melee vainilla.
        if (CombatAiUtil.isWindingUp(tgt)) {
            LOGGER.info("[Zenkai] {} (bloqueo) REACTIVO: {} está en wind-up", mob, tgt);
            return true;
        }

        long now = mob.level().getGameTime();
        if (now < readyAt) return false;
        if (now % CHECK_INTERVAL_TICKS != 0) return false;

        boolean roll = mob.getRandom().nextInt(CHECK_CHANCE) == 0;
        if (roll) LOGGER.info("[Zenkai] {} (bloqueo) HÁBITO: dado acertado", mob);
        return roll;
    }

    @Override
    public boolean canContinueToUse() { return ticksLeft > 0; }

    @Override
    public void start() {
        ticksLeft = BLOCK_DURATION_TICKS;
        mob.setBlocking(true);
    }

    @Override
    public void stop() {
        mob.setBlocking(false);
        readyAt = mob.level().getGameTime() + BLOCK_COOLDOWN_TICKS;
    }

    @Override
    public void tick() {
        ticksLeft--;
        LivingEntity tgt = mob.getTarget();
        if (tgt != null) mob.getLookControl().setLookAt(tgt, 30f, 30f);
    }
}
