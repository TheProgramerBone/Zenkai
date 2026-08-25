package com.hmc.zenkai.feature.sense;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Estado de mejoras de UN scouter. Vive en el ItemStack como data component, así que cada
 * aparato se mejora por separado y sobrevive a morir/guardar/comerciar.
 * Campos explícitos y no un Map<String,Integer>: las mejoras están hardcodeadas (ScouterUpgrade),
 * así que un mapa solo añadiría claves basura cuando alguien renombre algo y una capa de
 * indirección que no compra nada.
 * Cada campo es NIVEL. Las binarias valen 0 o 1: un solo tipo de dato en el
 * sistema, sin ramas de "esta es booleana".
 */
public record ScouterUpgrades(int range, int plCap, int analyzer, int areaScanner, int dragonRadar) {

    /** Scouter de fábrica. Ausencia del componente == esto. */
    public static final ScouterUpgrades NONE = new ScouterUpgrades(0, 0, 0, 0, 0);

    public static final Codec<ScouterUpgrades> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("range",        0).forGetter(ScouterUpgrades::range),
            Codec.INT.optionalFieldOf("pl_cap",       0).forGetter(ScouterUpgrades::plCap),
            Codec.INT.optionalFieldOf("analyzer",     0).forGetter(ScouterUpgrades::analyzer),
            Codec.INT.optionalFieldOf("area_scanner", 0).forGetter(ScouterUpgrades::areaScanner),
            Codec.INT.optionalFieldOf("dragon_radar", 0).forGetter(ScouterUpgrades::dragonRadar)
    ).apply(i, ScouterUpgrades::new));

    public static final StreamCodec<ByteBuf, ScouterUpgrades> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ScouterUpgrades::range,
            ByteBufCodecs.VAR_INT, ScouterUpgrades::plCap,
            ByteBufCodecs.VAR_INT, ScouterUpgrades::analyzer,
            ByteBufCodecs.VAR_INT, ScouterUpgrades::areaScanner,
            ByteBufCodecs.VAR_INT, ScouterUpgrades::dragonRadar,
            ScouterUpgrades::new);

    /** Nivel de una mejora concreta, ya clampado a su máximo. */
    public int level(ScouterUpgrade u) {
        int raw = switch (u) {
            case RANGE        -> range;
            case PL_CAP       -> plCap;
            case ANALYZER     -> analyzer;
            case AREA_SCANNER -> areaScanner;
            case DRAGON_RADAR -> dragonRadar;
        };
        return Math.max(0, Math.min(u.maxLevel(), raw));
    }

    /** ¿Está desbloqueada? (nivel >= 1). */
    public boolean has(ScouterUpgrade u) { return level(u) >= 1; }

    /** Copia con una mejora puesta a ese nivel. El record es inmutable a propósito:
     *  un componente mutable se comparte entre stacks al copiar y contamina. */
    public ScouterUpgrades with(ScouterUpgrade u, int lvl) {
        int v = Math.max(0, Math.min(u.maxLevel(), lvl));
        return switch (u) {
            case RANGE        -> new ScouterUpgrades(v, plCap, analyzer, areaScanner, dragonRadar);
            case PL_CAP       -> new ScouterUpgrades(range, v, analyzer, areaScanner, dragonRadar);
            case ANALYZER     -> new ScouterUpgrades(range, plCap, v, areaScanner, dragonRadar);
            case AREA_SCANNER -> new ScouterUpgrades(range, plCap, analyzer, v, dragonRadar);
            case DRAGON_RADAR -> new ScouterUpgrades(range, plCap, analyzer, areaScanner, v);
        };
    }

    /** Siguiente nivel comprable, o -1 si ya está al máximo. */
    public int nextLevel(ScouterUpgrade u) {
        int cur = level(u);
        return cur >= u.maxLevel() ? -1 : cur + 1;
    }
}