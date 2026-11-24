package com.hmc.zenkai.content.entity.misc;

import com.hmc.zenkai.content.item.CapsuleItem;
import com.hmc.zenkai.feature.capsule.CapsuleContent;
import com.hmc.zenkai.feature.capsule.CapsuleRelease;
import com.hmc.zenkai.registry.ModEntities;
import com.hmc.zenkai.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Cápsula llena lanzada (Fase 6, modo «bola de nieve»). Al caer suelta lo guardado con la capa de abajo ENCIMA del suelo
 * donde aterriza: si da en una pared o en el techo, baja hasta encontrar suelo (como mucho 16 bloques). Si da en una
 * entidad, se suelta a sus pies.
 *
 * Sin holograma no se puede prever si cabe: si hay obstrucción o un claim lo prohíbe, la cápsula VUELVE al jugador sin
 * gastarse, con el aviso de siempre (§3.5). Si quien la lanzó ya no está, cae como ítem donde dio.
 */
public class ThrownCapsuleEntity extends ThrowableItemProjectile {

    private static final int MAX_DROP = 16;

    public ThrownCapsuleEntity(EntityType<? extends ThrownCapsuleEntity> type, Level level) {
        super(type, level);
    }

    public ThrownCapsuleEntity(Level level, LivingEntity shooter) {
        super(ModEntities.THROWN_CAPSULE.get(), shooter, level);
    }

    @Override
    protected @NotNull Item getDefaultItem() { return ModItems.CAPSULE_COPPER.get(); }

    @Override
    protected void onHit(@NotNull HitResult hit) {
        super.onHit(hit);
        if (!(level() instanceof ServerLevel sl) || isRemoved()) return;
        BlockPos anchor;
        if (hit instanceof BlockHitResult bh) {
            anchor = bh.getBlockPos().relative(bh.getDirection());
        } else if (hit instanceof EntityHitResult eh) {
            anchor = eh.getEntity().blockPosition();
        } else {
            anchor = blockPosition();
        }
        anchor = settle(sl, anchor);
        land(sl, anchor);
        discard();
    }

    /** Baja hasta que lo de debajo no sea reemplazable (suelo de verdad). */
    private static BlockPos settle(ServerLevel level, BlockPos p) {
        BlockPos.MutableBlockPos m = p.mutable();
        for (int i = 0; i < MAX_DROP && m.getY() > level.getMinBuildHeight(); i++) {
            BlockPos below = m.below();
            if (!level.getBlockState(below).canBeReplaced()) break;
            m.move(0, -1, 0);
        }
        return m.immutable();
    }

    private void land(ServerLevel sl, BlockPos anchor) {
        ItemStack stack = getItem().copy();
        CapsuleContent c = CapsuleItem.content(stack);
        Entity owner = getOwner();
        ServerPlayer player = owner instanceof ServerPlayer sp && !sp.isRemoved() ? sp : null;
        if (c == null) {
            giveBack(sl, player, stack);
            return;
        }
        Player actor = player != null ? player : FakePlayerFactory.getMinecraft(sl);
        CapsuleRelease.Outcome o = CapsuleRelease.release(sl, anchor, c, actor, null);
        if (o == CapsuleRelease.Outcome.OK) {
            giveBack(sl, player, CapsuleItem.emptied(stack));
            return;
        }
        if (o == CapsuleRelease.Outcome.MISSING) stack = CapsuleItem.emptied(stack);
        if (player != null) {
            player.displayClientMessage(Component.translatable(
                    "message.zenkai.capsule.release_" + o.name().toLowerCase(java.util.Locale.ROOT)), false);
        }
        giveBack(sl, player, stack);
    }

    private void giveBack(ServerLevel sl, ServerPlayer player, ItemStack stack) {
        if (player != null && player.level() == sl) {
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        } else {
            spawnAtLocation(stack);
        }
    }
}
