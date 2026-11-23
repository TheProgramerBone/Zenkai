package com.hmc.zenkai.feature.armor;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ModDataComponents;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.EnumMap;
import java.util.Map;

/**
 * Suma los atributos imbuidos de las cuatro piezas puestas y los deja en la capa de atributos del jugador
 * (PlayerRaceStats#setArmorBonus), ANTES de StatSynergy/RaceStatTable (decisión 2026-11-16). Desde ahí entran solos en
 * melee, defensa, potencia y reserva de ki, pools, PL, capacidad de pesas y de gravedad, y el drenaje de formas/Kaioken.
 *
 * Por qué en el TICK y no en LivingEquipmentChangeEvent: el evento no ve un cambio del componente dentro de una pieza ya
 * puesta (el comando de depuración, la futura máquina) ni un cambio de caps.armor_attribute en caliente. Leer cuatro
 * ranuras por jugador y tick es despreciable, y solo se recalcula y sincroniza cuando el resultado CAMBIA, así que no hay
 * un packet por tick (la misma regla que CombatModeServerState con el attack_speed).
 * Solo las ranuras de armadura: una pieza imbuida en la mano o en el inventario no da nada (sin Curios a propósito, el
 * diseño habla de armaduras).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class ArmorAttributeBonus {
    private ArmorAttributeBonus() {}

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (refresh(sp)) PlayerLifeCycle.sync(sp);
    }

    /**
     * Recalcula el bono del jugador. Devuelve true si cambió (y entonces ya recalculó los pools; falta sincronizar).
     * Público para los gametests y el comando, que no quieren esperar al siguiente tick.
     */
    public static boolean refresh(ServerPlayer sp) {
        PlayerStatsAttachment att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        return att.setArmorBonus(compute(sp, ServerConfig.armorAttributeCap()));
    }

    /** Bono de las piezas puestas, con el techo total por atributo (P1). Sin efectos secundarios. */
    public static Map<ZenkaiAttributes, Integer> compute(LivingEntity entity, int cap) {
        Map<ZenkaiAttributes, Integer> out = new EnumMap<>(ZenkaiAttributes.class);
        long[] sums = new long[ZenkaiAttributes.values().length];
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            ImbuedAttributes imb = stack.get(ModDataComponents.IMBUED.get());
            if (imb == null) continue;
            for (ZenkaiAttributes a : ImbuedAttributes.IMBUABLE) sums[a.ordinal()] += imb.get(a);
        }
        // Suma en long: cuatro piezas editadas a mano con Integer.MAX_VALUE desbordarían un int a negativo.
        for (ZenkaiAttributes a : ZenkaiAttributes.values()) {
            out.put(a, (int) Math.min(Math.max(0, cap), sums[a.ordinal()]));
        }
        return out;
    }
}
