package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.ZenkaiMasterEntity;
import com.hmc.zenkai.content.entity.misc.IsaacEntity;
import com.hmc.zenkai.content.entity.misc.ShadowKintounEntity;
import com.hmc.zenkai.content.entity.namek.NamekianEntity;
import com.hmc.zenkai.content.entity.namek.NamekianWarriorEntity;
import com.hmc.zenkai.content.entity.misc.KintounEntity;
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
        event.put(ModEntities.KAMI.get(), ZenkaiMasterEntity.createAttributes().build());
        event.put(ModEntities.KAIO.get(), ZenkaiMasterEntity.createAttributes().build());
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