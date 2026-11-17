package com.hmc.zenkai.feature.rage;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.forms.FormRegistry;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SuperForms;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Migración de partidas anteriores a Rage Forms (archivo/2026-11-08-rage-forms-diseno-v2.md §12.4): cada forma que el jugador ya posee, y la que lleve
 * activa, se marca como secuencia larga YA vista. NO concede recompensas, desbloqueos ni maestría: solo las formas que consiga
 * después tendrán su primera secuencia larga. Se hace una vez, al entrar: la clave {@code rageMigrated} no existe en esas partidas
 * (PlayerStateFlags la lee como false) y toda partida nueva la guarda a true.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class RageMigration {
    private RageMigration() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) migrate(sp);
    }

    /** @return true si migró (la partida era anterior). Separado del evento para poder probarlo. */
    public static boolean migrate(Player p) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        if (att.flags().isRageMigrated()) return false;
        if (att.isRaceChosen()) {
            Race race = att.getRace();
            for (FormDef d : FormDef.all()) {
                if (FormIds.BASE.equals(d.id()) || !FormRegistry.isAllowed(race, d.id())) continue;
                if (SuperForms.unlocked(p, d.id())) att.flags().markLongCinematicSeen(d.id().toString());
            }
            PlayerFormAttachment forms = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
            if (!FormIds.BASE.equals(forms.getFormId())) att.flags().markLongCinematicSeen(forms.getFormId().toString());
        }
        att.flags().setRageMigrated(true);
        return true;
    }
}
