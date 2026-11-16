package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.ZenkaiMasterEntity;
import com.hmc.zenkai.content.entity.misc.IsaacEntity;
import com.hmc.zenkai.content.entity.misc.ShadowCloneEntity;
import com.hmc.zenkai.content.entity.misc.ShadowKintounEntity;
import com.hmc.zenkai.content.entity.namek.NamekianEntity;
import com.hmc.zenkai.content.entity.namek.NamekianWarriorEntity;
import com.hmc.zenkai.content.entity.misc.KintounEntity;
import com.hmc.zenkai.content.entity.otherworld.OgreEntity;
import com.hmc.zenkai.content.entity.otherworld.YemmaEntity;
import com.hmc.zenkai.content.entity.overworld.SaibamanEntity;
import com.hmc.zenkai.content.entity.overworld.ShenLongEntity;
import com.hmc.zenkai.content.entity.misc.SpacePodEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = Zenkai.MOD_ID)
public class ModEvents {

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.NAMEKIAN_WARRIOR.get(), NamekianWarriorEntity.createAttributes().build());
        event.put(ModEntities.NAMEKIAN.get(), NamekianEntity.createAttributes().build());
        event.put(ModEntities.SHENLONG.get(), ShenLongEntity.createAttributes().build());
        event.put(ModEntities.SPACE_POD.get(), SpacePodEntity.createAttributes().build());
        event.put(ModEntities.KINTOUN.get(), KintounEntity.createAttributes().build());
        event.put(ModEntities.SAIBAMAN.get(), SaibamanEntity.createAttributes().build());
        event.put(ModEntities.SHADOW_KINTOUN.get(), ShadowKintounEntity.createAttributes().build());
        event.put(ModEntities.ISAAC.get(), IsaacEntity.createAttributes().build());
        event.put(ModEntities.YEMMA.get(), YemmaEntity.createAttributes().build());
        event.put(ModEntities.URANAI_BABA.get(),
                com.hmc.zenkai.content.entity.otherworld.UranaiBabaEntity.createAttributes().build());
        event.put(ModEntities.KAMI.get(), ZenkaiMasterEntity.createAttributes().build());
        event.put(ModEntities.KAIO.get(), ZenkaiMasterEntity.createAttributes().build());
        event.put(ModEntities.KORIN.get(), ZenkaiMasterEntity.createAttributes().build());
        event.put(ModEntities.TORIBOT.get(), com.hmc.zenkai.content.entity.master.ToriBotEntity.createAttributes().build());
        event.put(ModEntities.SHADOW_CLONE.get(), ShadowCloneEntity.createAttributes().build());
        event.put(ModEntities.RED_OGRE.get(), OgreEntity.createAttributes().build());
        event.put(ModEntities.BLUE_OGRE.get(), OgreEntity.createAttributes().build());
        event.put(ModEntities.ANGEL.get(), com.hmc.zenkai.content.entity.otherworld.AngelEntity.createAttributes().build());
    }

    /**
     * Reglas de spawn natural de los ogros (las primeras del mod): sobre suelo firme, nunca en el
     * aire ni dentro de un líquido. Sin esto, un tipo sin registrar spawnea sin restricción de
     * posición. Mob::checkMobSpawnRules y no la de Monster: el HFIL no es oscuro y la de Monster
     * exige dificultad no pacífica; el ogro es MONSTER solo para que el spawner lo reponga cada tick.
     */
    @SubscribeEvent
    public static void onRegisterSpawnPlacements(net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent event) {
        for (var type : java.util.List.of(ModEntities.RED_OGRE.get(), ModEntities.BLUE_OGRE.get())) {
            event.register(type, net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    net.minecraft.world.entity.Mob::checkMobSpawnRules,
                    net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }


    @SubscribeEvent
    public static void onAttackEntity(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        Player player = event.getEntity();
        Entity target = event.getTarget();
        if (player.level().isClientSide) return;

        if (target.getType() == EntityType.INTERACTION && target.getTags().contains("dragon_barrier")) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("messages.zenkai.blocked_by_shenlong"), true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getLevel().isClientSide) return;

        Entity target = event.getTarget();

        if (target.getType() == EntityType.INTERACTION && target.getTags().contains("dragon_barrier")) {
            event.setCanceled(true);
            event.getEntity().displayClientMessage(Component.translatable("messages.zenkai.blocked_by_shenlong"), true);
        }
    }
}