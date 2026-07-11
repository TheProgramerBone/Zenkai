package com.hmc.zenkai.core.network.feature.player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Submódulo de PlayerStatsAttachment: habilidades desbloqueadas (ids de SkillDef).
 * Viaja dentro del save/load del attachment -> persiste, se copia al morir y se sincroniza
 * al cliente por el mismo canal que el resto de stats (PlayerLifeCycle.syncIfServer).
 */
public final class PlayerSkills {

    private final Set<String> unlocked = new LinkedHashSet<>();

    public boolean has(String id)     { return unlocked.contains(id); }
    public void unlock(String id)     { unlocked.add(id); }
    public Set<String> all()          { return Collections.unmodifiableSet(unlocked); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (String id : unlocked) list.add(StringTag.valueOf(id));
        tag.put("unlocked", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        unlocked.clear();
        if (tag.contains("unlocked")) {
            ListTag list = tag.getList("unlocked", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) unlocked.add(list.getString(i));
        }
    }
}