package com.hmc.zenkai.feature.party;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

/**
 * Modelo y persistencia de las parties. PURAMENTE DATOS: ni manda mensajes de chat ni
 * empaqueta nada de red — eso vive en {@link PartyService}, que es quien conoce las
 * REGLAS (quién puede invitar, tamaño máximo, etc.) y los EFECTOS (avisos, sync). Aquí
 * solo se guarda y se recupera qué party tiene cada uno.
 * MISMO PATRÓN que KorinSenzuData (feature/master): SavedData en el overworld, factory
 * estática + get(server).
 */
public class PartyManager extends SavedData {

    private static final String ID = "zenkai_party";

    /** Tamaño con el que nace toda party nueva. El líder puede subirlo después con
     *  /zparty maxsize (PartyService.setMaxSize, botón PartyConfig en PartyScreen), hasta el
     *  tope admin de CommonConfig.partyMaxSizeCeiling(). */
    public static final int DEFAULT_MAX_SIZE = 4;

    /**
     * Tope técnico absoluto del protocolo: DUPLICADO a propósito en
     * CommonConfig.PARTY_MAX_SIZE_CEILING_RAW (su límite superior de defineInRange) — el admin
     * solo puede BAJAR este número, nunca subirlo. Mismo criterio que las texturas
     * generadas (tools/gen_*.py): dos sitios con el mismo literal, comentado en los dos, en vez
     * de una dependencia cruzada config -> feature para ahorrarse una constante.
     */
    public static final int HARD_CAP = 32;

    public static final class Party {
        public final UUID id;
        public UUID leaderId;
        public final LinkedHashSet<UUID> members = new LinkedHashSet<>();
        /** Si es false (default), un miembro no puede dañar a otro miembro — ver
         *  PartyService.friendlyFireBlocked(), el único sitio que lee este campo para decidir. */
        public boolean friendlyFire = false;
        /** Tamaño máximo de ESTA party. Por-party y no global: cada líder ajusta el suyo (ver
         *  PartyService.setMaxSize), acotado a [members.size(), CommonConfig.partyMaxSizeCeiling()]. */
        public int maxSize = DEFAULT_MAX_SIZE;

        Party(UUID id, UUID leaderId) {
            this.id = id;
            this.leaderId = leaderId;
            this.members.add(leaderId);
        }
    }

    private final Map<UUID, Party> partiesById = new HashMap<>();
    /** Índice inverso jugador -> id de su party. Se reconstruye en load(), nunca se guarda. */
    private final Map<UUID, UUID> partyOfPlayer = new HashMap<>();

    /** invitado -> id de la party que le invitó. NO se persiste a propósito: una
     *  invitación pendiente no debería sobrevivir a un reinicio del servidor. */
    private final Map<UUID, UUID> pendingInvites = new HashMap<>();

    public static final SavedData.Factory<PartyManager> FACTORY =
            new SavedData.Factory<>(PartyManager::new, PartyManager::load, null);

    public static PartyManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    @Nullable
    public Party partyOf(UUID player) {
        UUID id = partyOfPlayer.get(player);
        return id == null ? null : partiesById.get(id);
    }

    @Nullable
    public Party party(UUID id) { return partiesById.get(id); }

    @Nullable
    public UUID pendingInviteOf(UUID invitee) { return pendingInvites.get(invitee); }

    // ── Mutaciones ───────────────────────────────────────────────────────────

    public Party createFor(UUID leader) {
        Party p = new Party(UUID.randomUUID(), leader);
        partiesById.put(p.id, p);
        partyOfPlayer.put(leader, p.id);
        setDirty();
        return p;
    }

    public void addMember(Party party, UUID player) {
        party.members.add(player);
        partyOfPlayer.put(player, party.id);
        setDirty();
    }

    /** Saca a un jugador de su party. Si se queda vacía (el líder también cuenta como
     *  miembro), la borra entera — no deja parties fantasma de un solo hueco vacío. */
    public void removeMember(Party party, UUID player) {
        party.members.remove(player);
        partyOfPlayer.remove(player);
        if (party.members.isEmpty()) partiesById.remove(party.id);
        setDirty();
    }

    public void disband(Party party) {
        for (UUID m : party.members) partyOfPlayer.remove(m);
        partiesById.remove(party.id);
        setDirty();
    }

    public void invite(UUID invitee, UUID partyId) { pendingInvites.put(invitee, partyId); }
    public void clearInvite(UUID invitee) { pendingInvites.remove(invitee); }

    public void setFriendlyFire(Party party, boolean on) {
        party.friendlyFire = on;
        setDirty();
    }

    /** Sin validar rango: PartyService.setMaxSize ya acotó contra el tope admin y el tamaño
     *  actual del grupo antes de llamar aquí — este method solo aplica y persiste. */
    public void setMaxSize(Party party, int size) {
        party.maxSize = size;
        setDirty();
    }

    // ── Persistencia ─────────────────────────────────────────────────────────

    public static PartyManager load(CompoundTag tag, HolderLookup.Provider registries) {
        PartyManager d = new PartyManager();
        CompoundTag parties = tag.getCompound("parties");
        for (String key : parties.getAllKeys()) {
            try {
                UUID id = UUID.fromString(key);
                CompoundTag entry = parties.getCompound(key);
                UUID leader = entry.getUUID("leader");
                Party party = new Party(id, leader);
                party.friendlyFire = entry.getBoolean("friendlyFire");
                // Ausente en guardados de antes de esta feature: cae al default de siempre,
                // no a 0 (que dejaría la party sin poder aceptar a nadie ni siquiera al líder).
                party.maxSize = entry.contains("maxSize") ? entry.getInt("maxSize") : DEFAULT_MAX_SIZE;

                ListTag membersTag = entry.getList("members", Tag.TAG_INT_ARRAY);
                for (Tag value : membersTag) {
                    UUID m = NbtUtils.loadUUID(value);
                    party.members.add(m);
                    d.partyOfPlayer.put(m, id);
                }
                d.partiesById.put(id, party);
            } catch (IllegalArgumentException ignored) {
                // UUID corrupto: se descarta esa party en vez de tirar el archivo entero
                // (mismo criterio que KorinSenzuData.load).
            }
        }
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider reg) {
        CompoundTag parties = new CompoundTag();
        for (Party p : partiesById.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("leader", p.leaderId);
            entry.putBoolean("friendlyFire", p.friendlyFire);
            entry.putInt("maxSize", p.maxSize);
            ListTag membersTag = new ListTag();
            for (UUID m : p.members) membersTag.add(NbtUtils.createUUID(m));
            entry.put("members", membersTag);
            parties.put(p.id.toString(), entry);
        }
        tag.put("parties", parties);
        return tag;
    }
}
