package com.hmc.zenkai.event;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.alignment.AlignmentFearGoals;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * El Villager VAINILLA real huye de jugadores de alineamiento EVIL, igual que NamekianEntity —
 * misma goal, misma condición (ver AlignmentFearGoals), sin tocar comercio ni precios.
 *
 * Verificado en fuente vanilla: ni Villager ni AbstractVillager registran ningún Goal propio en
 * goalSelector en esta versión (toda su IA normal sale del Brain), así que esta goal no compite
 * con nada A NIVEL DE GOALSELECTOR — el único riesgo real es GoalSelector vs Brain, dos sistemas
 * independientes que pueden pelear el control de movimiento el mismo tick (p. ej. un aldeano
 * "trabajando"). Confirmar jugando; no bloqueaba el añadir la goal.
 *
 * Marcador en persistentData: EntityJoinLevelEvent puede repetirse para la MISMA instancia viva
 * (no solo en spawn/carga de chunk); sin el marcador, un segundo disparo añadiría una SEGUNDA
 * AvoidEntityGoal al mismo goalSelector.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class VillagerFearHook {
    private VillagerFearHook() {}

    private static final String MARKER = "zenkai_evil_fear_goal";

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Villager villager)) return;

        CompoundTag pd = villager.getPersistentData();
        if (pd.getBoolean(MARKER)) return;
        pd.putBoolean(MARKER, true);

        villager.goalSelector.addGoal(1, AlignmentFearGoals.avoidingEvilPlayers(villager));
    }
}
