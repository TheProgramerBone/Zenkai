package com.hmc.zenkai.util;

import com.hmc.zenkai.core.config.StatsConfig;
import com.hmc.zenkai.core.network.feature.ZenkaiAttributes;
import com.hmc.zenkai.core.network.feature.Race;
import com.hmc.zenkai.core.network.feature.Style;

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
        // raceMult: [mSTR, mCON, mDEX, mWIL, mSPI, mMND]
        double[] r = StatsConfig.raceMultipliers(race);
        // styleMult: [sSTR, sCON, sDEX, sWIL, sSPI, sMND]
        double[] s = StatsConfig.styleMultipliers(style);

        int index = switch (attr) {
            case STRENGTH     -> 0;
            case CONSTITUTION -> 1;
            case DEXTERITY    -> 2;
            case WILLPOWER    -> 3;
            case SPIRIT       -> 4;
            case MIND         -> 5;
        };

        double rm = (r != null && r.length > index) ? r[index] : 1.0;
        double sm = (s != null && s.length > index) ? s[index] : 1.0;

        return base * rm * sm;
    }
}
