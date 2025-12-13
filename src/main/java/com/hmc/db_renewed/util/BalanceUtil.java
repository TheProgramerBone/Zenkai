package com.hmc.db_renewed.util;

import com.hmc.db_renewed.config.StatsConfig;
import com.hmc.db_renewed.network.stats.Dbrattributes;
import com.hmc.db_renewed.network.stats.Race;
import com.hmc.db_renewed.network.stats.Style;

import java.util.EnumMap;

public class BalanceUtil {

    public static void setBase(EnumMap<Dbrattributes,Integer> map,
                               int STR, int CON, int DEX, int WIL, int SPI, int MIND) {
        map.put(Dbrattributes.STRENGTH,     STR);
        map.put(Dbrattributes.CONSTITUTION, CON);
        map.put(Dbrattributes.DEXTERITY,    DEX);
        map.put(Dbrattributes.WILLPOWER,    WIL);
        map.put(Dbrattributes.SPIRIT,       SPI);
        map.put(Dbrattributes.MIND,         MIND);
    }

    /**
     * Calcula Stat = Atributo × MultRaza × MultEstilo
     * usando los multiplicadores configurables de StatsConfig.
     */
    public static double computeStat(int base, Race race, Style style, Dbrattributes attr) {
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
