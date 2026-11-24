package com.hmc.zenkai.content.blockentity;

import com.hmc.zenkai.client.gui.menu.ImbuingStationMenu;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.block.ImbuingStationBlock;
import com.hmc.zenkai.content.item.BloodItem;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.armor.ArmorImbuingRecipe;
import com.hmc.zenkai.feature.armor.AttributeTemplates;
import com.hmc.zenkai.feature.armor.ImbueCost;
import com.hmc.zenkai.feature.armor.ImbuedAttributes;
import com.hmc.zenkai.feature.armor.KiInfusion;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.registry.ModBlockEntities;
import com.hmc.zenkai.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Estación de imbuido (2026-11-23: la máquina de fabricación se partió en dos a petición del usuario, «para dar más
 * hincapié a recetas/crafteos»; Fase 5d de pendientes-0.7.0). Aquí vive lo que MODIFICA una pieza ya hecha; la
 * Fabricadora ({@link ArmorFabricatorBlockEntity}) se queda con crear piezas y duplicar plantillas. Dos pestañas:
 *
 * ATRIBUTOS: pieza de armadura (cualquiera: vanilla, de otro mod o nuestra) + sangre → +calidad puntos del atributo de esa
 * sangre en {@code zenkai:imbued} (P13: conserva los atributos propios de la pieza). Coste (decisión 2026-11-16): FE con
 * la curva de {@link ImbueCost} + fluido de Ki + la sangre. El tipo de Ki lo dice la receta {@link ArmorImbuingRecipe}
 * de la pieza (o su tipo automático); un tipo superior vale gastando la mitad por cada tipo de más (decisión del usuario
 * 2026-11-22). Pulsar Imbuir arranca una CARGA corta ({@link #IMBUE_TICKS}, acortada por las mejoras de velocidad) y el
 * coste se cobra al final; si se quita la pieza o la sangre a mitad, se cancela sin gastar. Así no se imbuye a base de
 * spamear el botón (pedido del usuario 2026-11-22; antes era instantáneo). Con una PLANTILLA en su ranura el botón la
 * aplica en vez de imbuir (misma carga y mismo cobro al final); copiar y transferir usan la ranura de pieza destino.
 * TOPE de puntos imbuidos por pieza (KiInfusion.pieceCap): imbuir y las plantillas se RECHAZAN con aviso al llegar.
 *
 * INFUNDIR: pieza de cualquier mod + lingotes infundidos del nivel + FE y fluido de ese nivel → la pieza recibe
 * zenkai:ki_infusion (durabilidad y tope). También SUBE de nivel una pieza ya infundida conservando lo imbuido; paga el
 * nivel nuevo entero. Misma mecánica de carga que imbuir ({@link #INFUSE_TICKS}).
 */
public class ImbuingStationBlockEntity extends KiMachineBlockEntity {

    // ── Ranuras (un solo sitio para block entity, menú y pantalla) ───────────
    public static final int SLOT_ARMOR = UPGRADE_SLOTS, SLOT_BLOOD = SLOT_ARMOR + 1;          // 4, 5
    /** Plantilla de atributos y pieza destino (copiar/transferir). */
    public static final int SLOT_TEMPLATE = SLOT_BLOOD + 1, SLOT_TARGET = SLOT_TEMPLATE + 1;  // 6, 7
    /** Pestaña Infundir: la pieza y los lingotes infundidos. */
    public static final int SLOT_INFUSE_PIECE = SLOT_TARGET + 1, SLOT_INFUSE_INGOTS = SLOT_INFUSE_PIECE + 1;   // 8, 9
    public static final int SLOTS = SLOT_INFUSE_INGOTS + 1;                                   // 10

    /** mB de fluido por punto imbuido bajo el umbral; pasado el umbral sube con el mismo factor que la FE. */
    public static final double IMBUE_MB_PER_POINT = 100.0;
    /** Ticks de la carga de imbuir sin mejoras (1,5 s, decisión del usuario 2026-11-22). */
    public static final int IMBUE_TICKS = 30;
    /** Ticks de la carga de infundir una pieza sin mejoras (3 s): más que imbuir, gasta un puñado de lingotes. */
    public static final int INFUSE_TICKS = 60;

    public static boolean validFor(int slot, ItemStack stack) {
        if (slot < UPGRADE_SLOTS) return upgradeValid(slot, stack);
        if (slot == SLOT_ARMOR || slot == SLOT_TARGET || slot == SLOT_INFUSE_PIECE) return isArmorPiece(stack);
        if (slot == SLOT_INFUSE_INGOTS) return KiInfusion.isKatchinIngot(stack) && KiInfusion.materialLevel(stack) != null;
        if (slot == SLOT_BLOOD) return stack.getItem() instanceof BloodItem;
        if (slot == SLOT_TEMPLATE) return AttributeTemplates.kindOf(stack) != null;
        return false;
    }

    public static int slotLimit(int slot) {
        if (slot < UPGRADE_SLOTS) return upgradeLimit(slot);
        if (slot == SLOT_ARMOR || slot == SLOT_TARGET || slot == SLOT_INFUSE_PIECE) return 1;
        return 64;
    }

    /** ¿Es una pieza de armadura de cabeza/pecho/piernas/pies? Vale cualquiera de cualquier mod. */
    public static boolean isArmorPiece(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Equipable eq = Equipable.get(stack);
        return eq != null && eq.getEquipmentSlot().isArmor();
    }

    /** Tipo de Ki que pide imbuir esta pieza: el de su receta {@link ArmorImbuingRecipe}, o el automático. */
    public static KiEnergyType requiredKiType(@Nullable Level level, ItemStack piece) {
        return ArmorImbuingRecipe.requiredFor(level, piece);
    }

    /** Carga de imbuir en curso (0 = parada); llega a IMBUE_TICKS. */
    private int imbueProgress;
    /** Carga de infundir en curso (0 = parada); llega a INFUSE_TICKS. */
    private int infuseProgress;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case ImbuingStationMenu.D_IMBUE_PROGRESS -> imbueProgress;
                case ImbuingStationMenu.D_INFUSE_PROGRESS -> infuseProgress;
                default -> sharedData(i);
            };
        }
        @Override public void set(int i, int v) { }
        @Override public int getCount() { return ImbuingStationMenu.DATA_SLOTS; }
    };

    public ImbuingStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IMBUING_STATION.get(), pos, state, WORKSHOP_LIMITS, SLOTS, ImbuingStationBlockEntity::validFor,
                ImbuingStationBlockEntity::validFor, ImbuingStationBlockEntity::slotLimit);
    }

    public ContainerData containerData() { return data; }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, ImbuingStationBlockEntity be) {
        be.tickShared(level, pos);
        be.tickImbue();
        be.tickInfuse();
        setLit(level, pos, state, be.imbueProgress > 0 || be.infuseProgress > 0);
    }

    // ── Imbuir ───────────────────────────────────────────────────────────────

    /** Coste de imbuir lo que hay puesto ahora; null si falta la pieza o la sangre. Lo usan el botón y la pantalla. */
    @Nullable
    public static ImbuePreview preview(@Nullable Level level, ItemStack piece, ItemStack blood) {
        if (!isArmorPiece(piece) || !(blood.getItem() instanceof BloodItem b)) return null;
        ZenkaiAttributes attr = b.attribute();
        int q = BloodItem.quality(blood);
        int cur = piece.getOrDefault(ModDataComponents.IMBUED.get(), ImbuedAttributes.NONE).get(attr);
        // Una pieza infundida abarata FE y fluido (multiplica el resultado, la curva no se toca).
        KiEnergyType inf = KiInfusion.of(piece);
        long fe = KiInfusion.discounted(ImbueCost.totalCost(cur, q), inf);
        long mb = KiInfusion.discounted(ImbueCost.totalCost(cur, q, IMBUE_MB_PER_POINT,
                ServerConfig.imbueCostThreshold(), ServerConfig.imbueCostScale()), inf);
        return new ImbuePreview(attr, q, cur, fe, mb, requiredKiType(level, piece),
                KiInfusion.usedPoints(piece), KiInfusion.pieceCap(piece));
    }

    /** Plan de la plantilla puesta (null si no hay plantilla). Lo usan el botón y la pantalla. */
    @Nullable
    public static AttributeTemplates.Plan templatePlan(@Nullable Level level, ItemStack template, ItemStack piece,
                                                       ItemStack target) {
        return AttributeTemplates.plan(level, template, piece, target);
    }

    @Nullable
    private AttributeTemplates.Plan templatePlan() {
        return templatePlan(level, items.getStackInSlot(SLOT_TEMPLATE), items.getStackInSlot(SLOT_ARMOR),
                items.getStackInSlot(SLOT_TARGET));
    }

    /** Clave de lang del motivo por el que la plantilla puesta no se puede aplicar, o null. */
    @Nullable
    public String templateProblemKey() {
        AttributeTemplates.Plan p = templatePlan();
        return p == null || p.ok() ? null : p.problem().langKey();
    }

    /**
     * {@code mb} = lo que cuesta con el tipo pedido ({@code fluid}). {@link #mbWith} da lo que cuesta con el tipo que hay
     * en el tanque (-1 si no vale): servidor y pantalla usan la misma cuenta. {@code used}/{@code cap} = puntos totales
     * imbuidos de la pieza y su tope.
     */
    public record ImbuePreview(ZenkaiAttributes attribute, int points, int current, long fe, long mb, KiEnergyType fluid,
                               int used, int cap) {
        public long mbWith(@Nullable KiEnergyType inTank) { return ArmorImbuingRecipe.scaledMb(mb, fluid, inTank); }
        /** ¿Esta sangre pasaría el tope de puntos de la pieza? Se rechaza entera, no se recorta. */
        public boolean overCap() { return used + points > cap; }
    }

    /** Resultado de pulsar Imbuir o Infundir: OK o el motivo por el que no se pudo (clave de lang). */
    public enum ImbueResult {
        OK, NO_INPUT, NO_ENERGY, NO_FLUID, BAD_TEMPLATE, OVER_CAP, NO_TOLERANCE, NOT_HIGHER, NO_INGOTS;
        public String langKey() { return "screen.zenkai.armor_fabricator.imbue." + name().toLowerCase(java.util.Locale.ROOT); }
    }

    /**
     * Botón Imbuir: comprueba que se pueda (mismos motivos que {@link #imbue}) y arranca la carga. Si ya hay una en curso
     * no hace nada. El coste se cobra al acabar.
     */
    public ImbueResult startImbue() {
        if (imbueProgress > 0) return ImbueResult.OK;
        ImbueResult check = canImbue();
        if (check == ImbueResult.OK) {
            imbueProgress = 1;
            setChanged();
        }
        return check;
    }

    public boolean imbuing() { return imbueProgress > 0; }

    private void tickImbue() {
        if (imbueProgress <= 0) return;
        if (canImbue() != ImbueResult.OK) {   // pieza o sangre quitada, o se quedó sin FE/fluido a mitad: se cancela
            imbueProgress = 0;
            setChanged();
            return;
        }
        imbueProgress += speedStep();
        if (imbueProgress >= IMBUE_TICKS) {
            imbueProgress = 0;
            imbue();
        }
        setChanged();
    }

    /** ¿Se podría imbuir (o aplicar la plantilla puesta) ahora mismo? OK o el motivo. */
    private ImbueResult canImbue() {
        if (!items.getStackInSlot(SLOT_TEMPLATE).isEmpty()) return canApplyTemplate(templatePlan());
        ImbuePreview p = preview(level, items.getStackInSlot(SLOT_ARMOR), items.getStackInSlot(SLOT_BLOOD));
        if (p == null) return ImbueResult.NO_INPUT;
        if (p.overCap()) return ImbueResult.OVER_CAP;
        if (energy.get() < p.fe()) return ImbueResult.NO_ENERGY;
        long mb = fluidCost(p.mbWith(tankType()));
        if (mb < 0 || tank.getFluidAmount() < mb) return ImbueResult.NO_FLUID;
        return ImbueResult.OK;
    }

    private ImbueResult canApplyTemplate(@Nullable AttributeTemplates.Plan p) {
        if (p == null) return ImbueResult.NO_INPUT;
        if (!p.ok()) return ImbueResult.BAD_TEMPLATE;
        if (energy.get() < p.fe()) return ImbueResult.NO_ENERGY;
        long mb = fluidCost(p.mbWith(tankType()));
        if (mb < 0 || tank.getFluidAmount() < mb) return ImbueResult.NO_FLUID;
        return ImbueResult.OK;
    }

    /**
     * Aplica la plantilla al momento: cobra FE y fluido, gasta UNA plantilla y deja las piezas nuevas en sus ranuras.
     * Lo llama el final de la carga (y los gametests). Servidor.
     */
    public ImbueResult applyTemplate() {
        AttributeTemplates.Plan p = templatePlan();
        ImbueResult check = canApplyTemplate(p);
        if (check != ImbueResult.OK) return check;
        pay(p.fe(), fluidCost(p.mbWith(tankType())));
        AttributeTemplates.Result r = AttributeTemplates.apply(p, items.getStackInSlot(SLOT_ARMOR), items.getStackInSlot(SLOT_TARGET));
        items.extractItem(SLOT_TEMPLATE, 1, false);
        items.setStackInSlot(SLOT_ARMOR, r.piece());
        if (p.kind().needsTarget()) items.setStackInSlot(SLOT_TARGET, r.target());
        setChanged();
        return ImbueResult.OK;
    }

    /**
     * Imbuye una vez (una sangre) al momento, sin carga: lo llama el final de la carga (y los gametests). Servidor.
     * Con una plantilla puesta aplica la plantilla (el botón es el mismo).
     */
    public ImbueResult imbue() {
        if (!items.getStackInSlot(SLOT_TEMPLATE).isEmpty()) return applyTemplate();
        ImbueResult check = canImbue();
        if (check != ImbueResult.OK) return check;
        ItemStack piece = items.getStackInSlot(SLOT_ARMOR);
        ImbuePreview p = preview(level, piece, items.getStackInSlot(SLOT_BLOOD));
        pay(p.fe(), fluidCost(p.mbWith(tankType())));
        items.extractItem(SLOT_BLOOD, 1, false);
        ItemStack out = piece.copy();
        ImbuedAttributes before = out.getOrDefault(ModDataComponents.IMBUED.get(), ImbuedAttributes.NONE);
        out.set(ModDataComponents.IMBUED.get(), before.plus(p.attribute(), p.points()));
        items.setStackInSlot(SLOT_ARMOR, out);
        setChanged();
        return ImbueResult.OK;
    }

    // ── Infundir ─────────────────────────────────────────────────────────────

    /** Por qué no se puede infundir lo puesto (o NONE). Claves {@code screen.zenkai.armor_fabricator.infuse.<id>}. */
    public enum InfuseProblem {
        NONE, NO_PIECE, NO_INGOTS, NOT_HIGHER, NOT_ENOUGH;
        public String langKey() { return "screen.zenkai.armor_fabricator.infuse." + name().toLowerCase(java.util.Locale.ROOT); }
    }

    /**
     * Lo que costaría infundir la pieza con los lingotes puestos: el nivel es el de los lingotes; {@code ingots} los que
     * pide la ranura de la pieza (KiInfusion.ingotsFor), {@code have} los que hay; FE y mB = lingotes × el coste por
     * lingote de la infusora (la pieza «se hace» con esos lingotes, así que cuesta lo mismo que infundirlos). Pide el
     * fluido EXACTO del nivel: es el que decide el nivel, como en la infusora. Null si no hay pieza.
     */
    public record InfusePreview(InfuseProblem problem, @Nullable KiEnergyType level, int ingots, int have, long fe, long mb) {
        public boolean ok() { return problem == InfuseProblem.NONE; }
    }

    @Nullable
    public static InfusePreview infusePreview(ItemStack piece, ItemStack ingots) {
        if (!isArmorPiece(piece)) return null;
        int need = KiInfusion.ingotsFor(piece);
        KiEnergyType lvl = KiInfusion.materialLevel(ingots);
        if (lvl == null || !KiInfusion.isKatchinIngot(ingots)) return new InfusePreview(InfuseProblem.NO_INGOTS, null, need, 0, 0, 0);
        long fe = (long) need * KiInfusion.FE_PER_ITEM, mb = (long) need * KiInfusion.MB_PER_ITEM;
        int have = ingots.getCount();
        if (KiInfusion.tier(KiInfusion.of(piece)) >= KiInfusion.tier(lvl)) {
            return new InfusePreview(InfuseProblem.NOT_HIGHER, lvl, need, have, fe, mb);
        }
        if (have < need) return new InfusePreview(InfuseProblem.NOT_ENOUGH, lvl, need, have, fe, mb);
        return new InfusePreview(InfuseProblem.NONE, lvl, need, have, fe, mb);
    }

    @Nullable
    private InfusePreview infusePreview() {
        return infusePreview(items.getStackInSlot(SLOT_INFUSE_PIECE), items.getStackInSlot(SLOT_INFUSE_INGOTS));
    }

    /** Clave de lang del motivo por el que no se puede infundir lo puesto, o null. */
    @Nullable
    public String infuseProblemKey() {
        InfusePreview p = infusePreview();
        return p == null || p.ok() ? null : p.problem().langKey();
    }

    public boolean infusing() { return infuseProgress > 0; }

    /** ¿Se podría infundir ahora? OK o el motivo. */
    private ImbueResult canInfuse() {
        InfusePreview p = infusePreview();
        if (p == null) return ImbueResult.NO_INPUT;
        if (p.problem() == InfuseProblem.NO_INGOTS || p.problem() == InfuseProblem.NOT_ENOUGH) return ImbueResult.NO_INGOTS;
        if (p.problem() == InfuseProblem.NOT_HIGHER) return ImbueResult.NOT_HIGHER;
        if (!tolerates(p.level())) return ImbueResult.NO_TOLERANCE;
        if (energy.get() < p.fe()) return ImbueResult.NO_ENERGY;
        if (tankType() != p.level() || tank.getFluidAmount() < fluidCost(p.mb())) return ImbueResult.NO_FLUID;
        return ImbueResult.OK;
    }

    /** Botón Infundir: valida y arranca la carga (si ya hay una, nada). El coste se cobra al acabar. */
    public ImbueResult startInfuse() {
        if (infuseProgress > 0) return ImbueResult.OK;
        ImbueResult check = canInfuse();
        if (check == ImbueResult.OK) {
            infuseProgress = 1;
            setChanged();
        }
        return check;
    }

    private void tickInfuse() {
        if (infuseProgress <= 0) return;
        if (canInfuse() != ImbueResult.OK) {   // pieza o lingotes quitados, o sin FE/fluido a mitad: se cancela sin gastar
            infuseProgress = 0;
            setChanged();
            return;
        }
        infuseProgress += speedStep();
        if (infuseProgress >= INFUSE_TICKS) {
            infuseProgress = 0;
            infuse();
        }
        setChanged();
    }

    /**
     * Infunde al momento, sin carga: cobra FE, fluido y lingotes y deja la pieza con la infusión nueva, su durabilidad
     * recalculada sobre la de fábrica y lo imbuido intacto. Lo llama el final de la carga (y los gametests). Servidor.
     */
    public ImbueResult infuse() {
        ImbueResult check = canInfuse();
        if (check != ImbueResult.OK) return check;
        InfusePreview p = infusePreview();
        pay(p.fe(), fluidCost(p.mb()));
        items.extractItem(SLOT_INFUSE_INGOTS, p.ingots(), false);
        items.setStackInSlot(SLOT_INFUSE_PIECE, KiInfusion.applyToArmor(items.getStackInSlot(SLOT_INFUSE_PIECE), p.level()));
        setChanged();
        return ImbueResult.OK;
    }

    // ── Menú ─────────────────────────────────────────────────────────────────

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("screen.zenkai.imbuing_station.title");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player player) {
        return new ImbuingStationMenu(id, inv, this);
    }
}
