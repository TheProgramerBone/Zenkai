package com.hmc.zenkai.content.entity.master;

import com.hmc.zenkai.content.entity.ZenkaiMasterEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/**
 * Korin. Enseña las habilidades de puño (ki_fist, ki_infuse) y, reparte semillas
 * del ermitaño: es la razón real por la que un jugador sube su torre.
 * El reparto de senzu NO vive aquí. Vive en KorinSenzuManager (tanda 2), por la misma razón
 * por la que los requisitos de admisión viven en MasterManager: la entidad es el punto de
 * contacto, no la regla. Si el reparto estuviera en mobInteract, /zenkai o cualquier otro
 * camino que quiera dar semillas tendría que reimplementar el contador diario.
 */
public class KorinEntity extends ZenkaiMasterEntity {

    public KorinEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public String masterId() { return "korin"; }
}