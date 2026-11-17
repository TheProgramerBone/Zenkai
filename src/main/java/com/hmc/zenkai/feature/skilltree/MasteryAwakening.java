package com.hmc.zenkai.feature.skilltree;

import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SuperForms;
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
 * nivel de {@code super_forms} con {@code grantWaived} (permanente y sin cobrar MIND). No transforma al jugador.
 */
public final class MasteryAwakening {
    private MasteryAwakening() {}

    public static final String VIA = "mastery";

    /** Solo datos: concede el peldaño si toca. Devuelve la forma despertada o null. Sin red, para los gametests. */
    public static ResourceLocation evaluate(ServerPlayer sp) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen()) return null;
        Race race = att.getRace();
        int level = att.skills().level(SuperForms.SKILL);
        List<ResourceLocation> chain = SuperForms.chain(race);
        int idx = level - 1;   // nivel 1 = Base: el siguiente peldaño es el primero de la cadena
        if (level < 1 || idx >= chain.size()) return null;
        ResourceLocation next = chain.get(idx);
        if (SuperForms.requiredLevel(race, next) != level + 1) return null;   // fuera de la cadena comprable (divina)
        FormDef def = FormDef.get(next);
        SkillTreeData data = SkillTreeData.get(next);
        if (def == null || data == null || !data.acquisition().contains(VIA) || data.requirements().isEmpty()) return null;
        for (SkillTreeRequirement r : data.requirements()) if (!r.test(sp)) return null;
        att.skills().grantWaived(SuperForms.SKILL, level + 1);
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
