package com.hmc.zenkai.content.entity.master;

import com.hmc.zenkai.content.entity.ZenkaiMasterEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/** Kamisama. Enseña fly, ki_sense, ki_block, ki_control, meditation y run. */
public class KamiEntity extends ZenkaiMasterEntity {

    public KamiEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public String masterId() { return "kami"; }
}