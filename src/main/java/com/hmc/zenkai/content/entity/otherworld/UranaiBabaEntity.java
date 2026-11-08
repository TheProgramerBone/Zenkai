package com.hmc.zenkai.content.entity.otherworld;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.entity.ZenkaiDefaultNPC;
import com.hmc.zenkai.feature.dialogue.DialogueService;
import com.hmc.zenkai.feature.dialogue.DialogueSpeaker;
import com.hmc.zenkai.feature.dialogue.OpenDialoguePayload;
import com.hmc.zenkai.feature.player.EarthVisit;
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

/**
 * Uranai Baba, junto a Yemma en el palacio del Otherworld. Con la muerte permanente encendida y el jugador muerto, le ofrece
 * una visita temporal a la Tierra ({@link EarthVisit}). El resto del tiempo hace de adivina: despacha con una frase.
 * Todo el estado (muerto, espera, hardcore) se lee del servidor en cada clic y se vuelve a comprobar al elegir.
 */
public class UranaiBabaEntity extends ZenkaiDefaultNPC implements DialogueSpeaker {

    private static final float LOOK_RANGE = 5.0f;
    /** Distancia máxima a la que vale una opción elegida (el id lo manda el cliente). */
    private static final double TALK_RANGE = 8.0;

    public UranaiBabaEntity(EntityType<? extends ZenkaiDefaultNPC> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

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
            Component name = sp.getDisplayName();
            List<OpenDialoguePayload.Choice> choices = new ArrayList<>();
            List<Component> pages = new ArrayList<>();

            switch (EarthVisit.check(sp)) {
                case OK -> {
                    // Dos páginas: lo que ofrece y lo que cuesta. La opción solo sale tras la última.
                    pages.add(line("offer", 2, name));
                    pages.add(Component.translatable("dialogue.zenkai.baba.terms",
                            ServerConfig.PERMADEATH_VISIT_DAYS.get(),
                            ServerConfig.PERMADEATH_VISIT_COOLDOWN_DAYS.get()));
                    choices.add(new OpenDialoguePayload.Choice("visit",
                            Component.translatable("dialogue.zenkai.baba.choice.visit")));
                }
                case COOLDOWN -> pages.add(line("cooldown", 2, name, EarthVisit.remainingLabel(EarthVisit.cooldownLeft(sp))));
                case HARDCORE -> pages.add(line("hardcore", 2, name));
                case DISABLED -> pages.add(line("disabled", 2, name));
                // Vivo (o, de forma anómala, ya de visita): lee la fortuna y lo despacha.
                case NOT_DEAD, ALREADY_ON_VISIT -> pages.add(line("alive", 3, name));
            }
            choices.add(DialogueService.leave());
            DialogueService.open(sp, this, pages, choices);
            talkSound();
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    /** Una de las variantes de la situación ({@code dialogue.zenkai.baba.<situación>.<n>}). */
    private Component line(String situation, int variants, Object... args) {
        int n = 1 + this.random.nextInt(variants);
        return Component.translatable("dialogue.zenkai.baba." + situation + "." + n, args);
    }

    /** Voz aguda y cascada: el mismo sonido de aldeano que Yemma pero muy por encima de su tono. */
    private void talkSound() {
        this.level().playSound(null, this.getX(), this.getEyeY(), this.getZ(),
                SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.9f, 1.35f);
    }

    @Override
    public void onDialogueChoice(ServerPlayer sp, String choiceId) {
        if (!"visit".equals(choiceId)) return;
        if (sp.distanceTo(this) > TALK_RANGE) return;
        // tryStart repite check(): muerto, permadeath encendida, sin hardcore, sin visita ni espera.
        if (EarthVisit.tryStart(sp) != EarthVisit.Check.OK) return;
        this.level().playSound(null, this.getX(), this.getEyeY(), this.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.NEUTRAL, 1.0f, 0.8f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0f)
                .add(Attributes.MOVEMENT_SPEED, 0.0f);
    }
}
