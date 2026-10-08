package com.hmc.zenkai.content.entity.technique;

import com.hmc.zenkai.feature.combat.SenseServerState;
import com.hmc.zenkai.feature.combat.ZenkaiStats;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.technique.TechniqueEffect;
import com.hmc.zenkai.registry.ModDamageTypes;
import com.hmc.zenkai.registry.ModEntities;
import com.hmc.zenkai.registry.ModGameRules;
import com.hmc.zenkai.registry.ModParticles;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SimpleExplosionDamageCalculator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

/**
 * Proyectil ki (sistema nuevo, desde cero). Tipo/color/tamaño viajan como entity data
 * (el renderer los lee directamente). El DAÑO se fija en servidor al disparar
 * (kiPower × dmgMult × sizeF, ver KiFirePacket) y entra al pipeline de combate como
 * proyectil: CombatZenkaiHooks NO lo recalcula como melee (bypass por instanceof).
 * Tipos especiales:
 *  - SPIRAL: vuela recto. El nombre viene de la forma helicoidal (ver KiVfxProfile/KiVfxGeometry),
 *    no de la trayectoria — llevó oscilación perpendicular y se retiró porque además torcía la
 *    estela.
 *  - BARRIER: no se mueve ni golpea; sigue el centro del dueño y muere al expirar
 *    (la absorción de daño vive en KiCombatServer, esta entidad es solo el visual).
 * Explosiva: al impactar (bloque o entidad) genera daño en ÁREA con caída lineal —
 * radio 1.5 + 0.35×size, daño AoE = 60% del directo. Sin daño a bloques (griefing off).
 * El objetivo directo recibe el daño completo y se excluye del AoE (no doble golpe);
 * el dueño también se excluye (sin auto-daño).
 * Efectos (TechniqueEffect; UNO SOLO por técnica, ver KiTechniqueType.allowsEffect para qué
 * tipo admite cuál):
 *  - PIERCING: atraviesa la primera entidad golpeada y sigue (mismo `pierced` que ya usa
 *    DISK sin necesidad de marcar nada — ver onHitEntity).
 *  - HOMING: applyHoming() curva el rumbo hacia el lock de Ki Sense del dueño, un poco cada
 *    tick.
 *  - FRAGMENTATION: al detonar, spawnFragments() suelta 3-4 bolas hijas más débiles y sin
 *    efecto propio.
 *  - LINGERING: al detonar, spawnLingeringZone() deja una KiLingeringZoneEntity que sigue
 *    dañando unos segundos.
 * Sin gravedad; muere al chocar o al agotar la vida.
 */
public class KiProjectileEntity extends Projectile {

    private static final EntityDataAccessor<Byte> DATA_TYPE =
            SynchedEntityData.defineId(KiProjectileEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_RGB =
            SynchedEntityData.defineId(KiProjectileEntity.class, EntityDataSerializers.INT);
    /** Segundo color (interior), -1 = ninguno. Ver KiTechnique.rgb2. */
    private static final EntityDataAccessor<Integer> DATA_RGB2 =
            SynchedEntityData.defineId(KiProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_SIZE =
            SynchedEntityData.defineId(KiProjectileEntity.class, EntityDataSerializers.BYTE);
    /** Ver {@link #freeze(Vec3)} — "/zenkai debug kivfx" congela una copia en el aire para
     *  comparar contra las referencias sin tener que dispararla ni perseguirla en vuelo. */
    private static final EntityDataAccessor<Boolean> DATA_FROZEN =
            SynchedEntityData.defineId(KiProjectileEntity.class, EntityDataSerializers.BOOLEAN);

    /** Retenido por un choque de haces (ver ClashManager). SINCRONIZADO: si solo lo supiera el
     *  servidor, el cliente seguiría prediciendo el avance cada tick y la bola se escaparía del
     *  punto de choque hasta el siguiente paquete de posición, que no llega porque no se mueve. */
    private static final EntityDataAccessor<Boolean> DATA_CLASH_HELD =
            SynchedEntityData.defineId(KiProjectileEntity.class, EntityDataSerializers.BOOLEAN);

    // ── Estela (SOLO cliente): historial de posiciones del centro, cabeza primero.
    //    Lo llena tick() y lo lee KiVfxProjectileRenderer. En server queda vacío. ──
    private final java.util.ArrayDeque<Vec3> trail = new java.util.ArrayDeque<>();
    public java.util.Deque<Vec3> trailHistory() { return trail; }

    /** Techo del historial de estela. Es el máximo que cualquier KiVfxProfile puede pedir; grabar
     *  de más cuesta un Vec3 por tick y ahorra tener el largo declarado en dos sitios. */
    public static final int TRAIL_MAX = 34;

    // ── Camino de la cabeza (SOLO cliente) para el haz ANCLADO: el cuerpo del haz no es la recta
    //    origen→cabeza sino la trayectoria que la cabeza ha recorrido de verdad — con HOMING esa
    //    trayectoria se curva y el haz debe curvarse con ella, como una serpiente que persigue al
    //    objetivo, no una barra recta que solo gira. Se guarda con separación mínima entre puntos
    //    y, si se llena, se ADELGAZA (se tira uno de cada dos y se dobla la separación) en vez de
    //    cortarse, para que un haz larguísimo conserve su origen. ──
    private static final int BEAM_PATH_MAX = 256;
    private static final double BEAM_PATH_MIN_STEP = 0.35;
    private final java.util.ArrayList<Vec3> beamPath = new java.util.ArrayList<>();
    private double beamPathMinSq = BEAM_PATH_MIN_STEP * BEAM_PATH_MIN_STEP;

    /** Centros de la cabeza en cada tick, del primero al último (último = posición del tick
     *  actual). Vista de solo lectura; vacía en servidor y en una copia congelada. */
    public java.util.List<Vec3> beamPath() { return java.util.Collections.unmodifiableList(beamPath); }

    private void recordBeamPath(Vec3 center) {
        if (!beamPath.isEmpty() && beamPath.get(beamPath.size() - 1).distanceToSqr(center) < beamPathMinSq) return;
        beamPath.add(center);
        if (beamPath.size() > BEAM_PATH_MAX) {
            java.util.ArrayList<Vec3> thinned = new java.util.ArrayList<>(BEAM_PATH_MAX / 2 + 1);
            for (int i = 0; i < beamPath.size(); i += 2) thinned.add(beamPath.get(i));
            thinned.add(beamPath.get(beamPath.size() - 1));
            beamPath.clear();
            beamPath.addAll(thinned);
            beamPathMinSq *= 4.0; // doble separación = cuádruple en distancia al cuadrado
        }
    }

    /** Desduplicador de efectos por tick para el renderer, que corre por FRAME. Devuelve true
     *  una sola vez por tick. Cliente: en servidor nadie lo llama. */
    private int lastFxTick = -1;
    public boolean consumeFxTick() {
        if (lastFxTick == tickCount) return false;
        lastFxTick = tickCount;
        return true;
    }

    // ── Base perpendicular ESTABLE para geometría de estela EN ESPACIO DE MUNDO (SOLO cliente).
    //    Nació para la estela en doble hélice (KiVfxProfile.helixTrail) pero KiVfxProjectileRenderer.
    //    renderTrail() la reutiliza también para la estela RECTA de cualquier haz: los dos casos
    //    necesitan lo mismo, un ancho que NO dependa de la cámara (ver el javadoc de
    //    KiRibbon para el bug de "volteo" que esto evita, ver KiAxis). Se fija UNA
    //    SOLA VEZ, la primera vez que hace falta, a partir de la dirección de vuelo EN ESE
    //    INSTANTE — nunca se recalcula después. Si se recalculara contra la dirección
    //    instantánea, un giro brusco del proyectil (p. ej. HOMING corrigiendo el rumbo)
    //    retorcería la geometría de golpe en vez de dejarla girar suavemente (misma lección que
    //    FlightMovement.refSpeed: no derivar "cómo debería ser esto ahora" de un valor que
    //    cambia tick a tick). ──
    private Vec3 flightRight, flightUp;

    // ── Punto de disparo del HAZ ANCLADO (SOLO cliente, ver KiVfxProfile.column): de dónde sale
    //    el tubo que se alarga hasta la cabeza. Se fija la PRIMERA vez que el renderer lo pide
    //    (posición del centro al aparecer en el cliente, que es la de las manos del dueño) y no
    //    se mueve después: si siguiera al dueño, un haz disparado en vuelo se doblaría con él. ──
    private Vec3 beamOrigin;

    public Vec3 beamOrigin(Vec3 ifUnset) {
        if (beamOrigin == null) beamOrigin = ifUnset;
        return beamOrigin;
    }

    /** @return [derecha, arriba] — perpendiculares entre sí y a la dirección de vuelo fijada en
     *  el primer uso. */
    public Vec3[] flightBasis() {
        if (flightRight == null) {
            Vec3 dir = getDeltaMovement();
            if (dir.lengthSqr() < 1.0e-6) dir = new Vec3(0, 0, 1);
            dir = dir.normalize();
            // "Arriba" de referencia: el eje mundo Y, salvo que el proyectil vuele casi vertical
            // (dir paralelo a Y), donde ese cruce degenera a un vector casi nulo.
            Vec3 worldUp = Math.abs(dir.y) > 0.999 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            flightRight = dir.cross(worldUp).normalize();
            flightUp = flightRight.cross(dir).normalize();
        }
        return new Vec3[]{flightRight, flightUp};
    }

    // ── DISK (siempre) y PIERCING (si se eligió ese efecto): ids de entidades ya atravesadas
    //    (solo server, para no re-golpear). No persiste en NBT a propósito: el proyectil vive
    //    segundos. ──
    private final java.util.Set<Integer> pierced = new java.util.HashSet<>();

    private double damage = 0;
    /** Margen de impacto por bloque de ancho del hitbox. El cuerpo se DIBUJA a 1.5x el ancho (radio
     *  0.75x, ver KiTechniqueType.visualDiameter); 0.6 deja el impacto un poco por dentro de la
     *  silueta visible, para que rozar el borde brillante no cuente como golpe pero sí el cuerpo. */
    private static final float HIT_MARGIN_PER_WIDTH = 0.6F;

    private int life = 100;
    private TechniqueEffect effect = TechniqueEffect.NONE;

    public KiProjectileEntity(EntityType<? extends KiProjectileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
        // En el CONSTRUCTOR, no en configure(): noCulling solo lo lee el renderer del CLIENTE
        // (EntityRenderer.shouldRender), y configure() corre solo en el servidor — en el cliente
        // la entidad nace del paquete de spawn por aquí. Con el flag puesto solo en configure(),
        // el cliente recortaba la técnica por su HITBOX, más pequeño que lo que se dibuja: la
        // BARRIER desaparecía entera según el ángulo de la cámara en tercera persona (reporte del
        // usuario 2026-09-24) y las estelas largas se cortaban al salir la cabeza de pantalla.
        this.noCulling = true;
    }

    /** Configuración al disparar (solo servidor; el data syncer propaga al cliente). */
    public void configure(LivingEntity owner, KiTechniqueType type, int rgb, int size,
                          double damage, int lifeTicks, TechniqueEffect effect) {
        setOwner(owner);
        this.entityData.set(DATA_TYPE, (byte) type.ordinal());
        this.entityData.set(DATA_RGB, rgb & 0xFFFFFF);
        this.entityData.set(DATA_SIZE, (byte) size);
        this.damage = damage;
        this.life = lifeTicks;
        this.effect = (effect == null || !type.allowsEffect(effect))
                ? TechniqueEffect.NONE : effect;
        refreshDimensions();
    }

    /** Tag para localizar y limpiar SOLO las entidades creadas por "/zenkai debug kivfx" —
     *  nunca un proyectil de combate real, aunque comparta clase. */
    public static final String DEBUG_TAG = "zenkai_debug_kivfx";

    /**
     * Congela esta entidad para inspección visual ("/zenkai debug kivfx spawn"): a partir de
     * aquí {@link #tick()} no la mueve, no comprueba impactos y no la hace expirar — se queda
     * flotando exactamente donde esté hasta que alguien la borre ("kivfx clear").
     * {@code facing} se guarda como velocidad (nunca aplicada a la posición mientras esté
     * congelada, ver tick()) solo para que lo que SÍ lee {@code getDeltaMovement()} sin mirar si
     * el tipo viaja de verdad —la orientación de formas no-esféricas en
     * {@code KiVfxProjectileRenderer.render()} y la base perpendicular de {@link #flightBasis()}—
     * siga dando una dirección coherente en vez de un vector nulo.
     * NO rellena la estela aquí: esta clase es COMÚN a servidor y cliente, y la estela depende
     * de {@code KiVfxProfile} (código de cliente) para saber cuántos puntos/qué tan separados —
     * la sintetiza {@code KiVfxProjectileRenderer.synthesizeFrozenTrail} la primera vez que la
     * dibuja, en el único sitio que ya conoce esa clase.
     */
    public void freeze(Vec3 facing) {
        this.entityData.set(DATA_FROZEN, true);
        setDeltaMovement(facing.lengthSqr() > 1.0e-6 ? facing.normalize() : new Vec3(0, 0, 1));
        addTag(DEBUG_TAG);
    }

    public boolean isFrozen() { return this.entityData.get(DATA_FROZEN); }

    public KiTechniqueType techniqueType() {
        int i = this.entityData.get(DATA_TYPE);
        KiTechniqueType[] all = KiTechniqueType.values();
        return all[Math.min(Math.max(i, 0), all.length - 1)];
    }

    public int rgb()  { return this.entityData.get(DATA_RGB); }
    public int rgb2() { return this.entityData.get(DATA_RGB2); }

    /** Tras configure(): el segundo color no está en su firma para no tocar a los que no lo
     *  tienen (NPCs, KiAttackGoal). */
    public void setRgb2(int v) { this.entityData.set(DATA_RGB2, v < 0 ? -1 : (v & 0xFFFFFF)); }
    public int size() { return this.entityData.get(DATA_SIZE); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TYPE, (byte) KiTechniqueType.BLAST.ordinal());
        builder.define(DATA_RGB, 0xFFFFFF);
        builder.define(DATA_RGB2, -1);
        builder.define(DATA_SIZE, (byte) 1);
        builder.define(DATA_FROZEN, false);
        builder.define(DATA_CLASH_HELD, false);
    }

    // ── Choque de haces (lo orquesta feature/technique/clash/ClashManager) ──

    public boolean isClashHeld() { return this.entityData.get(DATA_CLASH_HELD); }

    /** Daño con el que se disparó (el que se compara y se suma en un choque). */
    public double damage() { return damage; }

    /** Retiene el proyectil en el sitio (true) o lo suelta (false). Retenido no avanza, no
     *  comprueba impactos y no gasta vida. */
    public void setClashHeld(boolean held) {
        this.entityData.set(DATA_CLASH_HELD, held);
    }

    /** ¿Puede participar en un choque? Solo lo que VIAJA, golpea y no atraviesa por identidad. */
    /**
     * ALIMENTADOR de un choque de haces (fase 3 del choque por equipos): el haz de quien se UNE a
     * un choque sale de sus manos y vuela hasta la esfera, donde ClashManager lo retiene. Mientras
     * vuela no puede empezar otro choque ni golpear a nada — es solo el chorro que conecta al
     * ayudante con la esfera (el haz anclado se dibuja desde donde el cliente lo vio nacer, sus
     * manos, hasta la cabeza). Solo servidor: el cliente no necesita saberlo.
     */
    private boolean clashFeeder;

    public void markClashFeeder() {
        this.clashFeeder = true;
        this.life = Integer.MAX_VALUE; // ClashManager lo retira al acabar el choque
    }

    public boolean isClashFeeder() { return clashFeeder; }

    public boolean canClash() {
        return !clashFeeder && !isFrozen() && !isClashHeld() && techniqueType().travels()
                && techniqueType() != KiTechniqueType.DISK && damage > 0.0;
    }

    /**
     * El haz ganador sale de nuevo, ya reforzado, hacia el perdedor. Es el MISMO proyectil (mismo
     * tipo, color y camino de cabeza), así que visualmente el haz se alarga desde el ganador
     * atravesando el punto de choque hasta el rival.
     * @param dir        dirección de salida (se normaliza)
     * @param speedMult  fracción de la velocidad normal del tipo
     * @param newDamage  daño ya sumado
     */
    public void clashRelaunch(Vec3 dir, double speedMult, double newDamage,
                              LivingEntity homeOn, double homingTurn, double homingRange) {
        this.returnHomeTarget = homeOn == null || homingTurn <= 0.0 ? null : homeOn.getUUID();
        this.returnHomeTurn = homingTurn;
        this.returnHomeRange = homingRange;
        this.damage = newDamage;
        this.pierced.clear();
        this.flightRight = null; // la dirección cambió: la base del haz se recalcula
        this.life = Math.max(this.life, 100);
        setDeltaMovement(dir.normalize().scale(techniqueType().speed() * speedMult));
        setClashHeld(false);
        this.hasImpulse = true;
    }

    /**
     * Desvío LATERAL del kiai: aparta el proyectil hacia un lado y un poco hacia arriba SIN cambiar
     * de dueño ni devolverlo (devolver proyectiles es cosa del parry). Pasa a su lado en vez de
     * golpear a quien lo desvió. Conserva la velocidad.
     */
    public void scatter(java.util.Random rng) {
        Vec3 vel = getDeltaMovement();
        double speed = vel.length();
        if (speed < 1.0e-6) return;
        Vec3 fwd = vel.scale(1.0 / speed);
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 side = fwd.cross(up);
        side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
        if (rng.nextBoolean()) side = side.scale(-1.0);
        Vec3 dir = fwd.scale(0.25).add(side.scale(0.75)).add(up.scale(0.35 + 0.25 * rng.nextDouble()))
                .normalize();
        setDeltaMovement(dir.scale(speed));
        this.hasImpulse = true;
    }

    /**
     * Desvío del parry NORMAL: como {@link #scatter}, pero además el proyectil ya no puede volver a
     * golpear a quien lo desvió (se añade a {@code pierced}): sin esto, un haz grande que sigue solapando
     * al defensor el tick siguiente le haría el daño que acaba de parar. Solo el parry PERFECTO devuelve
     * el proyectil a su dueño ({@link #deflect}).
     */
    public void scatterAround(java.util.Random rng, Entity parrier) {
        pierced.add(parrier.getId());
        scatter(rng);
    }

    // Guiado del golpe de vuelta de un choque: persigue al perdedor, pero SOLO mientras esté
    // dentro de returnHomeRange. Fuera de ese radio vuela recto, así que alejarse lo bastante
    // (p. ej. con Transmisión Instantánea) sigue siendo una forma de esquivarlo.
    private java.util.UUID returnHomeTarget;
    private double returnHomeTurn;
    private double returnHomeRange;

    private void applyReturnHoming() {
        if (!(level() instanceof ServerLevel sl)) return;
        if (!(sl.getEntity(returnHomeTarget) instanceof LivingEntity t) || !t.isAlive()) {
            returnHomeTarget = null;
            return;
        }
        Vec3 toTarget = t.getBoundingBox().getCenter().subtract(position());
        double dist = toTarget.length();
        if (dist < 1.0e-4 || dist > returnHomeRange) return;
        Vec3 vel = getDeltaMovement();
        double speed = vel.length();
        if (speed <= 0.0) return;
        setDeltaMovement(vel.normalize().scale(1.0 - returnHomeTurn)
                .add(toTarget.normalize().scale(returnHomeTurn))
                .normalize().scale(speed));
    }

    /** Empate: el haz detona donde está, con su daño reducido. */
    public void clashDetonate(double damageFraction) {
        this.damage *= Math.max(0.0, damageFraction);
        detonate(position().add(0, getBbHeight() * 0.5, 0), null);
        discard();
    }

    /** Se disipa sin daño (el haz perdedor, absorbido por el ganador). */
    public void clashDissipate() {
        if (level() instanceof ServerLevel sl) {
            Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
            sl.sendParticles(ModParticles.spark(rgb(), 1.0f), c.x, c.y, c.z,
                    10 + size() * 2, 0.4, 0.4, 0.4, 0.2);
        }
        discard();
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SIZE.equals(key) || DATA_TYPE.equals(key)) refreshDimensions();
    }

    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull Pose pose) {
        // Un tamaño 5 no significa lo mismo en un láser que en un big blast: la escala vive en
        // el tipo, que es quien conoce su propia proporción.
        float d = (float) techniqueType().projectileSize(size());
        return EntityDimensions.scalable(d, d);
    }

    @Override
    public void tick() {
        super.tick();

        // Congelada por "/zenkai debug kivfx" (ver freeze()): ni movimiento, ni detección de
        // impacto, ni cuenta atrás de vida — se queda exactamente donde está hasta que se borre
        // a mano. Antes de cualquier otra rama, incluida la de "no viaja": una BARRIER/EXPLOSION
        // congelada tampoco debe seguir al dueño.
        if (isFrozen()) return;

        // Retenido por un choque de haces: quieto, sin impacto y sin gastar vida (en ambos lados).
        if (isClashHeld()) return;

        // Lo que no viaja va pegado al dueño. La barrera expira sin más; la explosión DETONA
        // al expirar — su `life` es la mecha, no su duración.
        if (!techniqueType().travels()) {
            tickAttached();
            return;
        }

        if (!level().isClientSide && !clashFeeder) {
            // ¿Se cruza con el haz de otro dueño? Si es así, ClashManager lo retiene y este tick
            // no avanza más (los dos quedan en el punto medio).
            if (com.hmc.zenkai.feature.technique.clash.ClashManager.tryStart(this)) return;

            if (returnHomeTarget != null) applyReturnHoming();
            else if (effect == TechniqueEffect.HOMING) applyHoming();

            // Margen de impacto contra ENTIDADES proporcional al tamaño de la técnica. Vanilla usa 0.3
            // fijo (getHitResultOnMoveVector) y trata al proyectil como un RAYO: una bola de 4
            // bloques de diámetro solo golpeaba lo que estuviera en su línea central, aunque
            // visualmente envolviera al objetivo. Con el margen escalado, lo que roza el cuerpo que se
            // ve también recibe el golpe.
            float margin = Math.max(0.3F, getBbWidth() * HIT_MARGIN_PER_WIDTH);
            // (ProjectileUtil.getHitResult con margen es privado: se repite aquí su mismo orden —
            // primero el bloque, que acota el recorrido, y después las entidades hasta ese punto.)
            Vec3 start = position();
            Vec3 end = start.add(getDeltaMovement());
            HitResult hit = level().clip(new net.minecraft.world.level.ClipContext(start, end,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS) end = hit.getLocation();
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level(), this, start, end,
                    getBoundingBox().expandTowards(getDeltaMovement()).inflate(1.0 + margin),
                    this::canHitEntity, margin);
            if (entityHit != null) hit = entityHit;
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
                if (isRemoved()) return;
            }
        }

        Vec3 vel = getDeltaMovement();
        Vec3 next = position().add(vel);

        setPos(next.x, next.y, next.z);

        // Estela: historial de posiciones en cliente (cabeza primero). Se graba SIEMPRE hasta
        // TRAIL_MAX y es el cliente (KiVfxProfile) quien decide cuántos puntos dibuja y con qué
        // ancho. Antes la longitud la mandaba el enum, que es código común: con eso, activar
        // una estela era tocar código de identidad compartido con el servidor.
        if (level().isClientSide) {
            trail.addFirst(position().add(0, getBbHeight() * 0.5, 0));
            while (trail.size() > TRAIL_MAX) trail.removeLast();
            recordBeamPath(trail.getFirst());
        }

        if (!level().isClientSide && --life <= 0) discard();
    }

    /** Sigue el centro del dueño; muere al expirar o si el dueño desaparece. */
    private void tickAttached() {
        Entity owner = getOwner();
        if (owner == null || !owner.isAlive()) {
            if (!level().isClientSide) discard();
            return;
        }
        Vec3 c = owner.position().add(0, owner.getBbHeight() * 0.5, 0);
        setPos(c.x, c.y - getBbHeight() * 0.5, c.z);

        if (!level().isClientSide && --life <= 0) {
            if (techniqueType() == KiTechniqueType.EXPLOSION) detonate(c, null);
            discard();
        }
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level().isClientSide) return;
        // Parry: si el golpeado está en su ventana, el proyectil se desvía y vuelve a su dueño en
        // vez de hacer daño. Sigue volando (no detona ni se descarta).
        if (hit.getEntity() instanceof ServerPlayer parrier
                && com.hmc.zenkai.feature.combat.ParryServer.tryParryProjectile(parrier, this)) {
            return;
        }
        LivingEntity owner = getOwner() instanceof LivingEntity le ? le : null;

        if (level().getServer() == null || ModGameRules.enableKiDamage(Objects.requireNonNull(level().getServer()))) {
            hit.getEntity().hurt(
                    damageSources().source(ModDamageTypes.forKiHit(techniqueType()), this, owner),
                    (float) damage);
        }

        // DISK atraviesa SIEMPRE (es su identidad de tipo, ver KiTechniqueType.allowsEffect);
        // PIERCING pone el mismo mecanismo a disposición de los demás tipos como una elección.
        // En los dos casos: marca al objetivo y sigue volando, sin detonar — el impacto con
        // bloque (onHit) sigue siendo lo único que dispara la explosión de un proyectil
        // perforante, nada de un tren de explosiones por cada entidad de por medio.
        if (techniqueType() == KiTechniqueType.DISK || effect == TechniqueEffect.PIERCING) {
            pierced.add(hit.getEntity().getId());
            return;
        }

        detonate(hit.getEntity().position(), hit.getEntity());
        discard();
    }

    /**
     * HOMING: si el dueño tiene un lock de Ki Sense (tecla de fijar), curva el rumbo hacia el
     * objetivo un poco cada tick — corrección SUAVE (HOMING_TURN), no un misil de persecución
     * perfecta. Atado a la HABILIDAD, no al MODO: basta con tener Ki Sense desbloqueado
     * (SkillEffects.lockOnBlocked, el mismo gate que ya usa el fijado en cliente —
     * LockOnClientState/SkillEffects.lockOnBlocked) para fijar y para que esto funcione; NO
     * hace falta tener el sentir el ki encendido (SenseServerState.senseActive es una cosa
     * distinta: si el escaneo periódico de modo sigue llegando, no si HAY lock). Sin la
     * habilidad o sin lock, no corrige nada y vuela recto como cualquier otro proyectil.
     *
     * HOMING_TURN es el PESO de la mezcla, no un ángulo: el giro máximo por tick sale de
     * atan(t/(1-t)), así que subirlo un 20 % (0.12 -> 0.144, ajuste pedido en 2026-09-06) da
     * algo más de un 20 % de ángulo — de 7.8° a 9.6° por tick. Se sube el peso y no el ángulo
     * porque es lo que de verdad hay escrito en la fórmula; si algún día se quiere razonar en
     * grados, hay que cambiar la mezcla por una rotación acotada, no reinterpretar esta
     * constante.
     */
    private static final double HOMING_TURN = 0.144;

    private void applyHoming() {
        if (!(getOwner() instanceof ServerPlayer sp)) return;
        if (SkillEffects.lockOnBlocked(sp)) return;
        int lockId = SenseServerState.lockOf(sp);
        if (lockId < 0) return;

        Entity target = level().getEntity(lockId);
        if (!(target instanceof LivingEntity le) || !le.isAlive() || target == getOwner()) return;

        Vec3 toTarget = le.getBoundingBox().getCenter().subtract(position());
        if (toTarget.lengthSqr() < 1.0E-4) return;

        Vec3 vel = getDeltaMovement();
        double speed = vel.length();
        if (speed <= 0.0) return;

        Vec3 newDir = vel.normalize().scale(1.0 - HOMING_TURN)
                .add(toTarget.normalize().scale(HOMING_TURN))
                .normalize();
        setDeltaMovement(newDir.scale(speed));
    }

    @Override
    protected void onHit(@NotNull HitResult hit) {
        super.onHit(hit);
        if (!level().isClientSide && hit.getType() == HitResult.Type.BLOCK) {
            detonate(hit.getLocation(), null);
            discard();
        }
    }

    /** Poder de ki de referencia con el que se calculó el daño. Se congela al desviar,
     *  porque a partir de ahí el dueño ya no es quien disparó y la defensa del receptor
     *  se escala contra el poder del ORIGINAL, no contra el de quien lo devolvió. */
    private double refPower = 0.0;

    public double refPower() { return refPower; }

    /**
     * Impacto: daño en ÁREA siempre, rotura de bloques con el efecto EXPLOSIVE o con `breaks_blocks`.
     * ANTES el área y la destrucción iban juntas bajo la marca `explosive`; separarlas es lo
     * que hace que la marca signifique una cosa sola. Consecuencia asumida: ahora cualquier técnica
     * reparte algo de área, según el aoeFactor de su tipo (un láser, un 20 %).
     * El objetivo directo y el dueño quedan fuera del área: el primero ya cobró el golpe
     * entero y el segundo nunca se daña a sí mismo — el autodaño de la explosión es otra cosa
     * y se aplica en KiFirePacket.
     */
    private void detonate(Vec3 center, Entity directHit) {
        if (!(level() instanceof ServerLevel sl)) return;

        double radius = techniqueType().explosionRadius(size());
        if (radius <= 0.0) return;

        double aoe = techniqueType().aoeFactor();
        double edge = techniqueType().aoeEdgeFalloff();
        LivingEntity owner = getOwner() instanceof LivingEntity le ? le : null;

        if (aoe > 0.0 && ModGameRules.enableKiDamage(sl.getServer())) {
            for (LivingEntity target : sl.getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(center, radius * 2, radius * 2, radius * 2),
                    t -> t.isAlive() && t != getOwner() && t != directHit)) {
                double dist = target.position().add(0, target.getBbHeight() * 0.5, 0)
                        .distanceTo(center);
                if (dist > radius) continue;
                // Los proyectiles caen a cero en el borde; la explosión conserva `edge`, que es
                // lo que la convierte en una zona de muerte y no en un golpe con halo.
                double falloff = 1.0 - (1.0 - edge) * (dist / radius);
                target.hurt(damageSources().source(ModDamageTypes.KI_EXPLOSION, this, owner),
                        (float) (damage * aoe * falloff));
            }
        }

        // Destello teñido del color de LA TÉCNICA, siempre — sea cual sea el efecto y pase lo
        // que pase después (grief, fragmentos, zona persistente). ANTES el impacto de un blast
        // caía siempre en el humo gris genérico de vainilla: una bola cargada y disparada de un
        // color concreto explotaba en gris, rompiendo la identidad de color que SÍ tienen la
        // carga (KiVfxChargeRenderer) y el vuelo (KiVfxProjectileRenderer). Mismo ModParticles.impact/
        // spark que ya usan PhysicalCombatServer (golpes físicos) y KiInfusionShooting
        // (infusión de ki) para su propio destello de impacto.
        //
        // TOPE en 4.5. Hasta 2026-10-01 `ki_impact_*.png` era un icono pixel-art de 32x32 con
        // bordes dentados y el tope era 3.0: más grande, el filtro nearest del atlas lo ampliaba
        // hasta un "sol" con anillos duros SIN shaders (confirmado con capturas del usuario). Ahora
        // son 64x64 con degradados continuos (tools/gen_ki_impact_fx.py: núcleo, destellos y un
        // anillo de choque que se expande), que aguantan la ampliación; el tope sigue existiendo
        // para que un EXPLOSION enorme no tape la pantalla con un solo quad. Una cifra más alta
        // solo se justifica mirando un impacto grande en juego, no por la fórmula.
        float flashScale = Math.min(4.5f, 1.0f + (float) radius * 0.18f);
        sl.sendParticles(ModParticles.impact(rgb(), flashScale),
                center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        sl.sendParticles(ModParticles.spark(rgb(), 1.0f),
                center.x, center.y, center.z, 6 + (int) Math.round(radius * 2.0),
                radius * 0.3, radius * 0.3, radius * 0.3, 0.15);

        // Van ANTES del branch de grief (que puede cortar con `return`) porque EXPLOSIVE,
        // FRAGMENTATION y LINGERING son efectos MUTUAMENTE EXCLUYENTES (uno solo por técnica,
        // ver TechniqueEffect): si `effect` es FRAGMENTATION o LINGERING, nunca es EXPLOSIVE,
        // así que este orden no cambia nada salvo garantizar que sí se ejecutan.
        if (effect == TechniqueEffect.FRAGMENTATION) spawnFragments(center, owner);
        if (effect == TechniqueEffect.LINGERING) spawnLingeringZone(center, owner);

        // Rompe bloques si el jugador eligió EXPLOSIVE O la técnica lo hace de serie (firma de maestro,
        // `breaks_blocks`), y SIEMPRE que la gamerule zenkai_enableKiGriefing esté encendida.
        boolean grief = (effect == TechniqueEffect.EXPLOSIVE || techniqueType().breaksBlocks())
                && ModGameRules.enableKiGriefing(sl.getServer());
        if (grief) {
            // DamageSource explícito (antes null) para que esta rama dé el MISMO mensaje de
            // muerte que la de arriba: con null, vanilla construye su propio DamageSource de
            // explosión atribuido a `this` (el proyectil), y "explotó por el Ki Projectile" en
            // vez del dueño era justo el tipo de mensaje raro que este sistema quiere evitar.
            sl.explode(this, damageSources().source(ModDamageTypes.KI_EXPLOSION, this, owner),
                    new SimpleExplosionDamageCalculator(true, false,
                            Optional.empty(), Optional.empty()),
                    center.x, center.y, center.z, (float) radius, false,
                    Level.ExplosionInteraction.TNT);
            return;
        }

        // SIN humo/escombros de vainilla a propósito: ParticleTypes.EXPLOSION_EMITTER/EXPLOSION
        // son una nube gris grande que opacaba el destello teñido de arriba (era justo la queja
        // — "el efecto que puse se ve opacado"), y el escalado por `radius` seguía sin bastar en
        // los tamaños grandes. El sonido se queda: es lo único que faltaría si se quita del todo.
        sl.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                1.2f + 0.15f * size(), 1.25f - 0.06f * size());
    }

    /**
     * FRAGMENTATION: 3-4 bolas pequeñas adicionales, más débiles, en direcciones dispersas.
     * SIN EFECTO propio a propósito (TechniqueEffect.NONE): dejarlas heredar FRAGMENTATION
     * las haría fragmentarse otra vez al impactar, y así hasta el infinito.
     */
    private void spawnFragments(Vec3 center, LivingEntity owner) {
        if (!(level() instanceof ServerLevel sl) || owner == null) return;

        KiTechniqueType type = techniqueType();
        int count = 3 + random.nextInt(2); // 3 o 4
        int fragSize = Math.max(1, size() - 2);
        double fragDamage = damage * 0.30;

        for (int i = 0; i < count; i++) {
            KiProjectileEntity frag = new KiProjectileEntity(ModEntities.KI_PROJECTILE.get(), sl);
            frag.configure(owner, type, rgb(), fragSize, fragDamage, 30, TechniqueEffect.NONE);
            frag.setPos(center.x, center.y, center.z);
            frag.setDeltaMovement(randomSpread().scale(type.speed() * 0.8));
            sl.addFreshEntity(frag);
        }
    }

    /** Vector unitario disperso: mayormente hacia fuera y algo hacia arriba, nunca hacia abajo
     *  por completo — una esquirla que solo pica hacia el suelo se lee como un fallo de física. */
    private Vec3 randomSpread() {
        double theta = random.nextDouble() * Math.PI * 2.0;
        double phi = Math.acos(2.0 * random.nextDouble() - 1.0);
        double y = Math.abs(Math.cos(phi)) * 0.6 + 0.2;
        return new Vec3(Math.sin(phi) * Math.cos(theta), y, Math.sin(phi) * Math.sin(theta)).normalize();
    }

    /**
     * LINGERING: zona de daño periódico en el punto de impacto (ver KiLingeringZoneEntity).
     * Radio y duración escalan con el tamaño de la técnica, igual que explosionRadius; el
     * daño por golpe es una FRACCIÓN pequeña del directo porque pega varias veces seguidas —
     * un DoT que iguala al golpe directo cada tick duplicaría el daño total sin avisar.
     */
    private void spawnLingeringZone(Vec3 center, LivingEntity owner) {
        if (!(level() instanceof ServerLevel sl)) return;

        double radius = 1.2 + 0.35 * size();
        double dmgPerHit = Math.max(0.5, damage * 0.10);
        int lifeTicks = 60 + size() * 10; // 3-6.5s según tamaño

        KiLingeringZoneEntity zone =
                new KiLingeringZoneEntity(ModEntities.KI_LINGERING_ZONE.get(), sl);
        zone.configure(owner, dmgPerHit, radius, lifeTicks);
        zone.setPos(center.x, center.y, center.z);
        sl.addFreshEntity(zone);
    }

    @Override
    protected boolean canHitEntity(@NotNull Entity e) {
        return super.canHitEntity(e) && e != getOwner() && !pierced.contains(e.getId());
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("damage", damage);
        tag.putInt("life", life);
        tag.putInt("effect", effect.ordinal());
        tag.putByte("ktype", this.entityData.get(DATA_TYPE));
        tag.putInt("rgb", rgb());
        tag.putByte("size", (byte) size());
        tag.putBoolean("frozen", isFrozen());
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getDouble("damage");
        life = tag.getInt("life");
        effect = tag.contains("effect") ? TechniqueEffect.byOrdinal(tag.getInt("effect"))
                : (tag.getBoolean("explosive") ? TechniqueEffect.EXPLOSIVE : TechniqueEffect.NONE);
        this.entityData.set(DATA_TYPE, tag.getByte("ktype"));
        this.entityData.set(DATA_RGB, tag.getInt("rgb"));
        this.entityData.set(DATA_SIZE, tag.getByte("size"));
        // Sin esto, recargar el mundo con una copia congelada dentro reactivaría su
        // getDeltaMovement() guardado (ver freeze()) como vuelo NORMAL en la siguiente carga.
        if (tag.getBoolean("frozen")) this.entityData.set(DATA_FROZEN, true);
    }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.@NotNull DamageSource source, float amount) {
        return false; // los proyectiles ki no reciben daño
    }

    /** Solo se devuelve lo que VIAJA. Ni la barrera ni la explosión: la primera es el visual de
     *  una burbuja pegada a su dueño y devolverla movería la esfera sin mover el pool de
     *  absorción; la segunda es una autodetonación, y devolvérsela a alguien no significa nada. */
    public boolean canBeDeflected() {
        return techniqueType().travels() && damage > 0.0;
    }

    /**
     * Desvío por kiai: el proyectil cambia de dueño. Con eso, quien lo disparó pasa a ser
     * blanco válido (canHitEntity excluye al dueño) y quien lo devuelve queda inmune, tanto
     * al impacto como a la explosión. El daño NO se recalcula: devuelve exactamente lo que
     * traía. Llamar ANTES de invertir la dirección.
     * @return false si este proyectil no es desviable (ver canBeDeflected).
     */
    public boolean deflect(LivingEntity newOwner) {
        if (!canBeDeflected()) return false;

        if (refPower <= 0.0 && getOwner() instanceof LivingEntity original) {
            var st = ZenkaiStats.of(original);
            if (st != null) refPower = st.computeKiPowerFinal();
        }
        setOwner(newOwner);
        pierced.clear(); // DISK: puede volver a atravesar a los que ya cruzó de ida
        return true;
    }
}