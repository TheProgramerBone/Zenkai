package com.hmc.zenkai.feature.player.death;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.entity.misc.PlayerBodyEntity;
import com.hmc.zenkai.feature.party.PartyManager;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ModEntities;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Crea el cuerpo de un jugador al morir y vigila el tope por jugador.
 *
 * Es un listener MÁS e independiente: no toca el orden de {@code OtherworldHandler}, {@code PlayerLifeCycle},
 * {@code DownedDeathGuard} ni {@code DeathScreenGuard}. Engancha {@link LivingDropsEvent}, que vanilla dispara
 * DESPUÉS de {@code die()} y ya con la lista de drops hecha, así que no corre dentro de ninguna pila de daño.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class PlayerBodyManager {
    private PlayerBodyManager() {}

    private record Safe(ResourceKey<Level> dim, BlockPos pos) {}

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
    }

    /** LOWEST: que cualquier otro mod haya tocado ya la lista de drops cuando la vaciamos. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || e.isCanceled()) return;
        if (!(sp.level() instanceof ServerLevel level)) return;

        List<ItemStack> items = new ArrayList<>();
        for (ItemEntity ie : e.getDrops()) {
            ItemStack s = ie.getItem();
            if (!s.isEmpty()) items.add(s.copy());
        }

        // Si algo falla creando el cuerpo NO se vacía la lista: los objetos caen al suelo como en vanilla.
        PlayerBodyEntity body = spawnFor(sp, level, items);
        if (body == null) return;
        e.getDrops().clear();

        boolean permanent = ServerConfig.PERMADEATH_ENABLED.get();
        notifyParty(sp, body);
        NeoForge.EVENT_BUS.post(new PlayerBodyCreatedEvent(sp, body, permanent));
        enforceCap(sp.server, sp.getUUID(), body.getUUID());
    }

    /**
     * Crea y coloca un cuerpo con esos objetos. También lo usa el comando de debug. null si no se pudo.
     * Captura raza y forma AHORA: tras el respawn el jugador es otra entidad.
     */
    @Nullable
    public static PlayerBodyEntity spawnFor(ServerPlayer sp, ServerLevel level, List<ItemStack> items) {
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
                String.valueOf(stats.getRace()), String.valueOf(form.getFormId()), level.getGameTime());
        body.moveTo(unsafe ? at.getX() + 0.5 : sp.getX(), unsafe ? at.getY() : sp.getY(),
                unsafe ? at.getZ() + 0.5 : sp.getZ(), sp.getYRot(), 0f);

        for (ItemStack s : items) {
            ItemStack left = body.insert(s);
            if (!left.isEmpty()) body.spawnAtLocation(left);   // más de 54 huecos (Curios, mods): el resto al suelo
        }
        if (!level.addFreshEntity(body)) return null;

        PlayerBodyRegistry.get(sp.server).put(new PlayerBodyRegistry.Entry(
                body.getUUID(), sp.getUUID(), level.dimension(), body.blockPosition(), body.createdAt()));
        return body;
    }

    /** Aviso a la party con coordenadas y dimensión (el dueño no lo recibe: ya sabe que murió). */
    private static void notifyParty(ServerPlayer owner, PlayerBodyEntity body) {
        PartyManager.Party party = PartyManager.get(owner.server).partyOf(owner.getUUID());
        if (party == null) return;
        BlockPos p = body.blockPosition();
        Component msg = Component.translatable("zenkai.body.party_notice", owner.getDisplayName(),
                p.getX(), p.getY(), p.getZ(), body.level().dimension().location().toString());
        for (UUID m : party.members) {
            if (m.equals(owner.getUUID())) continue;
            ServerPlayer other = owner.server.getPlayerList().getPlayer(m);
            if (other != null) other.sendSystemMessage(msg);
        }
    }

    /**
     * Tope por jugador: el más antiguo se vacía en el suelo y desaparece. (Cuando exista el ítem de cuerpo,
     * fase 6, aquí pasará a ítem.) Carga el chunk del viejo si hace falta: raro, y evita dejar el tope roto.
     */
    private static void enforceCap(MinecraftServer server, UUID owner, UUID keep) {
        int cap = ServerConfig.PERMADEATH_BODY_CAP.get();
        PlayerBodyRegistry reg = PlayerBodyRegistry.get(server);
        List<PlayerBodyRegistry.Entry> mine = reg.of(owner);
        for (PlayerBodyRegistry.Entry old : new ArrayList<>(mine)) {   // copia: se va quitando de `mine`
            if (mine.size() <= cap) break;
            if (old.bodyId().equals(keep)) continue;
            mine.remove(old);
            ServerLevel lvl = server.getLevel(old.dimension());
            if (lvl != null) {
                lvl.getChunkAt(old.pos());
                if (lvl.getEntity(old.bodyId()) instanceof PlayerBodyEntity b) {
                    b.spillContents();
                    b.discard();   // su remove() lo saca del índice
                    continue;
                }
            }
            reg.remove(old.bodyId());   // ya no existe de verdad
        }
    }
}
