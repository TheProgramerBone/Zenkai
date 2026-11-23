package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.RaceStatTable;
import com.hmc.zenkai.feature.StatSynergy;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.armor.ArmorAttributeBonus;
import com.hmc.zenkai.feature.armor.ImbueCost;
import com.hmc.zenkai.feature.armor.ImbuedAttributes;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ModDataComponents;
import com.hmc.zenkai.util.BalanceUtil;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Fase 5a de la 0.7.0: atributos de armadura en la capa de atributos, antes de StatSynergy. Comprueba la cadena entera
 * (melee, pools, PL, drenaje), que el bono no recorta ni encarece los puntos propios (P1), el techo total, que solo
 * cuentan las ranuras de armadura y la curva logarítmica de ImbueCost.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArmorAttributeGameTests {
    private ArmorAttributeGameTests() {}

    private static ItemStack imbued(net.minecraft.world.item.Item item, ImbuedAttributes imb) {
        ItemStack s = new ItemStack(item);
        s.set(ModDataComponents.IMBUED.get(), imb);
        return s;
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void armorBonusFeedsChainButNotTpCost(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.setAttribute(ZenkaiAttributes.STRENGTH, 100);
        att.setAttribute(ZenkaiAttributes.CONSTITUTION, 100);
        int wil = att.getAttribute(ZenkaiAttributes.WILLPOWER);

        int costBefore = att.previewTpCost(ZenkaiAttributes.STRENGTH, 1);
        long plBefore = att.getPowerLevel();
        double powerBefore = BalanceUtil.powerStat(att);
        int bodyBefore = att.raceStats().recalcAll().bodyMax();

        sp.setItemSlot(EquipmentSlot.CHEST, imbued(Items.IRON_CHESTPLATE,
                ImbuedAttributes.NONE.with(ZenkaiAttributes.STRENGTH, 50).with(ZenkaiAttributes.CONSTITUTION, 20)));
        helper.assertTrue(ArmorAttributeBonus.refresh(sp), "Ponerse una pieza imbuida debería cambiar el bono");
        helper.assertFalse(ArmorAttributeBonus.refresh(sp), "Sin cambios en el equipo el bono no debería volver a cambiar");

        helper.assertTrue(att.getArmorBonus(ZenkaiAttributes.STRENGTH) == 50, "Bono STR esperado 50");
        helper.assertTrue(att.getAttribute(ZenkaiAttributes.STRENGTH) == 100,
                "Los puntos propios no deben cambiar con la armadura, dan " + att.getAttribute(ZenkaiAttributes.STRENGTH));
        double expected = StatSynergy.melee(150, wil, RaceStatTable.melee(att.getRace(), att.getStyle()));
        double got = att.raceStats().computeMeleeFinal();
        helper.assertTrue(Math.abs(got - expected) < 1e-6, "Melee con armadura: esperado " + expected + ", da " + got);
        helper.assertTrue(att.previewTpCost(ZenkaiAttributes.STRENGTH, 1) == costBefore,
                "La armadura no debe encarecer ni abaratar el TP de los puntos propios");
        helper.assertTrue(att.getPowerLevel() > plBefore, "El PL debería subir con la armadura");
        helper.assertTrue(BalanceUtil.powerStat(att) > powerBefore,
                "El drenaje de formas/Kaioken debería contar la STR de la armadura (decisión 2026-11-22)");
        helper.assertTrue(att.raceStats().recalcAll().bodyMax() > bodyBefore, "La CON de la armadura debería subir el Body");

        // El bono viaja en save() (sync completo al cliente) y se recupera al cargar.
        PlayerStatsAttachment copy = new PlayerStatsAttachment();
        copy.load(att.save());
        helper.assertTrue(copy.getArmorBonus(ZenkaiAttributes.STRENGTH) == 50, "El bono debería sobrevivir a save/load");

        // El respec no toca la armadura ni la devuelve como TP.
        att.respec();
        helper.assertTrue(att.getArmorBonus(ZenkaiAttributes.STRENGTH) == 50, "El respec no debe borrar el bono de armadura");

        sp.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        ArmorAttributeBonus.refresh(sp);
        helper.assertTrue(att.getArmorBonus(ZenkaiAttributes.STRENGTH) == 0, "Quitarse la pieza debería dejar el bono a 0");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void armorBonusStacksAboveOwnCap(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        int cap = ServerConfig.globalAttributeCap();
        att.setAttribute(ZenkaiAttributes.STRENGTH, cap);
        sp.setItemSlot(EquipmentSlot.HEAD, imbued(Items.IRON_HELMET, ImbuedAttributes.NONE.with(ZenkaiAttributes.STRENGTH, 50)));
        ArmorAttributeBonus.refresh(sp);
        helper.assertTrue(att.getAttributeWithArmor(ZenkaiAttributes.STRENGTH) == cap + 50,
                "P1: al tope con +50 de armadura debe quedar en tope + 50, da " + att.getAttributeWithArmor(ZenkaiAttributes.STRENGTH));
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void onlyArmorSlotsCountAndTotalIsCapped(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        ImbuedAttributes thirty = ImbuedAttributes.NONE.with(ZenkaiAttributes.DEXTERITY, 30);
        sp.setItemSlot(EquipmentSlot.MAINHAND, imbued(Items.IRON_BOOTS, thirty));
        helper.assertTrue(ArmorAttributeBonus.compute(sp, 1000).get(ZenkaiAttributes.DEXTERITY) == 0,
                "Una pieza imbuida en la mano no debe dar nada");
        sp.setItemSlot(EquipmentSlot.FEET, imbued(Items.IRON_BOOTS, thirty));
        sp.setItemSlot(EquipmentSlot.LEGS, imbued(Items.IRON_LEGGINGS, thirty));
        helper.assertTrue(ArmorAttributeBonus.compute(sp, 1000).get(ZenkaiAttributes.DEXTERITY) == 60,
                "Dos piezas de +30 deberían sumar 60");
        helper.assertTrue(ArmorAttributeBonus.compute(sp, 40).get(ZenkaiAttributes.DEXTERITY) == 40,
                "Con techo 40 el total debería recortarse a 40");
        helper.assertTrue(ImbuedAttributes.NONE.with(ZenkaiAttributes.MIND, 9).isEmpty(), "MIND no se imbuye");
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void imbueCostIsLogarithmicPastThreshold(GameTestHelper helper) {
        double base = 1000, scale = 100;
        int thr = 100;
        helper.assertTrue(ImbueCost.factor(0, thr, scale) == 1.0 && ImbueCost.factor(thr, thr, scale) == 1.0,
                "Hasta el umbral cada punto debe valer exactamente la base");
        double f = ImbueCost.factor(thr + 100, thr, scale);
        helper.assertTrue(Math.abs(f - (1 + Math.log(2))) < 1e-9, "Exceso = escala debería dar 1 + ln 2, da " + f);
        helper.assertTrue(ImbueCost.totalCost(0, 5, base, thr, scale) == 5000, "5 puntos bajo el umbral = 5 x base");
        long a = ImbueCost.totalCost(thr, 1, base, thr, scale);
        long b = ImbueCost.totalCost(10_000, 1, base, thr, scale);
        helper.assertTrue(b > a, "El coste por punto debe crecer pasado el umbral");
        helper.assertTrue(b < 10 * a, "Pero de forma logarítmica: el punto 10 000 no debe costar 10 veces el del umbral (da "
                + b + " frente a " + a + ")");
        helper.succeed();
    }
}
