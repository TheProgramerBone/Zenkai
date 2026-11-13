package com.hmc.zenkai.api.wish;

import com.hmc.zenkai.Zenkai;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro de deseos de addons. Es estático y seguro entre hilos: registra tus deseos en
 * {@code FMLCommonSetupEvent} (dentro de {@code event.enqueueWork}); no hace falta bus propio. Registrar dos
 * veces el mismo id sustituye al primero y avisa en el log (así un addon puede reemplazar a otro a propósito).
 * El cliente nunca ve este registro: recibe solo la lista ya filtrada para él al hablar con el dragón.
 */
public final class WishRegistry {
    private WishRegistry() {}

    private static final Map<ResourceLocation, CustomWish> WISHES = new ConcurrentHashMap<>();

    public static void register(CustomWish wish) {
        if (WISHES.put(wish.id(), wish) != null) {
            Zenkai.LOGGER.warn("[Zenkai] El deseo {} ya estaba registrado: se sustituye.", wish.id());
        }
    }

    @Nullable
    public static CustomWish get(ResourceLocation id) { return WISHES.get(id); }

    /** Los deseos que este jugador puede pedir a este dragón, en orden estable por id. */
    public static List<CustomWish> availableFor(ServerPlayer player, DragonKind dragon) {
        List<CustomWish> out = new ArrayList<>();
        for (CustomWish w : WISHES.values()) {
            try {
                if (w.dragons().contains(dragon) && w.availableTo(player, dragon)) out.add(w);
            } catch (Exception ex) {
                Zenkai.LOGGER.warn("[Zenkai] El deseo {} falló en availableTo y se oculta", w.id(), ex);
            }
        }
        out.sort(Comparator.comparing(w -> w.id().toString()));
        return out;
    }
}
