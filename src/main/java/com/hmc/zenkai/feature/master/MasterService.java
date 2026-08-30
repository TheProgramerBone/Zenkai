package com.hmc.zenkai.feature.master;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Algo que un maestro ofrece desde la pestaña "Servicios" de MasterScreen, aparte de lo que
 * enseña (Skills/Técnicas). A diferencia de una habilidad, un servicio no tiene nivel ni
 * coste en TP: es una acción puntual (Kami: crecer/quitar la cola; Korin: la semilla del
 * día; Kaiosama: las pesas de entrenamiento), gateada por lo que cada maestro decida en su
 * propio claim().
 *
 * ZenkaiMasterEntity.services() es quien expone estas instancias por maestro (una lista fija
 * por entidad, ver esa clase); MasterManager.check/findNearby siguen siendo el único embudo
 * de admisión — un MasterService nunca revalida distancia/PL/alineamiento por su cuenta.
 */
public interface MasterService {

    /** Id estable dentro de ESE maestro (no hace falta que sea único entre maestros
     *  distintos: el packet que reclama ya lleva el masterId aparte). */
    String id();

    /** Etiqueta YA resuelta server-side (p. ej. "Semilla del día (3/5)") — el cliente nunca
     *  vuelve a calcular nada, solo la enseña. Se recalcula en cada apertura/refresco. */
    Component label(ServerPlayer sp);

    /** Intenta usarlo. true si tuvo efecto (ya se encargó de avisar al jugador por chat o
     *  action bar); false si no había nada que dar (p. ej. cupo diario agotado). */
    boolean claim(ServerPlayer sp);
}
