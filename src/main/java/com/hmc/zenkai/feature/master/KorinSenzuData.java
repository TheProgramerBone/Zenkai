package com.hmc.zenkai.feature.master;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cuántas semillas ha dado Korin a cada jugador y en qué día de Minecraft. Se guarda en el
 * store del overworld, igual que ZenkaiWorldData.
energy_generator * POR QUÉ NO VA EN PlayerStatsAttachment: eso es lo que se sincroniza al cliente en cada
 * cambio de stats, y esto el cliente no lo necesita para nada — Korin responde por chat. Meter
 * dos enteros ahí sería mandarlos por la red cada vez que alguien recibe un golpe.
energy_generator * EL DÍA SE GUARDA, NO EL INSTANTE DE EXPIRACIÓN. Un jugador que reciba sus cinco semillas al
 * anochecer debe poder volver a pedir al amanecer siguiente, no veinte minutos después: la
 * ración es "por día", y un temporizador de 24.000 ticks desde la última entrega convertiría
 * eso en una ventana móvil que nunca coincide con el amanecer.
 */
public class KorinSenzuData extends SavedData {

    private static final String ID = "zenkai_korin_senzu";

    /** Semillas por día y jugador. */
    public static final int DAILY_LIMIT = 5;

    private record Ration(long day, int given) {}

    private final Map<UUID, Ration> rations = new HashMap<>();

    public static final SavedData.Factory<KorinSenzuData> FACTORY =
            new SavedData.Factory<>(KorinSenzuData::new, KorinSenzuData::load, null);

    public static KorinSenzuData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    /** Día de Minecraft del overworld. Se usa SIEMPRE el del overworld aunque Korin viva en
     *  otro sitio: si cada dimensión llevara su reloj, el límite se reiniciaría viajando. */
    public static long dayOf(MinecraftServer server) {
        return server.overworld().getDayTime() / 24000L;
    }

    /** Cuántas le quedan hoy a este jugador. */
    public int remaining(MinecraftServer server, UUID player) {
        Ration r = rations.get(player);
        long today = dayOf(server);
        if (r == null || r.day() != today) return DAILY_LIMIT;
        return Math.max(0, DAILY_LIMIT - r.given());
    }

    /**
     * Apunta {@code amount} semillas entregadas hoy. Devuelve cuántas se pudieron dar de
     * verdad — nunca más de las que quedaban, aunque quien llame pida de más.
     */
    public int claim(MinecraftServer server, UUID player, int amount) {
        long today = dayOf(server);
        Ration r = rations.get(player);
        int already = (r != null && r.day() == today) ? r.given() : 0;

        int give = Math.min(amount, Math.max(0, DAILY_LIMIT - already));
        if (give <= 0) return 0;

        rations.put(player, new Ration(today, already + give));
        setDirty();
        return give;
    }

    public static KorinSenzuData load(CompoundTag tag, HolderLookup.Provider registries) {
        KorinSenzuData d = new KorinSenzuData();
        CompoundTag map = tag.getCompound("rations");
        for (String key : map.getAllKeys()) {
            try {
                CompoundTag entry = map.getCompound(key);
                d.rations.put(UUID.fromString(key),
                        new Ration(entry.getLong("day"), entry.getInt("given")));
            } catch (IllegalArgumentException ignored) {
                // UUID corrupto: se descarta esa entrada en vez de tirar el archivo entero.
                // El jugador afectado recupera su ración del día, que es el fallo benigno.
            }
        }
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider reg) {
        CompoundTag map = new CompoundTag();
        rations.forEach((id, r) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("day", r.day());
            entry.putInt("given", r.given());
            map.put(id.toString(), entry);
        });
        tag.put("rations", map);
        return tag;
    }
}