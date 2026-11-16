package com.hmc.zenkai.feature.player;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.compat.CuriosCompat;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.entity.misc.PlayerBodyEntity;
import com.hmc.zenkai.feature.player.death.PlayerBodyLoot;
import com.hmc.zenkai.feature.player.death.PlayerBodyManager;
import com.hmc.zenkai.feature.player.death.PlayerBodyRegistry;
import com.hmc.zenkai.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Visita temporal a la Tierra de un jugador muerto (muerte permanente, fase 5), concedida por Uranai Baba.
 *
 * <ul>
 *   <li>Durante la visita {@code inOtherworld} SIGUE puesto: el jugador sigue muerto (halo, Esferas, Yemma al volver). Lo que
 *       lo distingue es {@code earthVisitTicksLeft > 0}. Por eso la semántica de {@code isInOtherworld} no cambia y cada uso
 *       decide por separado (auditoría en {@code plan-muerte-permanente.md}).</li>
 *   <li>El reloj cuenta ticks del jugador, o sea solo con él conectado; {@code doDaylightCycle} y {@code /time set} no lo tocan.</li>
 *   <li>Al empezar, lo que guarda su cuerpo más reciente pasa al jugador. Al acabar el tiempo vuelve al Otherworld con ello.
 *       Si muere durante la visita, el cuerpo NUEVO se queda con lo que lleve y la visita termina
 *       ({@link OtherworldManager#keepInOtherworld} es el único embudo de esa muerte).</li>
 *   <li>Entre visitas hay una espera ({@code permadeath.visit_cooldown_days}, desde que acaba la anterior); revivir con las
 *       Esferas o {@code /zenkai reset visit} la borran.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class EarthVisit {
    private EarthVisit() {}

    /** Por qué se puede o no empezar una visita. La consulta Baba para elegir su diálogo. */
    public enum Check { OK, DISABLED, NOT_DEAD, HARDCORE, ALREADY_ON_VISIT, COOLDOWN, NEEDS_SOUL }

    /** Almas que cobra Baba por visita ({@code permadeath.visit_soul_cost}; 0 = gratis). */
    public static int soulCost() {
        return ServerConfig.PERMADEATH_VISIT_SOUL_COST.get();
    }

    /** Almas que lleva el jugador en el inventario (los Curios no cuentan: el alma no es equipo). */
    public static int soulsHeld(ServerPlayer sp) {
        int n = 0;
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack s = sp.getInventory().getItem(i);
            if (s.is(ModItems.SOUL.get())) n += s.getCount();
        }
        return n;
    }

    /** Quita {@code n} almas del inventario. false (sin quitar nada) si no llegan. */
    public static boolean paySouls(ServerPlayer sp, int n) {
        if (n <= 0) return true;
        if (soulsHeld(sp) < n) return false;
        int left = n;
        for (int i = 0; i < sp.getInventory().getContainerSize() && left > 0; i++) {
            ItemStack s = sp.getInventory().getItem(i);
            if (!s.is(ModItems.SOUL.get())) continue;
            int take = Math.min(left, s.getCount());
            s.shrink(take);
            left -= take;
        }
        return true;
    }

    /**
     * {@link #check} más el alma: lo que decide Baba. Con {@code OK} pero sin las almas que cobra devuelve
     * {@code NEEDS_SOUL}. {@link #check} se queda puro (sin inventario) para poder probarlo en gametest.
     */
    public static Check checkWithSouls(ServerPlayer sp) {
        Check c = check(sp);
        if (c == Check.OK && soulsHeld(sp) < soulCost()) return Check.NEEDS_SOUL;
        return c;
    }

    /** Un cuerpo cuyo contenido aún no se pudo recoger: su chunk acababa de cargar y las entidades cargan DESPUÉS. */
    private record PendingLoot(UUID bodyId, ResourceKey<Level> dim, BlockPos pos, int tries) {}

    private static final Map<UUID, PendingLoot> PENDING_LOOT = new ConcurrentHashMap<>();
    private static final int LOOT_RETRIES = 15;

    public static boolean isOnVisit(ServerPlayer sp) {
        PlayerStateFlags f = PlayerStatsAttachment.get(sp).flags();
        return f.isInOtherworld() && f.getEarthVisitTicksLeft() > 0L;
    }

    public static Check check(ServerPlayer sp) {
        if (!ServerConfig.PERMADEATH_ENABLED.get()) return Check.DISABLED;
        PlayerStateFlags f = PlayerStatsAttachment.get(sp).flags();
        if (!f.isInOtherworld()) return Check.NOT_DEAD;
        if (f.isHardcoreDeath()) return Check.HARDCORE;
        if (f.getEarthVisitTicksLeft() > 0L) return Check.ALREADY_ON_VISIT;
        if (f.getEarthVisitCooldownTicks() > 0L) return Check.COOLDOWN;
        return Check.OK;
    }

    /** Ticks que le faltan para poder pedir otra visita (0 = ya puede). */
    public static long cooldownLeft(ServerPlayer sp) {
        return PlayerStatsAttachment.get(sp).flags().getEarthVisitCooldownTicks();
    }

    /** Ticks que dura una visita según la config (días de Minecraft). */
    public static long configuredTicks() {
        return ServerConfig.PERMADEATH_VISIT_DAYS.get() * 24000L;
    }

    /**
     * Empieza la visita si {@link #checkWithSouls} lo permite y cobra las almas (se cobran justo antes de empezar, no al
     * abrir el diálogo). Devuelve el resultado de la comprobación; {@code OK} = la visita empezó.
     */
    public static Check tryStart(ServerPlayer sp) {
        Check c = checkWithSouls(sp);
        if (c == Check.OK) {
            paySouls(sp, soulCost());
            start(sp, configuredTicks());
        }
        return c;
    }

    /**
     * Empieza la visita sin comprobar nada más que lo imprescindible (la usa el comando de debug con ticks a medida). Si el
     * jugador no estaba muerto lo marca como tal, para poder probar sin morir antes.
     */
    public static void forceStart(ServerPlayer sp, long ticks) {
        PlayerStateFlags f = PlayerStatsAttachment.get(sp).flags();
        if (!f.isInOtherworld()) {
            f.setInOtherworld(true);
            f.setOtherworldSince(sp.serverLevel().getGameTime());
        }
        start(sp, ticks);
    }

    private static void start(ServerPlayer sp, long ticks) {
        PlayerStatsAttachment stats = PlayerStatsAttachment.get(sp);
        PlayerStateFlags f = stats.flags();
        f.setEarthVisitTicksLeft(ticks);
        sp.setInvulnerable(false);

        PlayerBodyRegistry.Entry entry = PlayerBodyRegistry.get(sp.server).newest(sp.getUUID());
        ServerLevel level = entry != null ? sp.server.getLevel(entry.dimension()) : null;
        double x, y, z;
        if (entry != null && level != null) {
            // Junto al cuerpo, no encima: a un bloque de él para no aparecer dentro de la entidad.
            x = entry.pos().getX() + 1.5;
            y = entry.pos().getY();
            z = entry.pos().getZ() + 0.5;
        } else {
            // Sin cuerpo (p. ej. se retiró por el tope): su punto de respawn, como al revivir.
            level = sp.server.getLevel(sp.getRespawnDimension());
            if (level == null) level = sp.server.overworld();
            BlockPos at = sp.getRespawnPosition();
            if (at == null) at = level.getSharedSpawnPos();
            x = at.getX() + 0.5;
            y = at.getY();
            z = at.getZ() + 0.5;
            entry = null;
        }
        sp.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        sp.fallDistance = 0.0F;
        sp.teleportTo(level, x, y, z, sp.getYRot(), sp.getXRot());

        if (entry != null && !tryLoot(sp, entry.bodyId(), entry.dimension(), entry.pos())) {
            PENDING_LOOT.put(sp.getUUID(), new PendingLoot(entry.bodyId(), entry.dimension(), entry.pos(), 0));
        }
        com.hmc.zenkai.feature.advancement.ZenkaiTriggers.MILESTONE.get().trigger(sp,
                com.hmc.zenkai.feature.advancement.ZenkaiTriggers.Kinds.EARTH_VISIT);
        PlayerLifeCycle.sync(sp);
    }

    /** Pasa el contenido (y la XP) del cuerpo al jugador. false si la entidad aún no está cargada. */
    private static boolean tryLoot(ServerPlayer sp, UUID bodyId, ResourceKey<Level> dim, BlockPos pos) {
        ServerLevel lvl = sp.server.getLevel(dim);
        if (lvl == null) return true;                  // dimensión que ya no existe: no hay nada que esperar
        lvl.getChunkAt(pos);                           // fuerza la carga del chunk; la entidad llega un poco después
        if (lvl.getEntity(bodyId) instanceof PlayerBodyEntity body) {
            PlayerBodyLoot.takeAll(sp, body);
            return true;
        }
        return false;
    }

    /**
     * Termina la visita y devuelve al jugador al Otherworld.
     * {@code died}: murió durante la visita, así que lo que lleve (inventario, Curios y parte de la XP) se queda en un cuerpo
     * NUEVO colocado donde cayó. Si el tiempo se acabó sin morir, vuelve con lo que lleva.
     */
    public static void end(ServerPlayer sp, boolean died) {
        PlayerStateFlags f = PlayerStatsAttachment.get(sp).flags();
        f.setEarthVisitTicksLeft(0L);
        // La espera empieza AHORA, no al pedir la visita: así una visita larga no se come su propia espera.
        f.setEarthVisitCooldownTicks(ServerConfig.PERMADEATH_VISIT_COOLDOWN_DAYS.get() * 24000L);
        PENDING_LOOT.remove(sp.getUUID());

        if (died) leaveNewBody(sp);

        OtherworldManager.anchorInOtherworld(sp);
        sp.sendSystemMessage(Component.translatable(died ? "zenkai.visit.died" : "zenkai.visit.ended"));
    }

    /** Cuerpo nuevo con lo que lleva el jugador; si no se puede crear, los objetos caen al suelo (nunca se pierden). */
    private static void leaveNewBody(ServerPlayer sp) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack s = sp.getInventory().getItem(i);
            if (!s.isEmpty()) items.add(s.copy());
        }
        List<CuriosCompat.Equipped> curios = CuriosCompat.allEquipped(sp);
        for (CuriosCompat.Equipped c : curios) if (!c.stack().isEmpty()) items.add(c.stack().copy());

        PlayerBodyManager.Snapshot snap = PlayerBodyManager.Snapshot.capture(sp);
        boolean hold = PlayerBodyManager.holdsItems();
        PlayerBodyEntity body = PlayerBodyManager.spawnFor(sp, sp.serverLevel(), hold ? items : List.of(), snap);

        if (body == null || !hold) {
            // Sin cuerpo que los guarde (o con un mod de tumbas a cargo): al suelo, como en vanilla.
            for (ItemStack s : items) sp.drop(s, true, false);
        } else {
            body.setXp(PlayerBodyManager.xpShare(snap.xp()));
            sp.setExperienceLevels(0);
            sp.setExperiencePoints(0);
        }
        sp.getInventory().clearContent();
        for (CuriosCompat.Equipped c : curios) {
            CuriosCompat.handler(sp, c.id()).ifPresent(h -> h.setStackInSlot(c.index(), ItemStack.EMPTY));
        }
        if (body != null) PlayerBodyManager.registerCreated(sp, body);
    }

    // ── Reloj ────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        PlayerStateFlags f = PlayerStatsAttachment.get(sp).flags();
        long left = f.getEarthVisitTicksLeft();
        if (left <= 0L) {
            // Sin visita en curso: solo corre la espera para la siguiente. Se sincroniza cada segundo mientras dure,
            // para que Baba (que lee el servidor) y cualquier pantalla del cliente vean el mismo valor.
            long cd = f.getEarthVisitCooldownTicks();
            if (cd > 0L) {
                f.setEarthVisitCooldownTicks(cd - 1L);
                if (cd - 1L <= 0L || sp.tickCount % 200 == 0) PlayerLifeCycle.sync(sp);
            }
            return;
        }
        if (!f.isInOtherworld()) {                     // revivido por un camino que no pasó por reviveTo: la visita ya no tiene sentido
            f.setEarthVisitTicksLeft(0L);
            PENDING_LOOT.remove(sp.getUUID());
            return;
        }
        // Un tick por tick del jugador: si está desconectado no hay evento, y por eso el reloj se pausa solo.
        f.setEarthVisitTicksLeft(left - 1L);
        if (left - 1L <= 0L) {
            end(sp, false);
            return;
        }
        if (sp.tickCount % 20 == 0) {
            retryLoot(sp);
            PlayerLifeCycle.sync(sp);                  // el cliente pinta el temporizador con este valor
        }
    }

    private static void retryLoot(ServerPlayer sp) {
        PendingLoot p = PENDING_LOOT.get(sp.getUUID());
        if (p == null) return;
        if (tryLoot(sp, p.bodyId(), p.dim(), p.pos()) || p.tries() + 1 >= LOOT_RETRIES) {
            // Si se agotan los intentos el contenido sigue en el cuerpo, a un paso del jugador: puede abrirlo como dueño.
            PENDING_LOOT.remove(sp.getUUID());
        } else {
            PENDING_LOOT.put(sp.getUUID(), new PendingLoot(p.bodyId(), p.dim(), p.pos(), p.tries() + 1));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        PENDING_LOOT.remove(e.getEntity().getUUID());
    }

    /** Texto corto del tiempo que queda: «N d» o, con menos de un día de Minecraft, «N h» (1 hora = 1000 ticks). */
    public static String remainingLabel(long ticks) {
        if (ticks >= 24000L) return ((ticks + 23999L) / 24000L) + " d";
        return Math.max(1L, (ticks + 999L) / 1000L) + " h";
    }
}
