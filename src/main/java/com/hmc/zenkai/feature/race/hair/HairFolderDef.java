package com.hmc.zenkai.feature.race.hair;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Metadatos de una carpeta de la biblioteca de pelo, declarados por un manifiesto de pack
 * ({@link HairPacks}) y sincronizados al cliente junto al catálogo. Una carpeta SIN manifiesto
 * (la de los peinados sueltos con "folder") sigue funcionando por convención: ícono en
 * &lt;ns&gt;:textures/gui/hair_folders/&lt;nombre&gt;.png y nombre en la clave hair_folder.&lt;ns&gt;.&lt;nombre&gt;.
 *
 * @param name nombre literal (sin él: clave de lang, y si tampoco existe, el nombre del id)
 * @param icon textura del ícono (sin ella: la de la convención)
 */
public record HairFolderDef(ResourceLocation id, @Nullable String name, @Nullable ResourceLocation icon, int sort) {

    public static final StreamCodec<FriendlyByteBuf, HairFolderDef> STREAM_CODEC =
            StreamCodec.of(HairFolderDef::write, HairFolderDef::read);

    private static void write(FriendlyByteBuf buf, HairFolderDef d) {
        buf.writeResourceLocation(d.id);
        buf.writeBoolean(d.name != null);
        if (d.name != null) buf.writeUtf(d.name, 128);
        buf.writeBoolean(d.icon != null);
        if (d.icon != null) buf.writeResourceLocation(d.icon);
        buf.writeVarInt(d.sort);
    }

    private static HairFolderDef read(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        String name = buf.readBoolean() ? buf.readUtf(128) : null;
        ResourceLocation icon = buf.readBoolean() ? buf.readResourceLocation() : null;
        return new HairFolderDef(id, name, icon, buf.readVarInt());
    }
}
