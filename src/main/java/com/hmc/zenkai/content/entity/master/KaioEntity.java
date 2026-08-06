package com.hmc.zenkai.content.entity.master;

import com.hmc.zenkai.content.entity.ZenkaiMasterEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/** Kaiosama. Enseña kaioken. Vive en el Otherworld, así que su PL requerido puede ser alto:
 *  llegar hasta él ya es la mitad del filtro. */
public class KaioEntity extends ZenkaiMasterEntity {

    public KaioEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public String masterId() { return "kaio"; }
}