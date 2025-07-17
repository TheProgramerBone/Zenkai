package com.hmc.db_renewed.common.stats;

import com.hmc.db_renewed.common.player.RaceDataHandler;
import com.hmc.db_renewed.common.player.RaceStatsGrowth;
import com.hmc.db_renewed.common.capability.StatAllocation;
import com.hmc.db_renewed.common.race.ModRaces;
import com.hmc.db_renewed.common.style.CombatStyle;
import net.minecraft.world.entity.player.Player;

public class FinalStatsCalculator {

    public static void update(Player player) {
        if (player == null || player.level().isClientSide) return;

        RaceDataHandler data = player.getCapability(RaceDataHandler.CAPABILITY);
        assert data != null;{
            StatAllocation alloc = data.getStatAllocation();
            ModRaces race = data.getRace();
            CombatStyle style = data.getCombatStyle();

            RaceStatsGrowth growth = RaceStatsGrowth.GROWTHS.getOrDefault(race, new RaceStatsGrowth(1f, 1f, 1f, 1f, 1f, 1f));

            // Estilo modificador (puedes personalizar estos multiplicadores)
            float strengthStyleMod = getStyleModifier(style, StatType.STRENGTH);
            float dexterityStyleMod = getStyleModifier(style, StatType.DEXTERITY);
            float constitutionStyleMod = getStyleModifier(style, StatType.CONSTITUTION);
            float willpowerStyleMod = getStyleModifier(style, StatType.WILLPOWER);
            float spiritStyleMod = getStyleModifier(style, StatType.SPIRIT);

            // Base stats según la raza
            StatAllocation base = com.hmc.db_renewed.common.race.ModRacesStats.DEFAULT_STATS.get(race);
            if (base == null) return;

            // Calcular valores finales
            alloc.strength = calculate(base.strength, alloc.getTpStrength(), growth.strengthMultiplier(), strengthStyleMod);
            alloc.dexterity = calculate(base.dexterity, alloc.getTpDexterity(), growth.dexterityMultiplier(), dexterityStyleMod);
            alloc.constitution = calculate(base.constitution, alloc.getTpConstitution(), growth.constitutionMultiplier(), constitutionStyleMod);
            alloc.willpower = calculate(base.willpower, alloc.getTpWillpower(), growth.willpowerMultiplier(), willpowerStyleMod);
            alloc.spirit = calculate(base.spirit, alloc.getTpSpirit(), growth.spiritMultiplier(), spiritStyleMod);

            // Mind se calcula sin modificadores
            alloc.mind = base.mind + alloc.getTpMind();

            // Aquí puedes agregar sincronización si fuera necesario
            // Por ejemplo, usando packets o métodos utilitarios
        };
    }

    private static int calculate(int base, int tp, float growthMultiplier, float styleModifier) {
        return Math.round(base + (tp * growthMultiplier * styleModifier));
    }

    private enum StatType {
        STRENGTH, DEXTERITY, CONSTITUTION, WILLPOWER, SPIRIT
    }

    private static float getStyleModifier(CombatStyle style, StatType type) {
        return switch (style) {
            case WARRIOR -> switch (type) {
                case STRENGTH -> 1.3f;
                case CONSTITUTION -> 1.2f;
                case DEXTERITY, WILLPOWER, SPIRIT -> 1.0f;
            };
            case MARTIAL_ARTIST -> switch (type) {
                case DEXTERITY -> 1.3f;
                case WILLPOWER -> 1.2f;
                case STRENGTH, CONSTITUTION, SPIRIT -> 1.0f;
            };
            case SPIRITUALIST -> switch (type) {
                case SPIRIT -> 1.4f;
                case WILLPOWER -> 1.2f;
                case STRENGTH, DEXTERITY, CONSTITUTION -> 1.0f;
            };
        };
    }
}