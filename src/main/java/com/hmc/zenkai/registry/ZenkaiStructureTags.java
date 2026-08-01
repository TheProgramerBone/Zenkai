package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class ZenkaiStructureTags {
    private ZenkaiStructureTags() {}

    /** Estructuras con protección Zenkai: no-spawn hostil, bloques irrompibles, aviso al entrar. */
    public static final TagKey<Structure> PROTECTED = TagKey.create(
            Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "protected"));
}