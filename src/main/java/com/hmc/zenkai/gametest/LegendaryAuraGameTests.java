package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * El estado Legendario viaja en el attachment de formas (el de stats no se sincroniza a quien te ve) para que el aura de los
 * demás jugadores pueda llevar el guiño verde (AuraVariants): se refresca, avisa del cambio y sobrevive a guardar y cargar.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LegendaryAuraGameTests {
    private LegendaryAuraGameTests() {}

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void legendaryStateMirrorsToFormAttachment(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.setRace(Race.SAIYAN);
        PlayerFormAttachment form = sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get());

        att.setLegendary(false);
        form.refreshPotentialCeiling(sp);
        helper.assertTrue(!form.isLegendaryState(), "Sin estado Legendario, el espejo está apagado");

        att.setLegendary(true);
        helper.assertTrue(form.refreshPotentialCeiling(sp), "Al activarse el estado, el refresco avisa de que cambió (hay que sincronizar)");
        helper.assertTrue(form.isLegendaryState(), "El espejo se enciende");
        helper.assertTrue(!form.refreshPotentialCeiling(sp), "Sin cambios, no vuelve a avisar");

        PlayerFormAttachment copy = new PlayerFormAttachment();
        copy.load(form.save());
        helper.assertTrue(copy.isLegendaryState(), "Viaja en el guardado/sincronización del attachment de formas");
        helper.succeed();
    }
}
