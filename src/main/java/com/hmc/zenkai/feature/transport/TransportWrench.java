package com.hmc.zenkai.feature.transport;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.block.TransportBlock;
import com.hmc.zenkai.content.blockentity.TransportBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Llaves sobre cables, tuberías y tanques: el Configurator de Mekanism y cualquier ítem del tag
 * {@code c:tools/wrench} (la llave de Create, etc.). SIN dependencia de Mekanism: su Configurator
 * anuncia lo que hace con «habilidades» de NeoForge (MekanismItemAbilities: {@code wrench_configure}
 * en sus modos de configurar, {@code wrench_dismantle} en el de llave), y una ItemAbility se identifica
 * solo por su nombre, así que basta con pedirla por ese nombre.
 *
 * Gestos, copiados de los transmisores de Mekanism:
 *  - Clic sin agacharse, con cualquiera de ellas: siguiente modo de la cara apuntada (por el punto
 *    golpeado: sobre un brazo de cable, ese brazo).
 *  - Configurator en modo CONFIGURAR + agachado: modo anterior.
 *  - Configurator en modo LLAVE o cualquier llave del tag + agachado: desmontar, con el bloque (y el
 *    contenido de un tanque) directo al inventario.
 *
 * POR QUÉ UN EVENTO Y NO useItemOn: con el Configurator en modo configurar, doesSneakBypassUse()
 * devuelve false, así que agachado Minecraft se SALTA el clic del bloque y solo llama al useOn del
 * ítem (que buscaría la capability IConfigurable de Mekanism). RightClickBlock llega antes que ambos.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class TransportWrench {
    private TransportWrench() {}

    private static final ItemAbility CONFIGURE = ItemAbility.get("wrench_configure");
    private static final ItemAbility DISMANTLE = ItemAbility.get("wrench_dismantle");

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TransportBlock)) return;

        boolean configure = stack.canPerformAction(CONFIGURE);
        boolean wrench = !configure && (stack.canPerformAction(DISMANTLE) || stack.is(Tags.Items.TOOLS_WRENCH));
        if (!configure && !wrench) return;

        // Cancelar en los dos lados: el cliente no ejecuta el useOn del ítem y el servidor recibe igual
        // el paquete de uso (se crea antes de disparar el evento).
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        Player player = event.getEntity();
        boolean sneaking = player.isShiftKeyDown();

        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof TransportBlockEntity be)) return;
        // Clic sin agacharse (llave o Configurator): cambia el modo de la cara apuntada, como Mekanism.
        // El 2026-11-17 se probó a abrir una pantalla de caras en cruz con este clic y el usuario prefirió
        // el clic directo en el puerto (con el sombreado de modos que pinta WrenchOverlay al llevar la llave).
        if (!sneaking) TransportBlock.configureSide(be, event.getHitVec(), player);
        else if (configure) TransportBlock.configureSideBack(be, event.getHitVec(), player);   // Configurator: modo anterior
        else dismantle((ServerLevel) level, pos, state, be, player, stack);
    }

    /** Quita el bloque y mete sus drops en el inventario (lo que no quepa cae al suelo). Lo usa también la llave de Zenkai. */
    public static void dismantle(ServerLevel level, BlockPos pos, BlockState state, @org.jetbrains.annotations.Nullable net.minecraft.world.level.block.entity.BlockEntity be,
                                  Player player, ItemStack tool) {
        // Los drops ANTES de quitar el bloque: la tabla de botín de un tanque copia contenido y modos
        // del block entity, que deja de existir al quitarlo.
        var drops = Block.getDrops(state, level, pos, be, player, tool);
        level.removeBlock(pos, false);
        for (ItemStack drop : drops) {
            if (!player.getInventory().add(drop)) Block.popResource(level, pos, drop);
        }
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4f, 1.2f);
    }
}
