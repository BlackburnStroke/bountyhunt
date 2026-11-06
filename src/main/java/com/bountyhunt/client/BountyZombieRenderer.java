package com.bountyhunt.client;

import com.bountyhunt.client.model.MonsterModels;
import com.bountyhunt.monster.BountyZombieEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.ZombieEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** One renderer, four models: the tier picks the model (extra horns, plates, spikes, cape) and its texture. */
public class BountyZombieRenderer extends BipedEntityRenderer<BountyZombieEntity, ZombieEntityModel<BountyZombieEntity>> {
	private static final Identifier[] TEXTURES = new Identifier[4];

	static {
		for (int i = 0; i < TEXTURES.length; i++) {
			TEXTURES[i] = new Identifier("bountyhunt", "textures/entity/bounty_zombie/tier_" + i + ".png");
		}
	}

	private final List<ZombieEntityModel<BountyZombieEntity>> models = new ArrayList<>();

	public BountyZombieRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new ZombieEntityModel<>(ctx.getPart(MonsterModels.ZOMBIE_LAYERS[0])), 0.5f);
		models.add(this.model);
		for (int i = 1; i < MonsterModels.ZOMBIE_LAYERS.length; i++) {
			models.add(new ZombieEntityModel<>(ctx.getPart(MonsterModels.ZOMBIE_LAYERS[i])));
		}
	}

	private static int tierOf(BountyZombieEntity entity) {
		return Math.max(0, Math.min(3, entity.getTier()));
	}

	@Override
	public void render(BountyZombieEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		this.model = models.get(tierOf(entity));
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	public Identifier getTexture(BountyZombieEntity entity) {
		return TEXTURES[tierOf(entity)];
	}
}
