package com.hmc.zenkai.core.skills;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Definición de una habilidad. Las definiciones VIVEN EN DATAPACK
 * (data/&lt;ns&gt;/zenkai_skills/&lt;id&gt;.json — ver SkillManager) y se sincronizan al
 * cliente con SkillSyncPacket. Esta clase es el snapshot vivo: en el server lo repone
 * cada /reload; en el cliente, lo que llegó por red.
 *
 * El EFECTO de cada habilidad se implementa donde corresponda consultando
 * PlayerStatsAttachment#skills().has(id).
 *
 * Claves de lang derivadas: skill.zenkai.&lt;id&gt; (nombre) y skill.zenkai.&lt;id&gt;.desc.
 */
public record SkillDef(String id, int tpCost, int mindReq, boolean purchasable) {

    private static volatile Map<String, SkillDef> REGISTRY = Map.of();

    /** Reemplaza el snapshot completo (reload del server o sync al cliente). */
    public static void replaceAll(Map<String, SkillDef> defs) {
        REGISTRY = Collections.unmodifiableMap(new LinkedHashMap<>(defs));
    }

    /** null si no existe. */
    public static SkillDef get(String id) { return REGISTRY.get(id); }

    /** En orden de carga (orden de la lista en la GUI). */
    public static Collection<SkillDef> all() { return REGISTRY.values(); }

    public String nameKey() { return "skill.zenkai." + id; }
    public String descKey() { return nameKey() + ".desc"; }

    public static final StreamCodec<FriendlyByteBuf, SkillDef> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SkillDef::id,
                    ByteBufCodecs.VAR_INT,     SkillDef::tpCost,
                    ByteBufCodecs.VAR_INT,     SkillDef::mindReq,
                    ByteBufCodecs.BOOL,        SkillDef::purchasable,
                    SkillDef::new);
}