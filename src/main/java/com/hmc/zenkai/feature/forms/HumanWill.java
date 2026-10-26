package com.hmc.zenkai.feature.forms;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.entity.ZenkaiDefaultNPC;
import com.hmc.zenkai.feature.combat.ComboTracker;
import com.hmc.zenkai.feature.party.PartyManager;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * «Voluntad colectiva» de la forma divina del humano (human_god; antes estaba en la Legendaria), decidida con el usuario (2026-10-25):
 * el poder crece con los ALIADOS cercanos (miembros de la party, mascotas propias, maestros y NPC amistosos) y, EN
 * SOLITARIO, con un «impulso» que se acumula al encadenar golpes sin ser golpeado (la cadena de {@link ComboTracker},
 * que ya se rompe al recibir daño). Es un bono ADITIVO al multiplicador de stats (MasteryEffects.formStatFactor), con
 * tope. Solo servidor: el resultado viaja a los clientes dentro del multiplicador de stats que FormSystem sincroniza.
 */
public final class HumanWill {
    private HumanWill() {}

    /** Aliados contados por jugador, refrescados cada segundo (la búsqueda de entidades no hace falta cada tick). */
    private record Allies(long tick, int count) {}

    private static final Map<UUID, Allies> CACHE = new HashMap<>();
    private static final int REFRESH_TICKS = 20;

    /** ¿Lleva puesta la forma divina del humano (human_god)? */
    public static boolean wearing(Player p) {
        return FormIds.HUMAN_GOD.equals(
                p.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).getFormId());
    }

    /** Bono aditivo de stats (0.25 = +25 %) de este jugador ahora mismo; 0 si no lleva la forma o no es el servidor. */
    public static double bonus(Player p) {
        if (!(p instanceof ServerPlayer sp) || !wearing(p)) return 0.0;
        int allies = allies(sp);
        double raw = allies > 0
                ? allies * ServerConfig.humanWillBonusPerAlly()
                : ComboTracker.currentHits(sp) * ServerConfig.humanWillBonusPerHit();
        return Math.min(raw, ServerConfig.humanWillMaxBonus());
    }

    /** Aliados cercanos (sin contarse a sí mismo). */
    public static int allies(ServerPlayer sp) {
        long now = sp.level().getGameTime();
        Allies c = CACHE.get(sp.getUUID());
        if (c != null && now - c.tick() < REFRESH_TICKS) return c.count();
        int n = count(sp);
        CACHE.put(sp.getUUID(), new Allies(now, n));
        return n;
    }

    /** Olvida al jugador (desconexión). */
    public static void forget(UUID id) {
        CACHE.remove(id);
    }

    private static int count(ServerPlayer sp) {
        double r = ServerConfig.humanWillAllyRadius();
        var area = sp.getBoundingBox().inflate(r);
        var party = PartyManager.get(sp.server).partyOf(sp.getUUID());
        int n = 0;
        for (LivingEntity e : sp.level().getEntitiesOfClass(LivingEntity.class, area, x -> x != sp && x.isAlive())) {
            if (e.distanceToSqr(sp) > r * r) continue;
            if (e instanceof ServerPlayer other) {
                if (party != null && party.members.contains(other.getUUID())) n++;
            } else if (e instanceof OwnableEntity pet) {
                if (sp.getUUID().equals(pet.getOwnerUUID())) n++;
            } else if (e instanceof ZenkaiDefaultNPC || e instanceof Villager || e instanceof IronGolem) {
                n++;
            }
        }
        return n;
    }
}
