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

    @Override
    public String masterId() { return "toribot"; }
}
