package com.hmc.zenkai.feature.capsule;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.blockentity.CapsuleBlockEntity;
import com.hmc.zenkai.registry.ModDataComponents;
import com.hmc.zenkai.registry.ModItems;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Lógica de servidor del control remoto de cápsulas (pedido del usuario 2026-11-25; decisiones por AskUser):
 *
 *   - VINCULAR: clic derecho sobre una cápsula colocada (vacía o llena). Otra vez sobre la misma la DESVINCULA y le quita
 *     el id (vuelve a apilarse con las nuevas). Hasta {@link CapsuleRemoteLinks#MAX_LINKS}; la nueva queda seleccionada.
 *   - ELEGIR: Shift + rueda del ratón con el control en la mano (CapsuleRemoteCyclePayload).
 *   - USAR: clic derecho al aire alterna abrir/recoger la seleccionada ({@link CapsuleBlockEntity#remoteToggle}).
 *   - OLVIDAR: Shift + clic derecho al aire quita la seleccionada del control (para una cápsula perdida o destruida, que ya
 *     no se puede clicar); la cápsula, si existe, conserva su id.
 *
 * VÍNCULO PERMANENTE (usuario 2026-11-26): la cápsula vinculada guarda su id para siempre, también como ítem. Al volver a
 * colocarla en cualquier sitio, {@link CapsuleLinkRegistry} la sitúa y el control la encuentra sin revincular. Si está en un
 * inventario, el control avisa de que no está colocada y NO borra el vínculo.
 *
 * Reglas: solo lo usa quien lo vinculó (el dueño queda fijado con el primer vínculo); misma dimensión y como mucho
 * {@code capsules.remote_range} bloques; NO carga chunks (si el de la cápsula no está cargado, avisa). El control no se salta
 * nada de la cápsula: la captura sigue siendo en un tick y esperando a que nadie quede en la zona, los claims se consultan
 * en nombre de quien lo usa y el contenido sigue en el archivo por cápsula.
 */
public final class CapsuleRemote {
    private CapsuleRemote() {}

    public static CapsuleRemoteLinks links(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.CAPSULE_REMOTE.get(), CapsuleRemoteLinks.EMPTY);
    }

    private static boolean notOwner(ServerPlayer sp, CapsuleRemoteLinks l) {
        return l.owner().isPresent() && !l.owner().get().equals(sp.getUUID());
    }

    /** Clic derecho del control sobre una cápsula colocada. */
    public static void link(ServerPlayer sp, ItemStack stack, CapsuleBlockEntity be) {
        CapsuleRemoteLinks l = links(stack);
        if (notOwner(sp, l)) {
            say(sp, Component.translatable("message.zenkai.capsule_remote.not_owner"));
            return;
        }
        UUID existing = be.linkId();
        int idx = existing == null ? -1 : l.indexOf(existing);
        if (idx >= 0) {
            l = l.remove(idx);
            stack.set(ModDataComponents.CAPSULE_REMOTE.get(), l);
            be.clearLinkId();
            say(sp, Component.translatable("message.zenkai.capsule_remote.unlinked", label(be.customName(), be.tier().ordinal())));
            return;
        }
        // Antes de darle id: con el control lleno no se toca la cápsula (si no, se quedaría con un id que no usa nadie).
        if (l.links().size() >= CapsuleRemoteLinks.MAX_LINKS) {
            say(sp, Component.translatable("message.zenkai.capsule_remote.full", ZenkaiNumbers.screen(CapsuleRemoteLinks.MAX_LINKS)));
            return;
        }
        UUID id = be.ensureLinkId();
        if (id == null) return;
        l = l.withOwner(sp.getUUID()).add(new CapsuleRemoteLinks.Link(id,
                GlobalPos.of(be.getLevel().dimension(), be.getBlockPos()), be.customName(), be.tier().ordinal()));
        stack.set(ModDataComponents.CAPSULE_REMOTE.get(), l);
        sp.level().playSound(null, be.getBlockPos(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4F, 1.6F);
        say(sp, Component.translatable("message.zenkai.capsule_remote.linked", label(be.customName(), be.tier().ordinal()),
                ZenkaiNumbers.screen(l.links().size()), ZenkaiNumbers.screen(CapsuleRemoteLinks.MAX_LINKS)));
    }

    /** Shift + rueda: cambia la seleccionada y dice cuál es. */
    public static void cycle(ServerPlayer sp, ItemStack stack, int dir) {
        CapsuleRemoteLinks l = links(stack);
        if (l.links().isEmpty()) {
            say(sp, Component.translatable("message.zenkai.capsule_remote.no_links"));
            return;
        }
        l = l.cycle(dir);
        stack.set(ModDataComponents.CAPSULE_REMOTE.get(), l);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.25F, 1.8F);
        say(sp, selectedLine(sp, l));
    }

    /** Shift + clic derecho al aire: quita la seleccionada del control (la cápsula, si existe, conserva su id). */
    public static void forgetSelected(ServerPlayer sp, ItemStack stack) {
        CapsuleRemoteLinks l = links(stack);
        Optional<CapsuleRemoteLinks.Link> cur = l.current();
        if (cur.isEmpty()) {
            say(sp, Component.translatable("message.zenkai.capsule_remote.no_links"));
            return;
        }
        if (notOwner(sp, l)) {
            say(sp, Component.translatable("message.zenkai.capsule_remote.not_owner"));
            return;
        }
        stack.set(ModDataComponents.CAPSULE_REMOTE.get(), l.remove(l.selectedIndex()));
        say(sp, Component.translatable("message.zenkai.capsule_remote.forgotten", label(cur.get().name(), cur.get().tier())));
    }

    /** Clic derecho al aire: abre o recoge la seleccionada. */
    public static void toggle(ServerPlayer sp, ItemStack stack) {
        CapsuleRemoteLinks l = links(stack);
        Optional<CapsuleRemoteLinks.Link> cur = l.current();
        if (cur.isEmpty()) {
            say(sp, Component.translatable("message.zenkai.capsule_remote.no_links"));
            return;
        }
        if (notOwner(sp, l)) {
            say(sp, Component.translatable("message.zenkai.capsule_remote.not_owner"));
            return;
        }
        CapsuleRemoteLinks.Link link = cur.get();
        // Dónde está colocada AHORA (registro); si no figura, la última posición conocida (cápsulas de antes del registro).
        GlobalPos where = CapsuleLinkRegistry.get(sp.server).where(link.id());
        boolean registered = where != null;
        if (where == null) where = link.pos();
        if (!where.dimension().equals(sp.level().dimension())) {
            say(sp, Component.translatable("message.zenkai.capsule_remote.other_dimension", label(link.name(), link.tier())));
            return;
        }
        BlockPos pos = where.pos();
        double range = ServerConfig.capsuleRemoteRange();
        double dist = Math.sqrt(sp.distanceToSqr(pos.getCenter()));
        ServerLevel level = sp.serverLevel();
        if (dist > range || !level.isLoaded(pos)) {
            say(sp, Component.translatable("message.zenkai.capsule_remote.out_of_range", label(link.name(), link.tier()),
                    ZenkaiNumbers.screen((int) Math.round(dist)), ZenkaiNumbers.screen((int) range)));
            return;
        }
        CapsuleBlockEntity be = find(level, pos, link.id());
        if (be == null) {
            // No está en el suelo: guardada como ítem (en un inventario o un cofre), o destruida. El vínculo se conserva;
            // si se perdió, Shift + clic derecho al aire lo quita del control.
            say(sp, Component.translatable(registered ? "message.zenkai.capsule_remote.lost"
                    : "message.zenkai.capsule_remote.not_placed", label(link.name(), link.tier())));
            return;
        }
        CapsuleRemoteLinks.Link fresh = new CapsuleRemoteLinks.Link(link.id(),
                GlobalPos.of(level.dimension(), be.getBlockPos()), be.customName(), be.tier().ordinal());
        if (!fresh.equals(link)) stack.set(ModDataComponents.CAPSULE_REMOTE.get(), l.replace(l.selectedIndex(), fresh));
        level.playSound(null, sp.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.5F, 1.5F);
        be.remoteToggle(sp);
    }

    /**
     * La cápsula con ese id en esa posición o un bloque arriba/abajo (abrir la sube uno, recoger la baja uno; ver
     * CapsuleBlockEntity; con el registro casi siempre acierta a la primera). Solo mira chunks ya cargados.
     */
    public static @Nullable CapsuleBlockEntity find(ServerLevel level, BlockPos pos, UUID id) {
        for (BlockPos p : new BlockPos[]{pos, pos.above(), pos.below()}) {
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof CapsuleBlockEntity be && id.equals(be.linkId())) {
                return be;
            }
        }
        return null;
    }

    /** «[2/3] Casa de playa · 45 m» (la distancia solo en la misma dimensión). */
    public static Component selectedLine(ServerPlayer sp, CapsuleRemoteLinks l) {
        CapsuleRemoteLinks.Link link = l.current().orElseThrow();
        Component where = link.pos().dimension().equals(sp.level().dimension())
                ? Component.translatable("message.zenkai.capsule_remote.distance",
                        ZenkaiNumbers.screen((int) Math.round(Math.sqrt(sp.distanceToSqr(link.pos().pos().getCenter())))))
                : Component.translatable("message.zenkai.capsule_remote.elsewhere");
        return Component.translatable("message.zenkai.capsule_remote.selected",
                ZenkaiNumbers.screen(l.selectedIndex() + 1), ZenkaiNumbers.screen(l.links().size()),
                label(link.name(), link.tier()), where);
    }

    /** Nombre puesto por el jugador o, si no tiene, el del rango («Cápsula de cobre»). */
    public static Component label(String name, int tier) {
        if (!name.isBlank()) return Component.literal(name).withStyle(ChatFormatting.AQUA);
        return Component.translatable(ModItems.capsule(CapsuleTier.byOrdinal(tier)).getDescriptionId())
                .withStyle(ChatFormatting.AQUA);
    }

    private static void say(ServerPlayer sp, Component msg) {
        sp.displayClientMessage(msg, true);
    }
}
