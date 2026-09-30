package com.hmc.zenkai.feature.stats;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormIds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TransformHoldPacket(Action action, boolean active) implements CustomPacketPayload {

    public enum Action {
        TRANSFORM_HOLD, // B sostenido
        DETRANSFORM     // B toque
    }

    public static final Type<TransformHoldPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "transform_hold"));

    public static final StreamCodec<FriendlyByteBuf, TransformHoldPacket> STREAM_CODEC =
            StreamCodec.of(TransformHoldPacket::encode, TransformHoldPacket::decode);

    private static void encode(FriendlyByteBuf buf, TransformHoldPacket pkt) {
        buf.writeEnum(pkt.action());
        buf.writeBoolean(pkt.active());
    }

    private static TransformHoldPacket decode(FriendlyByteBuf buf) {
        Action a = buf.readEnum(Action.class);
        boolean v = buf.readBoolean();
        return new TransformHoldPacket(a, v);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TransformHoldPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            var stats = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
            var form  = sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get());

            if (!stats.isRaceChosen()) {
                form.resetAll();
                PlayerLifeCycle.syncFormToTrackersAndSelf(sp);
                return;
            }

            if (pkt.action() == Action.DETRANSFORM) {
                // Se pela por CAPAS, y en este orden: el kaioken va ENCIMA de la forma, así
                // que el primer toque lo quita y el segundo devuelve a base. Antes solo se
                // miraba el formId; como el kaioken no es una forma sino otra capa, estando
                // en base la condición era falsa y el toque no hacía absolutamente nada.
                //
                // Una forma de CONTENCIÓN (second_form/third_form/final_form del arcosiano —
                // FormDef.descendable()) NO debe bajar a Base con un simple toque: para eso
                // está el botón "Descender" de la rueda (WheelSelectPacket.descend()), que
                // además aplica su propio cooldown. Sin este guardado, un toque de esta tecla
                // hacía exactamente lo mismo que Descender pero sin pasar por él.
                FormDef activeDef = form.activeDef();
                boolean descendable = activeDef != null && activeDef.descendable();
                if (!form.dropKaioken() && !FormIds.BASE.equals(form.getFormId()) && !descendable) {
                    // Golden/Black/sus variantes "fifth" (y cualquier forma futura que cuelgue
                    // de una de contención) NO son limitadores: son Power Points por ENCIMA de
                    // la forma original de verdad (final_form). Perder Golden con un toque debe
                    // devolver a Final Form, no saltarse toda la cadena hasta Base de golpe —
                    // eso es justo lo que "Descender" hace para las de contención, y esto no es
                    // eso. Solo pasa hoy con el arcosiano: es la única raza con algo colgando de
                    // una forma descendable (decisión explícita: NO se generaliza a las demás
                    // razas, donde un toque sigue reseteando a Base de golpe como siempre).
                    ResourceLocation parentId = activeDef == null ? null : activeDef.parent();
                    if (parentId != null && hasDescendableAncestor(activeDef)) {
                        form.setFormId(sp, parentId);
                    } else {
                        form.forceBase();
                    }
                }
                PlayerLifeCycle.syncFormToTrackersAndSelf(sp);
                return;
            }

            // Hold de transformación. Las cancelaciones (carga de ki, turbo, carga de técnica
            // en curso) las hace ActionResolver por Matriz A y B: aquí no se repiten.
            // setTransformHeld lo escribe el resolver, no este handler. El ritual de Oozaru
            // (Base -> Oozaru -> Super Oozaru -> SSJ4) NO pasa por aquí para su primer tramo
            // (lo fuerza OozaruSystem vía PlayerFormAttachment.oozaruForced, sin tecla), pero
            // los tramos 2 y 3 SÍ son un hold normal de esta tecla — ver
            // PlayerFormAttachment.tickFormLadder / resolveNextForm.
            com.hmc.zenkai.feature.action.ActionResolver.setTransformHeld(sp, pkt.active());
            PlayerLifeCycle.syncFormToTrackersAndSelf(sp);
        });
    }

    /** ¿Algún antepasado (parent, parent del parent...) de esta forma es descendable()? Ver el
     *  bloque de comentarios de más arriba — hoy solo es true para lo que cuelga de
     *  final_form/third_form/second_form del arcosiano. */
    private static boolean hasDescendableAncestor(FormDef def) {
        ResourceLocation parent = def.parent();
        while (parent != null) {
            FormDef parentDef = FormDef.get(parent);
            if (parentDef == null) return false;
            if (parentDef.descendable()) return true;
            parent = parentDef.parent();
        }
        return false;
    }
}
