package com.hmc.zenkai.client.gui;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.race.RacePassives;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ÚNICA fuente del texto de las pasivas de raza (selector de raza, nodo del árbol y pantalla de Stats).
 * Cada efecto sale UNA sola vez, como una línea con su número: no hay descripción en prosa aparte, ni línea de
 * cifras, ni «segunda línea», que decían lo mismo con otras palabras (decisión del usuario, 2026-11-25).
 * Los números salen de las curvas del JSON de la pasiva ({@link RacePassives#valueAt}), las mismas que aplica el
 * juego, así que el texto no puede discrepar del efecto.
 *
 * <p>Añadir un efecto a una pasiva = una línea en {@link #lines} y su clave de lang
 * {@code race.zenkai.passive.effect.<raza>.<id>}. Añadir una pasiva nueva a una raza = otra entrada en
 * {@link #passives}: el selector pinta un icono por entrada.</p>
 */
public final class RacePassiveText {
    private RacePassiveText() {}

    /** Una pasiva de raza: su nombre, sus efectos y la raza de la que sale su icono. */
    public record Passive(Component name, List<Component> lines, Race iconRace) {}

    /** Pasivas de la raza a ese nivel de la pasiva. Hoy una por raza; el selector admite varias. */
    public static List<Passive> passives(Race race, int level) {
        if (race == null) return List.of();
        return List.of(new Passive(Component.translatable(RacePassives.nameKey(race)), lines(race, level), race));
    }

    /** Los efectos de la pasiva de esa raza en ese nivel, uno por línea (vacío si no hay). */
    public static List<Component> lines(Race race, int level) {
        List<Component> out = new ArrayList<>();
        if (race == null) return out;
        switch (race) {
            case SAIYAN -> {
                out.add(effect(race, "zenkai",
                        pct(RacePassives.valueAt(race, "zenkai_bonus", level, RacePassives.ZENKAI_BONUS)),
                        num(RacePassives.valueAt(race, "zenkai_duration_s", level, RacePassives.ZENKAI_DURATION / 20.0)),
                        num(RacePassives.valueAt(race, "zenkai_cooldown_s", level, RacePassives.ZENKAI_COOLDOWN / 20.0) / 60.0)));
                out.add(effect(race, "gravity",
                        pct(RacePassives.valueAt(race, "gravity_tp", level, RacePassives.SAIYAN_GRAVITY_TP))));
            }
            case NAMEKIAN -> {
                out.add(effect(race, "regen",
                        num(RacePassives.valueAt(race, "regen_mult", level, RacePassives.NAMEKIAN_REGEN_MULT)),
                        num(RacePassives.valueAt(race, "regen_ki_per_point", level, RacePassives.NAMEKIAN_REGEN_KI_COST))));
                out.add(effect(race, "trade",
                        pct(RacePassives.valueAt(race, "trade_discount", level, RacePassives.NAMEKIAN_TRADE_DISCOUNT))));
            }
            case HUMAN -> {
                out.add(effect(race, "tp",
                        pct(RacePassives.valueAt(race, "tp_gain", level, RacePassives.HUMAN_TP_GAIN - 1.0))));
                out.add(effect(race, "minigames",
                        pct(RacePassives.valueAt(race, "minigame_tp", level, RacePassives.HUMAN_MINIGAME_TP))));
                out.add(effect(race, "stamina",
                        num(RacePassives.valueAt(race, "stamina_regen", level, RacePassives.HUMAN_STAMINA_REGEN))));
            }
            case ARCOSIAN -> {
                out.add(effect(race, "cold"));
                out.add(effect(race, "ki",
                        pct(RacePassives.valueAt(race, "ki_discount", level, 1.0 - RacePassives.ARCOSIAN_KI_DISCOUNT))));
                out.add(effect(race, "fire",
                        pct(RacePassives.valueAt(race, "fire_reduction", level, ServerConfig.arcosianFireDamageReduction()))));
            }
            case MAJIN -> {
                out.add(effect(race, "physical",
                        pct(RacePassives.valueAt(race, "phys_reduction", level, RacePassives.MAJIN_PHYSICAL_REDUCTION))));
                out.add(effect(race, "regen",
                        num(RacePassives.valueAt(race, "regen_per_second", level, RacePassives.MAJIN_PASSIVE_REGEN) * 100.0)));
            }
        }
        return out;
    }

    /** Todos los efectos en un solo texto con saltos de línea, para los tooltips de una sola fila (Stats). */
    public static Component joined(Race race, int level) {
        var out = Component.empty();
        List<Component> ls = lines(race, level);
        for (int i = 0; i < ls.size(); i++) {
            if (i > 0) out.append("\n");
            out.append(ls.get(i));
        }
        return out;
    }

    private static Component effect(Race race, String id, Object... args) {
        return Component.translatable("race.zenkai.passive.effect." + race.name().toLowerCase(java.util.Locale.ROOT) + "." + id, args);
    }

    /** Fracción → porcentaje sin ceros de sobra («15 %», «12,5 %»); el formato lo da {@link ZenkaiNumbers}. */
    private static String pct(double frac) { return trim(ZenkaiNumbers.fmt2(frac * 100.0)) + "%"; }

    private static String num(double v) { return trim(ZenkaiNumbers.fmt2(v)); }

    private static String trim(String s) {
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }
}
