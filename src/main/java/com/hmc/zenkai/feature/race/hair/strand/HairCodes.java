package com.hmc.zenkai.feature.race.hair.strand;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Punto de entrada del importador: reconoce el formato de un código pegado y lo lee, ya acotado
 * por {@link StrandLimits}.
 */
public final class HairCodes {
    private HairCodes() {}

    public enum Format {
        DMZ("DragonMineZ"),
        DBC("Dragon Block C");

        public final String display;

        Format(String display) {
            this.display = display;
        }
    }

    /** Resultado de leer un código: el juego por formas y los avisos de lo que se perdió por el camino. */
    public record Decoded(Format format, StrandSet.Variants variants, List<Component> warnings) {}

    public static Format detect(String code) throws HairCodeException {
        String c = DmzHairCodec.stripWhitespace(code);
        if (c.isEmpty()) throw new HairCodeException("empty");
        if (DmzHairCodec.looksLike(c)) return Format.DMZ;
        if (DbcHairCodec.looksLike(c)) return Format.DBC;
        throw new HairCodeException("unknown_format", DbcHairCodec.G2_LENGTH);
    }

    public static Decoded decode(String code) throws HairCodeException {
        List<Component> warnings = new ArrayList<>();
        Format format = detect(code);
        StrandSet.Variants raw = switch (format) {
            case DMZ -> DmzHairCodec.decode(code, warnings::add);
            case DBC -> StrandSet.Variants.single(DbcHairCodec.decode(code, warnings::add));
        };
        StrandSet base = StrandLimits.clamp(raw.base(), warnings::add);
        if (base.isEmpty()) throw new HairCodeException("no_strands");
        StrandSet.Variants clamped = new StrandSet.Variants(base, clampOrNull(raw.ssj(), warnings),
                clampOrNull(raw.ssj2(), warnings), clampOrNull(raw.ssj3(), warnings), clampOrNull(raw.ssj4(), warnings));
        return new Decoded(format, clamped, Collections.unmodifiableList(dedupe(warnings)));
    }

    private static StrandSet clampOrNull(StrandSet s, List<Component> warnings) {
        if (s == null) return null;
        StrandSet c = StrandLimits.clamp(s, warnings::add);
        return c.isEmpty() ? null : c;
    }

    private static List<Component> dedupe(List<Component> in) {
        List<Component> out = new ArrayList<>();
        for (Component s : in) if (!out.contains(s)) out.add(s);
        return out;
    }
}
