package com.hmc.zenkai.registry;

import com.hmc.zenkai.worldgen.StaticStructurePlacer.Segment;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.List;

public final class ModStructureSegments {
    private ModStructureSegments() {}


    public static final int KAMI_Y_OFFSET = 0;
    // Bioma donde debe aparecer Kami (cámbialo al que quieras)

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
    // Palacio + serpentina: 3×3 piezas de 48×40×48 (palacio) y luego parejas de 2
    // avanzando en +Z hasta z=912. Extensión total: X 0..143, Y 0..39, Z 0..926.
    public static final BlockPos OTHERWORLD_BASE = new BlockPos(0, 195, 0);

    // Planeta de Kaiosama: cubo 2×2×2 de 48³ (esfera de r=40 centrada en su local 41,41,41).
    // Estos 3 valores son el único mando: mueven el planeta entero respecto a OTHERWORLD_BASE.
    // Ahora mismo: centrado en X sobre la punta de la serpentina (x 31..49), ecuador a la
    // altura de la calzada (y 25..27) y pegado justo detrás del último bloque de camino (z 914).
    private static final int KAIO_DX = -1;
    private static final int KAIO_DY = 0;
    private static final int KAIO_DZ = 914;

    public static final BlockPos OTHERWORLD_NO_SPAWN_MIN = new BlockPos(
            OTHERWORLD_BASE.getX() - 16,
            OTHERWORLD_BASE.getY() - 16,
            OTHERWORLD_BASE.getZ() - 16);
    // SZ se corta justo donde empieza la zona de Kaiosama (ver KAIO_NO_SPAWN_MIN más abajo):
    // antes esta caja llegaba hasta z=1040 y se comía también el planeta de Kaiosama, así que
    // ese tramo (incluido el planeta) quedaba protegido a nombre de Yemma.
    public static final int OTHERWORLD_NO_SPAWN_SX = 176, OTHERWORLD_NO_SPAWN_SY = 120, OTHERWORLD_NO_SPAWN_SZ = 914;

    // Zona de no-spawn/protección propia del planeta de Kaiosama, separada de la de Yemma
    // (arriba) para que el protector mostrado en ese tramo sea Kaiosama y no Yemma.
    // Envuelve el cubo 2×2×2 de piezas de 48³ (ver comentario de KAIO_DX/DY/DZ) con el mismo
    // margen de 16 que usa la zona de Yemma.
    public static final BlockPos KAIO_NO_SPAWN_MIN = new BlockPos(
            OTHERWORLD_BASE.getX() + KAIO_DX - 16,
            OTHERWORLD_BASE.getY() + KAIO_DY - 16,
            OTHERWORLD_BASE.getZ() + KAIO_DZ - 16);
    public static final int KAIO_NO_SPAWN_SX = 128, KAIO_NO_SPAWN_SY = 128, KAIO_NO_SPAWN_SZ = 128;

    public static final List<Segment> OTHERWORLD = List.of(
            Segment.of("otherworld_palace_1",   0, 0,   0),
            Segment.of("otherworld_palace_2",  48, 0,   0),
            Segment.of("otherworld_palace_3",  96, 0,   0),
            Segment.of("otherworld_palace_4",  96, 0,  48),
            Segment.of("otherworld_palace_5",  48, 0,  48),
            Segment.of("otherworld_palace_6",   0, 0,  48),
            Segment.of("otherworld_palace_7",   0, 0,  96),
            Segment.of("otherworld_palace_8",  48, 0,  96),
            Segment.of("otherworld_palace_9",  96, 0,  96),
            Segment.of("otherworld_palace_10", 48, 0, 144),
            Segment.of("otherworld_palace_11",  0, 0, 144),
            Segment.of("otherworld_palace_12", 48, 0, 192),
            Segment.of("otherworld_palace_13",  0, 0, 192),
            Segment.of("otherworld_palace_14", 48, 0, 240),
            Segment.of("otherworld_palace_15",  0, 0, 240),
            Segment.of("otherworld_palace_16", 48, 0, 288),
            Segment.of("otherworld_palace_17",  0, 0, 288),
            Segment.of("otherworld_palace_18", 48, 0, 336),
            Segment.of("otherworld_palace_19",  0, 0, 336),
            Segment.of("otherworld_palace_20", 48, 0, 384),
            Segment.of("otherworld_palace_21",  0, 0, 384),
            Segment.of("otherworld_palace_22", 48, 0, 432),
            Segment.of("otherworld_palace_23",  0, 0, 432),
            Segment.of("otherworld_palace_24", 48, 0, 480),
            Segment.of("otherworld_palace_25",  0, 0, 480),
            Segment.of("otherworld_palace_26", 48, 0, 528),
            Segment.of("otherworld_palace_27",  0, 0, 528),
            Segment.of("otherworld_palace_28", 48, 0, 576),
            Segment.of("otherworld_palace_29",  0, 0, 576),
            Segment.of("otherworld_palace_30", 48, 0, 624),
            Segment.of("otherworld_palace_31",  0, 0, 624),
            Segment.of("otherworld_palace_32", 48, 0, 672),
            Segment.of("otherworld_palace_33",  0, 0, 672),
            Segment.of("otherworld_palace_34", 48, 0, 720),
            Segment.of("otherworld_palace_35",  0, 0, 720),
            Segment.of("otherworld_palace_36", 48, 0, 768),
            Segment.of("otherworld_palace_37",  0, 0, 768),
            Segment.of("otherworld_palace_38", 48, 0, 816),
            Segment.of("otherworld_palace_39",  0, 0, 816),
            Segment.of("otherworld_palace_40", 48, 0, 864),
            Segment.of("otherworld_palace_41",  0, 0, 864),
            Segment.of("otherworld_palace_42", 48, 0, 912),
            Segment.of("otherworld_palace_43",  0, 0, 912),

            // Planeta de Kaiosama, al final de la serpentina.
            Segment.of("kaiosama_1", KAIO_DX,      KAIO_DY,      KAIO_DZ),
            Segment.of("kaiosama_2", KAIO_DX + 48, KAIO_DY,      KAIO_DZ),
            Segment.of("kaiosama_3", KAIO_DX,      KAIO_DY,      KAIO_DZ + 48),
            Segment.of("kaiosama_4", KAIO_DX + 48, KAIO_DY,      KAIO_DZ + 48),
            Segment.of("kaiosama_5", KAIO_DX,      KAIO_DY + 48, KAIO_DZ),
            Segment.of("kaiosama_6", KAIO_DX + 48, KAIO_DY + 48, KAIO_DZ),
            Segment.of("kaiosama_7", KAIO_DX,      KAIO_DY + 48, KAIO_DZ + 48),
            Segment.of("kaiosama_8", KAIO_DX + 48, KAIO_DY + 48, KAIO_DZ + 48)
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