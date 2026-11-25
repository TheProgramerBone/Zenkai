package com.hmc.zenkai.feature.skilltree;

import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Peldaños de la cadena que NO se compran: despiertan solos al cumplir sus requisitos (0.7.0; el primero es el Namekiano
 * Naranja, que despierta al dominar al Súper Namekiano). Lo decide el JSON del nodo ({@code zenkai_skill_tree}):
 * {@code "acquisition": ["mastery"]} más su lista {@code "requirements"}; aquí no hay ningún número.
 * <p>
 * Solo actúa sobre el SIGUIENTE peldaño de la cadena comprable (nunca saltos) y lo concede como cualquier forma regalada:
 * nodo de la cadena mortal sin cobrar MIND (SkillTree.grantNode con waived). No transforma al jugador.
 */
public final class MasteryAwakening {
    private MasteryAwakening() {}

    public static final String VIA = "mastery";

    /** Solo datos: concede el peldaño si toca. Devuelve la forma despertada o null. Sin red, para los gametests. */
    public static ResourceLocation evaluate(ServerPlayer sp) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen()) return null;
        Race race = att.getRace();
        // El siguiente peldaño de la cadena mortal que aún no posee (los peldaños se compran en orden).
        ResourceLocation next = null;
        for (ResourceLocation id : FormAccess.purchasableChain(race)) {
            if (!att.skillTree().owns(id)) { next = id; break; }
        }
        if (next == null) return null;   // la cadena mortal entera ya es suya
        FormDef def = FormDef.get(next);
        SkillTreeData data = SkillTreeData.get(next);
        if (def == null || data == null || !data.acquisition().contains(VIA) || data.requirements().isEmpty()) return null;
        for (SkillTreeRequirement r : data.requirements()) if (!r.test(sp)) return null;
        SkillTree.grantNode(att, next, true);
        return next;
    }

    /** Lo mismo, con sync y aviso al jugador. Barato cuando no hay nada que conceder: se llama una vez por segundo. */
    public static void check(ServerPlayer sp) {
        ResourceLocation form = evaluate(sp);
        if (form == null) return;
        PlayerLifeCycle.syncIfServer(sp);
        sp.displayClientMessage(Component.translatable("messages.zenkai.form_awakened_by_mastery",
                Component.translatable(FormDef.nameKey(form))).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
    }
}
