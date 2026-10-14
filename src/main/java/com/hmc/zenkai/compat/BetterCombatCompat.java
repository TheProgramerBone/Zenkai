package com.hmc.zenkai.compat;

import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Puente OPCIONAL con Better Combat (BC). Cualquier referencia a clases de BC vive en {@link Impl}:
 * sin el mod esa clase no se carga y no hay NoClassDefFoundError (mismo patrón que CuriosCompat).
 *
 * QUÉ HACE, Y QUÉ NO (evaluación completa en {@code .claude/docs/compat-better-combat.md}):
 *  - SÍ: si el arma de la mano principal es de DOS manos según BC, el escudo de ki de la secundaria
 *    se desvanece. BC solo mira que la mano secundaria esté VACÍA para dejar blandir un arma de dos
 *    manos, y el escudo de ki la deja vacía a propósito: sin esto se verían las dos cosas a la vez.
 *  - NO integra las armas de ki en el sistema de combos de BC. BC solo consulta atributos POR ÍTEM
 *    (WeaponRegistry.getAttributes(ItemStack)) y las armas de ki viven con la mano vacía: darles
 *    animaciones de BC exigiría inyectar en ~19 llamadas internas de BC, y además BC 1.21.1
 *    depende de playerAnimator (KosmX) y Zenkai de la PAL de zigythebird. Con BC instalado las
 *    armas de ki siguen funcionando con el golpe de vanilla + su estela.
 */
public final class BetterCombatCompat {
    private BetterCombatCompat() {}

    private static final boolean LOADED = ModList.get().isLoaded("bettercombat");

    public static boolean isLoaded() { return LOADED; }

    /** ¿BC trata este ítem como un arma de dos manos? false si BC no está o no conoce el ítem. */
    public static boolean isTwoHandedWeapon(ItemStack stack) {
        if (!LOADED || stack.isEmpty()) return false;
        try {
            return Impl.isTwoHanded(stack);
        } catch (Throwable t) {
            // Otra versión de BC con la API movida: mejor perder el detalle que romper el servidor.
            return false;
        }
    }

    private static final class Impl {
        static boolean isTwoHanded(ItemStack stack) {
            net.bettercombat.api.WeaponAttributes attrs = net.bettercombat.logic.WeaponRegistry.getAttributes(stack);
            return attrs != null && attrs.isTwoHanded();
        }
    }
}
