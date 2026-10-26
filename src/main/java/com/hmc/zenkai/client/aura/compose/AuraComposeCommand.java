package com.hmc.zenkai.client.aura.compose;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.aura.flame.AuraSystemDispatcher;
import com.hmc.zenkai.client.aura.flame.AuraSystemMode;
import com.hmc.zenkai.client.aura.compose.component.AuraComponentTypes;
import com.hmc.zenkai.feature.aura.AuraSignatureRegistry;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.profiling.InactiveProfiler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.TreeSet;

/**
 * {@code /zenkaiaura} — comando SOLO-CLIENTE para probar el aura compuesta en caliente (mismo
 * espíritu que {@code /zenkaiauraflame} y {@code /zkvfx}: solo en memoria, se pierde al cerrar).
 * Afecta solo al aura PROPIA; los demás jugadores se siguen viendo con su forma real.
 * <ul>
 *   <li>{@code system auto|composed|legacy|config} — qué sistema dibuja el aura, para comparar
 *       la nueva con la clásica sin reiniciar; {@code config} = volver a la opción aura.system.</li>
 *   <li>{@code type <aura_type>|clear} — fuerza el estilo y la firma de un aura_type (ssj, ssj2,
 *       god, blue, ultra_ego...) sin cambiar de forma.</li>
 *   <li>{@code family <familia>|clear} — fuerza la familia desnuda (flame, divine, dark_dense).</li>
 *   <li>{@code debug on|off} — overlay con la composición activa (familia, cuerpo, componentes,
 *       estados, LOD).</li>
 *   <li>{@code reload} — relee solo los estilos (assets/zenkai/zenkai_aura_styles). En desarrollo,
 *       {@code ./gradlew processResources} antes.</li>
 *   <li>{@code layer <capa> on|off|solo} / {@code layer all} — apaga o aísla una capa del aura propia
 *       ({@code body}, {@code trail} o el tipo de un componente: motes, electricity, wisps, shell,
 *       rim, strands).</li>
 *   <li>{@code state <estado> on|off|auto} / {@code state reset} — fuerza un estado dinámico
 *       (charging, high_charge, transforming, turbo, kaioken, low_ki, suppressed, flying,
 *       perturbed) sin gastar ki ni cambiar nada en el servidor.</li>
 *   <li>{@code transform} — reproduce la secuencia de transformación del estilo actual.</li>
 *   <li>{@code look fps <n>} / {@code look bands3 on|off} / {@code look whitecap <0..1>} / {@code look topfill <0..1>} /
 *       {@code look reset} — prueba de dirección de arte (2026-10-26): movimiento a saltos, tres
 *       bandas duras y tope de blanco del borde, para CUALQUIER aura compuesta.</li>
 *   <li>{@code compare <aura_type>|off} — dibuja un aura fantasma con otro aura_type a tu derecha,
 *       con tu misma pose y tu mismo poder: dos auras lado a lado sin reiniciar.</li>
 * </ul>
 * La calibración fina de la llama sigue en {@code /zenkaiauraflame set|list|reset}.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraComposeCommand {

    private AuraComposeCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("zenkaiaura")
                .then(Commands.literal("system")
                        .then(Commands.literal("auto").executes(c -> system(c.getSource(), AuraSystemMode.AUTO)))
                        .then(Commands.literal("composed").executes(c -> system(c.getSource(), AuraSystemMode.COMPOSED)))
                        .then(Commands.literal("legacy").executes(c -> system(c.getSource(), AuraSystemMode.FORCE_LEGACY)))
                        .then(Commands.literal("config").executes(c -> system(c.getSource(), null))))
                .then(Commands.literal("type")
                        .then(Commands.literal("clear").executes(c -> type(c.getSource(), null)))
                        .then(Commands.argument("aura_type", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(knownTypes(), b))
                                .executes(c -> type(c.getSource(), StringArgumentType.getString(c, "aura_type")))))
                .then(Commands.literal("family")
                        .then(Commands.literal("clear").executes(c -> family(c.getSource(), null)))
                        .then(Commands.argument("family", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(AuraFamilies.ids(), b))
                                .executes(c -> family(c.getSource(), StringArgumentType.getString(c, "family")))))
                .then(Commands.literal("debug")
                        .then(Commands.literal("on").executes(c -> debug(c.getSource(), true)))
                        .then(Commands.literal("off").executes(c -> debug(c.getSource(), false))))
                .then(Commands.literal("reload").executes(c -> reload(c.getSource())))
                .then(Commands.literal("layer")
                        .then(Commands.literal("all").executes(c -> layerAll(c.getSource())))
                        .then(Commands.argument("layer", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(layerIds(), b))
                                .then(Commands.literal("on").executes(c -> layer(c.getSource(), StringArgumentType.getString(c, "layer"), "on")))
                                .then(Commands.literal("off").executes(c -> layer(c.getSource(), StringArgumentType.getString(c, "layer"), "off")))
                                .then(Commands.literal("solo").executes(c -> layer(c.getSource(), StringArgumentType.getString(c, "layer"), "solo")))))
                .then(Commands.literal("state")
                        .then(Commands.literal("reset").executes(c -> stateReset(c.getSource())))
                        .then(Commands.argument("state", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(AuraDynamics.State.values()).map(AuraDynamics.State::id), b))
                                .then(Commands.literal("on").executes(c -> state(c.getSource(), StringArgumentType.getString(c, "state"), Boolean.TRUE)))
                                .then(Commands.literal("off").executes(c -> state(c.getSource(), StringArgumentType.getString(c, "state"), Boolean.FALSE)))
                                .then(Commands.literal("auto").executes(c -> state(c.getSource(), StringArgumentType.getString(c, "state"), null)))))
                .then(Commands.literal("transform").executes(c -> transform(c.getSource())))
                .then(Commands.literal("look")
                        .then(Commands.literal("reset").executes(c -> { AuraDebug.lookTopFill = 0f; return look(c.getSource(), 0f, false, 0f); }))
                        .then(Commands.literal("topfill")
                                .then(Commands.argument("fill", FloatArgumentType.floatArg(0f, 1f))
                                        .executes(c -> {
                                            AuraDebug.lookTopFill = FloatArgumentType.getFloat(c, "fill");
                                            return look(c.getSource(), AuraDebug.lookFps, AuraDebug.lookBands3, AuraDebug.lookWhiteCap);
                                        })))
                        .then(Commands.literal("fps")
                                .then(Commands.argument("fps", FloatArgumentType.floatArg(0f, 60f))
                                        .executes(c -> look(c.getSource(), FloatArgumentType.getFloat(c, "fps"),
                                                AuraDebug.lookBands3, AuraDebug.lookWhiteCap))))
                        .then(Commands.literal("bands3")
                                .then(Commands.literal("on").executes(c -> look(c.getSource(), AuraDebug.lookFps, true, AuraDebug.lookWhiteCap)))
                                .then(Commands.literal("off").executes(c -> look(c.getSource(), AuraDebug.lookFps, false, AuraDebug.lookWhiteCap))))
                        .then(Commands.literal("whitecap")
                                .then(Commands.argument("cap", FloatArgumentType.floatArg(0f, 1f))
                                        .executes(c -> look(c.getSource(), AuraDebug.lookFps, AuraDebug.lookBands3,
                                                FloatArgumentType.getFloat(c, "cap"))))))
                .then(Commands.literal("compare")
                        .then(Commands.literal("off").executes(c -> compare(c.getSource(), null)))
                        .then(Commands.argument("aura_type", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(knownTypes(), b))
                                .executes(c -> compare(c.getSource(), StringArgumentType.getString(c, "aura_type"))))));
    }

    private static TreeSet<String> knownTypes() {
        TreeSet<String> all = new TreeSet<>(AuraStyleManager.loadedTypes());
        all.addAll(AuraSignatureRegistry.all().keySet());
        all.add("default");
        return all;
    }

    private static int system(CommandSourceStack src, @Nullable AuraSystemMode mode) {
        AuraSystemDispatcher.setSessionOverride(mode);
        String which = AuraSystemDispatcher.useComposed() ? "NUEVA (compuesta)" : "CLÁSICA (legacy)";
        src.sendSuccess(() -> Component.literal("[Aura] Modo " + AuraSystemDispatcher.effectiveMode()
                + (mode == null ? " (config)" : " (sesión)") + " -> " + which), false);
        return 1;
    }

    private static int type(CommandSourceStack src, @Nullable String t) {
        AuraDebug.typeOverride = t;
        AuraStyle s = AuraStyleManager.get(t);
        src.sendSuccess(() -> Component.literal(t == null ? "[Aura] aura_type: el de la forma"
                : "[Aura] aura_type forzado: " + t + " -> familia " + s.family().id()
                + (AuraStyleManager.loadedTypes().contains(t) ? "" : " (sin estilo JSON: familia flame desnuda)")), false);
        return 1;
    }

    private static int family(CommandSourceStack src, @Nullable String id) {
        if (id != null && AuraFamilies.get(id) == null) {
            src.sendFailure(Component.literal("[Aura] Familia desconocida: " + id + " (existen: " + AuraFamilies.ids() + ")"));
            return 0;
        }
        AuraDebug.familyOverride = id;
        src.sendSuccess(() -> Component.literal(id == null ? "[Aura] familia: la del estilo"
                : "[Aura] familia forzada: " + id), false);
        return 1;
    }

    private static int debug(CommandSourceStack src, boolean on) {
        AuraDebug.hud = on;
        if (!on) AuraDebug.LINES.clear();
        src.sendSuccess(() -> Component.literal("[Aura] overlay de depuración " + (on ? "activado" : "desactivado")), false);
        return 1;
    }

    private static TreeSet<String> layerIds() {
        TreeSet<String> ids = new TreeSet<>(AuraComponentTypes.ids());
        ids.add("body");
        ids.add("trail");
        return ids;
    }

    private static int layer(CommandSourceStack src, String layer, String mode) {
        if (!layerIds().contains(layer)) {
            src.sendFailure(Component.literal("[Aura] Capa desconocida: " + layer + " (existen: " + layerIds() + ")"));
            return 0;
        }
        switch (mode) {
            case "off" -> AuraDebug.DISABLED.add(layer);
            case "solo" -> AuraDebug.solo = layer;
            default -> {
                AuraDebug.DISABLED.remove(layer);
                if (layer.equals(AuraDebug.solo)) AuraDebug.solo = null;
            }
        }
        src.sendSuccess(() -> Component.literal("[Aura] capa " + layer + ": " + mode
                + (AuraDebug.solo != null ? "  (solo: " + AuraDebug.solo + ")" : "")
                + (AuraDebug.DISABLED.isEmpty() ? "" : "  apagadas: " + AuraDebug.DISABLED)), false);
        return 1;
    }

    private static int layerAll(CommandSourceStack src) {
        AuraDebug.DISABLED.clear();
        AuraDebug.solo = null;
        src.sendSuccess(() -> Component.literal("[Aura] todas las capas activas"), false);
        return 1;
    }

    private static int state(CommandSourceStack src, String id, @Nullable Boolean on) {
        AuraDynamics.State st = null;
        for (AuraDynamics.State s : AuraDynamics.State.values()) if (s.id().equals(id)) st = s;
        if (st == null) {
            src.sendFailure(Component.literal("[Aura] Estado desconocido: " + id));
            return 0;
        }
        if (on == null) AuraDebug.FORCED.remove(st);
        else AuraDebug.FORCED.put(st, on);
        String text = "[Aura] estado " + id + ": " + (on == null ? "real" : on ? "forzado on" : "forzado off")
                + "  forzados: " + AuraDebug.FORCED;
        src.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int stateReset(CommandSourceStack src) {
        AuraDebug.FORCED.clear();
        src.sendSuccess(() -> Component.literal("[Aura] estados: los reales (ninguno forzado)"), false);
        return 1;
    }

    private static int transform(CommandSourceStack src) {
        AuraDebug.burstRequested = true;
        src.sendSuccess(() -> Component.literal("[Aura] secuencia de transformación (con el aura encendida: carga ki)"), false);
        return 1;
    }

    private static int look(CommandSourceStack src, float fps, boolean bands3, float whiteCap) {
        AuraDebug.lookFps = fps;
        AuraDebug.lookBands3 = bands3;
        AuraDebug.lookWhiteCap = whiteCap;
        src.sendSuccess(() -> Component.literal("[Aura] look: " + (fps > 0f ? "a saltos " + fps + " fps" : "movimiento continuo")
                + ", " + (bands3 ? "3 bandas" : "bandas de siempre")
                + ", " + (whiteCap > 0f ? "tope de blanco " + whiteCap : "blanco de siempre")
                + (AuraDebug.lookTopFill > 0f ? ", relleno arriba " + AuraDebug.lookTopFill : "")), false);
        return 1;
    }

    private static int compare(CommandSourceStack src, @Nullable String t) {
        AuraDebug.compareType = t;
        src.sendSuccess(() -> Component.literal(t == null ? "[Aura] comparación desactivada"
                : "[Aura] aura fantasma a tu derecha: " + t + " (familia " + AuraStyleManager.get(t).family().id() + ")"), false);
        return 1;
    }

    private static int reload(CommandSourceStack src) {
        AuraStyleManager.INSTANCE.reloadNow(Minecraft.getInstance().getResourceManager(), InactiveProfiler.INSTANCE);
        src.sendSuccess(() -> Component.literal("[Aura] Estilos recargados: " + AuraStyleManager.loadedTypes()), false);
        return 1;
    }
}
