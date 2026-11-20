package com.hmc.zenkai.feature.player.death;

import com.hmc.zenkai.api.event.PlayerBodyCreatedEvent;

import com.hmc.zenkai.api.event.PartyMemberDiedEvent;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.compat.CuriosCompat;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.entity.misc.PlayerBodyEntity;
import com.hmc.zenkai.feature.party.PartyManager;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ModEntities;
import com.hmc.zenkai.registry.ModGameRules;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Crea el cuerpo de un jugador al morir y vigila los topes.
 *
 * Es un listener MÁS e independiente: no toca el orden de {@code OtherworldHandler}, {@code PlayerLifeCycle},
 * {@code DownedDeathGuard} ni {@code DeathScreenGuard}. Engancha {@link LivingDropsEvent}, que vanilla dispara
 * DESPUÉS de {@code die()} y ya con la lista de drops hecha, así que no corre dentro de ninguna pila de daño.
 *
 * CUÁNDO HAY CUERPO (decidido con el usuario, 2026-11-02):
 *  - gamerule {@code zenkai_enablePlayerBodies} en false y muerte permanente apagada: no hay cuerpo (vanilla / mod de tumbas).
 *    Con la muerte permanente ENCENDIDA los cuerpos se crean igual: sus salidas (deseo, visita de Baba) los necesitan.
 *  - {@code keepInventory}: el jugador conserva objetos y XP, así que un cuerpo sería una carcasa vacía: solo se crea con la
 *    muerte permanente encendida (hace de ancla).
 *  - {@code permadeath.body_items_mode}: AUTO guarda los objetos salvo que haya un mod de tumbas cargado; entonces el cuerpo
 *    es solo ANCLA y NO toca la lista de drops (la tumba ya la quedó o no) ni la XP.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class PlayerBodyManager {
    private PlayerBodyManager() {}

    private record Safe(ResourceKey<Level> dim, BlockPos pos) {}

    /**
     * Foto de SOLO LECTURA de dónde estaba cada objeto (ranura de inventario, armadura, mano, TODAS las ranuras de Curios con
     * su tipo, también las vacías) y cuánta experiencia tenía, en el instante de morir. Hace falta porque en
     * {@link LivingDropsEvent} la lista ya viene aplanada: solo así el cuerpo puede enseñar la armadura en sus huecos y cada
     * ranura de Curios con su icono. Se toma en {@link LivingDeathEvent} (LOWEST, o sea solo si nadie canceló la muerte) y no
     * toca nada del jugador: no es seguro llamar a {@code die()} desde ahí.
     */
    public record Snapshot(ItemStack[] slots, List<CuriosCompat.Equipped> curios, int xp, int selected) {
        public static Snapshot capture(ServerPlayer sp) {
            ItemStack[] slots = new ItemStack[PlayerBodyEntity.CURIO_BASE];
            for (int i = 0; i < slots.length; i++) slots[i] = sp.getInventory().getItem(i).copy();
            return new Snapshot(slots, CuriosCompat.allSlots(sp), totalXp(sp), sp.getInventory().selected);
        }
    }

    /** Puntos de experiencia totales (nivel + barra). No se fía de {@code totalExperience}, que {@code /xp add} deja desfasado. */
    public static int totalXp(Player p) {
        int l = p.experienceLevel;
        int atLevel = l <= 16 ? l * l + 6 * l : l <= 31 ? (int) (2.5 * l * l - 40.5 * l + 360) : (int) (4.5 * l * l - 162.5 * l + 2220);
        return atLevel + Math.round(p.experienceProgress * p.getXpNeededForNextLevel());
    }

    private static final Map<UUID, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && !e.isCanceled()) SNAPSHOTS.put(sp.getUUID(), Snapshot.capture(sp));
    }

    // ── Política ─────────────────────────────────────────────────────────────

    private static boolean bodiesEnabled(ServerLevel level) {
        return level.getGameRules().getBoolean(ModGameRules.ENABLE_PLAYER_BODIES) || ServerConfig.PERMADEATH_ENABLED.get();
    }

    private static boolean keepInventory(ServerLevel level) {
        return level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
    }

    /** ¿Guarda el cuerpo los objetos (y la XP)? Según el modo y los mods de tumbas cargados. */
    public static boolean holdsItems() {
        return switch (ServerConfig.PERMADEATH_BODY_ITEMS_MODE.get()) {
            case ALWAYS -> true;
            case NEVER -> false;
            case AUTO -> ServerConfig.PERMADEATH_TOMB_MODS.get().stream().noneMatch(id -> ModList.get().isLoaded(id));
        };
    }

    /** ¿Va a quedarse el cuerpo con la XP de este jugador? Si sí, los orbes de vanilla se anulan (si no, se duplicaría). */
    private static boolean holdsXp(ServerPlayer sp, @Nullable Snapshot snap) {
        return snap != null && snap.xp() > 0 && ServerConfig.PERMADEATH_XP_RETENTION.get() > 0
                && sp.level() instanceof ServerLevel lv && bodiesEnabled(lv) && !keepInventory(lv) && holdsItems();
    }

    @SubscribeEvent
    public static void onXpDrop(LivingExperienceDropEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && holdsXp(sp, SNAPSHOTS.get(sp.getUUID()))) e.setDroppedExperience(0);
    }

    // ── Posición segura ──────────────────────────────────────────────────────

    /** Último sitio firme donde estuvo cada jugador: para colocar el cuerpo si muere en el vacío o la lava. */
    private static final Map<UUID, Safe> LAST_SAFE = new ConcurrentHashMap<>();

    @Nullable
    public static BlockPos lastSafe(UUID owner, ResourceKey<Level> dim) {
        Safe s = LAST_SAFE.get(owner);
        return s != null && s.dim.equals(dim) ? s.pos : null;
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp.tickCount % 20 != 0) return;
        if (sp.isDeadOrDying() || !sp.onGround() || sp.isInLiquid() || sp.isSpectator()) return;
        if (sp.getY() < sp.level().getMinBuildHeight() + 2) return;
        LAST_SAFE.put(sp.getUUID(), new Safe(sp.level().dimension(), sp.blockPosition()));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        LAST_SAFE.remove(e.getEntity().getUUID());
        SNAPSHOTS.remove(e.getEntity().getUUID());
    }

    // ── Creación ─────────────────────────────────────────────────────────────

    /**
     * LOWEST y receiveCanceled: que cualquier otro mod haya tocado ya la lista de drops cuando la vaciamos, y que una tumba que
     * CANCELE el evento no deje al jugador sin cuerpo (el cuerpo es el ancla de las rage forms y de Baba).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDrops(LivingDropsEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (!(sp.level() instanceof ServerLevel level)) return;
        Snapshot snap = SNAPSHOTS.remove(sp.getUUID());

        if (!bodiesEnabled(level)) return;
        if (keepInventory(level) && !ServerConfig.PERMADEATH_ENABLED.get()) return;

        boolean hold = holdsItems();
        List<ItemStack> items = new ArrayList<>();
        if (hold && !e.isCanceled()) {
            for (ItemEntity ie : e.getDrops()) {
                ItemStack s = ie.getItem();
                if (!s.isEmpty()) items.add(s.copy());
            }
            applyDeathLoss(items, sp);
        }

        // Si algo falla creando el cuerpo NO se vacía la lista: los objetos caen al suelo como en vanilla.
        PlayerBodyEntity body = spawnFor(sp, level, items, snap);
        int xp = holdsXp(sp, snap) ? snap.xp() * ServerConfig.PERMADEATH_XP_RETENTION.get() / 100 : 0;
        if (body == null) {
            if (holdsXp(sp, snap)) ExperienceOrb.award(level, sp.position(), snap.xp());   // los orbes se anularon: no se pierde
            return;
        }
        body.setXp(xp);
        if (hold && !e.isCanceled()) e.getDrops().clear();

        registerCreated(sp, body);
    }

    /**
     * Lo que sigue a CUALQUIER cuerpo nuevo de una muerte: aviso a la party, evento para las rage forms y topes. Lo comparten
     * la muerte normal ({@link #onDrops}) y la muerte durante la visita de Uranai Baba (que no pasa por los drops).
     */
    public static void registerCreated(ServerPlayer sp, PlayerBodyEntity body) {
        if ((true)) notifyParty(sp, body);
        NeoForge.EVENT_BUS.post(new PlayerBodyCreatedEvent(sp, body, ServerConfig.PERMADEATH_ENABLED.get()));
        announceWitnesses(sp);
        enforceCap(sp.server, sp.getUUID(), body.getUUID());
        enforceWorldCap(sp.server, body.getUUID());
    }

    /** Miembros de la party del muerto que lo vieron caer (misma dimensión, dentro del radio): se avisa a los enganches. */
    private static void announceWitnesses(ServerPlayer dead) {
        List<ServerPlayer> witnesses = partyWitnesses(dead);
        if (!witnesses.isEmpty()) NeoForge.EVENT_BUS.post(new PartyMemberDiedEvent(dead, witnesses, ServerConfig.PERMADEATH_ENABLED.get()));
    }

    /**
     * Miembros de la party de {@code subject}, vivos, en su misma dimensión y a menos de {@code permadeath.witness_radius}
     * bloques: el único criterio de «testigo» (muerte y derribo). Vacía sin party o con radio 0.
     */
    public static List<ServerPlayer> partyWitnesses(ServerPlayer subject) {
        List<ServerPlayer> witnesses = new java.util.ArrayList<>();
        int radius = ServerConfig.PERMADEATH_WITNESS_RADIUS.get();
        if (radius <= 0) return witnesses;
        com.hmc.zenkai.feature.party.PartyManager.Party party = com.hmc.zenkai.feature.party.PartyManager.get(subject.server).partyOf(subject.getUUID());
        if (party == null) return witnesses;
        for (java.util.UUID id : party.members) {
            if (id.equals(subject.getUUID())) continue;
            ServerPlayer w = subject.server.getPlayerList().getPlayer(id);
            if (w != null && w.isAlive() && w.level() == subject.level() && w.distanceToSqr(subject) <= (double) radius * radius) witnesses.add(w);
        }
        return witnesses;
    }

    /** Porcentaje de la XP que guarda el cuerpo (config); lo usa la muerte durante la visita. */
    public static int xpShare(int totalXp) {
        return totalXp * ServerConfig.PERMADEATH_XP_RETENTION.get() / 100;
    }

    /** Riesgo de morir: cada pila puede perderse y los objetos con durabilidad pierden un porcentaje (sin llegar a romperse). */
    public static void applyDeathLoss(List<ItemStack> items, ServerPlayer sp) {
        int loss = ServerConfig.PERMADEATH_ITEM_LOSS_PERCENT.get();
        int dur = ServerConfig.PERMADEATH_DURABILITY_LOSS_PERCENT.get();
        if (loss <= 0 && dur <= 0) return;
        items.removeIf(s -> loss > 0 && sp.getRandom().nextInt(100) < loss);
        if (dur > 0) {
            for (ItemStack s : items) {
                if (!s.isDamageableItem()) continue;
                int add = Math.round(s.getMaxDamage() * dur / 100f);
                s.setDamageValue(Math.min(s.getMaxDamage() - 1, s.getDamageValue() + add));
            }
        }
    }

    /**
     * Crea y coloca un cuerpo con esos objetos. También lo usa el comando de debug. null si no se pudo.
     * Captura raza y forma AHORA: tras el respawn el jugador es otra entidad.
     */
    @Nullable
    public static PlayerBodyEntity spawnFor(ServerPlayer sp, ServerLevel level, List<ItemStack> items,
                                            @Nullable Snapshot snap) {
        PlayerBodyEntity body = ModEntities.PLAYER_BODY.get().create(level);
        if (body == null) return null;

        BlockPos at = sp.blockPosition();
        boolean unsafe = sp.getY() < level.getMinBuildHeight() + 2 || sp.isInLava();
        if (unsafe) {
            BlockPos safe = lastSafe(sp.getUUID(), level.dimension());
            if (safe != null) at = safe;
        }

        PlayerStatsAttachment stats = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        PlayerFormAttachment form = sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        body.configure(sp.getUUID(), sp.getGameProfile().getName(),
                String.valueOf(stats.getRace()), String.valueOf(form.getFormId()), texturesOf(sp), sp.getScale(), level.getGameTime());
        body.moveTo(unsafe ? at.getX() + 0.5 : sp.getX(), unsafe ? at.getY() : sp.getY(),
                unsafe ? at.getZ() + 0.5 : sp.getZ(), sp.getYRot(), 0f);

        placeItems(body, items, snap);
        if (snap != null) body.setHandSlot(snap.selected());   // lo que sostenía en la mano al morir (se ve junto al cuerpo)
        if (!level.addFreshEntity(body)) return null;

        PlayerBodyRegistry.get(sp.server).put(new PlayerBodyRegistry.Entry(
                body.getUUID(), sp.getUUID(), level.dimension(), body.blockPosition(), body.createdAt()));
        return body;
    }

    /**
     * Reparte los objetos en los huecos que ocupaban (si la foto los reconoce) y el resto a la mochila. Cada objeto de la foto
     * se gasta una sola vez ({@code ItemStack.matches} compara cantidad y componentes). Antes se reserva UNA ranura de Curios por
     * cada ranura que tenía el jugador, también las vacías, para que el cuerpo enseñe todos los huecos con su icono aunque no
     * llevara nada. Lo que no cabe cae al suelo.
     */
    private static void placeItems(PlayerBodyEntity body, List<ItemStack> items, @Nullable Snapshot snap) {
        boolean[] used = new boolean[PlayerBodyEntity.CURIO_BASE];
        boolean[] curioUsed = new boolean[PlayerBodyEntity.CURIO_MAX];
        List<CuriosCompat.Equipped> curios = snap == null ? List.of() : snap.curios();
        int claimed = 0;
        for (CuriosCompat.Equipped c : curios) {
            if (body.claimCurioSlot(c.id()) < 0) break;
            claimed++;
        }
        for (ItemStack item : items) {
            boolean placed = false;
            if (snap != null) {
                for (int i = 0; i < used.length && !placed; i++) {
                    if (!used[i] && !snap.slots()[i].isEmpty() && ItemStack.matches(snap.slots()[i], item)) {
                        body.inventory().setItem(i, item.copy());
                        used[i] = placed = true;
                    }
                }
                for (int k = 0; k < claimed && !placed; k++) {
                    if (curioUsed[k] || curios.get(k).stack().isEmpty() || !ItemStack.matches(curios.get(k).stack(), item)) continue;
                    body.inventory().setItem(PlayerBodyEntity.CURIO_BASE + k, item.copy());
                    curioUsed[k] = placed = true;
                }
            }
            if (!placed) {
                ItemStack left = body.insert(item);
                if (!left.isEmpty()) body.spawnAtLocation(left);   // la mochila está llena: el resto al suelo
            }
        }
    }

    /** Valor de la propiedad "textures" del perfil del jugador ("" si no tiene: servidor offline o skin sin cargar). */
    private static String texturesOf(ServerPlayer sp) {
        return sp.getGameProfile().getProperties().get("textures").stream().findFirst()
                .map(com.mojang.authlib.properties.Property::value).orElse("");
    }

    /** Aviso a la party (el dueño no lo recibe: ya sabe que murió). Con o sin coordenadas según la config. */
    private static void notifyParty(ServerPlayer owner, PlayerBodyEntity body) {
        PartyManager.Party party = PartyManager.get(owner.server).partyOf(owner.getUUID());
        if (party == null) return;
        BlockPos p = body.blockPosition();
        Component msg = (true)
                ? Component.translatable("zenkai.body.party_notice", owner.getDisplayName(),
                        p.getX(), p.getY(), p.getZ(), body.level().dimension().location().toString())
                : Component.translatable("zenkai.body.party_notice_nocoords", owner.getDisplayName());
        for (UUID m : party.members) {
            if (m.equals(owner.getUUID())) continue;
            ServerPlayer other = owner.server.getPlayerList().getPlayer(m);
            if (other != null) other.sendSystemMessage(msg);
        }
    }

    // ── Topes ────────────────────────────────────────────────────────────────

    /** Vacía un cuerpo en el suelo y lo elimina; si su chunk está descargado lo CONDENA (se elimina al volver a cargar). */
    private static void retire(MinecraftServer server, PlayerBodyRegistry reg, PlayerBodyRegistry.Entry old) {
        ServerLevel lvl = server.getLevel(old.dimension());
        if (lvl != null && lvl.getEntity(old.bodyId()) instanceof PlayerBodyEntity b) {
            b.retire(true);   // su remove() lo saca del índice
        } else {
            // Las entidades cargan DESPUÉS que el chunk: buscarlo tras getChunkAt devolvía null y lo dejaba vivo y fuera del índice.
            reg.condemn(old.bodyId(), true);
        }
    }

    /**
     * Retira los cuerpos de la vida anterior de un jugador que reencarna: vacían su contenido en el suelo (como con el tope)
     * y desaparecen. Sin esto el alma nueva podía abrir su propio cuerpo viejo y recuperar el inventario que la reencarnación
     * le quita; en el suelo queda al alcance de cualquiera, así que recuperarlo ya no es gratis.
     */
    public static void retireAllOf(MinecraftServer server, UUID owner) {
        PlayerBodyRegistry reg = PlayerBodyRegistry.get(server);
        for (PlayerBodyRegistry.Entry e : reg.of(owner)) retire(server, reg, e);
    }

    /** Tope por jugador: el más antiguo se vacía en el suelo y desaparece. */
    private static void enforceCap(MinecraftServer server, UUID owner, UUID keep) {
        int cap = ServerConfig.PERMADEATH_BODY_CAP.get();
        PlayerBodyRegistry reg = PlayerBodyRegistry.get(server);
        List<PlayerBodyRegistry.Entry> mine = reg.of(owner);   // copia ordenada: se va recortando a mano
        for (PlayerBodyRegistry.Entry old : new ArrayList<>(mine)) {
            if (mine.size() <= cap) break;
            if (old.bodyId().equals(keep)) continue;
            mine.remove(old);
            retire(server, reg, old);
        }
    }

    /** Tope global del mundo ({@code permadeath.world_body_cap}): cada cuerpo guarda su inventario como NBT. */
    private static void enforceWorldCap(MinecraftServer server, UUID keep) {
        int cap = ServerConfig.PERMADEATH_WORLD_BODY_CAP.get();
        if (cap <= 0) return;
        PlayerBodyRegistry reg = PlayerBodyRegistry.get(server);
        List<PlayerBodyRegistry.Entry> all = reg.all();
        all.sort(Comparator.comparingLong(PlayerBodyRegistry.Entry::createdAt));
        int excess = all.size() - cap;
        for (int i = 0; i < all.size() && excess > 0; i++) {
            if (all.get(i).bodyId().equals(keep)) continue;
            retire(server, reg, all.get(i));
            excess--;
        }
    }

    /**
     * Reabsorbe los cuerpos de un jugador revivido por el deseo: lo que guardan vuelve a su inventario (y su XP) y lo que no
     * cabe cae al suelo junto al cuerpo. Un cuerpo en un chunk descargado no se puede leer en el acto (las entidades cargan
     * DESPUÉS del chunk): se condena y suelta su contenido al suelo cuando el chunk vuelva a cargar. Devuelve cuántos tocó.
     */
    public static int reabsorb(ServerPlayer sp) {
        MinecraftServer server = sp.server;
        PlayerBodyRegistry reg = PlayerBodyRegistry.get(server);
        int n = 0;
        for (PlayerBodyRegistry.Entry e : new ArrayList<>(reg.of(sp.getUUID()))) {
            n++;
            ServerLevel lvl = server.getLevel(e.dimension());
            if (lvl != null && lvl.getEntity(e.bodyId()) instanceof PlayerBodyEntity b) {
                PlayerBodyLoot.takeAll(sp, b);
                b.retire(true);
            } else {
                reg.condemn(e.bodyId(), true);
            }
        }
        return n;
    }

    /**
     * Elimina cada cuerpo del mundo (los de chunks descargados al volver a cargar). {@code spill}: suelta antes su
     * contenido al suelo; si no, los objetos desaparecen con el cuerpo. Devuelve cuántos cuerpos tocó.
     */
    public static int killAll(MinecraftServer server, boolean spill) {
        PlayerBodyRegistry reg = PlayerBodyRegistry.get(server);
        int n = 0;
        for (PlayerBodyRegistry.Entry e : reg.all()) {
            n++;
            ServerLevel lvl = server.getLevel(e.dimension());
            if (lvl != null && lvl.getEntity(e.bodyId()) instanceof PlayerBodyEntity b) {
                b.retire(spill);
            } else {
                reg.condemn(e.bodyId(), spill);      // chunk descargado: se elimina al volver a cargar
            }
        }
        return n;
    }
}
