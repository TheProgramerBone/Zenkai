package com.hmc.zenkai.feature.teleport;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Locale;

/**
 * Los DOS "planetas" CURADOS del menú de Transmisión Instantánea: cada uno agrupa varios
 * {@link TeleportDestination} con posición FIJA y COMPARTIDA (ver TeleportAnchors) — Home/
 * Kami's Palace para el Overworld, Yemma/Kaiosama para el Otherworld. Ninguna otra dimensión
 * vive aquí como enum: NETHER/END (vainilla) y cualquier dimensión de un mod de terceros se
 * listan como filas GENÉRICAS dinámicas (ver GenericDimensionRow/DimensionIcons en
 * InstantTransmissionMenuScreen), resueltas por su ResourceLocation en vez de un valor de enum
 * fijo — pedido explícito del usuario ("que el uv del ícono se guíe por el nombre de la
 * dimensión independientemente del mod"). Antes NETHER/END/THIRD_PARTY vivían aquí también,
 * cada uno como caso especial; se retiraron al generalizar el sistema (ver
 * .claude/pendiente/instant-transmission-pendiente.md) — un solo mecanismo cubre ahora
 * cualquier dimensión que no sea una de estas dos curadas.
 * `iconColumn`/`iconRow` sustituyen a `ordinal()` para la celda del atlas
 * (icons_instant_transmision.png): con el enum reducido a 2 valores, el ordinal ya no coincide
 * con la columna histórica de cada uno (Overworld=0, Otherworld=3) — las columnas 1/2/4 de la
 * fila v=0 las usan ahora las filas genéricas (Nether/End/desconocida), ver
 * InstantTransmissionMenuScreen.iconColumnFor. Cada realm guarda su PROPIA fila además de su
 * columna (por defecto 0, la histórica) — mismo criterio que {@link GenericSubDestination}
 * (columna+fila libres) en vez de asumir siempre v=0: un ícono nuevo puede vivir en cualquier
 * celda del atlas sin más ceremonia que pasar ambos números al constructor, igual que
 * ClientZenkaiHooks resuelve cada badge del HUD con su propia columna+fila libres.
 */
public enum TeleportRealm {
    OVERWORLD(Level.OVERWORLD, 0, 0),
    OTHERWORLD(com.hmc.zenkai.registry.ModDimensions.OTHERWORLD_LEVEL, 3, 0);

    private final ResourceKey<Level> dimension;
    private final int iconColumn;
    private final int iconRow;

    TeleportRealm(ResourceKey<Level> dimension, int iconColumn, int iconRow) {
        this.dimension = dimension;
        this.iconColumn = iconColumn;
        this.iconRow = iconRow;
    }

    public ResourceKey<Level> dimension() { return dimension; }

    /** Columna de icons_instant_transmision.png — ver el javadoc de clase. */
    public int iconColumn() { return iconColumn; }

    /** Fila de icons_instant_transmision.png — ver el javadoc de clase. */
    public int iconRow() { return iconRow; }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public String nameKey() { return "screen.zenkai.instant_transmission.realm." + id(); }

    public java.util.List<TeleportDestination> destinations() {
        java.util.List<TeleportDestination> out = new java.util.ArrayList<>();
        for (TeleportDestination d : TeleportDestination.values()) {
            if (d.realm() == this) out.add(d);
        }
        return out;
    }
}
