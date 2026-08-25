package com.hmc.zenkai.content.item;

import com.hmc.zenkai.registry.ModDataComponents;
import com.hmc.zenkai.registry.ModSounds;
import com.hmc.zenkai.registry.ModTags;
import com.hmc.zenkai.worldgen.DragonBallIndex;
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
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Radar de esferas del dragón. Se enciende y apaga con click derecho (toggle).
 * Coste: la búsqueda de ESTRUCTURAS es lo caro (findNearestMapStructure consulta
 * StructureCheck, que puede leer chunks de disco). Antes se llamaba una vez POR CADA
 * esfera del tag; ahora {@link #locateUnlootedStructure} intenta primero el tag ENTERO
 * en una sola llamada (findNearestMapStructure ya soporta varias estructuras a la vez) y
 * solo cae al bucle por-esfera si esa más cercana ya está saqueada. Se hace UNA SOLA VEZ
 * al encender y fija el objetivo para la activación entera; si no hay nada en rango, el
 * radar ni siquiera se enciende. El cooldown de toggle (más largo que un click normal)
 * evita que un jugador dando clic rápido repita esta búsqueda cara una y otra vez.
 * Mientras está encendido solo corre el escaneo local de bloques, delegado en
 * {@link DragonBallIndex} (chunks ya cargados, indexados una sola vez por chunk en vez de
 * recorridos desde cero en cada tick de escaneo), que es casi gratis y cubre las esferas
 * que un jugador haya colocado en su base o las que aparezcan por el camino.
 * La AGUJA sí se actualiza continuamente: el cliente recalcula el ángulo cada frame con
 * la misma función que la brújula vanilla (ver ZenkaiClientSetup), a partir de la
 * posición objetiva y la posición/rotación del jugador. El servidor solo reescribe el
 * componente radar_target cuando el objetivo cambia.
 */
public class DragonRadarItem extends Item {

    /** Flag de encendido dentro de CUSTOM_DATA. */
    private static final String RADAR_ACTIVE = "RadarActive";
    /** Evita que un mismo click alterne dos veces (main + off hand) Y que dar clic rápido
     *  repita la búsqueda de estructura (la parte cara) varias veces por segundo. */
    private static final int TOGGLE_COOLDOWN_TICKS = 20;

    private static final int NEAR_RADIUS_SQR = 16 * 16;
    private static final int DETECTION_RADIUS = 32;      // escaneo de bloques cercano
    /** Cada cuántos ticks se reevalúa el escaneo local de bloques, por jugador. */
    private static final int SCAN_INTERVAL_TICKS = 30;
    private static final int STRUCTURE_SEARCH_CHUNKS = 32;
    private static final int LOOTED_MATCH_RADIUS = 32;   // margen entre la esfera y el inicio de su estructura

    /** Centinela de "sin objetivo" dentro del caché. */
    private static final long NO_TARGET = Long.MIN_VALUE;

    /** Si pasa este tiempo sin encontrar nada, el radar se apaga solo. */
    private static final int GIVE_UP_TICKS = 200; // 10 s

    /** Último tick con hallazgo, por jugador. Transitorio, solo servidor. */
    private static final java.util.Map<UUID, Integer> LAST_HIT = new java.util.HashMap<>();

    /** Objetivo fijado al encender, por jugador: {poseLong}. Transitorio, solo servidor. */
    private static final java.util.Map<UUID, long[]> TARGET_CACHE = new java.util.HashMap<>();

    /** Los tres avisos (mensaje + sonido) que puede mandar el escaneo periódico. */
    private enum RadarFeedback { NEAR, SEARCHING, NOT_IN_RANGE }

    /** Último aviso ya mandado a cada jugador. Sin esto, el escaneo periódico reenviaba el
     *  MISMO mensaje de action-bar + el MISMO sonido cada SCAN_INTERVAL_TICKS aunque el
     *  estado (cerca/buscando/fuera de rango) no hubiera cambiado desde el aviso anterior —
     *  tráfico de red y sonido redundantes. Transitorio, solo servidor. */
    private static final java.util.Map<UUID, RadarFeedback> LAST_FEEDBACK = new java.util.HashMap<>();

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
            if (isActive(stack)) {
                // Apagar: la aguja vuelve a girar sola y se olvida el objetivo.
                setActive(stack, false);
                stack.remove(ModDataComponents.RADAR_TARGET.get());
                TARGET_CACHE.remove(player.getUUID());
                LAST_HIT.remove(player.getUUID());
                LAST_FEEDBACK.remove(player.getUUID());
                player.displayClientMessage(Component.translatable(
                        "messages.zenkai.dragon_ball_radar_off"), true);
            } else {
                // Búsqueda ÚNICA de la activación: fija el objetivo de aquí en adelante.
                BlockPos target = level instanceof ServerLevel sl
                        ? locateUnlootedStructure(sl, player.blockPosition())
                        : null;

                if (target == null) {
                    // Sin señal: no se enciende, así no queda gastando búsquedas en balde.
                    player.displayClientMessage(Component.translatable(
                            "messages.zenkai.dragon_ball_radar_not_in_range"), true);
                } else {
                    TARGET_CACHE.put(player.getUUID(), new long[]{ target.asLong() });
                    LAST_HIT.put(player.getUUID(), player.tickCount);
                    // Encendido nuevo: se olvida cualquier aviso de una activación anterior,
                    // así el primer tick de escaneo SIEMPRE avisa (aunque coincida en estado
                    // con el último aviso de la vez pasada).
                    LAST_FEEDBACK.remove(player.getUUID());
                    setActive(stack, true);
                    player.displayClientMessage(Component.translatable(
                            "messages.zenkai.dragon_ball_radar_on"), true);
                }
            }
            player.getCooldowns().addCooldown(this, TOGGLE_COOLDOWN_TICKS);
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

        // Solo el radar en mano trabaja: si no, N radares en el inventario, cada uno.
        if (!selected && player.getOffhandItem() != stack) return;

        // Desfase por jugador: evita que varios radares caigan en el mismo tick del servidor.
        if ((player.tickCount + player.getId()) % SCAN_INTERVAL_TICKS != 0) return;

        BlockPos nearest = findNearestDragonBall(level, player, player.blockPosition());
        if (nearest != null) {
            LAST_HIT.put(player.getUUID(), player.tickCount);
            // Solo escribimos si cambió: un stack. Set cada segundo resynchronization el slot
            // al conjunto de clientes, que es justo el coste que evita este enfoque.
            GlobalPos target = new GlobalPos(level.dimension(), nearest);
            if (!target.equals(stack.get(ModDataComponents.RADAR_TARGET.get()))) {
                stack.set(ModDataComponents.RADAR_TARGET.get(), target);
            }

            double distanceSqr = player.blockPosition().distToCenterSqr(
                    nearest.getX(), nearest.getY(), nearest.getZ());
            RadarFeedback state = distanceSqr <= NEAR_RADIUS_SQR ? RadarFeedback.NEAR : RadarFeedback.SEARCHING;
            if (LAST_FEEDBACK.put(player.getUUID(), state) != state) {
                if (state == RadarFeedback.NEAR) {
                    player.displayClientMessage(Component.translatable(
                            "messages.zenkai.dragon_ball_radar_near"), true);
                    player.playNotifySound(ModSounds.DRAGON_BALL_RADAR_NEAR.get(), SoundSource.PLAYERS, 0.85F, 1.0F);
                } else {
                    player.displayClientMessage(Component.translatable(
                            "messages.zenkai.dragon_ball_radar_searching"), true);
                    player.playNotifySound(ModSounds.DRAGON_BALL_RADAR_SEARCHING.get(), SoundSource.PLAYERS, 0.85F, 1.0F);
                }
            }
        } else {
            stack.remove(ModDataComponents.RADAR_TARGET.get());

            int since = player.tickCount - LAST_HIT.getOrDefault(player.getUUID(), player.tickCount);
            if (since >= GIVE_UP_TICKS) {
                // El objetivo dejó de ser válido (ya saqueado, otra dimensión...): apagar.
                setActive(stack, false);
                TARGET_CACHE.remove(player.getUUID());
                LAST_HIT.remove(player.getUUID());
                LAST_FEEDBACK.remove(player.getUUID());
                player.displayClientMessage(Component.translatable(
                        "messages.zenkai.dragon_ball_radar_off"), true);
                player.playNotifySound(ModSounds.DRAGON_BALL_RADAR_USE.get(), SoundSource.PLAYERS, 0.9F, 0.7F);
            } else if (LAST_FEEDBACK.put(player.getUUID(), RadarFeedback.NOT_IN_RANGE) != RadarFeedback.NOT_IN_RANGE) {
                player.displayClientMessage(Component.translatable(
                        "messages.zenkai.dragon_ball_radar_not_in_range"), true);
            }
        }
    }

    /**
     * 1) Esferas físicas cerca (incluye las que un jugador haya colocado en su base): esto
     *    SÍ corre cada segundo, es barato y permite que el objetivo mejore sobre la marcha.
     * 2) Si no hay ninguna cerca, el objetivo fijado al encender. No se vuelve a buscar.
     */
    private BlockPos findNearestDragonBall(Level level, Player player, BlockPos origin) {
        if (!(level instanceof ServerLevel serverLevel)) return null;

        BlockPos nearby = DragonBallIndex.nearest(serverLevel, origin, DETECTION_RADIUS);
        if (nearby != null) return nearby;

        long[] cached = TARGET_CACHE.get(player.getUUID());
        if (cached == null || cached[0] == NO_TARGET) return null;
        return BlockPos.of(cached[0]);
    }

    /**
     * Busca la estructura no-saqueada más cercana entre TODAS las esferas del tag. Al iterar el
     * tag, las estructuras de Namek entran solas en cuanto las añadas a zenkai:dragon_balls.
     * OJO: es la forma CARA (findNearestMapStructure). Llamarlo solo desde use() (una vez por
     * activación) — por eso además el toggle tiene su propio cooldown, más largo que un click.
     * <p>
     * findNearestMapStructure ya acepta VARIAS estructuras en una sola llamada y devuelve la más
     * cercana de todas; el caso común (esa más cercana no está saqueada) resuelve así en 1
     * búsqueda en vez de hasta N. Solo si resulta saqueada hace falta seguir: se descarta ESA
     * estructura del conjunto y se repite con el resto, nunca desde el tag completo otra vez —
     * el peor caso (todas saqueadas menos la última) sigue costando N búsquedas, igual que antes.
     */
    private static BlockPos locateUnlootedStructure(ServerLevel level, BlockPos origin) {
        Optional<HolderSet.Named<Structure>> set =
                level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                        .get(ModTags.Structures.DRAGON_BALLS);
        if (set.isEmpty()) return null;

        LootedDragonBalls looted = LootedDragonBalls.get(level);
        var generator = level.getChunkSource().getGenerator();

        List<Holder<Structure>> remaining = new ArrayList<>(set.get().stream().toList());
        while (!remaining.isEmpty()) {
            var found = generator.findNearestMapStructure(
                    level, HolderSet.direct(remaining), origin, STRUCTURE_SEARCH_CHUNKS, false);
            if (found == null) return null;
            BlockPos pos = found.getFirst();
            if (!looted.isLootedNear(pos, LOOTED_MATCH_RADIUS)) return pos;
            remaining.remove(found.getSecond());
        }
        return null;
    }
}