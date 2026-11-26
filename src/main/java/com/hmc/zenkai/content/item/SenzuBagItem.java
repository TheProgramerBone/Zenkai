package com.hmc.zenkai.content.item;

import com.hmc.zenkai.registry.ModDataComponents;
import com.hmc.zenkai.registry.ModItems;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Bolsa de senzu (QOL pedido por el usuario 2026-11-26). Guarda hasta {@link #CAPACITY} senzu en el componente
 * {@code zenkai:senzu_count} (la senzu suelta va de 16 en 16, así que la bolsa ahorra tres ranuras).
 *
 * USO: clic derecho → se come una senzu guardada, con el MISMO efecto que la suelta ({@link SenzuBean#eat}) y el cooldown
 * COMPARTIDO con ella (decisión del usuario: alternar bolsa y senzu sueltas no cura el doble). Clic derecho sobre otro jugador
 * derribado o herido le da una ({@link SenzuBean#healOther}), como la suelta.
 *
 * METER Y SACAR, como el bundle vanilla y solo con clic DERECHO en el inventario: con la bolsa en el cursor sobre una ranura
 * de senzu las recoge, y sobre una ranura vacía suelta un montón (hasta 16); con la bolsa en una ranura, clic derecho con
 * senzu en el cursor las mete, y con el cursor vacío saca un montón al cursor. Solo acepta senzu.
 */
public class SenzuBagItem extends Item {

    public static final int CAPACITY = 64;
    /** Color de la barra de llenado: el verde de la senzu. */
    private static final int BAR_COLOR = 0x6CC24A;

    public SenzuBagItem(Properties props) {
        super(props);
    }

    public static int count(ItemStack bag) {
        return bag.getOrDefault(ModDataComponents.SENZU_COUNT.get(), 0);
    }

    /**
     * Etapa del icono (propiedad {@code zenkai:fill}, overrides de tools/gen_senzu_bag.py): 0 vacía, 1 hasta 16, 2 hasta
     * 48, 3 más (petición del usuario 2026-11-26: la textura cambia según lo llena que esté).
     */
    public static float fillStage(ItemStack bag) {
        int n = count(bag);
        return n <= 0 ? 0F : n <= 16 ? 1F : n <= 48 ? 2F : 3F;
    }

    private static void setCount(ItemStack bag, int n) {
        n = Mth.clamp(n, 0, CAPACITY);
        if (n == 0) bag.remove(ModDataComponents.SENZU_COUNT.get());
        else bag.set(ModDataComponents.SENZU_COUNT.get(), n);
    }

    private static boolean isSenzu(ItemStack s) {
        return s.is(ModItems.SENZU_BEAN.get());
    }

    /** Un montón de senzu (como mucho uno lleno) sacado de la bolsa. */
    private static ItemStack takeStack(ItemStack bag) {
        int n = Math.min(count(bag), ModItems.SENZU_BEAN.get().getDefaultMaxStackSize());
        if (n <= 0) return ItemStack.EMPTY;
        setCount(bag, count(bag) - n);
        return new ItemStack(ModItems.SENZU_BEAN.get(), n);
    }

    /** Mete en la bolsa lo que quepa de {@code senzu} (lo encoge). Devuelve cuántas metió. */
    private static int insert(ItemStack bag, ItemStack senzu) {
        int n = Math.min(senzu.getCount(), CAPACITY - count(bag));
        if (n <= 0) return 0;
        setCount(bag, count(bag) + n);
        senzu.shrink(n);
        return n;
    }

    // ── Uso ──────────────────────────────────────────────────────────────────

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        return 1;   // instantánea, como la senzu suelta
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
                                                           @NotNull InteractionHand hand) {
        ItemStack bag = player.getItemInHand(hand);
        if (count(bag) <= 0) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.zenkai.senzu_bag.empty"), true);
            }
            return InteractionResultHolder.fail(bag);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(bag);
    }

    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack bag, @NotNull Level level, @NotNull LivingEntity entity) {
        if (entity instanceof Player player && count(bag) > 0) {
            SenzuBean.eat(player, level);
            if (!level.isClientSide && !player.getAbilities().instabuild) setCount(bag, count(bag) - 1);
        }
        return bag;
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(@NotNull ItemStack bag, @NotNull Player user,
                                                           @NotNull LivingEntity target, @NotNull InteractionHand hand) {
        if (user.level().isClientSide || count(bag) <= 0 || !SenzuBean.healOther(user, target)) return InteractionResult.PASS;
        if (!user.getAbilities().instabuild) setCount(bag, count(bag) - 1);
        return InteractionResult.SUCCESS;
    }

    // ── Inventario (como el bundle) ──────────────────────────────────────────

    /** Bolsa en el cursor, clic derecho sobre una ranura. */
    @Override
    public boolean overrideStackedOnOther(@NotNull ItemStack bag, @NotNull Slot slot, @NotNull ClickAction action,
                                          @NotNull Player player) {
        if (action != ClickAction.SECONDARY || bag.getCount() != 1) return false;
        ItemStack inSlot = slot.getItem();
        if (inSlot.isEmpty()) {
            ItemStack out = takeStack(bag);
            if (out.isEmpty()) return true;
            ItemStack rest = slot.safeInsert(out);
            if (!rest.isEmpty()) setCount(bag, count(bag) + rest.getCount());   // lo que no cupo vuelve a la bolsa
            player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
            return true;
        }
        if (isSenzu(inSlot) && slot.mayPickup(player)) {
            int space = CAPACITY - count(bag);
            if (space <= 0) return true;
            ItemStack taken = slot.safeTake(inSlot.getCount(), space, player);
            if (insert(bag, taken) > 0) {
                player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
            }
            return true;
        }
        return false;
    }

    /** Bolsa en una ranura, clic derecho con algo (o nada) en el cursor. */
    @Override
    public boolean overrideOtherStackedOnMe(@NotNull ItemStack bag, @NotNull ItemStack carried, @NotNull Slot slot,
                                            @NotNull ClickAction action, @NotNull Player player, @NotNull SlotAccess access) {
        if (action != ClickAction.SECONDARY || bag.getCount() != 1 || !slot.allowModification(player)) return false;
        if (carried.isEmpty()) {
            ItemStack out = takeStack(bag);
            if (out.isEmpty()) return false;
            access.set(out);
            player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
            return true;
        }
        if (isSenzu(carried)) {
            if (insert(bag, carried) > 0) {
                player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
            }
            return true;
        }
        return false;
    }

    // ── Barra y tooltip ──────────────────────────────────────────────────────

    @Override
    public boolean isBarVisible(@NotNull ItemStack bag) {
        return count(bag) > 0;
    }

    @Override
    public int getBarWidth(@NotNull ItemStack bag) {
        return Math.min(13, 1 + 12 * count(bag) / CAPACITY);
    }

    @Override
    public int getBarColor(@NotNull ItemStack bag) {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack bag, @NotNull TooltipContext ctx, @NotNull List<Component> tip,
                                @NotNull TooltipFlag flag) {
        tip.add(Component.translatable("item.zenkai.senzu_bag.count", ZenkaiNumbers.screen(count(bag)),
                ZenkaiNumbers.screen(CAPACITY)).withStyle(ChatFormatting.GREEN));
        tip.add(Component.translatable("item.zenkai.senzu_bag.desc").withStyle(ChatFormatting.GRAY));
    }
}
