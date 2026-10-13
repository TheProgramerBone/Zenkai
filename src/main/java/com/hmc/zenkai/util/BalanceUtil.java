package com.hmc.zenkai.util;

import com.hmc.zenkai.feature.RaceStatTable;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.Style;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;

import java.util.EnumMap;

public class BalanceUtil {

    public static void setBase(EnumMap<ZenkaiAttributes,Integer> map,
                               int STR, int CON, int DEX, int WIL, int SPI, int MIND) {
        map.put(ZenkaiAttributes.STRENGTH,     STR);
        map.put(ZenkaiAttributes.CONSTITUTION, CON);
        map.put(ZenkaiAttributes.DEXTERITY,    DEX);
        map.put(ZenkaiAttributes.WILLPOWER,    WIL);
        map.put(ZenkaiAttributes.SPIRIT,       SPI);
        map.put(ZenkaiAttributes.MIND,         MIND);
    }

    /**
     * Calcula Stat = Atributo × MultRaza × MultEstilo
     * usando los multiplicadores configurables de StatsConfig.
     */
    public static double computeStat(int base, Race race, Style style, ZenkaiAttributes attr) {
        RaceStatTable.Col col = RaceStatTable.colFor(attr);
        return col == null ? base : base * RaceStatTable.get(race, style, col);
    }

    /** Suelo del poder: evita que un personaje recién creado sostenga formas/kaioken gratis. */
    public static final double MIN_POWER_STAT = 10.0;

    /**
     * Poder bruto del jugador = STR + WIL, cada uno ya ponderado por su raza/estilo. SUMA y no
     * máximo a propósito: con 1000 de STR y 999 de WIL un max() solo contaría el 1000 y ignoraría
     * el otro. Lo comparten el drenaje del Kaioken y el de las transformaciones, para que ambos
     * escalen con el mismo número.
     */
    public static double powerStat(PlayerStatsAttachment att) {
        double str = computeStat(att.getAttribute(ZenkaiAttributes.STRENGTH),
                att.getRace(), att.getStyle(), ZenkaiAttributes.STRENGTH);
        double wil = computeStat(att.getAttribute(ZenkaiAttributes.WILLPOWER),
                att.getRace(), att.getStyle(), ZenkaiAttributes.WILLPOWER);
        return Math.max(MIN_POWER_STAT, str + wil);
    }
}
