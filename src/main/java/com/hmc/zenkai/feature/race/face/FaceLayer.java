package com.hmc.zenkai.feature.race.face;

import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Una capa de un rasgo facial: un PNG de 256×256 (solo la región de cara x16-31,y16-31 tiene
 * píxeles) + de dónde sale su color. El color MULTIPLICA la textura: una capa con color pinta
 * en blanco/gris lo que debe tomar ese color; una con {@code none} se ve tal cual.
 *
 * Esclerótica, iris izquierdo/derecho, pupila, brillo, párpado, pestañas, dientes... no son
 * tipos de capa: son capas con la fuente de color que les toque. Así un ojo simple tiene 2
 * capas y uno expresivo 4-5 sin que el código cambie (decisión C2 de
 * .claude/pendiente/sistema-visual-personajes-diseno.md).
 *
 * @param fixedRgb solo para {@link Color#FIXED}; ignorado en el resto.
 */
public record FaceLayer(ResourceLocation texture, Color color, int fixedRgb) {

    /** Fuente del tinte de una capa. La clave en minúsculas es lo que se escribe en el JSON. */
    public enum Color {
        /** Sin tinte: contornos, esclerótica, brillo, dientes. */
        NONE,
        /** Color de piel del jugador. */
        SKIN,
        /** Color de ojos (el izquierdo si hay heterocromía). Sobrescrito por el eye_rgb de la forma. */
        EYE,
        EYE_LEFT,
        /** El derecho cae al izquierdo si el jugador no eligió heterocromía. */
        EYE_RIGHT,
        /** Color de pelo; si la forma declara hair_rgb (SSJ), ese — las cejas siguen al pelo. */
        HAIR,
        /** Color fijo del JSON ("#RRGGBB"). */
        FIXED;

        /** "none" | "skin" | "eye" | "eye_left" | "eye_right" | "hair" | "#RRGGBB" | "0xRRGGBB". */
        public static FaceLayer parse(ResourceLocation texture, @Nullable String raw, Color fallback) {
            if (raw == null || raw.isBlank()) return new FaceLayer(texture, fallback, 0);
            String s = raw.trim();
            if (s.startsWith("#") || s.startsWith("0x") || s.startsWith("0X")) {
                int rgb = Integer.decode(s.replace("#", "0x")) & 0xFFFFFF;
                return new FaceLayer(texture, FIXED, rgb);
            }
            Color c = Color.valueOf(s.toUpperCase(Locale.ROOT));
            if (c == FIXED) throw new IllegalArgumentException("color fijo: usar \"#RRGGBB\", no \"fixed\"");
            return new FaceLayer(texture, c, 0);
        }
    }

    /**
     * RGB (sin alfa) que multiplica esta capa para este jugador. Código común (no de cliente) a
     * propósito, para poder probarlo en gametest. El eye_rgb / hair_rgb de la forma activa
     * mandan sobre los colores del jugador.
     */
    public int resolveRgb(PlayerVisualAttachment vis, @Nullable FormDef form) {
        return resolveRgb(vis, form, -1, false);
    }

    /**
     * @param eyeOverride  &gt;= 0 = color que pisa los dos ojos por encima de la forma y del jugador
     *                     (Ultra Instinct activado como técnica: ojos blancos); -1 = ninguno.
     * @param eyesCategory true si la capa es del rasgo OJOS: ahí la capa que sigue al pelo es la de
     *                     la capa brow, que una forma puede teñir aparte (brow_rgb) sin tocar el pelo.
     */
    public int resolveRgb(PlayerVisualAttachment vis, @Nullable FormDef form, int eyeOverride,
                          boolean eyesCategory) {
        boolean formEyes = form != null && form.tintsEyes();
        boolean forced = eyeOverride >= 0;
        int rgb = switch (color) {
            case NONE      -> 0xFFFFFF;
            case SKIN      -> vis.getSkinColorRgb();
            case EYE, EYE_LEFT -> forced ? eyeOverride : formEyes ? form.eyeRgb() : vis.getEyeColorRgb();
            case EYE_RIGHT -> forced ? eyeOverride : formEyes ? form.eyeRgb() : vis.resolvedEyeColorRightRgb();
            case HAIR      -> (eyesCategory && form != null && form.tintsBrows()) ? form.browRgb()
                    : (form != null && form.tintsHair()) ? form.hairRgb() : vis.getHairColorRgb();
            case FIXED     -> fixedRgb;
        };
        return rgb & 0xFFFFFF;
    }

    public static final StreamCodec<FriendlyByteBuf, FaceLayer> STREAM_CODEC = StreamCodec.of(
            (buf, l) -> {
                buf.writeResourceLocation(l.texture);
                buf.writeEnum(l.color);
                buf.writeInt(l.fixedRgb);
            },
            buf -> new FaceLayer(buf.readResourceLocation(), buf.readEnum(Color.class), buf.readInt()));
}
