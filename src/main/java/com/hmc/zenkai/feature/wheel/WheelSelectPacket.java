package com.hmc.zenkai.feature.wheel;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.advancement.ZenkaiTriggers;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.forms.FormRegistry;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.skills.SkillToggles;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SuperForms;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: selección hecha en el menú radial. Un solo packet para lo que la rueda elige
 * (kind + carga útil en texto): añadir una categoría no obliga a registrar otro payload.
 * La rueda NO transforma. FORM fija la forma OBJETIVO y KAIOKEN alterna el interruptor;
 * subir de escalón es cosa de la tecla de transformar.
 * La rueda del cliente ya pinta en gris lo que no puedes usar, pero eso es cosmética:
 * se revalida aquí.
 */
public record WheelSelectPacket(String kind, String value) implements CustomPacketPayload {

    public static final Type<WheelSelectPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "wheel_select"));

    public static final StreamCodec<FriendlyByteBuf, WheelSelectPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, WheelSelectPacket::kind,
                    ByteBufCodecs.STRING_UTF8, WheelSelectPacket::value,
                    WheelSelectPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(WheelSelectPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            PlayerStatsAttachment st = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
            PlayerFormAttachment fm = sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
            if (st == null || fm == null || !st.isRaceChosen()) return;

            // Tutorial de "mantén X para el menú radial": dispara en cada uso, no solo la
            // primera vez, mismo criterio que COMBAT_STANCE en CombatModeServerState.
            ZenkaiTriggers.MILESTONE.get().trigger(sp, ZenkaiTriggers.Kinds.WHEEL_USED);

            boolean changed = switch (pkt.kind()) {
                case "FORM"    -> selectForm(sp, st.getRace(), fm, pkt.value());
                case "KAIOKEN" -> toggleKaioken(sp, fm);
                // flip ya sincroniza los STATS (ahí viven los interruptores); devolvemos
                // false para no disparar además el sync de FORMA, que aquí no cambió.
                case "TOGGLE"  -> {
                    SkillToggles.flip(sp, pkt.value()); yield false;}
                case "DESCEND" -> descend(sp, st, fm);
                // Cambia PlayerVisualAttachment, no la forma: sincroniza aparte (mismo criterio
                // que TOGGLE arriba) y devuelve false para no disparar además el sync de forma.
                case "TAIL_MODE" -> { toggleTailStyle(sp, st); yield false; }
                default        -> false;
            };
            if (changed) PlayerLifeCycle.syncFormIfServer(sp);
        });
    }

    /** Fija la forma objetivo. No transforma: solo dice hacia dónde apunta la tecla. */
    private static boolean selectForm(ServerPlayer sp, Race race,
                                      PlayerFormAttachment fm, String value) {
        ResourceLocation target = ResourceLocation.tryParse(value);
        if (target == null || race == null) return false;

        if (!FormIds.BASE.equals(target)) {
            if (!FormRegistry.isAllowed(race, target)) return false;
            FormDef def = FormRegistry.get(target);
            if (def == null) return false;
            // Formas fuera de la escalera normal (oozaru/super_oozaru: kind divine, parent
            // null, activadas por su propio sistema) declaran wheel_selectable=false. Sin este
            // candado, isAllowed()+unlocked() (unlocked() da true de oficio en cualquier forma
            // que no esté en la cadena de super_forms) las dejarían pasar aunque la rueda del
            // cliente nunca las enseñe.
            if (!def.wheelSelectable()) return false;
            if (!SuperForms.unlocked(sp, target)) return false;
        }
        if (target.equals(fm.getSelectedForm())) return false;

        fm.setSelectedForm(target);

        Component name = FormIds.BASE.equals(target)
                ? Component.translatable("wheel.zenkai.base")
                : Component.translatable("form.zenkai." + target.getPath());
        sp.displayClientMessage(
                Component.translatable("message.zenkai.form_selected", name), true);
        return true;
    }

    /**
     * Alterna el interruptor. Apagarlo NO baja el kaioken activo: solo devuelve la tecla de
     * transformar a la escalera de formas. El escalón se conserva.
     */
    private static boolean toggleKaioken(ServerPlayer sp, PlayerFormAttachment fm) {
        if (SkillEffects.level(sp, "kaioken") <= 0) return false;
        fm.setKaiokenSwitch(!fm.isKaiokenSwitch());
        return true;
    }

    /**
     * Alterna el estilo de la cola ("loose"/"waist"). Revalida raza+cola aquí (nunca fiarse
     * de lo que WheelMenu decidió mostrar en el cliente): un cliente modificado no puede
     * ponerle estilo de cola a alguien sin cola o de otra raza.
     */
    private static void toggleTailStyle(ServerPlayer sp, PlayerStatsAttachment st) {
        if (st.getRace() != Race.SAIYAN || !st.hasTail()) return;
        PlayerVisualAttachment vis = sp.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        boolean waist = "waist".equals(vis.getTailStyleId());
        vis.setTailStyleId(waist ? "loose" : "waist");
        PlayerLifeCycle.syncVisualToTrackersAndSelf(sp);
    }

    /** Cooldown de "Descender": 50 ticks (~2.5s), server-side, contra spam desde la rueda. */
    private static final long DESCEND_COOLDOWN_TICKS = 50;

    /**
     * "Descender": mismo efecto que el tap-to-revert de siempre (forceBase()), solo que
     * expuesto también como botón de rueda, con cooldown. Revalida descendable() aquí (no
     * basta con que WheelMenu no lo enseñe): un cliente modificado no puede tumbar Golden/Black
     * con esto. Si estaba forzando (powerPercent > 100), también lo baja a 100 de golpe — al
     * volver a Base ya no hay forma que sostenga forzar más allá del techo genérico.
     */
    private static boolean descend(ServerPlayer sp, PlayerStatsAttachment st, PlayerFormAttachment fm) {
        FormDef active = fm.activeDef();
        if (active == null || !active.descendable()) return false;

        long now = sp.level().getGameTime();
        if (now - st.getLastDescendTick() < DESCEND_COOLDOWN_TICKS) return false;

        fm.forceBase();
        st.setLastDescendTick(now);
        // Vuelta a Base: el techo ya no es el de la forma (bonus perdido), es 100 sin más.
        if (st.getPowerPercent() > 100 && st.setPowerPercent(100, 100)) {
            PlayerLifeCycle.syncIfServer(sp);
        }
        return true;
    }
}