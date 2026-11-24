package com.hmc.zenkai.feature.armor;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.blockentity.ImbuingStationBlockEntity;
import com.hmc.zenkai.content.item.AttributeTemplateItem;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModDataComponents;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Plantillas de atributos (Fase 5c de la 0.7.0; P13 y decisiones del usuario 2026-11-22). Actúan SOLO sobre lo imbuido
 * de las piezas ({@code zenkai:imbued}), nunca sobre los atributos del jugador, y se aplican en la pestaña de Atributos
 * de la máquina de fabricación. La plantilla SE GASTA al usarla (como la de netherita de vanilla; por eso se duplica).
 * NO confundir con el PATRÓN (qué armadura se fabrica): el patrón es diseño; la plantilla, atributos.
 * <ul>
 *   <li>REINICIAR: borra lo imbuido de la pieza. No devuelve sangre (P13). Sin coste aparte de la plantilla.</li>
 *   <li>COPIAR: lo imbuido de la pieza pasa también a la pieza destino (mismo tipo de pieza, destino sin imbuir); la
 *       pieza de origen lo conserva. Cobra la FE y el fluido de imbuir esos puntos en el destino desde cero (curva de
 *       ImbueCost, descuento de la infusión del destino y su tipo de Ki), SIN sangre: ahorra el farmeo, no la energía.</li>
 *   <li>TRANSFERIR: lo imbuido pasa de la pieza al destino (mismo tipo de pieza, destino sin imbuir) y la de origen queda
 *       limpia. Sin coste aparte de la plantilla: no crea puntos, solo los mueve.</li>
 *   <li>AUMENTAR: +1 a cada atributo que la pieza YA tiene imbuido, sin sangre; cobra FE y fluido con la curva. El techo
 *       {@code caps.armor_attribute} lo sigue aplicando la suma del jugador (ArmorAttributeBonus).</li>
 * </ul>
 * Exigir destino SIN imbuir en copiar/transferir evita pisar por error lo que ya tenía (para vaciarlo está reiniciar).
 *
 * TOPE POR PIEZA (rework del 2026-11-23, KiInfusion.pieceCap): copiar y transferir se RECHAZAN si lo imbuido del origen
 * no cabe en el tope del destino, y aumentar si el +1 pasaría el tope de la pieza. No se recorta nada: el jugador ve el
 * motivo y decide (infundir el destino para subirle el tope, o elegir otra pieza).
 */
public final class AttributeTemplates {
    private AttributeTemplates() {}

    public enum Kind {
        RESET, COPY, TRANSFER, AUGMENT;
        public String id() { return name().toLowerCase(Locale.ROOT); }
        /** ¿Necesita la segunda pieza (destino)? */
        public boolean needsTarget() { return this == COPY || this == TRANSFER; }
    }

    /** Por qué no se puede aplicar (clave de lang {@code screen.zenkai.armor_fabricator.template.<id>}). */
    public enum Problem {
        NONE, NO_PIECE, NOTHING_IMBUED, NO_TARGET, WRONG_PIECE_TYPE, TARGET_NOT_EMPTY, OVER_CAP;
        public String langKey() { return "screen.zenkai.armor_fabricator.template." + name().toLowerCase(Locale.ROOT); }
    }

    /**
     * Lo que haría la plantilla con lo puesto. {@code fluid} = tipo de Ki que pide (null si no gasta fluido); {@code mb}
     * = mB con ese tipo; {@link #mbWith} con el que hay en el tanque (-1 si no vale), como ImbuePreview.
     */
    public record Plan(Kind kind, Problem problem, long fe, long mb, @Nullable KiEnergyType fluid) {
        public boolean ok() { return problem == Problem.NONE; }
        public long mbWith(@Nullable KiEnergyType inTank) {
            if (fluid == null || mb <= 0) return 0;
            return ArmorImbuingRecipe.scaledMb(mb, fluid, inTank);
        }
    }

    @Nullable
    public static Kind kindOf(ItemStack template) {
        return template.getItem() instanceof AttributeTemplateItem t ? t.kind() : null;
    }

    public static ImbuedAttributes imbued(ItemStack s) {
        return s.getOrDefault(ModDataComponents.IMBUED.get(), ImbuedAttributes.NONE);
    }

    /** Plan para la plantilla, la pieza (origen) y el destino; null si no hay plantilla. */
    @Nullable
    public static Plan plan(@Nullable Level level, ItemStack template, ItemStack piece, ItemStack target) {
        Kind kind = kindOf(template);
        if (kind == null) return null;
        if (!ImbuingStationBlockEntity.isArmorPiece(piece)) return new Plan(kind, Problem.NO_PIECE, 0, 0, null);
        ImbuedAttributes src = imbued(piece);
        if (src.isEmpty()) return new Plan(kind, Problem.NOTHING_IMBUED, 0, 0, null);
        if (kind.needsTarget()) {
            if (!ImbuingStationBlockEntity.isArmorPiece(target)) return new Plan(kind, Problem.NO_TARGET, 0, 0, null);
            if (!sameSlot(piece, target)) return new Plan(kind, Problem.WRONG_PIECE_TYPE, 0, 0, null);
            if (!imbued(target).isEmpty()) return new Plan(kind, Problem.TARGET_NOT_EMPTY, 0, 0, null);
            if (KiInfusion.total(src) > KiInfusion.pieceCap(target)) return new Plan(kind, Problem.OVER_CAP, 0, 0, null);
        }
        if (kind == Kind.AUGMENT && KiInfusion.total(src) + augmentedCount(src) > KiInfusion.pieceCap(piece)) {
            return new Plan(kind, Problem.OVER_CAP, 0, 0, null);
        }
        return switch (kind) {
            case RESET, TRANSFER -> new Plan(kind, Problem.NONE, 0, 0, null);
            case COPY -> costed(level, kind, target, src, false);
            case AUGMENT -> costed(level, kind, piece, src, true);
        };
    }

    /**
     * FE y fluido de imbuir {@code points} en {@code payer}: desde cero (copiar) o +1 sobre lo que ya tiene (aumentar).
     * Misma curva, constante de fluido y descuento de infusión que imbuir con sangre.
     */
    private static Plan costed(@Nullable Level level, Kind kind, ItemStack payer, ImbuedAttributes points, boolean augment) {
        long fe = 0, mb = 0;
        int thr = ServerConfig.imbueCostThreshold();
        double scale = ServerConfig.imbueCostScale();
        for (ZenkaiAttributes a : ImbuedAttributes.IMBUABLE) {
            int v = points.get(a);
            if (v <= 0) continue;
            int from = augment ? v : 0, n = augment ? 1 : v;
            fe += ImbueCost.totalCost(from, n);
            mb += ImbueCost.totalCost(from, n, ImbuingStationBlockEntity.IMBUE_MB_PER_POINT, thr, scale);
        }
        KiEnergyType inf = KiInfusion.of(payer);
        return new Plan(kind, Problem.NONE, KiInfusion.discounted(fe, inf), KiInfusion.discounted(mb, inf),
                ArmorImbuingRecipe.requiredFor(level, payer));
    }

    /** Cuántos atributos sube aumentar (los que ya tienen algo imbuido: +1 a cada uno). */
    private static int augmentedCount(ImbuedAttributes a) {
        int n = 0;
        for (ZenkaiAttributes at : ImbuedAttributes.IMBUABLE) if (a.get(at) > 0) n++;
        return n;
    }

    /** Resultado de aplicar un plan válido: las dos piezas nuevas (el destino vacío si la plantilla no lo usa). */
    public record Result(ItemStack piece, ItemStack target) {}

    public static Result apply(Plan plan, ItemStack piece, ItemStack target) {
        ImbuedAttributes src = imbued(piece);
        ItemStack p = piece.copy(), t = target.copy();
        switch (plan.kind()) {
            case RESET -> p.remove(ModDataComponents.IMBUED.get());
            case COPY -> t.set(ModDataComponents.IMBUED.get(), src);
            case TRANSFER -> {
                t.set(ModDataComponents.IMBUED.get(), src);
                p.remove(ModDataComponents.IMBUED.get());
            }
            case AUGMENT -> {
                ImbuedAttributes next = src;
                for (ZenkaiAttributes a : ImbuedAttributes.IMBUABLE) if (src.get(a) > 0) next = next.plus(a, 1);
                p.set(ModDataComponents.IMBUED.get(), next);
            }
        }
        return new Result(p, t);
    }

    /** ¿Van en la misma ranura de armadura (casco con casco…)? */
    public static boolean sameSlot(ItemStack a, ItemStack b) {
        Equipable ea = Equipable.get(a), eb = Equipable.get(b);
        return ea != null && eb != null && ea.getEquipmentSlot() == eb.getEquipmentSlot();
    }
}
