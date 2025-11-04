package com.hmc.db_renewed.util;

import com.hmc.db_renewed.network.stats.Dbrattributes;
import com.hmc.db_renewed.network.stats.Race;
import com.hmc.db_renewed.network.stats.Style;

import java.util.EnumMap;

public class BalanceUtil {

    public static void setBase(EnumMap<Dbrattributes,Integer> map, int STR, int CON, int DEX, int WIL, int SPI, int MIND){
        map.put(Dbrattributes.STRENGTH, STR);
        map.put(Dbrattributes.CONSTITUTION, CON);
        map.put(Dbrattributes.DEXTERITY, DEX);
        map.put(Dbrattributes.WILLPOWER, WIL);
        map.put(Dbrattributes.SPIRIT, SPI);
        map.put(Dbrattributes.MIND, MIND);
    }

    // Calcula Stat = Atributo × MultRaza × MultEstilo
    public static double computeStat(int base, Race race, Style style, Dbrattributes attr) {
        double rm = switch (race) {
            case HUMAN -> switch (attr) {
                case STRENGTH, CONSTITUTION, DEXTERITY, WILLPOWER, SPIRIT, MIND -> 1.0;
            };
            case SAIYAN -> switch (attr) {
                case STRENGTH -> 1.3; case CONSTITUTION -> 1.0; case DEXTERITY -> 1.2; case WILLPOWER -> 0.8; case SPIRIT -> 0.7; case MIND -> 1.0;
            };
            case NAMEKIAN -> switch (attr) {
                case STRENGTH -> 0.8; case CONSTITUTION -> 0.9; case DEXTERITY -> 0.9; case WILLPOWER -> 1.1; case SPIRIT -> 1.3; case MIND -> 1.0;
            };
            case ARCOSIAN -> switch (attr) {
                case STRENGTH -> 0.9; case CONSTITUTION -> 0.9; case DEXTERITY -> 1.0; case WILLPOWER -> 1.2; case SPIRIT -> 1.1; case MIND -> 1.0;
            };
            case MAJIN -> switch (attr) {
                case STRENGTH -> 0.9; case CONSTITUTION -> 1.3; case DEXTERITY -> 0.9; case WILLPOWER -> 1.1; case SPIRIT -> 0.8; case MIND -> 1.0;
            };
        };
        double sm = switch (style) {
            case WARRIOR -> switch (attr) {
                case STRENGTH -> 1.2; case CONSTITUTION -> 1.1; case DEXTERITY -> 1.3; case WILLPOWER -> 0.8; case SPIRIT -> 0.8; case MIND -> 1.0;
            };
            case MARTIAL_ARTIST -> switch (attr) {
                case STRENGTH -> 1.1; case CONSTITUTION -> 1.0; case DEXTERITY -> 1.0; case WILLPOWER -> 1.1; case SPIRIT -> 1.0; case MIND -> 1.0;
            };
            case SPIRITUALIST -> switch (attr) {
                case STRENGTH -> 0.9; case CONSTITUTION -> 0.9; case DEXTERITY -> 0.9; case WILLPOWER -> 1.3; case SPIRIT -> 1.2; case MIND -> 1.0;
            };
        };
        return base * rm * sm;
    }
}