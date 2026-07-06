package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.worldgen.StaticStructurePlacer.Segment;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.List;

public final class ModStructureSegments {
    private ModStructureSegments() {}

    // ── KAMI (overworld, estructura única) ────────────────────────────────────
    public static final BlockPos KAMI_BASE = new BlockPos(0, 64, 0);

    // Anclaje de kami_1: true = nivel del mar; false = superficie del bioma
    public static final boolean KAMI_ANCHOR_SEA_LEVEL = true;
    // Radio inicial y tope del auto-expandible (bloques)
    public static final int KAMI_SEARCH_RADIUS = 500;
    public static final int KAMI_MAX_SEARCH_RADIUS = 8000;
    // ── Búsqueda de terreno PLANO del tamaño de kami_1 (para que la base no quede en pendiente) ──
    // Tamaño (X=Z) de la base kami_1 que debe caber en terreno plano.
    public static final int KAMI_FOOTPRINT = 15;
    // Radio (bloques) alrededor del punto del bioma donde se busca el cuadro plano.
    public static final int KAMI_FLAT_SEARCH_RADIUS = 64;
    // Paso del muestreo (más pequeño = más preciso pero genera más chunks al arrancar).
    public static final int KAMI_FLAT_STEP = 8;
    // Desnivel máximo tolerado en el cuadro (bloques). Si no encuentra, relaja hasta 8 solo.
    public static final int KAMI_FLAT_MAX_DIFF = 3;
    // Ajuste fino de altura sobre el ancla (0 = justo en el ancla; +/- para subir/bajar)
    public static final int KAMI_Y_OFFSET = 0;
    // Bioma donde debe aparecer Kami (cámbialo al que quieras)
    public static final ResourceKey<Biome> KAMI_BIOME = Biomes.PLAINS;
    // Caja de protección RELATIVA a la base de Kami (offset desde la base + tamaño)
    public static final int KAMI_NO_SPAWN_OFF_X = -64, KAMI_NO_SPAWN_OFF_Y = 0, KAMI_NO_SPAWN_OFF_Z = -64;
    public static final int KAMI_NO_SPAWN_SX = 160, KAMI_NO_SPAWN_SY = 360, KAMI_NO_SPAWN_SZ = 160;
    public static final BlockPos KAMI_NO_SPAWN_MIN = new BlockPos(KAMI_BASE.getX() - 64, KAMI_BASE.getY(), KAMI_BASE.getZ() - 64);

    public static final List<Segment> KAMI = List.of(
            Segment.of("kami_1",  0, 0, 0),   // 15×48×15
            //Segment.of("kami_2",  0, 47, 0),   // 15×48×15
            //Segment.of("kami_3",  0, 47*2, 0),   // 15×42×15
            Segment.of("kami_4",  -17, 136-94, -18),   // 48×48×48
            Segment.of("kami_5",  7, 185-104, 7),   // 1×48×1
            Segment.of("kami_6",  7, 233-104, 7),   // 11×27×11
            Segment.of("kami_7",  2, 260-104, 2),   // 11×48×11
            Segment.of("kami_8",  13, 260-104, 2),   // 48×48×11
            Segment.of("kami_9",  13, 260-104, 13),   // 48×48×48
            Segment.of("kami_10", 2, 260-104, 13),   // 11×48×48
            Segment.of("kami_11", -46, 260-104, 13),   // 48×48×48
            Segment.of("kami_12", -46, 260-104, 2),   // 48×48×11
            Segment.of("kami_13", -46, 260-104, -46),   // 48×48×48
            Segment.of("kami_14", 2, 260-104, -46),   // 11×48×48
            Segment.of("kami_15", 13, 260-104, -46),   // 48×48×48
            Segment.of("kami_16",  2, 308-104, 2),   // 11×48×11
            Segment.of("kami_17",  -46, 308-104, 2),   // 48×48×11
            Segment.of("kami_18",  -46, 308-104, -46),   // 48×48×48
            Segment.of("kami_19", 2, 308-104, -46),   // 11×48×48
            Segment.of("kami_20", 13, 308-104, -46),   // 48×48×48
            Segment.of("kami_21", 13, 308-104, 2),   // 48×48×11
            Segment.of("kami_22", 13, 308-104, 13),   // 48×48×48
            Segment.of("kami_23", 2, 308-104, 13),   // 11×48×48
            Segment.of("kami_24", -46, 308-104, 13)   // 48×48×48
    );

    // ── OTHERWORLD (dimensión del otro mundo, estructura única) ────────────────
    public static final BlockPos OTHERWORLD_BASE = new BlockPos(0, 145, 0);
    public static final BlockPos OTHERWORLD_NO_SPAWN_MIN = new BlockPos(OTHERWORLD_BASE.getX() - 8, OTHERWORLD_BASE.getY(), OTHERWORLD_BASE.getZ() - 8);
    public static final int OTHERWORLD_NO_SPAWN_SX = 140, OTHERWORLD_NO_SPAWN_SY = 60, OTHERWORLD_NO_SPAWN_SZ = 120;

    public static final List<Segment> OTHERWORLD = List.of(
            Segment.of("otherworld_palace_1",0,0,0),   // 48×40×48
            Segment.of("otherworld_palace_2",48,0,0),  // 48×40×48
            Segment.of("otherworld_palace_3",96,0,0),  // 20×40×48
            Segment.of("otherworld_palace_4",0,0,48),  // 48×40×48
            Segment.of("otherworld_palace_5",48,0,48), // 48×40×48
            Segment.of("otherworld_palace_6",96,0,48), // 20×40×48
            Segment.of("otherworld_palace_7",46,1,96)  // 23×39×13
    );

    // ── HABITACIÓN DEL TIEMPO (HTC, dimensión propia, estructura única) ────────
    // La base del suelo de htc_block queda en ~y63; coloca la estructura sobre él.
    public static final BlockPos HTC_BASE = new BlockPos(0, 64, 0);

    // Dónde aparece el jugador al entrar (ajústalo a donde esté el portal dentro de tu htc_x).
    public static final BlockPos HTC_ENTRANCE = new BlockPos(39, 67, 32);
    public static final BlockPos HTC_NO_SPAWN_MIN = new BlockPos(HTC_BASE.getX() - 32, HTC_BASE.getY() - 8, HTC_BASE.getZ() - 32);
    public static final int HTC_NO_SPAWN_SX = 96, HTC_NO_SPAWN_SY = 120, HTC_NO_SPAWN_SZ = 96;

    // Rellena con tus segmentos htc_x (mismo patrón que KAMI/OTHERWORLD: nombre + offset X,Y,Z).
    public static final List<Segment> HTC = List.of(
            Segment.of("htc_1", 0, 0, 0),
            Segment.of("htc_2",48,0,0)
    );
}