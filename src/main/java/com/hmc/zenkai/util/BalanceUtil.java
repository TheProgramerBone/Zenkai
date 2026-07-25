package com.hmc.zenkai.util;

import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.feature.RaceStatTable;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.Style;

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
}
