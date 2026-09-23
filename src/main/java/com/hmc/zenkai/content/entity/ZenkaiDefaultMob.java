package com.hmc.zenkai.content.entity;

import com.hmc.zenkai.content.entity.ai.AttackTelegraph;
import com.hmc.zenkai.content.entity.ai.BlockingMob;
import com.hmc.zenkai.content.entity.ai.KiAttackGoal;
import com.hmc.zenkai.content.entity.ai.PhysicalAttackGoal;
import com.hmc.zenkai.content.entity.ai.VexStyleFlightControl;
import com.hmc.zenkai.feature.combat.entity.EntityStatsManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

// Esta clase será para los enemigos neutrales

public abstract class ZenkaiDefaultMob extends PathfinderMob implements GeoEntity, AttackTelegraph {

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-CombatAI");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** AttackTelegraph: ¿en pleno wind-up de un ataque real ahora mismo? Puesto por
     *  KiAttackGoal/PhysicalAttackGoal.start()/stop() — no sincronizado, solo se lee server-side
     *  (BlockHabitGoal del objetivo). No confundir con PosedAttacker (eso es render). */
    private boolean windingUp = false;

    @Override
    public void setWindingUp(boolean windingUp) { this.windingUp = windingUp; }

    @Override
    public boolean isWindingUp() { return windingUp; }

    /** gameTime a partir de la cual se permite EMPEZAR un nuevo wind-up (ki o físico) —
     *  compartido entre KiAttackGoal y PhysicalAttackGoal para que no se turnen sin respiro
     *  apenas termina uno (pendiente real de la sesión anterior: "el ciclo ki/físicas
     *  turnándose demasiado rápido"). Cada uno lo consulta en canUse() y lo actualiza en fire(). */
    private long nextActionAt = 0;

    public boolean isActionOnCooldown() { return level().getGameTime() < nextActionAt; }

    public void markActionUsed(int breathingRoomTicks) {
        nextActionAt = level().getGameTime() + breathingRoomTicks;
    }

    protected ZenkaiDefaultMob(EntityType<? extends ZenkaiDefaultMob> type, Level level) {
        super(type, level);
        // Lectura DIRECTA del JSON (no del hook overridable canFlyNow()) — ver su javadoc para
        // el porqué: esto corre dentro del constructor de Mob, antes de que el constructor de
        // cualquier subclase (p. ej. ShadowCloneEntity.flightAllowed) se haya inicializado.
        if (EntityStatsManager.get(entityId()) != null && EntityStatsManager.get(entityId()).canFly()) {
            this.moveControl = new VexStyleFlightControl(this);
        }
    }

    private ResourceLocation entityId() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(getType());
    }

    /** Vuelo estilo Vex disparado por "moveset.can_fly" en el JSON (ver ModEntityStatsProvider/
     *  EntityStatDef) — generaliza lo que antes solo tenía ShadowCloneEntity hardcodeado. Igual
     *  que Vex.createNavigation: FlyingPathNavigation de verdad (pathing 3D), no solo estética. */
    @Override
    protected @NotNull PathNavigation createNavigation(@NotNull Level level) {
        var def = EntityStatsManager.get(entityId());
        if (def != null && def.canFly()) {
            FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
            nav.setCanOpenDoors(false);
            nav.setCanFloat(true);
            return nav;
        }
        return super.createNavigation(level);
    }

    /** Hook OVERRIDABLE: ¿vuela AHORA MISMO? Por defecto, la capacidad estructural del JSON —
     *  ShadowCloneEntity lo sobreescribe para además exigir que el dueño tenga la skill fly (ver
     *  su javadoc). A diferencia de createNavigation()/el constructor (que leen el JSON directo),
     *  este SÍ es seguro de llamar desde tick() (ya construido del todo para entonces) y es la
     *  única vía correcta de gatear el vuelo por-instancia sin poder cambiar el tipo de
     *  PathNavigation ya instalado. Público: KiAttackGoal/PhysicalAttackGoal lo consultan para
     *  saber si ESTE mob puede de verdad cerrar distancia con un objetivo en el aire. */
    public boolean canFlyNow() {
        var def = EntityStatsManager.get(entityId());
        return def != null && def.canFly();
    }

    /** Reevalúa qué PathNavigation Y qué MoveControl debería tener este mob AHORA MISMO (según
     *  canFlyNow(), el gate por-instancia — no la capacidad estática del JSON que ya decidió
     *  createNavigation()/el constructor) y los reinstala si no coinciden. Necesario para el caso
     *  Shadow: el constructor instala FlyingPathNavigation + VexStyleFlightControl porque el JSON
     *  dice can_fly:true, pero setFlightAllowed(false) (el dueño sin la skill) llega DESPUÉS —
     *  sin este swap, la Sombra se queda para siempre con la pareja de vuelo aunque nunca vaya a
     *  volar de verdad:
     *  - FlyingPathNavigation (pensada para Vex/Bee, sin asistencia de "subir 1 bloque" como
     *    GroundPathNavigation) es incapaz de perseguir por terreno normal con escalones — "no
     *    puede subir bloques", confirmado jugando.
     *  - Bug real encontrado DESPUÉS, más grave: esta función SOLO cambiaba `navigation`, nunca
     *    `moveControl` — así que aunque `navigation` pasara a GroundPathNavigation, el mob seguía
     *    con `VexStyleFlightControl` (empuja velocidad DIRECTA hacia el punto deseado, ver su
     *    javadoc) en vez del `MoveControl` vainilla que `GroundPathNavigation` espera (gira hacia
     *    el objetivo y avanza con `zza`, no con un vector delta crudo) — con la pareja
     *    desemparejada, el mob podía quedarse efectivamente INMÓVIL en vez de solo "sin subir
     *    escalones". Ahora ambos se reevalúan juntos.
     *  Seguro de llamar justo tras spawnear, ANTES de addFreshEntity — cambiar la navegación/el
     *  MoveControl mientras un Goal ya los está usando activamente no está probado. */
    protected void refreshNavigationType() {
        boolean shouldFly = canFlyNow();

        boolean isFlyingNav = this.navigation instanceof FlyingPathNavigation;
        if (shouldFly != isFlyingNav) {
            if (shouldFly) {
                FlyingPathNavigation nav = new FlyingPathNavigation(this, level());
                nav.setCanOpenDoors(false);
                nav.setCanFloat(true);
                this.navigation = nav;
            } else {
                this.navigation = new net.minecraft.world.entity.ai.navigation.GroundPathNavigation(this, level());
            }
        }

        boolean hasFlightControl = this.moveControl instanceof VexStyleFlightControl;
        if (shouldFly != hasFlightControl) {
            this.moveControl = shouldFly
                    ? new VexStyleFlightControl(this)
                    : new net.minecraft.world.entity.ai.control.MoveControl(this);
        }
    }

    /** Espejo de addKiAttackGoalIfDefined/addPhysicalAttackGoalIfDefined para el deambular en
     *  vuelo (WaterAvoidingRandomFlyingGoal) — se añade sin condición runtime porque
     *  WaterAvoidingRandomFlyingGoal no sabe consultar canFlyNow() por sí sola; si el vuelo está
     *  denegado en caliente (Sombra sin la skill del dueño), setNoGravity nunca se reafirma (ver
     *  tick()) y este goal simplemente no consigue despegar — igual de inofensivo que cualquier
     *  otro goal de movimiento con un mob sin gravedad negada. */
    protected void addFlightWanderGoalIfCanFly(int priority, double speed) {
        var def = EntityStatsManager.get(entityId());
        if (def != null && def.canFly()) {
            this.goalSelector.addGoal(priority, new WaterAvoidingRandomFlyingGoal(this, speed));
        }
    }

    @Override
    public @NotNull AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(ZenkaiCommonAnimations.genericWalkController(this));
        // Melee/ki/físicas en UN controlador triggereado: 'triggerAnim' reinicia siempre, así
        // que ya no se queda pegado como el de 'swinging'. Las 4 físicas reproducen el clip REAL
        // (zenkai.phys_*, copiado tal cual de player_animations/phys_*.animation.json — ver
        // PhysicalAttackGoal) por el motor NATIVO de GeckoLib, no un sampler a mano.
        controllers.add(new AnimationController<>(
                this, "KiAttack", 5, state -> PlayState.STOP)
                .triggerableAnim("strike",         ZenkaiCommonAnimations.ATTACK_STRIKE)
                .triggerableAnim("ki_charge",       ZenkaiCommonAnimations.ATTACK_CHARGE)
                .triggerableAnim("ki_shoot",        ZenkaiCommonAnimations.ATTACK_SHOOT)
                .triggerableAnim("phys_dash_punch", ZenkaiCommonAnimations.PHYS_DASH_PUNCH)
                .triggerableAnim("phys_heavy_blow", ZenkaiCommonAnimations.PHYS_HEAVY_BLOW)
                .triggerableAnim("phys_barrage",    ZenkaiCommonAnimations.PHYS_BARRAGE)
                .triggerableAnim("phys_kiai",       ZenkaiCommonAnimations.PHYS_KIAI));

        // Bloqueo: SOLO si el mob implementa BlockingMob (Saibaman/NamekianWarrior no bloquean
        // hoy). Predicado, no trigger: mientras isBlockingNow() sea true reproduce/mantiene
        // zenkai.block (clip real, "hold_on_last_frame"); en cuanto deja de bloquear, STOP y
        // vuelve a lo que toque (walk/idle). BlockHabitGoal solo toca setBlocking(), no sabe nada
        // de animación — la desacopla por completo.
        if (this instanceof BlockingMob bm) {
            controllers.add(new AnimationController<>(this, "Block", 5,
                    state -> bm.isBlockingNow()
                            ? state.setAndContinue(ZenkaiCommonAnimations.ZENKAI_BLOCK)
                            : PlayState.STOP));
        }
    }

    /** Un mob que puede volar AHORA MISMO no debería morir de caída al perder el vuelo un
     *  instante (p. ej. sobrecarga momentánea) ni tomar daño de caída normal mientras vuela —
     *  pedido explícito del usuario. Gateado por canFlyNow(), no por la capacidad estructural del
     *  JSON: la Sombra sin la skill fly del dueño SÍ debe tomar daño de caída normal, como
     *  cualquier mob terrestre. */
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, @NotNull DamageSource source) {
        if (canFlyNow()) return false;
        return super.causeFallDamage(fallDistance, multiplier, source);
    }

    /** Ticks que dura "attack.strike" (animation_length 0.3333 s en
     *  zenkai_animations.animation.json) — server-side no se puede leer ese archivo de cliente,
     *  mismo razonamiento que ya se documentó para PhysicalAttackGoal antes de que pasara a
     *  animTicks() del datapack: aquí no hay TechniqueDef del que tirar porque es melee
     *  vainilla, no una técnica. Si se retoca el clip en Blockbench, actualizar este número. */
    private static final int MELEE_STRIKE_ANIM_TICKS = 7;

    /** gameTime hasta la cual el swing de melee sigue "sonando" — ver isMeleeStrikePlaying()/
     *  ZenkaiMeleeAttackGoal.isInterruptable(). */
    private long meleeStrikeProtectedUntil = 0;

    /** ¿Sigue reproduciéndose el swing de melee disparado en doHurtTarget() ahora mismo?
     *  Público: lo consulta {@link com.hmc.zenkai.content.entity.ai.ZenkaiMeleeAttackGoal
     *  #isInterruptable()} para que Ki/Físicas (mayor prioridad) no puedan cortarlo a mitad —
     *  la misma protección que ya tienen KiAttackGoal/PhysicalAttackGoal entre sí (ver su
     *  javadoc), que a Melee (la de menor prioridad de las tres) nunca le llegaba. */
    public boolean isMeleeStrikePlaying() {
        return level().getGameTime() < meleeStrikeProtectedUntil;
    }

    /** Melee: dispara el strike triggereado en el conjunto de clientes que ven al mob. Ya NO
     *  hace falta ningún canal alternativo (PosedAttacker) para esto: todo zenkaimob de combate
     *  es GeckoLib de verdad (incluida la Sombra desde que se convirtió a
     *  {@link com.hmc.zenkai.client.render_and_model_entities.entity.PosedHumanoidGeoModel}),
     *  así que triggerAnim SIEMPRE llega al modelo. */
    @Override
    public boolean doHurtTarget(@NotNull Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !level().isClientSide()) {
            triggerAnim("KiAttack", "strike");
            meleeStrikeProtectedUntil = level().getGameTime() + MELEE_STRIKE_ANIM_TICKS;
        }
        return hit;
    }

    /** Reafirma setNoGravity si canFlyNow() — igual que hacía Vex.tick()/ShadowCloneEntity antes
     *  de generalizarse aquí; por si algo más lo resetea. Si canFlyNow() es false (p. ej. la
     *  Sombra sin la skill fly del dueño), NO se fuerza nada: gravedad normal. */
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && canFlyNow()) setNoGravity(true);
    }

    /** Añade el goal de ki SOLO si el datapack define ataques para esta entidad. Lo llama
     *  la subclase desde registerGoals (p. ej. un soldado de Freezer sí, el saibaman no). */
    protected void addKiAttackGoalIfDefined(int priority, double moveSpeed) {
        addKiAttackGoalIfDefined(priority, moveSpeed, 0.0);
    }

    /** minRange: ver el javadoc de KiAttackGoal — 0 = sin mínimo (comportamiento de siempre). */
    protected void addKiAttackGoalIfDefined(int priority, double moveSpeed, double minRange) {
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(getType());
        var def = EntityStatsManager.get(id);
        if (def != null && def.hasKiAttacks()) {
            LOGGER.info("[Zenkai] {} registra KiAttackGoal con {} ataque(s) (prioridad {}, minRange={})",
                    id, def.kiAttacks().size(), priority, minRange);
            this.goalSelector.addGoal(priority,
                    new KiAttackGoal<>(this, def.kiAttacks(), moveSpeed, minRange));
        } else {
            LOGGER.info("[Zenkai] {} SIN ki_attacks en su EntityStatDef (def={})", id, def);
        }
    }

    /** Espejo de addKiAttackGoalIfDefined para técnicas físicas (physical_attacks del
     *  datapack) — ver PhysicalAttackGoal. Mismo criterio: sin entradas, no se añade el goal. */
    protected void addPhysicalAttackGoalIfDefined(int priority, double moveSpeed) {
        addPhysicalAttackGoalIfDefined(priority, moveSpeed, Double.MAX_VALUE);
    }

    /** engageRange: ver el javadoc de PhysicalAttackGoal — MAX_VALUE = sin tope (siempre
     *  dispuesto a acercarse, comportamiento de siempre). */
    protected void addPhysicalAttackGoalIfDefined(int priority, double moveSpeed, double engageRange) {
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(getType());
        var def = EntityStatsManager.get(id);
        if (def != null && def.hasPhysicalAttacks()) {
            LOGGER.info("[Zenkai] {} registra PhysicalAttackGoal con {} ataque(s) (prioridad {}, engageRange={})",
                    id, def.physicalAttacks().size(), priority, engageRange);
            this.goalSelector.addGoal(priority,
                    new PhysicalAttackGoal<>(this, def.physicalAttacks(), moveSpeed, engageRange));
        } else {
            LOGGER.info("[Zenkai] {} SIN physical_attacks en su EntityStatDef (def={})", id, def);
        }
    }
}