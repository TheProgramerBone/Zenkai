package com.hmc.zenkai.api.master;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.master.MasterService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servicios de maestro añadidos por addons. Registra un {@link MasterService} para el id de un maestro
 * ({@code kami}, {@code korin}, {@code kaio}, {@code toribot} o el de un maestro de otro addon) en
 * {@code FMLCommonSetupEvent} (dentro de {@code enqueueWork}) y aparecerá en la pestaña «Servicios» de su pantalla,
 * después de los propios del maestro. Los servicios de Zenkai y los de addons se resuelven igual: etiqueta y
 * disponibilidad las calcula el servidor, y {@code claim} revalida siempre (un cliente modificado puede mandar el clic
 * sin haber visto la pantalla). El maestro ya filtró distancia, poder y alineamiento ({@code MasterManager.check}).
 * Usa un id con tu namespace ({@code mi_addon:prueba}) para no chocar con los de Zenkai ni con otros addons: si dos
 * servicios del mismo maestro comparten id, solo funciona el primero.
 */
public final class MasterServiceRegistry {
    private MasterServiceRegistry() {}

    private static final Map<String, List<MasterService>> BY_MASTER = new ConcurrentHashMap<>();

    public static void register(String masterId, MasterService service) {
        List<MasterService> list = BY_MASTER.computeIfAbsent(masterId, k -> new CopyOnWriteArrayList<>());
        for (MasterService s : list) {
            if (s.id().equals(service.id())) {
                Zenkai.LOGGER.warn("[Zenkai] El maestro {} ya tiene un servicio con id {}: se ignora el nuevo.", masterId, service.id());
                return;
            }
        }
        list.add(service);
    }

    /** Los servicios de addons de ese maestro, en orden de registro (lista vacía si no hay). */
    public static List<MasterService> forMaster(String masterId) {
        List<MasterService> list = BY_MASTER.get(masterId);
        return list == null ? List.of() : new ArrayList<>(list);
    }
}
