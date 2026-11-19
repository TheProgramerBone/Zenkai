package com.hmc.zenkai.feature.technique.clash;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.entity.technique.ClashOrbEntity;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.hmc.zenkai.registry.ModEntities;
import com.hmc.zenkai.feature.action.ActionResolver;
import com.hmc.zenkai.feature.party.PartyManager;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ModParticles;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Choque de haces. Detecta el cruce de dos proyectiles de ki de dueños distintos, los retiene en
 * el punto medio, deja inmóviles a los participantes (ActionType.CLASH) y lleva el minijuego de
 * temporización: una barra 0-1000 cuyo cursor sube y baja (ClashRules) y que cada uno detiene
 * UNA vez. Gana el valor más alto; el haz ganador absorbe al perdedor y SALE DE NUEVO hacia él,
 * con el daño de los dos sumado, más lento y telegrafiado. El perdedor queda libre en ese
 * momento: puede bloquear, parar o esquivarlo (p. ej. con Transmisión Instantánea) — no recibe el
 * golpe de una sola vez.
 *
 * EQUIPOS (choque por equipos, ver .claude/docs/combate-y-muerte.md): el choque son
 * dos {@link Side}, cada uno con 1..N {@link Participant}. El primero de cada lado es el LÍDER —
 * quien disparó el haz original: su proyectil es el que se retiene, se relanza o se disipa, y es
 * el blanco del golpe de vuelta si su lado pierde. La puntuación de un lado es la suma de sus
 * miembros con el multiplicador del equipo pequeño (ClashRules.teamScore). Un 1 contra 1 se
 * comporta EXACTAMENTE como antes (multiplicador 1, teamScore == score).
 *
 * UNIRSE (fase 2, {@link #join}): un jugador que apunta a un luchador y hace clic derecho entra en
 * su lado, paga el ki de la técnica de su posición seleccionada y aporta su daño. Con
 * clash.join_resets_minigame apagado (por defecto) se CONSERVAN las pulsaciones hechas y el que
 * entra recibe su propia barra (barStart), alargando el plazo; encendido, el minijuego se reinicia
 * para todos.
 *
 * Es autoritativo del servidor. El cliente solo recibe qué pintar (ClashStartPacket,
 * ClashResultPacket) y manda "pulsé" (ClashPressPacket); el valor lo calcula este lado con su tick.
 *
 * Fases: CONTACT (haces quietos, esfera creciendo) -> MINIGAME -> RESOLVE (se enseñan las
 * cifras) -> fin (se sueltan los participantes y sale el haz reforzado).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class ClashManager {
    private ClashManager() {}

    // Ticks entre el cruce y el inicio de la barra (esfera creciendo, haces quietos): clash.contact_ticks.
    // Ticks que se enseñan las cifras antes de soltar el golpe de vuelta: clash.resolve_ticks.
    /** Cuánto se separan los dos haces del punto medio para que se vean tocándose. */
    private static final double TOUCH_GAP = 0.35;

    private enum Phase { CONTACT, MINIGAME, RESOLVE }

    /** Un jugador o mob dentro del choque. */
    private static final class Participant {
        final UUID uuid;
        /** Su haz: el retenido para el líder; el ALIMENTADOR (KiProjectileEntity.markClashFeeder)
         *  para quien se une — vuela de sus manos a la esfera y ahí se retiene. */
        UUID proj;
        final float seed;
        final double damage;
        final boolean wasNoAi;
        /** Tick de juego en que arranca SU barra. Hoy el mismo para todos; con el modo «alargar»
         *  de la fase 2, quien se une tarde recibe uno propio. */
        long barStart;
        int mobPress = -1;
        boolean pressed;
        double cursor;
        int stage = 2;

        Participant(LivingEntity e, KiProjectileEntity proj, double damage, long barStart) {
            this.uuid = e.getUUID();
            this.proj = proj != null ? proj.getUUID() : null;
            this.seed = e.getRandom().nextFloat();
            this.damage = damage;
            this.wasNoAi = e instanceof Mob m && m.isNoAi();
            this.barStart = barStart;
        }
    }

    /** Un lado del choque. members.get(0) es el líder. */
    private static final class Side {
        final List<Participant> members = new ArrayList<>();

        Participant lead() { return members.get(0); }

        double totalDamage() {
            double d = 0.0;
            for (Participant p : members) d += p.damage;
            return d;
        }
    }

    private static final class Clash {
        final int id;
        final ResourceKey<Level> dim;
        final Side a = new Side(), b = new Side();
        final Vec3 mid;
        final long created;
        final ClashRules.Params params;

        Phase phase = Phase.CONTACT;
        /** Inicio de la barra del choque (el de sus fundadores); lo que recibe ClashStartPacket. */
        long minigameStart;
        long resolveStart;
        ClashRules.Outcome outcome = ClashRules.Outcome.TIE;
        /** Esfera de choque en el mundo (ClashOrbEntity); null si no se pudo crear. */
        UUID orb;
        /** Del punto medio hacia el haz A (unitario) y diámetro base de cada mitad de la esfera
         *  (el de la cabeza del haz de cada líder); crece con los miembros (rosterChanged). */
        Vec3 axisToA = new Vec3(0, 0, 1);
        float lobeA = 1.5f, lobeB = 1.5f;

        Clash(int id, ResourceKey<Level> dim, Vec3 mid, long now, ClashRules.Params params) {
            this.id = id;
            this.dim = dim;
            this.mid = mid;
            this.created = now;
            this.minigameStart = now + ServerConfig.clashContactTicks();
            this.params = params;
        }

        Side sideOf(UUID u) {
            for (Participant p : a.members) if (p.uuid.equals(u)) return a;
            for (Participant p : b.members) if (p.uuid.equals(u)) return b;
            return null;
        }

        Participant find(UUID u) {
            for (Participant p : a.members) if (p.uuid.equals(u)) return p;
            for (Participant p : b.members) if (p.uuid.equals(u)) return p;
            return null;
        }

        Side other(Side s) { return s == a ? b : a; }

        List<Participant> all() {
            List<Participant> l = new ArrayList<>(a.members);
            l.addAll(b.members);
            return l;
        }

        /** Fin del plazo: el de la barra que acabe más tarde. */
        long deadline() {
            long d = minigameStart + params.duration();
            for (Participant p : all()) d = Math.max(d, p.barStart + params.duration());
            return d;
        }

        boolean allPressed() {
            for (Participant p : all()) if (!p.pressed) return false;
            return true;
        }
    }

    private static final List<Clash> ACTIVE = new ArrayList<>();
    private static final Map<UUID, Clash> BY_OWNER = new HashMap<>();
    private static int nextId = 1;

    public static boolean inClash(UUID owner) { return BY_OWNER.containsKey(owner); }

    /** ¿Pertenece esta esfera a un choque vivo? La esfera se borra sola si no (ClashOrbEntity.tick). */
    public static boolean ownsOrb(ClashOrbEntity orb) {
        for (Clash c : ACTIVE) if (orb.getUUID().equals(c.orb)) return true;
        return false;
    }

    // ── Detección ────────────────────────────────────────────────────────────

    /**
     * Llamado por cada proyectil en su tick (servidor), ANTES de avanzar. Si se cruza con el haz
     * de otro dueño, arranca el choque y devuelve true: el llamante no debe moverse más este tick.
     * Cuesta un getEntitiesOfClass acotado a su caja de barrido por proyectil y tick, y solo si el
     * proyectil puede chocar; los disparos que no se acercan a ningún otro salen enseguida.
     */
    public static boolean tryStart(KiProjectileEntity a) {
        if (!ServerConfig.clashEnabled() || !a.canClash()) return false;
        if (a.damage() < ServerConfig.clashMinDamage()) return false;
        if (!(a.level() instanceof ServerLevel lvl)) return false;
        if (!(a.getOwner() instanceof LivingEntity ownerA) || !ownerA.isAlive()) return false;
        if (BY_OWNER.containsKey(ownerA.getUUID())) return false;

        Vec3 va = a.getDeltaMovement();
        AABB sweep = a.getBoundingBox().expandTowards(va).inflate(2.0);
        for (KiProjectileEntity b : lvl.getEntitiesOfClass(KiProjectileEntity.class, sweep,
                p -> p != a && p.canClash())) {
            if (!(b.getOwner() instanceof LivingEntity ownerB) || !ownerB.isAlive()) continue;
            if (ownerB == ownerA || BY_OWNER.containsKey(ownerB.getUUID())) continue;
            if (b.damage() < ServerConfig.clashMinDamage()) continue;
            if (sameParty(lvl.getServer(), ownerA, ownerB)) continue;

            Vec3 vb = b.getDeltaMovement();
            // Dos haces que van más o menos en el mismo sentido no «chocan», se persiguen.
            double la = va.length(), lb = vb.length();
            if (la > 1.0e-6 && lb > 1.0e-6 && va.dot(vb) > 0.2 * la * lb) continue;

            // Distancia mínima entre los dos centros durante este tick (movimiento relativo).
            Vec3 r0 = a.position().subtract(b.position());
            Vec3 rv = va.subtract(vb);
            double rvSq = rv.lengthSqr();
            double t = rvSq < 1.0e-9 ? 0.0 : Math.max(0.0, Math.min(1.0, -r0.dot(rv) / rvSq));
            double dist = r0.add(rv.scale(t)).length();
            double reach = (a.getBbWidth() + b.getBbWidth()) * 0.75 + 0.5;
            if (dist > reach) continue;

            begin(lvl, ownerA, ownerB, a, b);
            return true;
        }
        return false;
    }

    private static boolean sameParty(MinecraftServer server, LivingEntity x, LivingEntity y) {
        if (!(x instanceof ServerPlayer) || !(y instanceof ServerPlayer)) return false;
        var mgr = PartyManager.get(server);
        var px = mgr.partyOf(x.getUUID());
        var py = mgr.partyOf(y.getUUID());
        return px != null && py != null && px.id.equals(py.id);
    }

    private static void begin(ServerLevel lvl, LivingEntity ownerA, LivingEntity ownerB,
                              KiProjectileEntity pa, KiProjectileEntity pb) {
        long now = lvl.getGameTime();
        Vec3 ca = pa.position().add(0, pa.getBbHeight() * 0.5, 0);
        Vec3 cb = pb.position().add(0, pb.getBbHeight() * 0.5, 0);
        Vec3 mid = ca.add(cb).scale(0.5);

        // UNA velocidad por choque, la misma para los dos y hasta el final (como en DB Legends):
        // clash.fixed_speed la fija; con -1 se sortea entre las tres.
        int fixed = ServerConfig.clashFixedSpeed();
        int speed = fixed >= 0 ? fixed : lvl.random.nextInt(3);
        int period = switch (speed) {
            case ClashRules.SPEED_SLOW -> ServerConfig.clashPeriodSlow();
            case ClashRules.SPEED_MEDIUM -> ServerConfig.clashPeriodMedium();
            default -> ServerConfig.clashPeriodFast();
        };
        ClashRules.Params params = new ClashRules.Params(ServerConfig.clashDurationTicks(), period, speed);
        Clash c = new Clash(nextId++, lvl.dimension(), mid, now, params);

        // Los dos haces quedan quietos, tocándose en el punto medio.
        Vec3 axis = ca.subtract(cb);
        axis = axis.lengthSqr() < 1.0e-6 ? pa.getDeltaMovement().scale(-1.0) : axis;
        axis = axis.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : axis.normalize();
        Vec3 posA = mid.add(axis.scale(TOUCH_GAP));
        Vec3 posB = mid.subtract(axis.scale(TOUCH_GAP));
        pa.setPos(posA.x, posA.y - pa.getBbHeight() * 0.5, posA.z);
        pb.setPos(posB.x, posB.y - pb.getBbHeight() * 0.5, posB.z);
        pa.setClashHeld(true);
        pb.setClashHeld(true);

        // El Participant guarda wasNoAi ANTES de holdOwner (que pone noAi a los mobs).
        c.axisToA = axis;
        addParticipant(c, c.a, ownerA, pa, pa.damage(), 0);
        addParticipant(c, c.b, ownerB, pb, pb.damage(), 0);

        ACTIVE.add(c);
        spawnOrb(lvl, c, pa, pb, axis);

        lvl.sendParticles(ModParticles.impact(pa.rgb(), 2.5f), mid.x, mid.y, mid.z,
                1, 0.0, 0.0, 0.0, 0.0);
        lvl.playSound(null, mid.x, mid.y, mid.z, SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.PLAYERS, 1.0f, 1.6f);

        for (Participant p : c.all()) {
            if (lvl.getEntity(p.uuid) instanceof ServerPlayer sp) {
                sendStart(sp, c, p);
                com.hmc.zenkai.feature.advancement.ZenkaiTriggers.MILESTONE.get().trigger(sp, com.hmc.zenkai.feature.advancement.ZenkaiTriggers.Kinds.CLASH_STARTED);
            }
        }
        rosterChanged(lvl, c, false);
    }

    /** Mete a {@code e} en el lado {@code side}: lo inmoviliza, sortea su pulsación si es un mob y
     *  lo indexa en BY_OWNER. Punto único para los fundadores y, en la fase 2, para quien se una. */
    private static Participant addParticipant(Clash c, Side side, LivingEntity e,
                                              KiProjectileEntity proj, double damage, int visual) {
        Participant p = new Participant(e, proj, damage, c.minigameStart);
        side.members.add(p);
        holdOwner(e, visual);
        // Los mobs no juegan la barra: «pulsan» en un tick sesgado por su habilidad.
        if (!(e instanceof ServerPlayer)) {
            p.mobPress = ClashRules.mobPressTick(new java.util.Random(e.getRandom().nextLong()),
                    p.seed, ServerConfig.clashMobSkill(), c.params);
        }
        BY_OWNER.put(p.uuid, c);
        return p;
    }

    /** Cada lóbulo de la esfera SUSTITUYE a la cabeza de su haz (que no se pinta mientras está
     *  retenida): mismo diámetro visual, acotado para que una Genki Dama no se trague la escena
     *  entera ni un Death Beam quede en un punto invisible. */
    private static final double LOBE_MIN = 1.0, LOBE_MAX = 6.0;

    private static void spawnOrb(ServerLevel lvl, Clash c, KiProjectileEntity pa, KiProjectileEntity pb,
                                 Vec3 axisToA) {
        ClashOrbEntity orb = ModEntities.CLASH_ORB.get().create(lvl);
        if (orb == null) return;
        float dA = (float) Math.max(LOBE_MIN, Math.min(LOBE_MAX, pa.techniqueType().visualDiameter(pa.size())));
        float dB = (float) Math.max(LOBE_MIN, Math.min(LOBE_MAX, pb.techniqueType().visualDiameter(pb.size())));
        orb.configure(pa.rgb(), pa.rgb2(), pb.rgb(), pb.rgb2(), axisToA, dA, dB);
        c.lobeA = dA;
        c.lobeB = dB;
        orb.setPos(c.mid.x, c.mid.y, c.mid.z);
        if (lvl.addFreshEntity(orb)) c.orb = orb.getUUID();
    }

    private static ClashOrbEntity orbOf(ServerLevel lvl, Clash c) {
        return c.orb != null && lvl.getEntity(c.orb) instanceof ClashOrbEntity o ? o : null;
    }

    /** Arranque para un jugador: su rival en la ficha es el LÍDER del otro lado. */
    private static void sendStart(ServerPlayer to, Clash c, Participant me) {
        Side mine = c.sideOf(me.uuid);
        Entity rival = to.serverLevel().getEntity(c.other(mine).lead().uuid);
        if (!(rival instanceof LivingEntity r)) return;
        boolean rivalIsPlayer = r instanceof ServerPlayer;
        PacketDistributor.sendToPlayer(to, new ClashStartPacket(c.id, r.getId(),
                r.getDisplayName().getString(), rivalIsPlayer,
                rivalIsPlayer ? r.getUUID() : new UUID(0L, 0L), me.seed,
                me.barStart, c.params.duration(), c.params.period(), c.params.speed(),
                c.mid.x, c.mid.y, c.mid.z));
    }

    // ── Participantes ────────────────────────────────────────────────────────

    private static void holdOwner(LivingEntity e, int visual) {
        if (e instanceof ServerPlayer sp) {
            ActionResolver.beginClash(sp, visual);
        } else if (e instanceof Mob m) {
            m.setNoAi(true);
            m.getNavigation().stop();
        }
        stop(e);
    }

    private static void releaseOwner(LivingEntity e, boolean wasNoAi) {
        if (e instanceof ServerPlayer sp) {
            ActionResolver.endClash(sp);
        } else if (e instanceof Mob m) {
            m.setNoAi(wasNoAi);
        }
    }

    /** Anula el movimiento. Los jugadores lo reciben por hurtMarked; el cliente además bloquea
     *  su propio input (ClashClientState), porque el servidor no manda sobre un jugador que se
     *  mueve por predicción. */
    private static void stop(LivingEntity e) {
        e.setDeltaMovement(0.0, Math.min(0.0, e.getDeltaMovement().y), 0.0);
        e.fallDistance = 0.0f;
        if (e instanceof ServerPlayer) e.hurtMarked = true;
    }

    // ── Entrada del jugador ──────────────────────────────────────────────────

    /**
     * Pulsación de un jugador. Se evalúa en el tick que reporta el cliente (el que VIO en su
     * barra), pero solo hacia atrás y con dos topes: clash.ping_grace_ticks y el ping real del
     * jugador en ticks (+1 de margen). Un tick del futuro o anterior al inicio de la barra se
     * recorta; así la compensación nunca sirve para elegir puntuación, solo para no castigar
     * una conexión lenta.
     */
    public static void press(ServerPlayer sp, int clashId, long clientTick) {
        Clash c = BY_OWNER.get(sp.getUUID());
        if (c == null || c.id != clashId || c.phase != Phase.MINIGAME) return;
        Participant me = c.find(sp.getUUID());
        if (me == null || me.pressed) return;

        long now = sp.level().getGameTime();
        if (now < me.barStart) return; // su barra aún no ha empezado
        long pingTicks = sp.connection.latency() / 50L + 1L;
        long grace = Math.min(ServerConfig.clashPingGraceTicks(), pingTicks);
        long evalTick = Math.max(now - grace, Math.min(now, clientTick));

        double t = Math.max(0.0, Math.min(c.params.duration(), evalTick - me.barStart));
        me.pressed = true;
        me.cursor = ClashRules.cursor(t, me.seed, c.params);
        me.stage = ClashRules.stage(t, c.params);
        rosterChanged(sp.serverLevel(), c, false);
    }

    // ── Unirse ───────────────────────────────────────────────────────────────

    /**
     * Un jugador quiere ayudar a {@code targetEntityId}. Se valida cada cosa aquí: hay choque y no se
     * ha resuelto, el objetivo es un luchador, distancia (clash.join_range), sitio en su equipo
     * (clash.team_max_size), no ser de la party de nadie del otro lado, una técnica de ki ofensiva
     * en la posición seleccionada, lista y pagable. Lo que no cuadra se rechaza con un aviso en la
     * barra de acción y sin cobrar nada.
     */
    public static void join(ServerPlayer sp, int targetEntityId, int position) {
        if (!ServerConfig.clashEnabled() || sp.isSpectator() || !sp.isAlive() || downed(sp)) return;
        if (BY_OWNER.containsKey(sp.getUUID())) return;
        ServerLevel lvl = sp.serverLevel();
        if (!(lvl.getEntity(targetEntityId) instanceof LivingEntity target)) return;
        Clash c = BY_OWNER.get(target.getUUID());
        if (c == null || c.phase == Phase.RESOLVE || !c.dim.equals(lvl.dimension())) return;
        Side side = c.sideOf(target.getUUID());
        if (side == null) return;

        if (sp.distanceTo(target) > ServerConfig.clashJoinRange()) { joinReject(sp, "far"); return; }
        if (side.members.size() >= ServerConfig.clashTeamMaxSize()) { joinReject(sp, "full"); return; }
        for (Participant p : c.other(side).members) {
            if (lvl.getEntity(p.uuid) instanceof LivingEntity foe && sameParty(lvl.getServer(), sp, foe)) {
                joinReject(sp, "party");
                return;
            }
        }

        // La técnica es la de la posición seleccionada del overlay, resuelta con las asignaciones
        // del SERVIDOR (el cliente solo dice qué posición tiene marcada).
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        int slot = att.techniques().binding(position);
        com.hmc.zenkai.feature.technique.KiTechnique tech = att.techniques().slot(slot);
        if (tech == null) { joinReject(sp, "no_technique"); return; }
        com.hmc.zenkai.feature.technique.KiTechniqueType type = tech.type();
        if (!type.enabled() || !att.techniques().isUnlocked(type) || type.defensive() || !type.travels()
                || type == com.hmc.zenkai.feature.technique.KiTechniqueType.DISK) {
            joinReject(sp, "no_technique");
            return;
        }
        if (!com.hmc.zenkai.feature.technique.KiCombatServer.isReady(sp, slot)) { joinReject(sp, "cooldown"); return; }
        // «Como si lanzara su técnica» a carga completa: mismo coste y mismo daño que un disparo
        // normal al 100 % (KiFirePacket.execute / ActionResolver.releaseKi).
        int cost = (int) Math.max(1, Math.ceil(
                com.hmc.zenkai.feature.technique.KiCombatServer.computeCost(att, type, tech.size(), tech.effect())
                        * com.hmc.zenkai.feature.technique.KiCombatServer.chargeCostFactor(1.0)
                        * att.powerFraction()
                        * com.hmc.zenkai.feature.technique.KiCombatServer.ueImbueCostMult(sp)));
        if (att.getEnergy() < cost) { joinReject(sp, "no_ki"); return; }
        if (!com.hmc.zenkai.feature.technique.KiCombatServer.tryFire(sp, slot,
                com.hmc.zenkai.feature.technique.KiCombatServer.cooldownTicksFor(type, tech.size()))) {
            joinReject(sp, "cooldown");
            return;
        }
        att.addEnergy(-cost);
        com.hmc.zenkai.feature.player.PlayerLifeCycle.syncIfServer(sp);
        double damage = com.hmc.zenkai.feature.technique.KiCombatServer.computePlayerDamage(
                att.computeKiPowerFinal(), type, tech.size());

        long now = lvl.getGameTime();
        boolean reset = ServerConfig.clashJoinResetsMinigame() && c.phase == Phase.MINIGAME;
        Participant joined = addParticipant(c, side, sp, null, damage, tech.visual());
        placeAndFeed(lvl, c, side, joined, sp, type, tech.rgb(), tech.rgb2(), tech.size(), damage);
        long start = Math.max(now, c.minigameStart);
        if (reset) {
            // Modo «reiniciar»: barra nueva para TODOS, pulsaciones borradas.
            c.minigameStart = start;
            for (Participant p : c.all()) {
                p.barStart = start;
                p.pressed = false;
                p.cursor = 0.0;
                if (p.mobPress >= 0 && lvl.getEntity(p.uuid) instanceof LivingEntity e) {
                    p.mobPress = ClashRules.mobPressTick(new java.util.Random(e.getRandom().nextLong()),
                            p.seed, ServerConfig.clashMobSkill(), c.params);
                }
            }
            for (Participant p : c.all()) {
                if (lvl.getEntity(p.uuid) instanceof ServerPlayer psp) sendStart(psp, c, p);
            }
        } else {
            // Modo «alargar» (por defecto): solo el recién llegado recibe barra nueva; el plazo del
            // choque (deadline) se estira hasta el final de la suya.
            joined.barStart = start;
            sendStart(sp, c, joined);
        }
        sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("zenkai.clash.join.ok"), true);
        com.hmc.zenkai.feature.advancement.ZenkaiTriggers.MILESTONE.get().trigger(sp, com.hmc.zenkai.feature.advancement.ZenkaiTriggers.Kinds.CLASH_STARTED);
        lvl.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BEACON_POWER_SELECT,
                SoundSource.PLAYERS, 0.7f, 1.6f);
        rosterChanged(lvl, c, false);
    }

    /**
     * SOLO DEPURACIÓN (/zenkai debug dummy join): mete un mob en el choque de {@code player}, en su
     * equipo o en el contrario, para probar los choques por equipos sin un segundo cliente. En
     * juego real solo se unen jugadores ({@link #join}). Aporta el daño del líder de ese lado y
     * «pulsa» como cualquier mob (ClashRules.mobPressTick) dentro de su propia barra.
     * @return clave del motivo si no se pudo, o null si entró
     */
    public static String debugJoinMob(ServerPlayer player, LivingEntity mob, boolean ally) {
        Clash c = BY_OWNER.get(player.getUUID());
        if (c == null || c.phase == Phase.RESOLVE) return "no_clash";
        if (BY_OWNER.containsKey(mob.getUUID())) return "busy";
        Side mine = c.sideOf(player.getUUID());
        Side side = ally ? mine : c.other(mine);
        if (side.members.size() >= ServerConfig.clashTeamMaxSize()) return "full";
        ServerLevel lvl = player.serverLevel();
        Participant joined = addParticipant(c, side, mob, null, side.lead().damage, 0);
        KiProjectileEntity leadProj = projectile(lvl, side.lead().proj);
        if (leadProj != null) {
            placeAndFeed(lvl, c, side, joined, mob, leadProj.techniqueType(), leadProj.rgb(),
                    leadProj.rgb2(), leadProj.size(), side.lead().damage);
        }
        joined.barStart = Math.max(lvl.getGameTime(), c.minigameStart);
        rosterChanged(lvl, c, false);
        return null;
    }

    /** Tamaño máximo con que se DIBUJA el haz de un ayudante (ver placeAndFeed). */
    private static final int FEEDER_MAX_SIZE = 2;

    /** Separación lateral de cada ayudante respecto al líder de su equipo, en bloques. */
    private static final double HELPER_SIDE_OFFSET = 1.6;

    /**
     * Fase 3 (cinematográfica, pedido del usuario): quien se une se COLOCA a un lado del líder de
     * su equipo —el primer ayudante a la derecha, el segundo a la izquierda, perpendicular al eje
     * del choque— mirando a la esfera, y lanza su ALIMENTADOR: un proyectil de su técnica que sale
     * de sus manos y vuela hasta la mitad de su equipo, donde se retiene ({@link #tickFeeders}).
     * Si el hueco está ocupado por bloques no se le mueve: solo se le gira hacia la esfera.
     */
    private static void placeAndFeed(ServerLevel lvl, Clash c, Side side, Participant p, LivingEntity e,
                                     com.hmc.zenkai.feature.technique.KiTechniqueType type,
                                     int rgb, int rgb2, int size, double damage) {
        LivingEntity lead = living(lvl, side.lead().uuid);
        Vec3 axis = side == c.a ? c.axisToA : c.axisToA.scale(-1.0);
        Vec3 perp = axis.cross(new Vec3(0, 1, 0));
        perp = perp.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : perp.normalize();
        int idx = side.members.indexOf(p);
        double sign = idx % 2 == 1 ? 1.0 : -1.0;
        if (lead != null) {
            Vec3 spot = lead.position().add(perp.scale(sign * HELPER_SIDE_OFFSET * ((idx + 1) / 2)));
            net.minecraft.world.phys.AABB box = e.getDimensions(e.getPose()).makeBoundingBox(spot);
            if (lvl.noCollision(e, box)) {
                float[] yp = yawPitch(spot.add(0, e.getEyeHeight(), 0), c.mid);
                if (e instanceof ServerPlayer sp) {
                    sp.connection.teleport(spot.x, spot.y, spot.z, yp[0], yp[1]);
                } else {
                    e.moveTo(spot.x, spot.y, spot.z, yp[0], yp[1]);
                    e.setYHeadRot(yp[0]);
                }
            } else {
                float[] yp = yawPitch(e.getEyePosition(), c.mid);
                e.setYRot(yp[0]);
                e.setXRot(yp[1]);
                e.setYHeadRot(yp[0]);
                if (e instanceof ServerPlayer sp) sp.connection.teleport(sp.getX(), sp.getY(), sp.getZ(), yp[0], yp[1]);
            }
        }
        // Alimentador: de las manos hacia el centro de la mitad de su equipo.
        Vec3 eye = e.getEyePosition();
        Vec3 target = feederTarget(c, side);
        Vec3 dir = target.subtract(eye);
        dir = dir.lengthSqr() < 1.0e-6 ? axis.scale(-1.0) : dir.normalize();
        KiProjectileEntity proj = new KiProjectileEntity(ModEntities.KI_PROJECTILE.get(), lvl);
        // Tamaño VISUAL acotado: es el chorro que une al ayudante con la esfera, no su técnica a
        // escala real. Con una técnica grande, la bola de origen del haz anclado envolvía al
        // jugador y tapaba la escena (capturas 2026-10-08). El daño no depende de esto.
        proj.configure(e, type, rgb, Math.min(size, FEEDER_MAX_SIZE), damage, 100,
                com.hmc.zenkai.feature.technique.TechniqueEffect.NONE);
        proj.setRgb2(rgb2);
        proj.markClashFeeder();
        Vec3 spawn = eye.add(dir.scale(0.9));
        proj.setPos(spawn.x, spawn.y - proj.getBbHeight() * 0.5, spawn.z);
        proj.setDeltaMovement(dir.scale(Math.max(0.6, type.speed())));
        if (lvl.addFreshEntity(proj)) p.proj = proj.getUUID();
    }

    /** Centro de la mitad de la esfera de un lado: ahí acaban los alimentadores. */
    private static Vec3 feederTarget(Clash c, Side side) {
        double r = (side == c.a ? c.lobeA : c.lobeB) * 0.3;
        return c.mid.add((side == c.a ? c.axisToA : c.axisToA.scale(-1.0)).scale(r));
    }

    private static float[] yawPitch(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, h));
        return new float[]{yaw, pitch};
    }

    /** Los alimentadores en vuelo se retienen al llegar a su mitad de la esfera. */
    private static void tickFeeders(ServerLevel lvl, Clash c) {
        for (Side s : new Side[]{c.a, c.b}) {
            for (int i = 1; i < s.members.size(); i++) {
                KiProjectileEntity k = projectile(lvl, s.members.get(i).proj);
                if (k == null || k.isClashHeld()) continue;
                Vec3 target = feederTarget(c, s);
                Vec3 center = k.position().add(0, k.getBbHeight() * 0.5, 0);
                double step = Math.max(0.6, k.getDeltaMovement().length());
                if (center.distanceTo(target) <= step || center.subtract(target).dot(k.getDeltaMovement()) > 0) {
                    k.setPos(target.x, target.y - k.getBbHeight() * 0.5, target.z);
                    k.setClashHeld(true);
                }
            }
        }
    }

    /** Retira el alimentador de un ayudante (no el haz de un líder). */
    private static void discardFeeder(ServerLevel lvl, Participant p) {
        KiProjectileEntity k = projectile(lvl, p.proj);
        if (k != null && k.isClashFeeder()) k.discard();
    }

    /** Factor de crecimiento de una mitad de la esfera por cada miembro extra de su equipo. */
    private static final float LOBE_GROW_PER_MEMBER = 0.25f;

    private static void joinReject(ServerPlayer sp, String why) {
        sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("zenkai.clash.join." + why), true);
    }

    // ── Roster (HUD y esfera) ────────────────────────────────────────────────

    /**
     * Algo cambió en quién está o en quién pulsó: se reenvía el roster a cada jugador del choque
     * (ClashRosterPacket, desde su lado) y se actualiza la pista de la esfera para el aviso de
     * unirse. {@code reveal}: solo al resolver viajan los valores.
     */
    private static void rosterChanged(ServerLevel lvl, Clash c, boolean reveal) {
        ClashOrbEntity orb = orbOf(lvl, c);
        if (orb != null) {
            orb.setRoster(ids(lvl, c.a), ids(lvl, c.b), c.phase != Phase.RESOLVE,
                    ServerConfig.clashTeamMaxSize(), (float) ServerConfig.clashJoinRange());
            // Cada miembro extra hace crecer la mitad de su equipo: el empuje se VE.
            orb.setLobeSizes(c.lobeA * (1f + LOBE_GROW_PER_MEMBER * (c.a.members.size() - 1)),
                    c.lobeB * (1f + LOBE_GROW_PER_MEMBER * (c.b.members.size() - 1)));
        }
        float multA = (float) multOf(c, c.a), multB = (float) multOf(c, c.b);
        List<ClashRosterPacket.Member> la = members(lvl, c.a, multA, reveal);
        List<ClashRosterPacket.Member> lb = members(lvl, c.b, multB, reveal);
        for (Participant p : c.all()) {
            if (!(lvl.getEntity(p.uuid) instanceof ServerPlayer sp)) continue;
            boolean onA = c.sideOf(p.uuid) == c.a;
            PacketDistributor.sendToPlayer(sp, new ClashRosterPacket(c.id,
                    onA ? la : lb, onA ? lb : la, onA ? multA : multB, onA ? multB : multA));
        }
    }

    private static int[] ids(ServerLevel lvl, Side s) {
        int[] out = new int[s.members.size()];
        for (int i = 0; i < out.length; i++) {
            Entity e = lvl.getEntity(s.members.get(i).uuid);
            out[i] = e != null ? e.getId() : -1;
        }
        return out;
    }

    private static List<ClashRosterPacket.Member> members(ServerLevel lvl, Side s, float mult, boolean reveal) {
        List<ClashRosterPacket.Member> out = new ArrayList<>();
        for (Participant p : s.members) {
            Entity e = lvl.getEntity(p.uuid);
            if (e == null) continue;
            boolean isPlayer = e instanceof ServerPlayer;
            out.add(new ClashRosterPacket.Member(e.getId(), e.getDisplayName().getString(), isPlayer,
                    isPlayer ? e.getUUID() : new UUID(0L, 0L), p.pressed,
                    reveal ? (int) Math.max(0, Math.round(p.cursor * mult)) : -1));
        }
        return out;
    }

    private static double multOf(Clash c, Side s) {
        return ClashRules.outnumberedMultiplier(s.members.size(), c.other(s).members.size(),
                ServerConfig.clashOutnumberedBonus());
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        MinecraftServer server = event.getServer();
        Iterator<Clash> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Clash c = it.next();
            if (!tickOne(server, c)) {
                it.remove();
                for (Participant p : c.all()) BY_OWNER.remove(p.uuid);
            }
        }
    }

    /** @return false cuando el choque terminó (bien o abortado) y hay que retirarlo. */
    private static boolean tickOne(MinecraftServer server, Clash c) {
        ServerLevel lvl = server.getLevel(c.dim);
        if (lvl == null) { abort(server, c, null); return false; }

        // Los LÍDERES y sus haces sostienen el choque: si cualquiera falta, se aborta entero.
        LivingEntity a = living(lvl, c.a.lead().uuid), b = living(lvl, c.b.lead().uuid);
        KiProjectileEntity ka = projectile(lvl, c.a.lead().proj), kb = projectile(lvl, c.b.lead().proj);
        if (a == null || b == null || ka == null || kb == null) {
            abort(server, c, lvl);
            return false;
        }
        // Un miembro que no es líder y desaparece (murió, se desconectó, lo derribaron) sale del
        // choque sin arrastrar a los demás.
        dropLostHelpers(lvl, c);

        long now = lvl.getGameTime();
        for (Participant p : c.all()) {
            LivingEntity e = living(lvl, p.uuid);
            if (e != null) stop(e);
        }
        tickFeeders(lvl, c);

        if ((now - c.created) % 2 == 0) {
            lvl.sendParticles(ModParticles.spark(ka.rgb(), 1.0f), c.mid.x, c.mid.y, c.mid.z,
                    3, 0.35, 0.35, 0.35, 0.15);
        }

        switch (c.phase) {
            case CONTACT -> {
                if (now >= c.minigameStart) c.phase = Phase.MINIGAME;
            }
            case MINIGAME -> {
                for (Participant p : c.all()) {
                    long t = now - p.barStart;
                    if (p.mobPress >= 0 && !p.pressed && t >= p.mobPress) {
                        p.pressed = true;
                        p.cursor = ClashRules.cursor(t, p.seed, c.params);
                        p.stage = ClashRules.stage(t, c.params);
                        rosterChanged(lvl, c, false);
                    }
                }
                if (c.allPressed() || now >= c.deadline()) {
                    resolve(lvl, c);
                }
            }
            case RESOLVE -> {
                if (now - c.resolveStart >= ServerConfig.clashResolveTicks()) {
                    finish(lvl, c, ka, kb);
                    return false;
                }
            }
        }
        return true;
    }

    private static LivingEntity living(ServerLevel lvl, UUID u) {
        return lvl.getEntity(u) instanceof LivingEntity e && e.isAlive() && !downed(e) ? e : null;
    }

    private static KiProjectileEntity projectile(ServerLevel lvl, UUID u) {
        return u != null && lvl.getEntity(u) instanceof KiProjectileEntity k && !k.isRemoved() ? k : null;
    }

    private static void dropLostHelpers(ServerLevel lvl, Clash c) {
        boolean changed = false;
        for (Side s : new Side[]{c.a, c.b}) {
            for (int i = s.members.size() - 1; i >= 1; i--) {
                Participant p = s.members.get(i);
                if (living(lvl, p.uuid) != null) continue;
                s.members.remove(i);
                BY_OWNER.remove(p.uuid);
                discardFeeder(lvl, p);
                changed = true;
                if (lvl.getEntity(p.uuid) instanceof LivingEntity e) {
                    releaseOwner(e, p.wasNoAi);
                    if (e instanceof ServerPlayer sp) PacketDistributor.sendToPlayer(sp,
                            new ClashResultPacket(c.id, ClashResultPacket.ABORT, 0, 0, 0, 0));
                }
            }
        }
        if (changed) rosterChanged(lvl, c, false);
    }

    private static boolean downed(LivingEntity e) {
        return e instanceof ServerPlayer sp && PlayerStatsAttachment.get(sp).flags().isDowned();
    }

    // ── Resolución ───────────────────────────────────────────────────────────

    private static double scoreOf(Clash c, Side s) {
        Side o = c.other(s);
        double[] values = new double[s.members.size()];
        for (int i = 0; i < values.length; i++) {
            Participant p = s.members.get(i);
            if (!p.pressed) {
                p.cursor = ClashRules.noPressValue(p.seed, c.params, ServerConfig.clashNoPressMultiplier());
            }
            values[i] = p.cursor;
        }
        double dOther = o.totalDamage();
        double rel = dOther > 1.0e-6 ? s.totalDamage() / dOther : 1.0;
        double mult = ClashRules.outnumberedMultiplier(s.members.size(), o.members.size(),
                ServerConfig.clashOutnumberedBonus());
        return ClashRules.teamScore(values, mult, rel, ServerConfig.clashStatBonusCap());
    }

    private static void resolve(ServerLevel lvl, Clash c) {
        double scoreA = scoreOf(c, c.a);
        double scoreB = scoreOf(c, c.b);
        c.outcome = ClashRules.compare(scoreA, scoreB, ServerConfig.clashTieMargin());
        c.phase = Phase.RESOLVE;
        c.resolveStart = lvl.getGameTime();
        // La esfera solo se entera del resultado AHORA: hasta aquí no se ha inclinado hacia nadie.
        ClashOrbEntity orb = orbOf(lvl, c);
        if (orb != null) orb.resolve(switch (c.outcome) {
            case A_WINS -> ClashOrbEntity.OUTCOME_A;
            case B_WINS -> ClashOrbEntity.OUTCOME_B;
            case TIE -> ClashOrbEntity.OUTCOME_TIE;
        });

        int shownA = (int) Math.max(0, Math.round(scoreA));
        int shownB = (int) Math.max(0, Math.round(scoreB));
        for (Participant p : c.all()) {
            boolean onA = c.sideOf(p.uuid) == c.a;
            boolean won = c.outcome == (onA ? ClashRules.Outcome.A_WINS : ClashRules.Outcome.B_WINS);
            if (!(lvl.getEntity(p.uuid) instanceof ServerPlayer sp)) continue;
            // Logros: ganar el choque, y detener la barra en la zona máxima (gane o no).
            awardClash(sp, won, p.pressed && p.cursor >= ClashRules.PERFECT_FROM);
            // mine/rival son los TOTALES de equipo; la aportación de cada jugador va en el roster.
            PacketDistributor.sendToPlayer(sp, new ClashResultPacket(c.id, resultFor(c, onA),
                    onA ? shownA : shownB, onA ? shownB : shownA, p.stage,
                    c.other(c.sideOf(p.uuid)).lead().stage));
        }
        // Ahora sí: la aportación de cada uno (barra × multiplicador), para las fichas del HUD.
        rosterChanged(lvl, c, true);
    }

    private static void awardClash(ServerPlayer sp, boolean won, boolean perfect) {
        if (won) com.hmc.zenkai.feature.advancement.ZenkaiTriggers.MILESTONE.get().trigger(sp, com.hmc.zenkai.feature.advancement.ZenkaiTriggers.Kinds.CLASH_WON);
        if (perfect) com.hmc.zenkai.feature.advancement.ZenkaiTriggers.MILESTONE.get().trigger(sp, com.hmc.zenkai.feature.advancement.ZenkaiTriggers.Kinds.CLASH_PERFECT);
    }

    private static int resultFor(Clash c, boolean forA) {
        return switch (c.outcome) {
            case TIE -> ClashResultPacket.TIE;
            case A_WINS -> forA ? ClashResultPacket.WIN : ClashResultPacket.LOSE;
            case B_WINS -> forA ? ClashResultPacket.LOSE : ClashResultPacket.WIN;
        };
    }

    /** Suelta a todos y lanza el golpe de vuelta (o detona, si fue empate). */
    private static void finish(ServerLevel lvl, Clash c, KiProjectileEntity ka, KiProjectileEntity kb) {
        for (Participant p : c.all()) {
            if (lvl.getEntity(p.uuid) instanceof LivingEntity e) releaseOwner(e, p.wasNoAi);
        }
        // El ganador «absorbe» la esfera al relanzarse; en el empate la sustituye la detonación.
        // Los alimentadores de los ayudantes se funden en ella: su daño ya va en el del equipo.
        ClashOrbEntity orb = orbOf(lvl, c);
        if (orb != null) orb.discard();
        for (Participant p : c.all()) discardFeeder(lvl, p);

        if (c.outcome == ClashRules.Outcome.TIE) {
            double f = ServerConfig.clashTieDamageFraction();
            ka.clashDetonate(f);
            kb.clashDetonate(f);
            return;
        }
        boolean aWins = c.outcome == ClashRules.Outcome.A_WINS;
        Side win = aWins ? c.a : c.b, lose = aWins ? c.b : c.a;
        KiProjectileEntity winner = aWins ? ka : kb;
        KiProjectileEntity loserProj = aWins ? kb : ka;
        // Blanco: el LÍDER del lado perdedor, quien disparó el haz original (decidido con el
        // usuario); el área del impacto alcanza a sus ayudantes, que están a su lado.
        LivingEntity loser = living(lvl, lose.lead().uuid);
        if (loser == null && lvl.getEntity(lose.lead().uuid) instanceof LivingEntity le) loser = le;

        loserProj.clashDissipate();
        if (loser == null) return;
        Vec3 from = winner.position().add(0, winner.getBbHeight() * 0.5, 0);
        Vec3 to = loser.position().add(0, loser.getBbHeight() * 0.5, 0);
        // Daño: el del lado ganador entero + una fracción del perdedor. Cada miembro aporta el de
        // su técnica, «como si la lanzara»; con uno por lado es dW + dL × fracción, como siempre.
        winner.clashRelaunch(to.subtract(from),
                ServerConfig.clashReturnSpeedMultiplier(),
                win.totalDamage() + lose.totalDamage() * ServerConfig.clashDamageSumFraction(),
                loser, ServerConfig.clashReturnHoming(), ServerConfig.clashReturnHomingRange());
        lvl.playSound(null, from.x, from.y, from.z, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 0.9f, 1.8f);
    }

    /** Alguien murió, se desconectó, cambió de dimensión o un haz desapareció: se libera a
     *  todos y los haces que sobrevivan siguen su camino. Es la red que evita dejar a un
     *  jugador inmóvil para siempre. */
    private static void abort(MinecraftServer server, Clash c, ServerLevel lvl) {
        for (ServerLevel l : server.getAllLevels()) {
            for (Participant p : c.all()) {
                Entity e = l.getEntity(p.uuid);
                if (e instanceof LivingEntity le) releaseOwner(le, p.wasNoAi);
                if (p.proj != null && l.getEntity(p.proj) instanceof KiProjectileEntity k) {
                    if (k.isClashFeeder()) k.discard();
                    else k.setClashHeld(false);
                }
                if (e instanceof ServerPlayer sp) {
                    PacketDistributor.sendToPlayer(sp,
                            new ClashResultPacket(c.id, ClashResultPacket.ABORT, 0, 0, 0, 0));
                }
            }
            if (c.orb != null && l.getEntity(c.orb) instanceof ClashOrbEntity orb) orb.discard();
        }
    }
}
