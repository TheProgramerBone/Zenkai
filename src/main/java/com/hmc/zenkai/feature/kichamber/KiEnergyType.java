package com.hmc.zenkai.feature.kichamber;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.forms.FormRegistry;
import com.hmc.zenkai.feature.forms.LegendaryForms;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.registry.ModFluids;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Los cuatro tipos de energía de Ki (0.7.0, Fase 2a). Cada uno es un FLUIDO propio: el tipo lo
 * decide la forma que lleva puesta el jugador que carga en la cámara de contención.
 *
 * {@link #of(ResourceLocation)} es el EMBUDO ÚNICO de la regla forma → energía: lo consultan el
 * block entity, el menú y la pantalla. Con la tabla replicada, la pantalla podría anunciar un
 * tipo y la máquina producir otro. Orden de la regla (decidido con el usuario, 2026-11-16):
 *   1. Oozaru y Súper Oozaru: no cargan (null). Ni caben en la cámara ni tienen energía propia.
 *   2. Línea Legendaria (ikari, ssj_green, ssj_green_max y las *_legendary) → LEGENDARY. Cuenta
 *      la forma PUESTA, no el flag isLegendary: un Legendario en Base da Base.
 *   3. La forma, o algún ancestro por parent, es un peldaño divino (divine_tier), o es Ultra
 *      Instinto (Señal incluida) o Ultra Ego → DIVINE.
 *   4. SSJ1 a SSJ4, Súper Namekiano, Namekiano Naranja y cualquier forma de tier EVOLUTION → SUPER.
 *   5. El resto (Base, Potencial Desatado, Kaioken sin forma y formas sin regla) → BASE.
 * El Kaioken no cuenta: es una capa aparte de la forma y no cambia el tipo.
 *
 * El orden del enum es el que viaja por ContainerData y por NBT (ordinal): añadir tipos SOLO al final.
 */
public enum KiEnergyType {
    BASE("ki_base", 0xFF2D78F0),
    SUPER("ki_super", 0xFFE6B428),
    DIVINE("ki_divine", 0xFFDC283C),
    LEGENDARY("ki_legendary", 0xFF6EDC3C);

    private static final Set<ResourceLocation> SUPER_FORMS = Set.of(
            FormIds.SSJ1, FormIds.SSJ2, FormIds.SSJ3, FormIds.SSJ4,
            ResourceLocation.fromNamespaceAndPath("zenkai", "namek_super"),
            ResourceLocation.fromNamespaceAndPath("zenkai", "namek_orange"));

    private static final Set<ResourceLocation> DIVINE_FORMS = Set.of(
            FormIds.ULTRA_INSTINCT_SIGN, FormIds.ULTRA_INSTINCT, FormIds.ULTRA_EGO);

    private final String fluidName;
    /** == ZenkaiTechPalette.KI_BASE / KI_SUPER / KI_DIVINE / KI_LEGENDARY. Se duplica aquí porque
     *  esta clase es común (servidor incluido) y la paleta vive en el paquete de cliente. */
    private final int argb;

    KiEnergyType(String fluidName, int argb) {
        this.fluidName = fluidName;
        this.argb = argb;
    }

    /** Id del fluido sin namespace (también el nombre de sus sprites y de su clave de lang). */
    public String fluidName() { return fluidName; }
    public int argb() { return argb; }
    public String langKey() { return "fluid.zenkai." + fluidName; }

    public ModFluids.KiFluid fluid() {
        return switch (this) {
            case BASE -> ModFluids.KI_BASE;
            case SUPER -> ModFluids.KI_SUPER;
            case DIVINE -> ModFluids.KI_DIVINE;
            case LEGENDARY -> ModFluids.KI_LEGENDARY;
        };
    }

    /** FE que rinde un mB de este tipo en el conversor de la cámara. */
    public int fePerMb() {
        return switch (this) {
            case BASE -> ServerConfig.kiChamberFePerMbBase();
            case SUPER -> ServerConfig.kiChamberFePerMbSuper();
            case DIVINE -> ServerConfig.kiChamberFePerMbDivine();
            case LEGENDARY -> ServerConfig.kiChamberFePerMbLegendary();
        };
    }

    @Nullable
    public static KiEnergyType byOrdinal(int i) {
        KiEnergyType[] v = values();
        return i >= 0 && i < v.length ? v[i] : null;
    }

    /** Tipo de un fluido, o null si no es un fluido de Ki. */
    @Nullable
    public static KiEnergyType ofFluid(Fluid fluid) {
        for (KiEnergyType t : values()) {
            if (t.fluid().source().get().isSame(fluid)) return t;
        }
        return null;
    }

    /** Tipo que produce el jugador AHORA, o null si con esta forma no puede cargar. */
    @Nullable
    public static KiEnergyType of(Player p) {
        PlayerFormAttachment form = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        return of(form.getFormId());
    }

    /** La regla, sin jugador (también la usan los gametests). Ver el javadoc de la clase. */
    @Nullable
    public static KiEnergyType of(@Nullable ResourceLocation formId) {
        if (formId == null || FormIds.BASE.equals(formId)) return BASE;
        if (FormIds.OOZARU.equals(formId) || FormIds.SUPER_OOZARU.equals(formId)) return null;
        if (LegendaryForms.ownerOf(formId) != null) return LEGENDARY;
        if (DIVINE_FORMS.contains(formId) || hasDivineAncestor(formId)) return DIVINE;
        if (SUPER_FORMS.contains(formId)) return SUPER;
        FormDef def = FormRegistry.get(formId);
        if (def != null && def.tier() == FormDef.Tier.EVOLUTION) return SUPER;
        return BASE;
    }

    /** ¿La forma o alguno de sus ancestros es un peldaño divino? Con tope de pasos por si un
     *  datapack trae un ciclo de parent. */
    private static boolean hasDivineAncestor(ResourceLocation formId) {
        ResourceLocation cur = formId;
        for (int i = 0; i < 16 && cur != null; i++) {
            FormDef def = FormRegistry.get(cur);
            if (def == null) return false;
            if (def.divineTier()) return true;
            cur = def.parent();
        }
        return false;
    }
}
