package com.hmc.db_renewed.event;

import com.hmc.db_renewed.DragonBlockRenewed;
import com.hmc.db_renewed.entity.ModEntities;
import com.hmc.db_renewed.entity.namekian.NamekianEntity;
import com.hmc.db_renewed.entity.namekian.NamekianWarriorEntity;
import com.hmc.db_renewed.entity.shenlong.ShenLongEntity;
import com.hmc.db_renewed.item.custom.HammerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashSet;
import java.util.Set;

@EventBusSubscriber(modid = DragonBlockRenewed.MOD_ID)
public class ModEvents {

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.NAMEKIAN_WARRIOR.get(), NamekianWarriorEntity.createAttributes().build());
        event.put(ModEntities.NAMEKIAN.get(), NamekianEntity.createAttributes().build());
        event.put(ModEntities.SHENLONG.get(), ShenLongEntity.createAttributes().build());
    }

    private static final Set<BlockPos> HARVESTED_BLOCKS = new HashSet<>();
    // Done with the help of https://github.com/CoFH/CoFHCore/blob/1.19.x/src/main/java/cofh/core/event/AreaEffectEvents.java
    // Don't be a jerk License

    @SubscribeEvent
    public static void onHammerUsage(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHandItem = player.getMainHandItem();

        if (mainHandItem.getItem() instanceof HammerItem hammer && player instanceof ServerPlayer serverPlayer) {
            if (player.isCrouching()) {
                return;
            }
        }

        if(mainHandItem.getItem() instanceof HammerItem hammer && player instanceof ServerPlayer serverPlayer) {
            BlockPos initialBlockPos = event.getPos();
            if(HARVESTED_BLOCKS.contains(initialBlockPos)) {
                return;
            }

            for(BlockPos pos : HammerItem.getBlocksToBeDestroyed(1, initialBlockPos, serverPlayer)) {
                if(pos.equals(initialBlockPos) || !hammer.isCorrectToolForDrops(mainHandItem, event.getLevel().getBlockState(pos))) {
                    continue;
                }
                HARVESTED_BLOCKS.add(pos);
                serverPlayer.gameMode.destroyBlock(pos);
                HARVESTED_BLOCKS.remove(pos);
            }
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        Player player = event.getEntity();
        Entity target = event.getTarget();
        if (player.level().isClientSide) return;

        if (target.getType() == EntityType.INTERACTION && target.getTags().contains("dragon_barrier")) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("messages.db.renewed.blocked_by_shenlong"), true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getLevel().isClientSide) return;

        Entity target = event.getTarget();

        if (target.getType() == EntityType.INTERACTION && target.getTags().contains("dragon_barrier")) {
            event.setCanceled(true);
            event.getEntity().displayClientMessage(Component.translatable("messages.db.renewed.blocked_by_shenlong"), true);
        }
    }
}