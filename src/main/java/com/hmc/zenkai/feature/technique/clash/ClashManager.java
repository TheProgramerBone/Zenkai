package com.hmc.zenkai.feature.technique.clash;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
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
 * el punto medio, deja inmóviles a los dos dueños (ActionType.CLASH) y lleva el minijuego de
 * temporización: una barra 0-1000 cuyo cursor sube y baja (ClashRules) y que cada uno detiene
 * UNA vez. Gana el valor más alto; el haz ganador absorbe al perdedor y SALE DE NUEVO hacia él,
 * con el daño de los dos sumado, más lento y telegrafiado. El perdedor queda libre en ese
 * momento: puede bloquear, parar o esquivarlo (p. ej. con Transmisión Instantánea) — no recibe el
 * golpe de una sola vez.
 *
 * Todo es autoritativo del servidor. El cliente solo recibe qué pintar (ClashStartPacket,
 * ClashResultPacket) y manda "pulsé" (ClashPressPacket); el valor lo calcula este lado con su tick.
 *
 * Fases: CONTACT (haces quietos, esfera creciendo) -> MINIGAME -> RESOLVE (se enseñan las
 * cifras) -> fin (se sueltan los dueños y sale el haz reforzado).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class ClashManager {
    private ClashManager() {}

    // Ticks entre el cruce y el inicio de la barra (esfera creciendo, haces quietos): clash.contact_ticks.
    // Ticks que se enseñan las cifras antes de soltar el golpe de vuelta: clash.resolve_ticks.
    /** Cuánto se separan los dos haces del punto medio para que se vean tocándose. */
    private static final double TOUCH_GAP = 0.35;

    private enum Phase { CONTACT, MINIGAME, RESOLVE }

    private static final class Clash {
        final int id;
        final ResourceKey<Level> dim;
        final UUID ownerA, ownerB, projA, projB;
        final Vec3 mid;
        final long created;
        final ClashRules.Params params;
        final float seedA, seedB;
        final double dmgA, dmgB;
        final boolean aWasNoAi, bWasNoAi;

        Phase phase = Phase.CONTACT;
        long minigameStart;
        long resolveStart;
        int mobPressA = -1, mobPressB = -1;
        boolean pressedA, pressedB;
        double cursorA, cursorB;
        int stageA = 2, stageB = 2;
        ClashRules.Outcome outcome = ClashRules.Outcome.TIE;

        Clash(int id, ResourceKey<Level> dim, LivingEntity a, LivingEntity b,
              KiProjectileEntity pa, KiProjectileEntity pb, Vec3 mid, long now,
              ClashRules.Params params) {
            this.id = id;
            this.dim = dim;
            this.ownerA = a.getUUID();
            this.ownerB = b.getUUID();
            this.projA = pa.getUUID();
            this.projB = pb.getUUID();
            this.mid = mid;
            this.created = now;
            this.minigameStart = now + ServerConfig.clashContactTicks();
            this.params = params;
            this.seedA = a.getRandom().nextFloat();
            this.seedB = b.getRandom().nextFloat();
            this.dmgA = pa.damage();
            this.dmgB = pb.damage();
            this.aWasNoAi = a instanceof Mob ma && ma.isNoAi();
            this.bWasNoAi = b instanceof Mob mb && mb.isNoAi();
        }

        boolean isA(UUID u) { return ownerA.equals(u); }
    }

    private static final List<Clash> ACTIVE = new ArrayList<>();
    private static final Map<UUID, Clash> BY_OWNER = new HashMap<>();
    private static int nextId = 1;

    public static boolean inClash(UUID owner) { return BY_OWNER.containsKey(owner); }

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
        Clash c = new Clash(nextId++, lvl.dimension(), ownerA, ownerB, pa, pb, mid, now, params);

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

        holdOwner(ownerA);
        holdOwner(ownerB);

        // Los mobs no juegan la barra: «pulsan» en un tick sesgado por su habilidad.
        if (!(ownerA instanceof ServerPlayer)) {
            c.mobPressA = ClashRules.mobPressTick(new java.util.Random(ownerA.getRandom().nextLong()),
                    c.seedA, ServerConfig.clashMobSkill(), params);
        }
        if (!(ownerB instanceof ServerPlayer)) {
            c.mobPressB = ClashRules.mobPressTick(new java.util.Random(ownerB.getRandom().nextLong()),
                    c.seedB, ServerConfig.clashMobSkill(), params);
        }

        ACTIVE.add(c);
        BY_OWNER.put(c.ownerA, c);
        BY_OWNER.put(c.ownerB, c);

        lvl.sendParticles(ModParticles.impact(pa.rgb(), 2.5f), mid.x, mid.y, mid.z,
                1, 0.0, 0.0, 0.0, 0.0);
        lvl.playSound(null, mid.x, mid.y, mid.z, SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.PLAYERS, 1.0f, 1.6f);

        if (ownerA instanceof ServerPlayer sa) {
            sendStart(sa, c, ownerB, c.seedA);
            com.hmc.zenkai.feature.advancement.ZenkaiTriggers.MILESTONE.get().trigger(sa, com.hmc.zenkai.feature.advancement.ZenkaiTriggers.Kinds.CLASH_STARTED);
        }
        if (ownerB instanceof ServerPlayer sb) {
            sendStart(sb, c, ownerA, c.seedB);
            com.hmc.zenkai.feature.advancement.ZenkaiTriggers.MILESTONE.get().trigger(sb, com.hmc.zenkai.feature.advancement.ZenkaiTriggers.Kinds.CLASH_STARTED);
        }
    }

    private static void sendStart(ServerPlayer to, Clash c, LivingEntity rival, float seed) {
        boolean rivalIsPlayer = rival instanceof ServerPlayer;
        PacketDistributor.sendToPlayer(to, new ClashStartPacket(c.id, rival.getId(),
                rival.getDisplayName().getString(), rivalIsPlayer,
                rivalIsPlayer ? rival.getUUID() : new UUID(0L, 0L), seed,
                c.minigameStart, c.params.duration(), c.params.period(), c.params.speed(),
                c.mid.x, c.mid.y, c.mid.z));
    }

    // ── Participantes ────────────────────────────────────────────────────────

    private static void holdOwner(LivingEntity e) {
        if (e instanceof ServerPlayer sp) {
            ActionResolver.beginClash(sp);
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
        boolean isA = c.isA(sp.getUUID());
        if (isA ? c.pressedA : c.pressedB) return;

        long now = sp.level().getGameTime();
        long pingTicks = sp.connection.latency() / 50L + 1L;
        long grace = Math.min(ServerConfig.clashPingGraceTicks(), pingTicks);
        long evalTick = Math.max(now - grace, Math.min(now, clientTick));

        double t = Math.max(0.0, Math.min(c.params.duration(), evalTick - c.minigameStart));
        double value = ClashRules.cursor(t, isA ? c.seedA : c.seedB, c.params);
        int stage = ClashRules.stage(t, c.params);
        if (isA) { c.pressedA = true; c.cursorA = value; c.stageA = stage; }
        else     { c.pressedB = true; c.cursorB = value; c.stageB = stage; }

        // Aviso al rival (icono de check) si es un jugador.
        Entity rival = sp.serverLevel().getEntity(isA ? c.ownerB : c.ownerA);
        if (rival instanceof ServerPlayer rp) {
            PacketDistributor.sendToPlayer(rp, new ClashPressedPacket(c.id));
        }
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
                BY_OWNER.remove(c.ownerA);
                BY_OWNER.remove(c.ownerB);
            }
        }
    }

    /** @return false cuando el choque terminó (bien o abortado) y hay que retirarlo. */
    private static boolean tickOne(MinecraftServer server, Clash c) {
        ServerLevel lvl = server.getLevel(c.dim);
        if (lvl == null) { abort(server, c, null); return false; }

        Entity ea = lvl.getEntity(c.ownerA), eb = lvl.getEntity(c.ownerB);
        Entity pa = lvl.getEntity(c.projA), pb = lvl.getEntity(c.projB);
        if (!(ea instanceof LivingEntity a) || !a.isAlive() || !(eb instanceof LivingEntity b)
                || !b.isAlive() || !(pa instanceof KiProjectileEntity ka) || ka.isRemoved()
                || !(pb instanceof KiProjectileEntity kb) || kb.isRemoved()
                || downed(a) || downed(b)) {
            abort(server, c, lvl);
            return false;
        }

        long now = lvl.getGameTime();
        stop(a);
        stop(b);

        if ((now - c.created) % 2 == 0) {
            lvl.sendParticles(ModParticles.spark(ka.rgb(), 1.0f), c.mid.x, c.mid.y, c.mid.z,
                    3, 0.35, 0.35, 0.35, 0.15);
        }

        switch (c.phase) {
            case CONTACT -> {
                if (now >= c.minigameStart) c.phase = Phase.MINIGAME;
            }
            case MINIGAME -> {
                long t = now - c.minigameStart;
                if (c.mobPressA >= 0 && !c.pressedA && t >= c.mobPressA) {
                    c.pressedA = true;
                    c.cursorA = ClashRules.cursor(t, c.seedA, c.params);
                    c.stageA = ClashRules.stage(t, c.params);
                    if (b instanceof ServerPlayer sb) {
                        PacketDistributor.sendToPlayer(sb, new ClashPressedPacket(c.id));
                    }
                }
                if (c.mobPressB >= 0 && !c.pressedB && t >= c.mobPressB) {
                    c.pressedB = true;
                    c.cursorB = ClashRules.cursor(t, c.seedB, c.params);
                    c.stageB = ClashRules.stage(t, c.params);
                    if (a instanceof ServerPlayer sa) {
                        PacketDistributor.sendToPlayer(sa, new ClashPressedPacket(c.id));
                    }
                }
                if ((c.pressedA && c.pressedB) || t >= c.params.duration()) {
                    resolve(c, a, b, now);
                }
            }
            case RESOLVE -> {
                if (now - c.resolveStart >= ServerConfig.clashResolveTicks()) {
                    finish(lvl, c, a, b, ka, kb);
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean downed(LivingEntity e) {
        return e instanceof ServerPlayer sp && PlayerStatsAttachment.get(sp).flags().isDowned();
    }

    // ── Resolución ───────────────────────────────────────────────────────────

    private static void resolve(Clash c, LivingEntity a, LivingEntity b, long now) {
        if (!c.pressedA) {
            c.cursorA = ClashRules.noPressValue(c.seedA, c.params, ServerConfig.clashNoPressMultiplier());
        }
        if (!c.pressedB) {
            c.cursorB = ClashRules.noPressValue(c.seedB, c.params, ServerConfig.clashNoPressMultiplier());
        }
        double cap = ServerConfig.clashStatBonusCap();
        double relA = c.dmgB > 1.0e-6 ? c.dmgA / c.dmgB : 1.0;
        double relB = c.dmgA > 1.0e-6 ? c.dmgB / c.dmgA : 1.0;
        double scoreA = ClashRules.score(c.cursorA, relA, cap);
        double scoreB = ClashRules.score(c.cursorB, relB, cap);
        c.outcome = ClashRules.compare(scoreA, scoreB, ServerConfig.clashTieMargin());
        c.phase = Phase.RESOLVE;
        c.resolveStart = now;

        // Logros: ganar el choque, y detener la barra en la zona máxima (gane o no).
        awardClash(a, c.outcome == ClashRules.Outcome.A_WINS, c.pressedA && c.cursorA >= ClashRules.PERFECT_FROM);
        awardClash(b, c.outcome == ClashRules.Outcome.B_WINS, c.pressedB && c.cursorB >= ClashRules.PERFECT_FROM);
        int shownA = (int) Math.max(0, Math.round(scoreA));
        int shownB = (int) Math.max(0, Math.round(scoreB));
        if (a instanceof ServerPlayer sa) {
            PacketDistributor.sendToPlayer(sa, new ClashResultPacket(c.id, resultFor(c, true),
                    shownA, shownB, c.stageA, c.stageB));
        }
        if (b instanceof ServerPlayer sb) {
            PacketDistributor.sendToPlayer(sb, new ClashResultPacket(c.id, resultFor(c, false),
                    shownB, shownA, c.stageB, c.stageA));
        }
    }

    private static void awardClash(LivingEntity e, boolean won, boolean perfect) {
        if (!(e instanceof ServerPlayer sp)) return;
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

    /** Suelta a los dos dueños y lanza el golpe de vuelta (o detona, si fue empate). */
    private static void finish(ServerLevel lvl, Clash c, LivingEntity a, LivingEntity b,
                               KiProjectileEntity ka, KiProjectileEntity kb) {
        releaseOwner(a, c.aWasNoAi);
        releaseOwner(b, c.bWasNoAi);

        if (c.outcome == ClashRules.Outcome.TIE) {
            double f = ServerConfig.clashTieDamageFraction();
            ka.clashDetonate(f);
            kb.clashDetonate(f);
            return;
        }
        boolean aWins = c.outcome == ClashRules.Outcome.A_WINS;
        KiProjectileEntity winner = aWins ? ka : kb;
        KiProjectileEntity loserProj = aWins ? kb : ka;
        LivingEntity loser = aWins ? b : a;
        double dW = aWins ? c.dmgA : c.dmgB;
        double dL = aWins ? c.dmgB : c.dmgA;

        loserProj.clashDissipate();
        Vec3 from = winner.position().add(0, winner.getBbHeight() * 0.5, 0);
        Vec3 to = loser.position().add(0, loser.getBbHeight() * 0.5, 0);
        winner.clashRelaunch(to.subtract(from),
                ServerConfig.clashReturnSpeedMultiplier(),
                dW + dL * ServerConfig.clashDamageSumFraction(),
                loser, ServerConfig.clashReturnHoming(), ServerConfig.clashReturnHomingRange());
        lvl.playSound(null, from.x, from.y, from.z, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 0.9f, 1.8f);
    }

    /** Alguien murió, se desconectó, cambió de dimensión o un haz desapareció: se libera a
     *  todos y los haces que sobrevivan siguen su camino. Es la red que evita dejar a un
     *  jugador inmóvil para siempre. */
    private static void abort(MinecraftServer server, Clash c, ServerLevel lvl) {
        for (ServerLevel l : server.getAllLevels()) {
            Entity ea = l.getEntity(c.ownerA), eb = l.getEntity(c.ownerB);
            if (ea instanceof LivingEntity a) releaseOwner(a, c.aWasNoAi);
            if (eb instanceof LivingEntity b) releaseOwner(b, c.bWasNoAi);
            if (l.getEntity(c.projA) instanceof KiProjectileEntity ka) ka.setClashHeld(false);
            if (l.getEntity(c.projB) instanceof KiProjectileEntity kb) kb.setClashHeld(false);
            if (ea instanceof ServerPlayer sa) {
                PacketDistributor.sendToPlayer(sa,
                        new ClashResultPacket(c.id, ClashResultPacket.ABORT, 0, 0, 0, 0));
            }
            if (eb instanceof ServerPlayer sb) {
                PacketDistributor.sendToPlayer(sb,
                        new ClashResultPacket(c.id, ClashResultPacket.ABORT, 0, 0, 0, 0));
            }
        }
    }
}
