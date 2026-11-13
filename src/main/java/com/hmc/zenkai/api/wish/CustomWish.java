package com.hmc.zenkai.api.wish;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Un deseo añadido por un addon. Se registra con {@link WishRegistry#register} (normalmente en
 * {@code FMLCommonSetupEvent}, dentro de {@code enqueueWork}) y aparece al final de la lista de deseos de
 * Shenlong/Porunga, tras «Revivir mascota». La validación entera es del SERVIDOR: la pantalla solo muestra lo
 * que {@link #availableTo} permite, y el servidor lo vuelve a comprobar al recibir la petición, junto con
 * la sesión de deseo abierta ({@code WishSessions}). {@link #grant} no puede ser llamado sin que el jugador
 * esté hablando con un dragón vivo, con deseos en el pool y a menos de 32 bloques.
 */
public interface CustomWish {
    /** Id único y estable, p. ej. {@code mi_addon:riqueza}. Es el que viaja por la red. */
    ResourceLocation id();

    /** Texto del botón. Puede ser {@code Component.translatable} con la clave de tu propio lang. */
    Component name();

    /** Ítem que hace de icono del botón (su id de registro). Si no existe se pinta una barrera. */
    ResourceLocation iconItem();

    /** Dragones que lo conceden; por defecto, ambos. */
    default Set<DragonKind> dragons() { return EnumSet.allOf(DragonKind.class); }

    /**
     * ¿Puede este jugador pedirlo AHORA con este dragón? Se usa para mostrar el botón y se repite en el servidor
     * antes de conceder. Por defecto, sí.
     */
    default boolean availableTo(ServerPlayer player, DragonKind dragon) { return true; }

    /**
     * Concede el deseo. Devuelve la descripción que se difunde a los jugadores cercanos («X ha pedido …») si se
     * concedió: entonces Zenkai cierra la sesión, descuenta un deseo del pool y avisa. Devuelve
     * {@link Optional#empty()} si se rechaza (el addon avisa él mismo al jugador): no se gasta nada y el
     * deseo sigue disponible. Una excepción cuenta como rechazo y queda en el log.
     */
    Optional<Component> grant(ServerPlayer player, DragonKind dragon);
}
