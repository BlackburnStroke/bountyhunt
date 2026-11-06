package com.bountyhunt.client;

import com.bountyhunt.util.Icons;
import com.bountyhunt.client.model.MonsterModels;
import com.bountyhunt.gui.ModScreens;
import com.bountyhunt.monster.ModEntities;
import com.bountyhunt.item.ModItems;
import com.bountyhunt.network.LobbySnapshot;
import com.bountyhunt.network.ModNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.item.CompassAnglePredicateProvider;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.UUID;

public class BountyHuntClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HandledScreens.register(ModScreens.BOARD, BoardScreen::new);

		// Custom bounty monsters: one model per difficulty tier
		MonsterModels.registerLayers();
		EntityRendererRegistry.register(ModEntities.BOUNTY_ZOMBIE, BountyZombieRenderer::new);
		EntityRendererRegistry.register(ModEntities.BOUNTY_SKELETON, BountySkeletonRenderer::new);

		// Needle: the vanilla compass logic itself, pointed at the monster instead of world spawn.
		CompassAnglePredicateProvider vanillaCompass = new CompassAnglePredicateProvider((world, stack, entity) -> CompassClient.target(world));
		ModelPredicateProviderRegistry.register(ModItems.HUNTING_COMPASS, new Identifier("angle"),
				(stack, world, entity, seed) -> vanillaCompass.call(stack, world, entity, seed));

		// Forget which tier textures exist whenever resources reload (F3+T, resource pack change).
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return new Identifier("bountyhunt", "tier_textures");
			}

			@Override
			public void reload(ResourceManager manager) {
				TierTextures.clear();
			}
		});

		HudRenderCallback.EVENT.register(AnnouncementOverlay::render);

		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.COMPASS, (client, handler, buf, sender) -> {
			boolean has = buf.readBoolean();
			double x = buf.readDouble();
			double z = buf.readDouble();
			client.execute(() -> CompassClient.set(has, x, z));
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.ANNOUNCE, (client, handler, buf, sender) -> {
			Text title = buf.readText();
			Identifier sound = buf.readIdentifier();
			client.execute(() -> {
				AnnouncementOverlay.show(title);
				SoundEvent event = Registries.SOUND_EVENT.get(sound);
				if (event != null) {
					client.getSoundManager().play(PositionedSoundInstance.master(event, 1.0f, 1.0f));
				}
			});
		});

		// "Create a party for this bounty?" looks and behaves like the server resource pack prompt.
		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.CONFIRM, (client, handler, buf, sender) -> {
			UUID bountyId = buf.readUuid();
			Text title = buf.readText();
			Text message = buf.readText();
			client.execute(() -> client.setScreen(new ConfirmScreen(yes -> {
				PacketByteBuf reply = PacketByteBufs.create();
				reply.writeUuid(bountyId);
				reply.writeBoolean(yes);
				ClientPlayNetworking.send(ModNetworking.CONFIRM_REPLY, reply);
				client.setScreen(null);
			}, title, message,
					Icons.CHECK.label(Text.literal("Yes").formatted(Formatting.GREEN)),
					Icons.CROSS.label(Text.literal("No").formatted(Formatting.RED)))));
		});

		ClientPlayNetworking.registerGlobalReceiver(ModNetworking.LOBBY, (client, handler, buf, sender) -> {
			LobbySnapshot snapshot = LobbySnapshot.read(buf);
			client.execute(() -> LobbyScreen.handle(client, snapshot));
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			CompassClient.clear();
			AnnouncementOverlay.clear();
		});
	}
}
