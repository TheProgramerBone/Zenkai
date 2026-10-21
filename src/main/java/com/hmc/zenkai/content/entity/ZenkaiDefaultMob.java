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

/**
 * Base de cualquier NPC de combate de Zenkai (Saibaman, Guerrero Namekiano, Sombra, ogros...) y de los
 * NPC voladores pacíficos (ángeles). Desde 2026-10-02 la IA de combate es UNIVERSAL (petición del
 * usuario: "si toca ajustar IA de combate se soluciona todo desde una sola pasada, lo mismo para
 * el combate volador"): {@link #registerCombatGoals} pone en un solo sitio el orden de goals ya
 * depurado con la Sombra (ki → físicas → cuerpo a cuerpo → guardia → deambular/vuelo), y el
 * BLOQUEO vive aquí (antes solo en ShadowCloneEntity). Qué usa cada NPC lo dice su ficha JSON
 * (zenkai_entities: ki_attacks, physical_attacks, melee, can_fly, can_block); la subclase solo
 * decide a quién ataca (targetSelector) y lo propio de su papel (guardar un puesto, dar vueltas).
 */
public abstract class ZenkaiDefaultMob extends PathfinderMob implements GeoEntity, AttackTelegraph, BlockingMob {

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-CombatAI");

    /** Guardia sincronizada al cliente (la anima el controlador "Block"). */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_TRANSFORMING =
            net.minecraft.network.syncher.SynchedEntityData.defineId(ZenkaiDefaultMob.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_BLOCKING =
            net.minecraft.network.syncher.SynchedEntityData.defineId(ZenkaiDefaultMob.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    /** Reducción de daño en guardia. Los mobs no tienen la skill ki_block del jugador: número fijo
     *  a medio camino de su curva (0.80 sin skill .. 0.50 a nivel 5). Antes vivía en la Sombra. */
    private static final double BLOCK_DAMAGE_MULTIPLIER = 0.65;

    /** Con ki Y físicas: por encima de esta distancia, ki; por debajo, físicas (ver el javadoc de
     *  KiAttackGoal/PhysicalAttackGoal: minRange/engageRange). Sin esto, con muchos ki de cooldown
     *  corto ganaba ki SIEMPRE aunque el objetivo estuviera pegado (visto en juego con la Sombra). */
    private static final double KI_PHYSICAL_SWITCH_DISTANCE = 10.0;

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BLOCKING, false);
        builder.define(DATA_TRANSFORMING, false);
    }

    /** En plena animación de transformación (TransformationGoal). Sincronizado: lo anima el controlador
     *  "Transform" y lo lee el Ki Sense. Mientras dura el mob es invulnerable. */
    public boolean isTransforming() { return this.entityData.get(DATA_TRANSFORMING); }

    public void setTransforming(boolean transforming) { this.entityData.set(DATA_TRANSFORMING, transforming); }

    /** La ficha JSON de este mob, o null. */
    public com.hmc.zenkai.feature.combat.entity.EntityStatDef statDef() { return EntityStatsManager.get(entityId()); }

    @Override
    public boolean isInvulnerableTo(@NotNull DamageSource source) {
        if (isTransforming() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
        return super.isInvulnerableTo(source);
    }

    @Override
    public void setBlocking(boolean blocking) { this.entityData.set(DATA_BLOCKING, blocking); }

    @Override
    public boolean isBlockingNow() { return this.entityData.get(DATA_BLOCKING); }

    @Override
    public double blockDamageMultiplier() { return BLOCK_DAMAGE_MULTIPLIER; }

    /** Tipos de goal de combate que una subclase puede apagar por completo (ver
     *  {@link #combatGoalAllowed}). */
    public static final String GOAL_KI = "ki", GOAL_PHYSICAL = "physical",
            GOAL_MELEE = "melee", GOAL_BLOCK = "block";

    /**
     * Permiso para que un goal de combate EMPIECE. Siempre true salvo en el muñeco de pruebas
     * (ShadowCloneEntity con guion de "/zenkai debug dummy"), que apaga los goals que no le tocan
     * para hacer solo la acción pedida. Cada goal lo consulta al principio de canUse().
     */
    public boolean combatGoalAllowed(String kind) { return true; }

    /** ¿Sabe ponerse en guardia? "moveset.can_block" de su ficha. */
    public boolean canBlock() {
        var def = EntityStatsManager.get(entityId());
        return def != null && def.canBlock();
    }

    /**
     * La IA de combate entera, igual para cualquier NPC — llamar desde registerGoals() y luego
     * añadir solo lo propio (objetivos, puesto de guardia...). Prioridades:
     * <ol start="0">
     *   <li>Flotar en el agua y, si la ficha trae "transformation", transformarse por vida.</li>
     *   <li>Ki (si la ficha trae ki_attacks).</li>
     *   <li>Físicas (si trae physical_attacks).</li>
     *   <li>Cuerpo a cuerpo (si "melee" no es false).</li>
     *   <li>Guardia (si "can_block"; sin flags, corre en paralelo).</li>
     *   <li>(5-6 libres para la subclase.)</li>
     *   <li>Deambular: en vuelo si "can_fly", andando si no.</li>
     *   <li>Mirar a jugadores / alrededor.</li>
     * </ol>
     * Ki y físicas van POR DELANTE del cuerpo a cuerpo a propósito: con el melee delante, al
     * recalcular su camino desalojaba a ki/físicas a mitad de la carga y nunca disparaban
     * (encontrado con la Sombra). El melee usa followingTargetEvenIfNotSeen=false por la misma
     * razón (con true no suelta MOVE/LOOK nunca). Si la ficha trae ki y físicas, se reparten por
     * distancia (KI_PHYSICAL_SWITCH_DISTANCE).
     *
     * @param speed velocidad de persecución; las físicas van un 15 % más rápido (cierran distancia).
     */
    protected void registerCombatGoals(double speed) {
        var def = EntityStatsManager.get(entityId());
        boolean ki = def != null && def.hasKiAttacks();
        boolean phys = def != null && def.hasPhysicalAttacks();
        boolean split = ki && phys;

        this.goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.FloatGoal(this));
        if (def != null && def.hasTransformation()) {
            this.goalSelector.addGoal(0, new com.hmc.zenkai.content.entity.ai.TransformationGoal(this, def.transformations()));
        }
        if (ki) addKiAttackGoalIfDefined(1, speed, split ? KI_PHYSICAL_SWITCH_DISTANCE : 0.0);
        if (phys) addPhysicalAttackGoalIfDefined(2, speed * 1.15, split ? KI_PHYSICAL_SWITCH_DISTANCE : Double.MAX_VALUE);
        if (def == null || def.movesetMelee()) {
            this.goalSelector.addGoal(3, new com.hmc.zenkai.content.entity.ai.ZenkaiMeleeAttackGoal(this, speed, false));
        }
        if (def != null && def.canBlock()) {
            this.goalSelector.addGoal(4, new com.hmc.zenkai.content.entity.ai.BlockHabitGoal<>(this));
        }
        if (def != null && def.canFly()) {
            addFlightWanderGoalIfCanFly(7, speed);
        } else {
            this.goalSelector.addGoal(7, new net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal(this, speed * 0.7));
        }
        this.goalSelector.addGoal(8, new net.minecraft.world.entity.ai.goal.LookAtPlayerGoal(this,
                net.minecraft.world.entity.player.Player.class, 8.0F));
        this.goalSelector.addGoal(8, new net.minecraft.world.entity.ai.goal.RandomLookAroundGoal(this));
    }

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

        // Bloqueo: en cualquier NPC (universal desde 2026-10-02). Predicado, no trigger: mientras
        // isBlockingNow() (sincronizado) sea true reproduce/mantiene zenkai.block (clip real,
        // "hold_on_last_frame"); en cuanto deja de bloquear, STOP. Un mob sin can_block nunca
        // pone isBlockingNow a true, así que el controlador simplemente no hace nada. Se registra
        // siempre (y no según la ficha) porque esto corre también en el cliente, que no tiene por
        // qué tener la ficha JSON cargada.
        // Transformación: predicado sobre el dato sincronizado, como el bloqueo. STOP al terminar.
        controllers.add(new AnimationController<>(this, "Transform", 5,
                state -> isTransforming()
                        ? state.setAndContinue(ZenkaiCommonAnimations.TRANSFORMATION_MOB)
                        : PlayState.STOP));

        controllers.add(new AnimationController<>(this, "Block", 5,
                state -> isBlockingNow()
                        ? state.setAndContinue(ZenkaiCommonAnimations.ZENKAI_BLOCK)
                        : PlayState.STOP));
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

    /** Tick de juego a partir del cual este mob puede volver a parar un golpe (ver ParryServer.tryMobParry). */
    private long parryReadyAt = 0L;
    public long parryReadyAt() { return parryReadyAt; }
    public void setParryReadyAt(long tick) { this.parryReadyAt = tick; }

    /**
     * ¿Sabe kiai? Es lo que habilita al NPC para parar golpes: lo decide su ficha (physical_attacks
     * del datapack tiene una entrada "kiai"), igual que habilita su kiai en combate. Sin kiai en la
     * ficha, el mob no para nada.
     */
    public boolean knowsKiai() {
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(getType());
        var def = EntityStatsManager.get(id);
        if (def == null || !def.hasPhysicalAttacks()) return false;
        for (var a : def.physicalAttacks()) {
            if (a.type() == com.hmc.zenkai.feature.technique.PhysicalTechnique.KIAI) return true;
        }
        return false;
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