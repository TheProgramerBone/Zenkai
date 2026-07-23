package com.hmc.zenkai.core.network.feature.forms;

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

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, path);
    }

    private FormIds() {}
}