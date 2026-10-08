package com.hmc.zenkai.feature.technique;

import com.hmc.zenkai.feature.alignment.AlignmentTier;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.world.entity.player.Player;

/**
 * Genki Dama «malvada»: cuando quien la lanza tiene alineamiento EVIL, la técnica cambia de
 * esquema de color al de las energías oscuras de Dragon Ball (Goku Black): exterior rosa-violeta
 * intenso e interior casi negro. Los rayos carmesí los pone el cliente al ver un segundo color
 * oscuro (ver KiVfxColors.isDarkInterior).
 *
 * Sirve en servidor Y cliente (el alineamiento viaja en el sync de stats): el icono y la barra de
 * carga de quien la lleva usan el mismo color que ve el resto. Es SOLO presentación: el daño, el coste y el bonus no cambian. El color se decide en el SERVIDOR
 * al empezar la carga (KiChargeServer) y al disparar (KiFirePacket), y viaja con los packets de
 * siempre, así que cualquiera que vea la técnica la ve igual. Si el alineamiento cambia entre la
 * carga y el disparo, la bola y el proyectil pueden salir de colores distintos (caso raro).
 */
public final class EvilSpiritBomb {
    private EvilSpiritBomb() {}

    /** Exterior: magenta/rosa violeta intenso. */
    public static final int OUTER_RGB = 0xD62EA8;
    /** Interior: morado casi negro (no negro puro: así sigue leyéndose como cuerpo, no como hueco). */
    public static final int INNER_RGB = 0x2A0A3D;

    public static boolean applies(Player sp, KiTechnique tech) {
        return tech.type() == KiTechniqueType.SPIRIT_BOMB
                && AlignmentTier.of(PlayerStatsAttachment.get(sp).getAlignment()) == AlignmentTier.EVIL;
    }

    /** Color principal de la técnica para ESTE jugador. */
    public static int rgb(Player sp, KiTechnique tech) {
        return applies(sp, tech) ? OUTER_RGB : tech.rgb();
    }

    /** Segundo color (interior) de la técnica para ESTE jugador. */
    public static int rgb2(Player sp, KiTechnique tech) {
        return applies(sp, tech) ? INNER_RGB : tech.rgb2();
    }
}
