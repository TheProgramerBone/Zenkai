package com.hmc.zenkai.worldgen;

import com.hmc.zenkai.worldgen.StaticStructurePlacer.Segment;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Define cómo se reensamblan los palacios (offsets de cada segmento) y dónde se
 * colocan. AJUSTA los offsets de Kami a mano: cada Segment.of(nombre, x, y, z)
 * desplaza esa pieza respecto a la esquina origen (0,0,0) del palacio.
 *
 * El tamaño de cada pieza va en comentario para ayudarte a calcular.
 * Los NBT deben estar en data/zenkai/structure/&lt;nombre&gt;.nbt
 */
public final class ModStructureSegments {
    private ModStructureSegments() {}

    // ── KAMI (overworld, estructura única) ────────────────────────────────────
    /** Posición fija del palacio de Kami en el overworld. AJUSTA a gusto. */
    public static final BlockPos KAMI_BASE = new BlockPos(0, 64, 0);

    /**
     * 24 segmentos. RELLENA los offsets (ahora todos en 0,0,0 = se solapan).
     * Pista: las piezas 16–24 son 46 de alto (las demás 48/42) → probablemente
     * forman una capa superior (offset Y distinto). kami_5 (1×48×1) es un pilar.
     */
    public static final List<Segment> KAMI = List.of(
            Segment.of("kami_1",  0, 0, 0),   // 15×48×15
            Segment.of("kami_2",  0, 47, 0),   // 15×48×15
            Segment.of("kami_3",  0, 47*2, 0),   // 15×42×15
            Segment.of("kami_4",  24, 47*3, -18),   // 48×48×48
            Segment.of("kami_5",  0, 185, 0),   // 1×48×1  (pilar)
            Segment.of("kami_6",  0, 233, 0),   // 11×27×11
            Segment.of("kami_7",  0, 260, 0),   // 11×48×11
            Segment.of("kami_8",  0, 47*7, 0),   // 48×48×11
            Segment.of("kami_9",  0, 47*8, 0),   // 48×48×48
            Segment.of("kami_10", 0, 47*9, 0),   // 11×48×48
            Segment.of("kami_11", 0, 47*10, 0),   // 48×48×48
            Segment.of("kami_12", 0, 47*11, 0),   // 48×48×11
            Segment.of("kami_13", 0, 47*12, 0),   // 48×48×48
            Segment.of("kami_14", 0, 47*13, 0),   // 11×48×48
            Segment.of("kami_15", 0, 47*14, 0),   // 48×48×48
            Segment.of("kami_16", 0, 47*15, 0),   // 11×46×11
            Segment.of("kami_17", 0, 47*16, 0),   // 48×46×11
            Segment.of("kami_18", 0, 47*17, 0),   // 48×46×48
            Segment.of("kami_19", 0, 47*18, 0),   // 11×46×48
            Segment.of("kami_20", 0, 47*19, 0),   // 48×46×48
            Segment.of("kami_21", 0, 47*20, 0),   // 48×46×11
            Segment.of("kami_22", 0, 47*21, 0),   // 48×46×48
            Segment.of("kami_23", 0, 47*22, 0),   // 11×46×48
            Segment.of("kami_24", 0, 47*23, 0)    // 48×46×48
    );

    // ── OTHERWORLD (dimensión del otro mundo, estructura única) ────────────────
    /**
     * Esquina origen del palacio en el otro mundo. El jugador aparece en
     * OtherworldManager.OTHERWORLD_SPAWN, así que ALINEA ambas: coloca la base
     * de modo que OTHERWORLD_SPAWN caiga en la entrada del palacio.
     */
    public static final BlockPos OTHERWORLD_BASE = new BlockPos(0, 64, 0);

    /** Rejilla 3×2 deducida (1–6). Ajusta _7 (23×39×13) a su sitio. */
    public static final List<Segment> OTHERWORLD = List.of(
            Segment.of("otherworld_palace_1",  0, 0,  0),  // 48×40×48
            Segment.of("otherworld_palace_2", 48, 0,  0),  // 48×40×48
            Segment.of("otherworld_palace_3", 96, 0,  0),  // 20×40×48
            Segment.of("otherworld_palace_4",  0, 0, 48),  // 48×40×48
            Segment.of("otherworld_palace_5", 48, 0, 48),  // 48×40×48
            Segment.of("otherworld_palace_6", 96, 0, 48),  // 20×40×48
            Segment.of("otherworld_palace_7",  0, 0,  0)   // 23×39×13  ← AJUSTAR
    );
}