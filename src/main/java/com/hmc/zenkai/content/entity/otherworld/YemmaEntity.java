package com.hmc.zenkai.content.entity.otherworld;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.entity.ZenkaiDefaultNPC;
import com.hmc.zenkai.feature.dialogue.DialogueService;
import com.hmc.zenkai.feature.dialogue.DialogueSpeaker;
import com.hmc.zenkai.feature.dialogue.OpenDialoguePayload;
import com.hmc.zenkai.feature.player.CharacterReset;
import com.hmc.zenkai.registry.ModGameRules;
import com.hmc.zenkai.feature.player.OtherworldManager;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class YemmaEntity extends ZenkaiDefaultNPC implements DialogueSpeaker {

    public YemmaEntity(EntityType<? extends ZenkaiDefaultNPC> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    /** Hacia dónde mira Emma en reposo (sin jugador cerca). */
    private static final float REST_YAW = 180.0f;
    /** Rango en el que "mira al jugador"; fuera de esto, vuelve a REST_YAW. */
    private static final float LOOK_RANGE = 5.0f;

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, LOOK_RANGE));
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;   // una sola réplica por clic
        if (!this.level().isClientSide && player instanceof ServerPlayer sp) {
            PlayerStatsAttachment stats = PlayerStatsAttachment.get(sp);
            Component name = sp.getDisplayName();
            List<OpenDialoguePayload.Choice> choices = new ArrayList<>();
            Component page;

            if (!stats.isInOtherworld()) {
                // Vivo: Enma lo despacha con desgana, y no siempre con la misma frase.
                page = line("alive", 3, name);
            } else if (stats.isHardcoreDeath()) {
                page = line("hardcore", 2, name);
            } else if (ServerConfig.PERMADEATH_ENABLED.get()) {
                // Muerte permanente: Yemma NO revive por temporizador. Las salidas son el deseo de las Esferas,
                // renunciar o la visita de Uranai Baba (fases siguientes).
                page = line("permadeath", 3, name);
                // Sin poder elegir raza (gamerule zenkai_allowRaceSelection apagada) la reencarnación no terminaría nunca.
                if (ModGameRules.allowRaceSelection(sp.server)) {
                    choices.add(new OpenDialoguePayload.Choice("renounce",
                            Component.translatable("dialogue.zenkai.yemma.choice.renounce")));
                }
            } else {
                long remaining = remainingTicks(stats);
                if (remaining > 0) {
                    page = line("wait", 3, name, formatTime(remaining));
                } else {
                    page = line("revive", 2, name);
                    choices.add(new OpenDialoguePayload.Choice("revive",
                            Component.translatable("dialogue.zenkai.yemma.choice.revive")));
                }
            }
            choices.add(DialogueService.leave());
            DialogueService.open(sp, this, List.of(page), choices);
            talkSound();
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    /** Una de las variantes de la situación ({@code dialogue.zenkai.yemma.<situación>.<n>}). */
    private Component line(String situation, int variants, Object... args) {
        int n = 1 + this.random.nextInt(variants);
        return Component.translatable("dialogue.zenkai.yemma." + situation + "." + n, args);
    }

    /** Voz grave y pausada, a la altura de su cara. */
    private void talkSound() {
        this.level().playSound(null, this.getX(), this.getEyeY(), this.getZ(),
                SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.9f, 0.55f);
    }

    private long remainingTicks(PlayerStatsAttachment stats) {
        return OtherworldManager.REVIVE_DELAY_TICKS - (this.level().getGameTime() - stats.getOtherworldSince());
    }

    /** Opción elegida en la pantalla. El id lo manda el cliente: se vuelve a comprobar el estado entero. */
    @Override
    public void onDialogueChoice(ServerPlayer sp, String choiceId) {
        PlayerStatsAttachment stats = PlayerStatsAttachment.get(sp);
        if ("renounce".equals(choiceId) || "renounce_confirm".equals(choiceId)) {
            // Solo en muerte permanente, muerto y sin cierre hardcore (ese sigue su propio camino).
            if (!stats.isInOtherworld() || stats.isHardcoreDeath() || !ServerConfig.PERMADEATH_ENABLED.get()) return;
            if (!ModGameRules.allowRaceSelection(sp.server)) return;
            if ("renounce".equals(choiceId)) {
                // Irreversible: segunda pantalla de confirmación explícita.
                DialogueService.open(sp, this, List.of(Component.translatable("dialogue.zenkai.yemma.renounce.confirm",
                                sp.getDisplayName())),
                        List.of(new OpenDialoguePayload.Choice("renounce_confirm",
                                        Component.translatable("dialogue.zenkai.yemma.choice.renounce_confirm")),
                                DialogueService.leave()));
                talkSound();
            } else {
                CharacterReset.perform(sp);
            }
            return;
        }
        if (!"revive".equals(choiceId)) return;
        if (!stats.isInOtherworld() || stats.isHardcoreDeath() || ServerConfig.PERMADEATH_ENABLED.get()) return;
        if (remainingTicks(stats) > 0) return;
        OtherworldManager.revive(sp);
    }

    /** Ticks restantes -> "M:SS". */
    private static String formatTime(long ticks) {
        long totalSeconds = Math.max(0L, ticks) / 20L;
        return String.format("%d:%02d", totalSeconds / 60L, totalSeconds % 60L);
    }

        public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0f)
                .add(Attributes.MOVEMENT_SPEED, 0.0f);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) return;

        Player nearest = this.level().getNearestPlayer(this, LOOK_RANGE);
        if (nearest == null) {
            this.setYRot(REST_YAW);
            this.yRotO = REST_YAW;
            this.setYBodyRot(REST_YAW);
            this.setYHeadRot(REST_YAW);
            this.yHeadRotO = REST_YAW;
            this.getNavigation().stop();
        }
    }
}