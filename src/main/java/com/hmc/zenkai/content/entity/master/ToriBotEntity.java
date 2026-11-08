package com.hmc.zenkai.content.entity.master;

import com.hmc.zenkai.content.entity.ZenkaiMasterEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/**
 * Tori-Bot. No es un maestro canónico de la serie con su propio templo o planeta — es el
 * catch-all de "cosas sin maestro adecuado" entre Kami/Korin/King Kai: enseña god_ki,
 * instant_transmission y potential_unlock, mecánicas exóticas de final de partida que ningún
 * personaje concreto del elenco principal enseña en el manga/anime. Por eso, a diferencia de
 * los otros tres, su MasterDef (zenkai_masters/toribot.json) no filtra por alineamiento: es una
 * máquina, no juzga.
 */
public class ToriBotEntity extends ZenkaiMasterEntity {

    public ToriBotEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    /** Mitad de tamaño (atributo SCALE, no el modelo): también encoge su hitbox y su retrato. */
    public static final double SCALE = 0.5;

    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes() {
        return ZenkaiMasterEntity.createAttributes()
                .add(net.minecraft.world.entity.ai.attributes.Attributes.SCALE, SCALE);
    }

    /** Un Tori-Bot guardado antes del cambio trae SCALE 1.0 en su NBT y pisaría el valor del registro. */
    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        var scale = this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
        if (scale != null) scale.setBaseValue(SCALE);
    }

    @Override
    public String masterId() { return "toribot"; }

    /** Audiencia divina y prueba de God Ki (skill tree F5): los da cualquier maestro de la lista
     *  "masters" de god_ki.json, ver GodKi.services. */
    @Override
    protected java.util.List<com.hmc.zenkai.feature.master.MasterService> services() {
        return com.hmc.zenkai.feature.skilltree.GodKi.services(masterId());
    }
}
