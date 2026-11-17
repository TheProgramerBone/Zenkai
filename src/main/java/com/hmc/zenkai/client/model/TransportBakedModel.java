package com.hmc.zenkai.client.model;

import com.hmc.zenkai.content.blockentity.TransportBlockEntity;
import com.hmc.zenkai.feature.transport.Look;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.CompositeModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Modelo de los cables, tuberías y tanques de transporte. El JSON es un modelo COMPUESTO de
 * NeoForge con una pieza por cada aspecto posible de cada cara (datagen, ModBlockStateProvider);
 * este envoltorio decide en cada dibujo QUÉ piezas se ven según el aspecto que el block entity manda
 * como ModelData. Así el bloque tiene un solo blockstate (la técnica de Mekanism con su
 * TransmitterBakedModel) en vez de una propiedad por cara.
 *
 * Nombres de pieza: cable = «core» + «&lt;cara&gt;_&lt;normal|push|pull&gt;»; tanque = «face_&lt;cara&gt;_&lt;0-15&gt;»
 * (textura conectada: bordes del grupo multibloque) + «&lt;cara&gt;_&lt;push|pull|none&gt;». Mientras no llegue el
 * dato (primer fotograma) el cable enseña solo su núcleo y el tanque se ve suelto.
 */
public class TransportBakedModel extends BakedModelWrapper<CompositeModel.Baked> {

    private final boolean cable;

    public TransportBakedModel(CompositeModel.Baked original, boolean cable) {
        super(original);
        this.cable = cable;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                             @NotNull RandomSource rand, @NotNull ModelData data,
                                             @Nullable RenderType renderType) {
        List<BakedQuad> out = new ArrayList<>();
        if (cable) {
            addPart(out, "core", state, side, rand, data, renderType);
        } else {
            // Textura conectada: la variante de cada cara según los bordes del grupo (solo, si aún no llegó).
            Integer ct = data.get(TransportBlockEntity.CT_PROPERTY);
            int ctBits = ct != null ? ct : TransportBlockEntity.CT_ALONE;
            for (Direction d : Direction.values()) {
                int variant = (ctBits >> (4 * d.get3DDataValue())) & 15;
                addPart(out, "face_" + d.getName() + "_" + variant, state, side, rand, data, renderType);
            }
        }
        Integer bits = data.get(TransportBlockEntity.LOOK_PROPERTY);
        if (bits == null) return out;
        for (Direction d : Direction.values()) {
            Look look = Look.unpack(bits, d.get3DDataValue());
            boolean visible = cable
                    ? look == Look.NORMAL || look == Look.PUSH || look == Look.PULL
                    : look == Look.PUSH || look == Look.PULL || look == Look.NONE;
            if (visible) addPart(out, d.getName() + "_" + look.part(), state, side, rand, data, renderType);
        }
        return out;
    }

    private void addPart(List<BakedQuad> out, String name, @Nullable BlockState state, @Nullable Direction side,
                         RandomSource rand, ModelData data, @Nullable RenderType renderType) {
        BakedModel part = originalModel.getPart(name);
        if (part == null) return;
        // Cada pieza en SU capa (las placas del tanque van en recorte): un modelo simple no filtra por
        // renderType, así que se filtra aquí como hace CompositeModel.Baked; si no, la placa
        // transparente saldría también en la capa sólida, con fondo negro.
        if (renderType != null && state != null && !part.getRenderTypes(state, rand, data).contains(renderType)) return;
        out.addAll(part.getQuads(state, side, rand, data, renderType));
    }
}
