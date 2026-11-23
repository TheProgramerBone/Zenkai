package com.hmc.zenkai.feature.armor;

import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.registry.ModDataComponents;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * {@code /zenkai debug imbue <str|con|dex|wil|spi> <cantidad>} y {@code /zenkai debug imbue clear}: imbuye la pieza de la
 * mano principal sin la máquina de la Fase 5b, que aún no existe (decisión 2026-11-22: único modo de probar la 5a en
 * juego). La cantidad SUMA (negativa resta, sin bajar de 0). Informa del coste que habría cobrado la máquina con la curva
 * de ImbueCost, para ver la curva sin calculadora. Herramienta de operador: cuelga de {@code /zenkai debug}, que ya exige
 * permiso 2. Texto literal a propósito, como el resto de comandos de depuración.
 */
public final class ImbueDebugCommands {
    private ImbueDebugCommands() {}

    private static final String[] KEYS = { "str", "con", "dex", "wil", "spi" };

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("imbue")
                .then(Commands.literal("clear").executes(ImbueDebugCommands::clear))
                .then(Commands.argument("attribute", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(KEYS, b))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(-1_000_000, 1_000_000))
                                .executes(ImbueDebugCommands::add)));
    }

    private static ZenkaiAttributes parse(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "str" -> ZenkaiAttributes.STRENGTH;
            case "con" -> ZenkaiAttributes.CONSTITUTION;
            case "dex" -> ZenkaiAttributes.DEXTERITY;
            case "wil" -> ZenkaiAttributes.WILLPOWER;
            case "spi" -> ZenkaiAttributes.SPIRIT;
            default -> null;
        };
    }

    private static int add(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        ZenkaiAttributes attr = parse(StringArgumentType.getString(ctx, "attribute"));
        if (attr == null) {
            ctx.getSource().sendFailure(Component.literal("Atributo no imbuible. Usa str, con, dex, wil o spi."));
            return 0;
        }
        ItemStack stack = sp.getMainHandItem();
        if (stack.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Necesitas una pieza en la mano principal."));
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        ImbuedAttributes before = stack.getOrDefault(ModDataComponents.IMBUED.get(), ImbuedAttributes.NONE);
        int cur = before.get(attr);
        ImbuedAttributes after = before.plus(attr, amount);
        long cost = amount > 0 ? ImbueCost.totalCost(cur, amount) : 0L;
        store(stack, after);
        if (ArmorAttributeBonus.refresh(sp)) PlayerLifeCycle.sync(sp);
        int now = after.get(attr);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "%s: %d -> %d en la pieza. La máquina habría cobrado %,d FE (punto siguiente: %.0f FE).",
                attr.name(), cur, now, cost, ImbueCost.pointCost(now))), false);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        ItemStack stack = sp.getMainHandItem();
        stack.remove(ModDataComponents.IMBUED.get());
        if (ArmorAttributeBonus.refresh(sp)) PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(() -> Component.literal("Atributos imbuidos borrados de la pieza."), false);
        return 1;
    }

    /** Sin componente cuando queda vacío: un stack «imbuido a cero» no apilaría con uno limpio igual. */
    private static void store(ItemStack stack, ImbuedAttributes imb) {
        if (imb.isEmpty()) stack.remove(ModDataComponents.IMBUED.get());
        else stack.set(ModDataComponents.IMBUED.get(), imb);
    }
}
