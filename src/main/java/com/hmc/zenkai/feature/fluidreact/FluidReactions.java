package com.hmc.zenkai.feature.fluidreact;

import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Transformaciones de BLOQUES SÓLIDOS junto a un fluido del mod (0.7.0). Las reacciones fluido contra
 * fluido viven en {@code KiFluidInteractions} y {@code HfilWaterInteractions}; la tabla completa y su
 * justificación, en {@code .claude/docs/interacciones-de-fluidos.md}.
 *
 * Identidad (decidida con el usuario, 2026-11-17): el KI fragmenta y transforma (y el súper «cuece»,
 * porque es calor), el AGUA HIRVIENDO deteriora (lixivia, ablanda, derrite, agrieta) y el AGUA CURATIVA
 * restaura y fertiliza. Regla de exclusividad: ningún resultado es un bloque propio de otro bioma o
 * dimensión, y nada del HFIL ni de Namek aparece aquí (la tabla es una lista blanca: lo que no está,
 * no se toca).
 *
 * Mecánica: cada bloque de fluido programa un tic de bloque por segundo MIENTRAS tenga algo que
 * transformar a su alrededor (y se despierta con onPlace, con cualquier cambio de vecino y con un tic
 * aleatorio por si el bloque sólido se puso mucho antes). En cada tic prueba sus seis vecinos; cada
 * regla tiene un tiempo medio en segundos y se cumple con probabilidad 1/segundos por tic (los
 * tiempos son medios, no exactos). Una transformación por tic. Sin configuración, a petición del
 * usuario.
 */
public final class FluidReactions {
    private FluidReactions() {}

    /** Quién reacciona. Los cuatro ki se separan porque cada uno tiene su propia tabla. */
    public enum Family {
        BOILING, HEALING, KI_BASE, KI_SUPER, KI_DIVINE, KI_LEGENDARY;

        public static Family ofKi(KiEnergyType t) {
            return switch (t) {
                case BASE -> KI_BASE;
                case SUPER -> KI_SUPER;
                case DIVINE -> KI_DIVINE;
                case LEGENDARY -> KI_LEGENDARY;
            };
        }

        boolean isKi() { return this != BOILING && this != HEALING; }
    }

    /** Un cambio posible: bloque resultante, tiempo medio en segundos (0 = inmediato) y desde qué lado. */
    private record Rule(BlockState to, double seconds, Predicate<Direction> side) {}

    /** Segundos entre tics de una transformación activa. */
    private static final int PERIOD = 20;
    /** Probabilidad de que un fluido de ki se gaste (desaparezca) tras transformar un bloque: sin esto un
     *  solo bloque de ki procesaría para siempre. */
    private static final double KI_SPENT_CHANCE = 0.20;
    /** Agua curativa y cultivos: un retoque pequeño, 1 de cada tantos tics extra de crecimiento por cultivo. */
    private static final int CROP_BOOST_ONE_IN = 20;
    private static final int CROP_RADIUS = 3;

    private static Map<Family, Map<Block, List<Rule>>> table;

    private static Map<Family, Map<Block, List<Rule>>> table() {
        if (table == null) table = build();
        return table;
    }

    // ───────────────────────────── tabla ─────────────────────────────

    private static Map<Family, Map<Block, List<Rule>>> build() {
        Map<Family, Map<Block, List<Rule>>> t = new EnumMap<>(Family.class);
        for (Family f : Family.values()) t.put(f, new HashMap<>());
        Predicate<Direction> any = d -> true;

        // ── AGUA HIRVIENTE: deteriora ──
        Map<Block, List<Rule>> b = t.get(Family.BOILING);
        add(b, Blocks.GRASS_BLOCK, Blocks.DIRT, 5, any);
        add(b, Blocks.WET_SPONGE, Blocks.SPONGE, 5, any);
        add(b, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE, 5, any);
        add(b, Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS, 5, any);
        add(b, Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, 5, any);
        add(b, Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, 5, any);
        add(b, Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES, 5, any);
        add(b, Blocks.NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS, 5, any);
        // Derrite: la nieve y el hielo se vuelven vapor (sin dejar agua: el agua vanilla junto a esta poza se evapora igual).
        add(b, Blocks.SNOW, Blocks.AIR, 3, any);
        add(b, Blocks.SNOW_BLOCK, Blocks.AIR, 5, any);
        add(b, Blocks.POWDER_SNOW, Blocks.AIR, 3, any);
        add(b, Blocks.ICE, Blocks.AIR, 5, any);
        add(b, Blocks.PACKED_ICE, Blocks.ICE, 5, any);
        add(b, Blocks.BLUE_ICE, Blocks.PACKED_ICE, 5, any);

        // ── AGUA CURATIVA: restaura y fertiliza ──
        Map<Block, List<Rule>> h = t.get(Family.HEALING);
        add(h, Blocks.DIRT, Blocks.GRASS_BLOCK, 20, d -> d != Direction.DOWN);
        add(h, Blocks.DIRT, Blocks.MUD, 5, d -> d == Direction.DOWN);   // tierra SUMERGIDA (el fluido está encima)
        add(h, Blocks.COARSE_DIRT, Blocks.DIRT, 10, any);
        add(h, Blocks.ROOTED_DIRT, Blocks.DIRT, 10, any);
        add(h, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, 15, any);
        add(h, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, 15, any);
        add(h, Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICKS, 15, any);
        add(h, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS, 15, any);
        add(h, Blocks.CRACKED_DEEPSLATE_TILES, Blocks.DEEPSLATE_TILES, 15, any);
        add(h, Blocks.CRACKED_NETHER_BRICKS, Blocks.NETHER_BRICKS, 15, any);
        for (DyeColor c : DyeColor.values()) {   // hormigón en polvo: inmediato, como con agua vanilla
            add(h, named(c.getName() + "_concrete_powder"), named(c.getName() + "_concrete"), 0, any);
        }

        // ── KI: fragmenta y transforma ──
        for (Family f : new Family[]{Family.KI_BASE, Family.KI_SUPER, Family.KI_DIVINE, Family.KI_LEGENDARY}) {
            Map<Block, List<Rule>> k = t.get(f);
            // adoquín → grava: cuanto más fuerte, más rápido (el legendario pulveriza)
            double cobble = switch (f) { case KI_SUPER -> 4; case KI_LEGENDARY -> 2; default -> 8; };
            add(k, Blocks.COBBLESTONE, Blocks.GRAVEL, cobble, any);
            add(k, Blocks.MUD, Blocks.CLAY, f == Family.KI_DIVINE ? 5 : 10, any);
        }
        Map<Block, List<Rule>> ks = t.get(Family.KI_SUPER);
        Map<Block, List<Rule>> kl = t.get(Family.KI_LEGENDARY);
        for (Map<Block, List<Rule>> k : List.of(ks, kl)) {
            double s = k == ks ? 8 : 4;
            add(k, Blocks.GRAVEL, Blocks.SAND, s, any);
            add(k, Blocks.SANDSTONE, Blocks.SAND, s, any);
            add(k, Blocks.RED_SANDSTONE, Blocks.RED_SAND, s, any);
        }
        // El súper es calor: aquí «cuece» (antes lo hacía el agua hirviendo, que a ~98 °C no funde nada).
        add(ks, Blocks.SAND, Blocks.GLASS, 10, any);
        add(ks, Blocks.RED_SAND, Blocks.GLASS, 10, any);
        add(ks, Blocks.CLAY, Blocks.TERRACOTTA, 10, any);
        add(ks, Blocks.STONE, Blocks.SMOOTH_STONE, 5, any);
        for (DyeColor c : DyeColor.values()) add(ks, named(c.getName() + "_terracotta"), named(c.getName() + "_glazed_terracotta"), 10, any);
        // El divino purifica: quita el tinte.
        Map<Block, List<Rule>> kd = t.get(Family.KI_DIVINE);
        for (DyeColor c : DyeColor.values()) {
            add(kd, named(c.getName() + "_stained_glass"), Blocks.GLASS, 5, any);
            add(kd, named(c.getName() + "_terracotta"), Blocks.TERRACOTTA, 5, any);
        }
        return t;
    }

    private static Block named(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(path));
    }

    private static void add(Map<Block, List<Rule>> m, Block from, Block to, double seconds, Predicate<Direction> side) {
        m.computeIfAbsent(from, k -> new ArrayList<>()).add(new Rule(to.defaultBlockState(), seconds, side));
    }

    // ───────────────────────────── mecánica ─────────────────────────────

    /** Despierta el bloque de fluido si no tenía ya un tic pendiente. Solo servidor. */
    public static void wake(Level level, BlockPos pos, Block self) {
        if (level.isClientSide()) return;
        if (!level.getBlockTicks().hasScheduledTick(pos, self)) level.scheduleTick(pos, self, PERIOD);
    }

    public static void tick(ServerLevel level, BlockPos pos, Block self, Family family) {
        if (level.getBlockState(pos).getBlock() != self) return;
        boolean busy = false;
        Map<Block, List<Rule>> rules = table().get(family);

        List<Direction> dirs = new ArrayList<>(List.of(Direction.values()));
        Collections.shuffle(dirs, new java.util.Random(level.random.nextLong()));
        boolean converted = false;
        for (Direction d : dirs) {
            if (converted) break;
            BlockPos np = pos.relative(d);
            List<Rule> list = rules.get(level.getBlockState(np).getBlock());
            if (list == null) continue;
            for (Rule r : list) {
                if (!r.side().test(d)) continue;
                busy = true;
                double p = r.seconds() <= 1 ? 1.0 : 1.0 / r.seconds();
                if (level.random.nextDouble() < p) {
                    level.setBlock(np, r.to(), Block.UPDATE_ALL);
                    puff(level, np, family);
                    converted = true;
                    break;
                }
            }
        }

        if (converted && family.isKi() && level.random.nextDouble() < KI_SPENT_CHANCE) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);   // el ki se gastó
            puff(level, pos, family);
            return;
        }
        if (family == Family.HEALING && boostCrops(level, pos)) busy = true;
        if (busy) level.scheduleTick(pos, self, PERIOD);
    }

    /** Agua curativa: da un tic de crecimiento extra, de vez en cuando, a los cultivos y retoños cercanos.
     *  Devuelve si había alguno (para seguir despierta). La hidratación de la tierra de cultivo va aparte
     *  (FluidType canHydrate en ModFluids). */
    private static boolean boostCrops(ServerLevel level, BlockPos pos) {
        boolean found = false;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -CROP_RADIUS; dx <= CROP_RADIUS; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -CROP_RADIUS; dz <= CROP_RADIUS; dz++) {
                    p.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    BlockState s = level.getBlockState(p);
                    Block bl = s.getBlock();
                    if (!(bl instanceof CropBlock || bl instanceof SaplingBlock || bl instanceof StemBlock)) continue;
                    found = true;
                    if (level.random.nextInt(CROP_BOOST_ONE_IN) == 0) {
                        s.randomTick(level, p.immutable(), level.random);
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
                                2, 0.25, 0.25, 0.25, 0.0);
                    }
                }
            }
        }
        return found;
    }

    private static void puff(ServerLevel level, BlockPos pos, Family family) {
        ParticleOptions particle = switch (family) {
            case HEALING -> ParticleTypes.HAPPY_VILLAGER;
            case BOILING -> ParticleTypes.CLOUD;
            default -> ParticleTypes.ELECTRIC_SPARK;
        };
        level.sendParticles(particle, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
    }
}
