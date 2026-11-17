package com.hmc.zenkai.feature.rage;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SuperForms;
import com.hmc.zenkai.feature.skilltree.SkillTree;
import com.hmc.zenkai.feature.skilltree.SkillTreeNodes;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Rage Forms, lado servidor: evalúa UN acontecimiento para UN beneficiario y concede el resultado (flujo de
 * archivo/2026-11-08-rage-forms-diseno-v2.md §4.1). Es autoritativo: el cliente solo presenta. Los detectores ({@link RageListeners}) solo
 * dicen «pasó X a Y»; aquí se decide todo lo demás.
 *
 * Reglas:
 *  - Una forma se concede SOLO si es la siguiente válida de la cadena: el padre ya se posee. Nunca hay saltos.
 *  - Falta de requisito (maestría del padre) => PROGRESS al padre, no despertar. Una forma ya poseída recibe PROGRESS.
 *  - Un hecho se recompensa una vez ({@code eventId} en el anillo del jugador) y los de ALLY_* comparten un enfriamiento
 *    en tiempo del servidor, comprobado ANTES de recompensar: activo => RECORDED y no se concede nada.
 *  - La forma se REGALA sin cobrar MIND ({@code PlayerSkills#grantWaived}) y sin TP.
 *  - Todo hecho (salvo uno repetido) deja una entrada en la {@link RageChronicle}, también los RECORDED.
 */
public final class RageSystem {
    private RageSystem() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-Rage");

    /** Minutos de maestría -> delta crudo de {@code addFormMastery}: 0,5 %/min base; la propia forma aplica su mastery_gain. */
    static final float MASTERY_PER_MINUTE = 0.5f;

    /** Lo pone awaken() mientras cambia de forma, para que el oyente de «primera transformación» sepa que la vía es Rage. */
    static final ThreadLocal<Boolean> AWAKENING_IN_PROGRESS = new ThreadLocal<>();
    /** El trigger que causa el despertar en curso: la escena (pose, nota de sonido) cambia según cuál fue. Vacío fuera de Rage. */
    static final ThreadLocal<RageTrigger> AWAKENING_TRIGGER = new ThreadLocal<>();

    public static RageResult handle(ServerPlayer sp, RageTrigger trigger, String eventId) {
        return handle(sp, trigger, trigger.intensity(), eventId, null);
    }

    /** Con el nombre de a quién se refiere el hecho (aliado, mascota) para la crónica. */
    public static RageResult handle(ServerPlayer sp, RageTrigger trigger, String eventId, @Nullable String related) {
        return handle(sp, trigger, trigger.intensity(), eventId, related);
    }

    /** Evalúa, concede y SINCRONIZA al beneficiario si hubo cambio. */
    public static RageResult handle(ServerPlayer sp, RageTrigger trigger, RageIntensity intensity, @Nullable String eventId,
                                    @Nullable String related) {
        RageResult r = evaluate(sp, trigger, intensity, eventId, related);
        if (r != RageResult.RECORDED) PlayerLifeCycle.syncIfServer(sp);
        // El despertar cambia la forma activa: el propio jugador y quien lo mira deben verla (sin esto el cliente seguía en base).
        if (r == RageResult.AWAKENING) PlayerLifeCycle.syncFormToTrackersAndSelf(sp);
        // Aviso a los addons con el resultado YA aplicado (informativo; la forma despertada llega por FormChangedEvent).
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new com.hmc.zenkai.api.event.RageResolvedEvent(sp, trigger.name(), intensity.name(), r.name(), eventId));
        return r;
    }

    public static RageResult evaluate(ServerPlayer sp, RageTrigger trigger, RageIntensity intensity, @Nullable String eventId) {
        return evaluate(sp, trigger, intensity, eventId, null);
    }

    /** Lo que pasó con la forma tocada, para la crónica. */
    private static final class Trace {
        ResourceLocation form;
        float before = -1f, after = -1f;
        boolean cinematic;
        boolean cooldownIgnored;
    }

    /**
     * Igual que {@link #handle} pero sin sync: el estado queda aplicado y el resultado se devuelve (los gametests lo usan).
     * Todo hecho que llega aquí (salvo uno repetido) deja una entrada en la {@link RageChronicle}: también los que no
     * recompensan, que quedan como RECORDED.
     */
    public static RageResult evaluate(ServerPlayer sp, RageTrigger trigger, RageIntensity intensity, @Nullable String eventId,
                                      @Nullable String related) {
        if (!ServerConfig.RAGE_ENABLED.get()) return RageResult.RECORDED;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen() || att.isInOtherworld()) return RageResult.RECORDED;

        String key = eventId == null ? null : trigger.name() + ":" + eventId;
        if (key != null) {
            if (att.flags().hasRageEvent(key)) return RageResult.RECORDED;   // una muerte/rescate, una recompensa
            att.flags().addRageEvent(key);                                   // aunque el resultado sea RECORDED, no se reintenta
        }

        Trace trace = new Trace();
        RageResult result = resolve(sp, att, trigger, intensity, trace);
        record(sp, trigger, eventId, related, result, trace);
        return result;
    }

    private static RageResult resolve(ServerPlayer sp, PlayerStatsAttachment att, RageTrigger trigger, RageIntensity intensity,
                                      Trace trace) {
        long now = sp.level().getGameTime();
        if (trigger.consumesCooldown()) {
            long last = att.flags().getRageAllyEventTick();
            if (last >= 0 && now - last < ServerConfig.RAGE_ALLY_EVENT_COOLDOWN.get()) {
                trace.cooldownIgnored = true;
                return RageResult.RECORDED;   // ignorado por enfriamiento
            }
        }

        if (trigger.consumesMinorCooldown()) {
            long last = att.flags().getRageMinorEventTick();
            if (last >= 0 && now - last < ServerConfig.RAGE_MINOR_EVENT_COOLDOWN.get()) {
                trace.cooldownIgnored = true;
                return RageResult.RECORDED;   // ignorado por el enfriamiento corto de hechos menores
            }
        }

        Race race = att.getRace();
        PlayerFormAttachment forms = sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        List<RageAwakening> matching = new ArrayList<>();
        for (RageAwakening a : RageAwakeningManager.forRace(race)) if (a.matches(trigger, intensity)) matching.add(a);
        if (matching.isEmpty()) return RageResult.RECORDED;
        // La cadena en orden: la menos profunda primero (sin profundidad, por último). Entre las que no están en la cadena (ramas,
        // Legendarios, Beast) manda el número de antepasados: así «la más alta que posee» es de verdad la última de su línea
        // (Ikari, Legendario, Pleno) y no depende del orden en que se cargaron los archivos.
        matching.sort(Comparator.comparingInt((RageAwakening a) -> depth(race, a.form()))
                .thenComparingInt(a -> ancestors(a.form())));

        RageResult result = RageResult.RECORDED;
        RageAwakening lastOwned = null;
        for (RageAwakening a : matching) {
            // Sangre legendaria: SSJ2 y SSJ3 no se pueden LLEVAR puestas. Sin este salto, la muerte de un aliado le despertaba a un
            // Legendario un SSJ2 que no puede usar (o sumaba maestría a uno comprado); ahora el hecho sigue hacia su propia línea.
            if (SuperForms.legendaryBlocked(sp, a.form())) continue;
            if (SuperForms.unlocked(sp, a.form())) {
                // Ya la tiene: candidata a PROGRESS, salvo que su maestría esté al tope. Antes un hecho que no sumaba NADA (todas las
                // formas del acontecimiento al 100 %) se devolvía como PROGRESS y gastaba igualmente el enfriamiento de 30 min,
                // bloqueando después un despertar de verdad. Ahora, sin nada que sumar, queda RECORDED y el enfriamiento intacto.
                if (forms.getFormMastery(a.form()) < 100f) lastOwned = a;
                continue;
            }
            ResourceLocation parent = parentOf(a.form());
            if (parent != null && !FormIds.BASE.equals(parent) && !SuperForms.unlocked(sp, parent)) continue;   // sin saltos

            // Candidata: siguiente forma válida. Una entrada solo de progreso no desbloquea nada.
            if (!a.awakens()) continue;
            if (parent != null && !FormIds.BASE.equals(parent) && forms.getFormMastery(parent) < minParentMastery(a)) {
                result = progress(forms, parent, intensity, trace);   // falta maestría del padre: suma a la del padre
                break;
            }
            if (awaken(sp, att, forms, a, trace, trigger)) {
                result = RageResult.AWAKENING;
                break;
            }
        }
        // Ninguna candidata: si ya posee alguna de las formas del acontecimiento, el hecho suma a la más alta.
        if (result == RageResult.RECORDED && lastOwned != null) result = progress(forms, lastOwned.form(), intensity, trace);

        if (result != RageResult.RECORDED && trigger.consumesCooldown()) att.flags().setRageAllyEventTick(now);
        if (result != RageResult.RECORDED && trigger.consumesMinorCooldown()) att.flags().setRageMinorEventTick(now);
        return result;
    }

    private static void record(ServerPlayer sp, RageTrigger trigger, @Nullable String eventId, @Nullable String related,
                               RageResult result, Trace t) {
        RageChronicle.get(sp).add(new RageChronicle.Entry(
                eventId == null ? "" : eventId, trigger.name(), t.form == null ? "" : t.form.toString(), result.name(),
                result == RageResult.AWAKENING ? "rage" : "", related == null ? "" : related,
                sp.level().getGameTime(), t.before, t.after, t.cinematic, false, t.cooldownIgnored));
    }

    /** Maestría mínima del padre. Beast la sigue sacando de beast.unlock_mystic_mastery_percent (la opción de siempre). */
    private static double minParentMastery(RageAwakening a) {
        return FormIds.BEAST.equals(a.form()) ? ServerConfig.beastUnlockMysticMastery() : a.minParentMastery();
    }

    private static int depth(Race race, ResourceLocation form) {
        int d = SuperForms.depthOf(race, form);
        return d <= 0 ? Integer.MAX_VALUE : d;
    }

    /** Cuántos antepasados tiene la forma hasta la base (desempate de las que no están en la cadena comprable). */
    private static int ancestors(ResourceLocation form) {
        int n = 0;
        ResourceLocation p = parentOf(form);
        while (p != null && !FormIds.BASE.equals(p) && n < 32) {
            n++;
            p = parentOf(p);
        }
        return n;
    }

    private static @Nullable ResourceLocation parentOf(ResourceLocation form) {
        FormDef d = FormDef.get(form);
        return d == null ? null : d.parent();
    }

    private static RageResult progress(PlayerFormAttachment forms, ResourceLocation form, RageIntensity intensity, Trace trace) {
        float delta = (float) (intensity.minutes() * MASTERY_PER_MINUTE);
        if (delta <= 0f) return RageResult.RECORDED;
        trace.form = form;
        trace.before = forms.getFormMastery(form);
        forms.addFormMastery(form, delta);
        trace.after = forms.getFormMastery(form);
        return RageResult.PROGRESS;
    }

    /** Concede la forma por el mecanismo que la posee y la activa en el acto. false si no se sabe concederla. */
    private static boolean awaken(ServerPlayer sp, PlayerStatsAttachment att, PlayerFormAttachment forms, RageAwakening a,
                                  Trace trace, RageTrigger trigger) {
        ResourceLocation form = a.form();
        Race race = att.getRace();
        int depth = SuperForms.depthOf(race, form);
        if (FormIds.BEAST.equals(form)) {
            att.setBeastFury(true);                                  // Beast no es un nivel ni un nodo: es un flag
        } else if (depth > 0) {
            att.skills().grantWaived(SuperForms.SKILL, depth + 1);   // cadena mortal: nivel de super_forms, sin cobrar MIND
        } else if (SkillTreeNodes.find(race, form) != null) {
            SkillTree.grant(sp, form, 1, SkillTree.Via.EVENT);        // nodo propio (divinas, ramas)
        } else {
            LOGGER.warn("[Zenkai] Despertar de {} sin mecanismo de posesión conocido: se ignora", form);
            return false;
        }
        if (!SuperForms.unlocked(sp, form)) {
            LOGGER.warn("[Zenkai] {} concedida pero sigue sin estar desbloqueada (requisitos propios sin cumplir)", form);
            return false;
        }
        trace.form = form;
        trace.before = trace.after = forms.getFormMastery(form);
        // Despertar estando derribado (ally_dies_while_downed): la escena dura 6,3 s y la ventana de derribo solo 5 s, así que sin esto
        // el jugador moría por timeout en mitad de su propio despertar. Se levanta con el body de revivir de siempre.
        if (att.flags().isDowned()) com.hmc.zenkai.event.tick.DownedSystem.standUp(sp, att);
        // «Primera vez» se decide ANTES de cambiar de forma: el oyente de FormChangedEvent la marca como vista.
        trace.cinematic = !att.flags().hasSeenLongCinematic(form.toString());
        AWAKENING_IN_PROGRESS.set(Boolean.TRUE);
        AWAKENING_TRIGGER.set(trigger);
        try {
            forms.setFormId(sp, form);   // el despertar transforma en el acto; FormChangedEvent puede vetarlo, el desbloqueo queda
        } finally {
            AWAKENING_IN_PROGRESS.remove();
            AWAKENING_TRIGGER.remove();
        }
        sp.displayClientMessage(Component.translatable("message.zenkai.rage_awakening",
                Component.translatable(FormDef.nameKey(form))).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        return true;
    }
}
