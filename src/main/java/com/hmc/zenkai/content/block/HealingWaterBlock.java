package com.hmc.zenkai.content.block;

import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Cubo/bloque del agua curativa de Namek. Se coloca y se recoge exactamente como el agua
 * vanilla (BucketPickup lo hereda de LiquidBlock sin tocar nada) — las dos únicas
 * diferencias de comportamiento están aquí:
 *
 * 1. Curación PASIVA por contacto (entityInside): rellena body/stamina/ki directamente vía
 *    PlayerStatsAttachment, mismo patrón que ImmortalityEffect.applyEffectTick (fracción del
 *    máximo por segundo, tick interval propio, mirror a corazones vía
 *    PlayerLifeCycle.syncIfServer) — NO el efecto vanilla Regeneración que llevaba la
 *    primera versión. Ese primer intento solo curaba corazones (Regeneración cura vida
 *    vanilla directamente) y dejaba body/stamina/ki intactos porque, con raza elegida, esos
 *    tres pools son la vida REAL del jugador y los corazones son solo un espejo que
 *    PlayerLifeCycle.sync() recalcula a partir de body — aplicar Regeneración encima no solo
 *    no tocaba el pool real, además competía con ese espejo (ver el mismo razonamiento en
 *    ImmortalityEffect y SenzuBean: "aquí no se toca la vida vanilla, sería el segundo
 *    escritor"). Sin raza elegida (fuera del sistema Zenkai) sí se cura vida vanilla
 *    directamente, igual que hace ImmortalityEffect en ese mismo caso.
 * 2. Recogerla con una botella de vidrio da la botella de agua curativa YA EXISTENTE
 *    (bebible), no una poción de agua vanilla (useItemOn). Vanilla resuelve el llenado de
 *    botella dentro de BottleItem.use() (el "innate use" del item, con su propio raytrace
 *    que solo mira FluidTags.WATER) — ese camino se dispara DESPUÉS de useItemOn si este
 *    devuelve PASS_TO_DEFAULT_BLOCK_INTERACTION, así que interceptar aquí con un resultado
 *    que consume la acción evita que BottleItem.use() llegue a ejecutarse. El cubo (vacío o
 *    de agua normal) no pasa por aquí: BucketItem tiene su propio camino de uso totalmente
 *    aparte (su propio Item.use()), así que no hay cruce con el llenado de cubo normal.
 */
public class HealingWaterBlock extends LiquidBlock {

    /** Fracción del máximo curada POR SEGUNDO para body/stamina/ki. Más suave que
     *  ImmortalityEffect (0.15): esto es un recurso de mapa fácil de conseguir, no un buff
     *  legendario — de 0 a lleno en ~12.5s con esta fracción. */
    private static final double POOL_FRACTION_PER_SECOND = 0.08;

    /** Corazones por segundo para quien aún no ha elegido raza (fuera del sistema Zenkai). */
    private static final float VANILLA_HEAL_PER_SECOND = 4.0F;

    /** Cada cuántos ticks corre. entityInside se llama TODOS los ticks que la entidad solapa
     *  el fluido (a diferencia de un MobEffect, que ya trae su propio intervalo) — sin este
     *  throttle sería curar 20 veces por segundo en vez de un ritmo razonable. */
    private static final int TICK_INTERVAL = 10;

    public HealingWaterBlock(FlowingFluid fluid, BlockBehaviour.Properties properties) {
        super(fluid, properties);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide() && entity instanceof Player player && entity.tickCount % TICK_INTERVAL == 0) {
            healPools(player);
        }
        super.entityInside(state, level, pos, entity);
    }

    private static void healPools(Player player) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(player);

        // Sin raza elegida el jugador está fuera del pipeline de Zenkai: no hay body/stamina/
        // ki que curar, manda la vida vanilla (mismo camino que ImmortalityEffect).
        if (!att.isRaceChosen()) {
            float heal = VANILLA_HEAL_PER_SECOND * (TICK_INTERVAL / 20.0F);
            player.heal(heal);
            return;
        }

        int bodyMax = att.getBodyMax();
        if (bodyMax > 0) {
            att.addBody(poolRegen(bodyMax));
        }
        int staminaMax = att.getStaminaMax();
        if (staminaMax > 0) {
            att.addStamina(poolRegen(staminaMax));
        }
        int energyMax = att.getEnergyMax();
        if (energyMax > 0) {
            att.addEnergy(poolRegen(energyMax));
        }

        // El espejo body -> corazones vive en PlayerLifeCycle.sync(). Aquí no se toca la vida
        // vanilla directamente por la misma razón que ImmortalityEffect: sería el segundo
        // escritor de la misma vida.
        PlayerLifeCycle.syncIfServer(player);
    }

    private static int poolRegen(int max) {
        return (int) Math.max(1, Math.round(max * POOL_FRACTION_PER_SECOND * (TICK_INTERVAL / 20.0)));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.is(Items.GLASS_BOTTLE) && state.getValue(LiquidBlock.LEVEL) == 0) {
            if (!level.isClientSide()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_ALL);
                level.playSound(player, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);

                ItemStack filled = new ItemStack(ModItems.HEALING_WATER_BOTTLE.get());
                ItemStack result = ItemUtils.createFilledResult(stack, player, filled);
                player.setItemInHand(hand, result);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }
}
