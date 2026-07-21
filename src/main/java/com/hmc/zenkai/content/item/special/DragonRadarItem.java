package com.hmc.zenkai.content.item.special;

import com.hmc.zenkai.content.item.ModDataComponents;
import com.hmc.zenkai.content.sound.ModSounds;
import com.hmc.zenkai.util.ModTags;
import com.hmc.zenkai.worldgen.LootedDragonBalls;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Radar de esferas del dragón. Se enciende y apaga con click derecho (toggle).
 * Mientras está encendido, una vez por segundo busca la esfera más cercana y escribe su
 * posición en el componente radar_target; la aguja la dibuja el cliente con la misma
 * función que la brújula vanilla (ver ZenkaiClientSetup), lo que da el giro suavizado y
 * evita resincronizar el slot cada segundo.
 * Búsqueda en dos pasos: primero bloques de esfera cercanos (cubre las que un jugador
 * haya colocado en su base), y si no hay, la ESTRUCTURA no saqueada más cercana.
 */
public class DragonRadarItem extends Item {

    /** Flag de encendido dentro de CUSTOM_DATA. */
    private static final String RADAR_ACTIVE = "RadarActive";
    /** Evita que un mismo click alterne dos veces (main + off hand). */
    private static final int TOGGLE_COOLDOWN_TICKS = 5;

    private static final int NEAR_RADIUS_SQR = 16 * 16;
    private static final int DETECTION_RADIUS = 32;      // escaneo de bloques cercano
    private static final int STRUCTURE_SEARCH_CHUNKS = 100;
    private static final int LOOTED_MATCH_RADIUS = 32;   // margen entre la esfera y el inicio de su estructura
    private static final int TARGET_CACHE_TICKS = 200;   // 10 s entre búsquedas completas

    /** Si pasa este tiempo sin encontrar nada, el radar se apaga solo (ahorra búsquedas). */
    private static final int GIVE_UP_TICKS = 200; // 10 s

    /** Último tick con hallazgo, por jugador. Transitorio, solo servidor. */
    private static final java.util.Map<UUID, Integer> LAST_HIT = new java.util.HashMap<>();

    /** Objetivo cacheado por jugador: {posLong, tickDeCaducidad}. Solo servidor, no persiste. */
    private static final java.util.Map<UUID, long[]> TARGET_CACHE = new java.util.HashMap<>();

    public DragonRadarItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.zenkai.dragon_ball_radar"));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    public static boolean isActive(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getBoolean(RADAR_ACTIVE);
    }

    private static void setActive(ItemStack stack, boolean on) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (on) tag.putBoolean(RADAR_ACTIVE, true);
        else tag.remove(RADAR_ACTIVE);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player,
                                                           @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            boolean on = !isActive(stack);
            setActive(stack, on);
            if (!on) {
                // Apagar: la aguja vuelve a girar sola y se olvida el objetivo cacheado.
                stack.remove(ModDataComponents.RADAR_TARGET.get());
                TARGET_CACHE.remove(player.getUUID());
            }
            player.displayClientMessage(Component.translatable(
                    on ? "messages.zenkai.dragon_ball_radar_on"
                            : "messages.zenkai.dragon_ball_radar_off"), true);
            player.getCooldowns().addCooldown(this, TOGGLE_COOLDOWN_TICKS);
        }

        boolean on = !isActive(stack);
        setActive(stack, on);
        if (on) {
            LAST_HIT.put(player.getUUID(), player.tickCount);
        } else {
            // Apagar: la aguja vuelve a girar sola y se olvida el objetivo cacheado.
            stack.remove(ModDataComponents.RADAR_TARGET.get());
            TARGET_CACHE.remove(player.getUUID());
            LAST_HIT.remove(player.getUUID());
        }

        player.playNotifySound(ModSounds.DRAGON_BALL_RADAR_USE.get(), SoundSource.PLAYERS, 0.9F, 0.85F);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, Level level,
                              net.minecraft.world.entity.@NotNull Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof Player player)) return;

        if (!isActive(stack)) {
            if (stack.has(ModDataComponents.RADAR_TARGET.get())) {
                stack.remove(ModDataComponents.RADAR_TARGET.get());
            }
            return;
        }

        // Solo el radar en mano trabaja: si no, N radares en el inventario ticarían todos.
        if (!selected && player.getOffhandItem() != stack) return;

        // Desfase por jugador: evita que varios radares caigan en el mismo tick del servidor.
        if ((player.tickCount + player.getId()) % 20 != 0) return;

        BlockPos nearest = findNearestDragonBall(level, player, player.blockPosition());
        if (nearest != null) {
            LAST_HIT.put(player.getUUID(), player.tickCount);
            // Solo escribimos si cambió: un stack.set cada segundo resincronizaría el slot
            // a todos los clientes, que es justo el coste que evita este enfoque.
            GlobalPos target = new GlobalPos(level.dimension(), nearest);
            if (!target.equals(stack.get(ModDataComponents.RADAR_TARGET.get()))) {
                stack.set(ModDataComponents.RADAR_TARGET.get(), target);
            }

            double distanceSqr = player.blockPosition().distToCenterSqr(
                    nearest.getX(), nearest.getY(), nearest.getZ());
            if (distanceSqr <= NEAR_RADIUS_SQR) {
                player.displayClientMessage(Component.translatable(
                        "messages.zenkai.dragon_ball_radar_near"), true);
                player.playNotifySound(ModSounds.DRAGON_BALL_RADAR_NEAR.get(), SoundSource.PLAYERS, 0.85F, 1.0F);
            } else {
                player.displayClientMessage(Component.translatable(
                        "messages.zenkai.dragon_ball_radar_searching"), true);
                player.playNotifySound(ModSounds.DRAGON_BALL_RADAR_SEARCHING.get(), SoundSource.PLAYERS, 0.85F, 1.0F);
            }
        } else {
            stack.remove(ModDataComponents.RADAR_TARGET.get());

            int since = player.tickCount - LAST_HIT.getOrDefault(player.getUUID(), player.tickCount);
            if (since >= GIVE_UP_TICKS) {
                // Nada en rango durante 10 s: apagado automático para no seguir buscando.
                setActive(stack, false);
                TARGET_CACHE.remove(player.getUUID());
                LAST_HIT.remove(player.getUUID());
                player.displayClientMessage(Component.translatable(
                        "messages.zenkai.dragon_ball_radar_off"), true);
                player.playNotifySound(ModSounds.DRAGON_BALL_RADAR_USE.get(), SoundSource.PLAYERS, 0.9F, 0.7F);
            } else {
                player.displayClientMessage(Component.translatable(
                        "messages.zenkai.dragon_ball_radar_not_in_range"), true);
            }
        }
    }

    /**
     * 1) Esferas físicas cerca (incluye las que un jugador haya colocado en su base).
     * 2) Si no hay, la estructura NO saqueada más cercana, cacheada por jugador.
     */
    private BlockPos findNearestDragonBall(Level level, Player player, BlockPos origin) {
        if (!(level instanceof ServerLevel serverLevel)) return null;

        BlockPos nearby = scanNearbyBalls(serverLevel, origin);
        if (nearby != null) return nearby;

        long[] cached = TARGET_CACHE.get(player.getUUID());
        if (cached != null && player.tickCount < cached[1]) return BlockPos.of(cached[0]);

        BlockPos target = locateUnlootedStructure(serverLevel, origin);
        if (target != null) {
            TARGET_CACHE.put(player.getUUID(),
                    new long[]{ target.asLong(), player.tickCount + TARGET_CACHE_TICKS });
        } else {
            TARGET_CACHE.remove(player.getUUID());
        }
        return target;
    }

    /**
     * Esfera física más cercana, SOLO en chunks ya cargados. Por chunk descarta las secciones
     * cuya paleta no contiene esferas (maybeHas es barato) y solo itera las que sí; además poda
     * los chunks cuya esquina más próxima ya queda más lejos que el mejor hallazgo.
     * Mismo enfoque que ScouterAreaScanPacket: el cubo bruto costaba ~275k lecturas por segundo.
     */
    private static BlockPos scanNearbyBalls(ServerLevel level, BlockPos origin) {
        int minCx = (origin.getX() - DETECTION_RADIUS) >> 4, maxCx = (origin.getX() + DETECTION_RADIUS) >> 4;
        int minCz = (origin.getZ() - DETECTION_RADIUS) >> 4, maxCz = (origin.getZ() + DETECTION_RADIUS) >> 4;

        BlockPos nearest = null;
        double bestSqr = (double) DETECTION_RADIUS * DETECTION_RADIUS;

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz); // nunca fuerza carga
                if (chunk == null) continue;

                double dx = axisGap(origin.getX(), cx << 4, (cx << 4) + 15);
                double dz = axisGap(origin.getZ(), cz << 4, (cz << 4) + 15);
                if (dx * dx + dz * dz > bestSqr) continue;

                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection sec = sections[i];
                    if (sec.hasOnlyAir()) continue;
                    if (!sec.maybeHas(s -> s.is(ModTags.Blocks.DRAGON_BALLS_BLOCK))) continue;

                    int baseY = level.getSectionYFromSectionIndex(i) << 4;
                    if (baseY + 15 < origin.getY() - DETECTION_RADIUS
                            || baseY > origin.getY() + DETECTION_RADIUS) continue;

                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                if (!sec.getBlockState(x, y, z).is(ModTags.Blocks.DRAGON_BALLS_BLOCK)) continue;
                                BlockPos p = new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z);
                                double d = origin.distSqr(p);
                                if (d < bestSqr) { bestSqr = d; nearest = p; }
                            }
                        }
                    }
                }
            }
        }
        return nearest;
    }

    /** Distancia de v al intervalo [lo, hi] (0 si está dentro). */
    private static double axisGap(int v, int lo, int hi) {
        if (v < lo) return lo - v;
        if (v > hi) return v - hi;
        return 0;
    }

    /**
     * Busca la estructura más cercana de CADA esfera del tag por separado y se queda con la más
     * próxima que no esté marcada como saqueada. Ir por estructura (y no con el tag entero) es lo
     * que permite tener alternativa cuando la más cercana ya está vacía. Al iterar el tag, las
     * estructuras de Namek entran solas en cuanto las añadas a zenkai:dragon_balls.
     */
    private static BlockPos locateUnlootedStructure(ServerLevel level, BlockPos origin) {
        Optional<HolderSet.Named<Structure>> set =
                level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                        .get(ModTags.Structures.DRAGON_BALLS);
        if (set.isEmpty()) return null;

        LootedDragonBalls looted = LootedDragonBalls.get(level);
        BlockPos best = null;
        double bestSqr = Double.MAX_VALUE;

        for (Holder<Structure> holder : set.get()) {
            var found = level.getChunkSource().getGenerator().findNearestMapStructure(
                    level, HolderSet.direct(holder), origin, STRUCTURE_SEARCH_CHUNKS, false);
            if (found == null) continue;
            BlockPos pos = found.getFirst();
            if (looted.isLootedNear(pos, LOOTED_MATCH_RADIUS)) continue;
            double d = origin.distSqr(pos);
            if (d < bestSqr) { bestSqr = d; best = pos; }
        }
        return best;
    }
}