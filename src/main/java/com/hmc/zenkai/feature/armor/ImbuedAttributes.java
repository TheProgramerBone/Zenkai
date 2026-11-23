package com.hmc.zenkai.feature.armor;

import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Atributos imbuidos en UNA pieza de armadura (data component {@code zenkai:imbued}, Fase 5a de la 0.7.0).
 * Se suman a los propios de la pieza (vanilla o de otro mod), que no se tocan (P13): el componente es aparte.
 * Cinco campos y no un mapa, por la misma razón que ScouterUpgrades: los atributos imbuibles son fijos y un mapa solo
 * admitiría claves basura. MIND no se imbuye (no hay sangre de MND: una sangre por atributo, P2) y por eso no está.
 * Inmutable a propósito: un componente mutable se comparte entre stacks al copiarlos y contamina.
 * Valores siempre ≥ 0; no hay tope por pieza (solo el coste logarítmico de ImbueCost y el techo total del jugador).
 */
public record ImbuedAttributes(int str, int con, int dex, int wil, int spi) {

    /** Pieza sin imbuir. Ausencia del componente == esto. */
    public static final ImbuedAttributes NONE = new ImbuedAttributes(0, 0, 0, 0, 0);

    public static final Codec<ImbuedAttributes> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("str", 0).forGetter(ImbuedAttributes::str),
            Codec.INT.optionalFieldOf("con", 0).forGetter(ImbuedAttributes::con),
            Codec.INT.optionalFieldOf("dex", 0).forGetter(ImbuedAttributes::dex),
            Codec.INT.optionalFieldOf("wil", 0).forGetter(ImbuedAttributes::wil),
            Codec.INT.optionalFieldOf("spi", 0).forGetter(ImbuedAttributes::spi)
    ).apply(i, ImbuedAttributes::new));

    public static final StreamCodec<ByteBuf, ImbuedAttributes> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ImbuedAttributes::str,
            ByteBufCodecs.VAR_INT, ImbuedAttributes::con,
            ByteBufCodecs.VAR_INT, ImbuedAttributes::dex,
            ByteBufCodecs.VAR_INT, ImbuedAttributes::wil,
            ByteBufCodecs.VAR_INT, ImbuedAttributes::spi,
            ImbuedAttributes::new);

    /** Orden en que se listan en el tooltip (el mismo que la pantalla de Stats). */
    public static final ZenkaiAttributes[] IMBUABLE = {
            ZenkaiAttributes.STRENGTH, ZenkaiAttributes.CONSTITUTION, ZenkaiAttributes.DEXTERITY,
            ZenkaiAttributes.WILLPOWER, ZenkaiAttributes.SPIRIT };

    public static boolean imbuable(ZenkaiAttributes a) { return a != ZenkaiAttributes.MIND; }

    /** Puntos imbuidos en ese atributo; MIND siempre 0. Un valor negativo (NBT editado a mano) cuenta como 0. */
    public int get(ZenkaiAttributes a) {
        int v = switch (a) {
            case STRENGTH     -> str;
            case CONSTITUTION -> con;
            case DEXTERITY    -> dex;
            case WILLPOWER    -> wil;
            case SPIRIT       -> spi;
            case MIND         -> 0;
        };
        return Math.max(0, v);
    }

    /** Copia con ese atributo a {@code value} (≥ 0). MIND se ignora. */
    public ImbuedAttributes with(ZenkaiAttributes a, int value) {
        int v = Math.max(0, value);
        return switch (a) {
            case STRENGTH     -> new ImbuedAttributes(v, con, dex, wil, spi);
            case CONSTITUTION -> new ImbuedAttributes(str, v, dex, wil, spi);
            case DEXTERITY    -> new ImbuedAttributes(str, con, v, wil, spi);
            case WILLPOWER    -> new ImbuedAttributes(str, con, dex, v, spi);
            case SPIRIT       -> new ImbuedAttributes(str, con, dex, wil, v);
            case MIND         -> this;
        };
    }

    /** Copia con {@code delta} puntos más en ese atributo, sin desbordar el int. */
    public ImbuedAttributes plus(ZenkaiAttributes a, int delta) {
        long next = (long) get(a) + delta;
        return with(a, (int) Math.max(0, Math.min(Integer.MAX_VALUE, next)));
    }

    public boolean isEmpty() {
        for (ZenkaiAttributes a : IMBUABLE) if (get(a) > 0) return false;
        return true;
    }
}
