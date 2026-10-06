package com.hmc.zenkai.content.entity.misc;

import com.hmc.zenkai.content.entity.ZenkaiDefaultMob;
import com.hmc.zenkai.content.entity.ai.PosedAttacker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * "Train with your shadow" (TrainingHubScreen): un clon del propio jugador para entrenar solo.
 * Ver {@link com.hmc.zenkai.feature.training.ShadowTrainingManager} para el spawn/tracking/reward
 * — esta clase es solo la entidad (IA + identidad), el conjunto de la resolución de stats/TP vive allí.
 *
 * NEUTRAL HASTA QUE LE PEGAN: mismo comportamiento que {@link
 * com.hmc.zenkai.content.entity.overworld.SaibamanEntity}, pero SIN su
 * NearestAttackableTargetGoal&lt;Player&gt; — el targetSelector de esta clase lleva ÚNICAMENTE
 * HurtByTargetGoal, así que nunca inicia el combate por su cuenta, solo responde a un golpe.
 *
 * VUELA estilo Vex (vainilla) — pero YA NO hardcodeado en esta clase: la capacidad estructural
 * (FlyingPathNavigation + VexStyleFlightControl, ver {@link ZenkaiDefaultMob}) sale de
 * "moveset.can_fly" en shadow_clone.json, igual que cualquier otro zenkaimob. Lo que SÍ es
 * específico de la Sombra es el GATE por-instancia: {@link #canFlyNow()} además exige que el
 * DUEÑO tenga la skill fly desbloqueada (ver {@link #setFlightAllowed}, puesto por
 * ShadowTrainingManager.start() vía SkillEffects.canFly) — pedido explícito del usuario ("si el
 * jugador tiene la habilidad de volar, que la sombra también pueda volar"). Sin esa skill, la
 * Sombra se queda con la infraestructura de vuelo instalada pero setNoGravity nunca se reafirma
 * (ver ZenkaiDefaultMob.tick()), así que en la práctica se comporta con gravedad normal.
 *
 * En Zenkai el jugador pasa media pelea en el aire, así que una Sombra puramente terrestre
 * simplemente dejaba de poder alcanzarlo en cuanto despegaba. Sigue siendo NEUTRAL hasta que le
 * pegan — el vuelo es solo persecución, no agro proactivo.
 *
 * PRIORIDAD DE ATAQUE: Ki(1)/Físicas(2) van POR DELANTE de Melee(3) a propósito. Antes Melee
 * iba primero y, al recalcular su camino cada ~20 ticks (MeleeAttackGoal.canUse()), podía
 * interrumpir a Ki/Físicas A MITAD DE LA CARGA (WrappedGoal.canBeReplacedBy: un goal de mejor
 * prioridad desaloja al que ya esté corriendo) — así que nunca llegaban a disparar. Con Ki/
 * Físicas por delante, Melee ya no puede desalojarlas; solo actúa de relleno cuando ninguna de
 * las dos tiene nada listo.
 *
 * MODELO: GeckoLib de verdad — geo/shadow_clone.geo.json (rig humanoide, copia EXACTA de
 * geo/saibaman.geo.json sin el 0.75 de escala: mismos huesos/proporciones que un jugador
 * vainilla) + {@link com.hmc.zenkai.client.render_and_model_entities.entity.PosedHumanoidGeoModel}
 * + textura FIJA totalmente negra (no el skin real del dueño). ANTES usaba {@code PlayerModel}
 * vainilla con un sampler propio a mano para reproducir clips — se abandonó tras confirmar
 * jugando que 1) el swing 100% vainilla no se veía y 2) el clip de swing reusado de
 * GeckoLib (attack.strike) se aplicaba con la convención de rotación EQUIVOCADA (GeckoLib niega
 * X/Y al cargar Bedrock JSON, PAL no — confirmado decompilando ambas librerías). Pasar la Sombra
 * a GeckoLib de verdad resuelve las dos cosas de raíz: `triggerAnim` deja de ser inerte (el swing
 * es ahora el MISMO mecanismo nativo que ya usan Saibaman/NamekianWarrior) y ya no hace falta
 * ningún sampler manual con negación de ejes para el melee. Pedido explícito del usuario: "todos
 * los NPCs que se deriven de este sistema serán GeckoLib humanoides" — este es el primero.
 *
 * IMPLEMENTA PosedAttacker: swing/ki_charge/ki_shoot/las 4 técnicas físicas/bloqueo YA usan
 * triggerAnim (o un controlador predicado, para el bloqueo) de verdad — clips REALES de
 * zenkai_animations.animation.json (zenkai.phys_*, copiados tal cual de
 * player_animations/phys_*.animation.json; zenkai.block, que ya vivía ahí sin usarse), motor
 * nativo de GeckoLib, sin sampler propio ni convención de ejes que adivinar. Solo queda la pose
 * de ki (un brazo fijo, no una animación, pedido explícito "es como mejor así") en este canal —
 * ver {@link com.hmc.zenkai.client.render_and_model_entities.entity.PosedHumanoidGeoModel
 * #setCustomAnimations}.
 *
 * BLOQUEO (de hábito + REACTIVO, BlockHabitGoal): desde 2026-10-02 lo implementa
 * ZenkaiDefaultMob para cualquier NPC con "can_block" en su ficha; la Sombra fue la primera en
 * tenerlo y de aquí salió el diseño.
 */
public class ShadowCloneEntity extends ZenkaiDefaultMob implements PosedAttacker {

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-ShadowAI");

    private static final EntityDataAccessor<Integer> DATA_ATTACK_POSE =
            SynchedEntityData.defineId(ShadowCloneEntity.class, EntityDataSerializers.INT);

    // Bloqueo y reparto ki/físicas por distancia: ya NO viven aquí — se subieron a
    // ZenkaiDefaultMob (2026-10-02, IA de combate universal). La Sombra los activa por su ficha
    // (shadow_clone.json: can_block, ki_attacks y physical_attacks).

    /** Dueño (quien la invocó) — puramente informativo/para limpieza; el tracking real de
     *  "qué sombra pertenece a qué jugador" vive en ShadowTrainingManager. */
    private UUID ownerId;

    /** Gate por-instancia sobre la capacidad de vuelo estructural (JSON) — ver
     *  {@link #canFlyNow()}. Default true: si nadie llama setFlightAllowed (p. ej. un futuro
     *  spawn de la Sombra que no pase por ShadowTrainingManager), se comporta como antes de
     *  este cambio. ShadowTrainingManager.start() lo fija a SkillEffects.canFly(dueño) justo
     *  después de setOwner, antes de addFreshEntity. */
    private boolean flightAllowed = true;

    public void setFlightAllowed(boolean flightAllowed) {
        this.flightAllowed = flightAllowed;
        refreshNavigationType(); // ver su javadoc: sin esto se queda con FlyingPathNavigation aunque nunca vaya a volar
    }

    /** Además de la capacidad estructural del JSON (super), exige que el DUEÑO tenga la skill
     *  fly — ver el javadoc de clase. */
    @Override
    public boolean canFlyNow() {
        return super.canFlyNow() && flightAllowed;
    }

    public ShadowCloneEntity(EntityType<? extends ZenkaiDefaultMob> type, Level level) {
        super(type, level);
    }

    /** Log de diagnóstico: cuándo y a quién apunta la Sombra. Si nunca aparece "target -> null"
     *  tras el primer golpe, el objetivo no se está perdiendo — el problema está en los goals
     *  de ataque, no en HurtByTargetGoal. */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target);
        LOGGER.info("[Zenkai] Sombra {} target -> {}", this.getId(), target);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ATTACK_POSE, POSE_NONE);
    }

    @Override
    public void setAttackPose(int pose) { this.entityData.set(DATA_ATTACK_POSE, pose); }

    @Override
    public int getAttackPose() { return this.entityData.get(DATA_ATTACK_POSE); }

    @Override
    protected void registerGoals() {
        // IA de combate universal (ver ZenkaiDefaultMob.registerCombatGoals): ki/físicas por
        // delante del cuerpo a cuerpo, reparto por distancia, guardia y vuelo — todo lo que antes
        // estaba escrito aquí a mano, ahora compartido con cualquier NPC de combate.
        registerCombatGoals(1.4D);

        // SOLO HurtByTargetGoal: a propósito no hay NearestAttackableTargetGoal aquí, así
        // nunca ataca primero — el pedido explícito era "IA neutral al inicio, solo se vuelve
        // agresiva cuando el jugador le golpea".
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, ShadowCloneEntity.class).setAlertOthers());
    }

    public void setOwner(Player owner) {
        this.ownerId = owner.getUUID();
        Component name = Component.literal(owner.getName().getString() + "'s Shadow");
        setCustomName(name);
        setCustomNameVisible(true);
    }

    public UUID getOwnerId() { return ownerId; }

    @Override
    public boolean isAlliedTo(@NotNull Entity entity) {
        if (super.isAlliedTo(entity)) return true;
        return entity instanceof ShadowCloneEntity;
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Los números reales (vida, daño) los fija EntityStats.applyDef/mirrorToVanilla al
        // spawnear (ver ShadowTrainingManager) — estos son solo el piso mientras eso no ha
        // corrido todavía, mismo criterio que cualquier otra ZenkaiDefaultMob.
        return AbstractVillager.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                // Explícito y generoso a propósito (antes heredaba el default de
                // AbstractVillager sin que nadie lo hubiera decidido): un sparring que se rinde
                // en cuanto el jugador se aleja un poco no sirve de mucho como entrenamiento.
                .add(Attributes.FOLLOW_RANGE, 64.0);
                // Attributes.FLYING_SPEED YA NO HACE FALTA: VexStyleFlightControl (ver
                // ZenkaiDefaultMob) empuja velocidad directamente (estilo Vex) en vez de leer ese
                // atributo. Si algún día se vuelve a un MoveControl basado en atributos, esta
                // línea habría que devolverla (si no, crashea al despegar).
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("owner", ownerId);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("owner")) ownerId = tag.getUUID("owner");
    }
}
