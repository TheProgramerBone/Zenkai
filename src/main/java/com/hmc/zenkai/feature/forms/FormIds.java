package com.hmc.zenkai.feature.forms;

import com.hmc.zenkai.Zenkai;
import net.minecraft.resources.ResourceLocation;

/**
 * Ids de forma que el CÓDIGO JAVA necesita nombrar a pelo.
 * OJO: esto ya NO es la lista de formas del mod. Las formas viven en datapack
 * (data/<ns>/zenkai_forms/) y se consultan por FormDef / FormRegistry; añadir una forma
 * NO requiere tocar este archivo. Aquí solo hay dos casos legítimos:
 *  1. BASE: el estado neutro. No está en datapack porque es la AUSENCIA de forma.
 *  2. Formas con assets cableados en Java (pelo, skin, aura). Una textura no se puede
 *     datapackear, así que ese código necesita comparar contra un id concreto.
 * Si añades una constante aquí sin un uso de esos dos tipos, probablemente lo que quieres
 * es un campo nuevo en el JSON de la forma.
 */
public final class FormIds {

    /** Estado neutro: sin transformación. El único id que no vive en datapack. */
    public static final ResourceLocation BASE = id("base");

    // ── Formas con assets cableados en Java ─────────────────────────────────
    // (HairResolver / FormSkinResolver comparan contra estos para elegir textura)

    public static final ResourceLocation SSJ1 = id("ssj1");
    public static final ResourceLocation SSJ2 = id("ssj2");
    public static final ResourceLocation SSJ3 = id("ssj3");
    public static final ResourceLocation SSJ4 = id("ssj4");

    public static final ResourceLocation SECOND_FORM = id("second_form");
    public static final ResourceLocation THIRD_FORM  = id("third_form");
    public static final ResourceLocation FINAL_FORM  = id("final_form");
    public static final ResourceLocation GOLDEN_FORM = id("golden_form");
    public static final ResourceLocation BLACK_FORM  = id("black_form");
    public static final ResourceLocation FIFTH_FORM  = id("fifth_form");
    public static final ResourceLocation GOLDEN_FIFTH_FORM = id("golden_fifth_form");
    public static final ResourceLocation BLACK_FIFTH_FORM  = id("black_fifth_form");
    /** Potential Unlock. Hay lógica Java que compara contra este id (el multiplicador
    +     *  acoplado y el castigo de TP), así que es de los que sí van cableados. */
    public static final ResourceLocation POTENTIAL_UNLOCK = id("potential_unlock");

    /** Oozaru / Super Oozaru: OozaruSystem las compara contra estos ids para forzar la
     *  transformación (luna llena) y para revertirla; TransformHoldPacket compara contra
     *  SUPER_OOZARU para el "ritual" que otorga SSJ4. Kind divine + parent null en su JSON:
     *  viven FUERA de la cadena normal de super_forms (ver FormDef.wheelSelectable). */
    public static final ResourceLocation OOZARU       = id("oozaru");
    public static final ResourceLocation SUPER_OOZARU = id("super_oozaru");

    /** Hermanas equivalentes de la rama divina Saiyan (mismo parent ssj_god, mismos números —
     *  ver feature.skills.DivineForms). PlayerFormAttachment compara contra estos ids para
     *  decidir si comparten maestría (ServerConfig.ssjBlueRoseShareMastery()). */
    public static final ResourceLocation SSJ_BLUE = id("ssj_blue");
    public static final ResourceLocation SSJ_ROSE = id("ssj_rose");

    /** Rutas de endgame universales (0.3.0, Destroyer/Angels/Mystic Path — ver
     *  {@code .claude/docs/jugador-razas-formas.md}). Igual que OOZARU/
     *  SUPER_OOZARU: kind divine + parent null, fuera de la cadena normal de super_forms, así
     *  que {@code EndgamePaths} necesita conocer las 3 raíces a pelo para poder ofrecerlas en
     *  la rueda (no son alcanzables por FormRegistry.childrenOf desde ninguna raza). */
    public static final ResourceLocation ULTRA_EGO = id("ultra_ego");
    public static final ResourceLocation ULTRA_INSTINCT_SIGN = id("ultra_instinct_sign");
    public static final ResourceLocation ULTRA_INSTINCT = id("ultra_instinct");
    public static final ResourceLocation ULTIMATE_MYSTIC = id("ultimate_mystic");
    public static final ResourceLocation BEAST = id("beast");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, path);
    }

    private FormIds() {}
}