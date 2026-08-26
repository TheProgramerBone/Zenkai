package com.hmc.zenkai.feature.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recuerda el ÚLTIMO {@link DamageSource} que dejó el body de un jugador a 0. Hace falta porque
 * la muerte de un jugador Zenkai NO pasa por hurt()/die() en ese instante: CombatZenkaiHooks.
 * applyToZenkaiVictim anula el daño real del golpe ({@code e.setNewDamage(0)}) y lo manda a
 * "derribado" en su lugar (ver DownedSystem.handleBodyDepleted); la muerte de verdad llega hasta
 * 5 s después, si nadie lo cura, cuando DownedSystem.handleDowned por fin llama a
 * {@code sp.die(...)}. Para entonces el DamageSource del golpe que lo tumbó ya se había perdido
 * — por eso esa llamada solo podía usar {@code damageSources().generic()}, y el mensaje de
 * muerte era siempre genérico sin importar si murió por un ataque de ki, una explosión o una
 * técnica física. record()/take() cierran ese hueco sin tocar el pipeline de daño en sí.
 */
public final class DeathCauseTracker {
    private DeathCauseTracker() {}

    private static final Map<UUID, DamageSource> LAST = new ConcurrentHashMap<>();

    /** Llamar cada vez que un golpe deja (o mantiene) el body en 0 — incluido un golpe de
     *  gracia mientras ya está derribado, que así se convierte en la causa "oficial" de la
     *  muerte si el jugador no se recupera a tiempo. */
    public static void record(ServerPlayer sp, DamageSource source) {
        LAST.put(sp.getUUID(), source);
    }

    /** Consume la causa guardada (o null si no hay ninguna: /kill, ahogamiento, el vacío...).
     *  Se retira al leerla — la próxima muerte de este jugador necesita su propia causa, no
     *  arrastrar la de la partida anterior. */
    public static DamageSource take(UUID id) {
        return LAST.remove(id);
    }

    /** Llamar al desconectar, igual que CombatZenkaiHooks.forgetAttackScale: sin esto queda una
     *  entrada por cada jugador que haya entrado al servidor alguna vez. */
    public static void forget(UUID id) {
        LAST.remove(id);
    }
}
