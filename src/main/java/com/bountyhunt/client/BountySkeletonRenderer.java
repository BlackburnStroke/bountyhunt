package com.bountyhunt.client;

import com.bountyhunt.client.model.MonsterModels;
import com.bountyhunt.monster.BountySkeletonEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.SkeletonEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * One renderer, four models: the tier picks the model (bone plates, helm, quiver, gold crown) and its texture.
 * On Legendary the floating bone shards slowly orbit the head and the staff's soulfire flame flickers.
 */
public class BountySkeletonRenderer extends BipedEntityRenderer<BountySkeletonEntity, SkeletonEntityModel<BountySkeletonEntity>> {
	private static final Identifier[] TEXTURES = new Identifier[4];

	static {
		for (int i = 0; i < TEXTURES.length; i++) {
			TEXTURES[i] = new Identifier("bountyhunt", "textures/entity/bounty_skeleton/tier_" + i + ".png");
		}
	}

	private static final int SHARD_COUNT = 6;
	private static final float ORBIT_SPEED = 0.028f;  // radians per tick
	private static final float BOB_SPEED = 0.075f;

	private final List<SkeletonEntityModel<BountySkeletonEntity>> models = new ArrayList<>();

	/** The Legendary model's animateable parts (from the top model layer), with their resting offsets. */
	private final List<ModelPart> shards = new ArrayList<>();
	private final float[] shardRadius = new float[SHARD_COUNT];
	private final float[] shardBaseAngle = new float[SHARD_COUNT];
	private final float[] shardBaseY = new float[SHARD_COUNT];
	private final float[] shardRoll = new float[SHARD_COUNT];
	private float flamePivotX;
	private ModelPart flame;

	public BountySkeletonRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new SkeletonEntityModel<>(ctx.getPart(MonsterModels.SKELETON_LAYERS[0])), 0.5f);
		models.add(this.model);
		for (int i = 1; i < MonsterModels.SKELETON_LAYERS.length; i++) {
			models.add(new SkeletonEntityModel<>(ctx.getPart(MonsterModels.SKELETON_LAYERS[i])));
		}
		// cache the Legendary extras (last layer) once; only that model carries these children
		ModelPart root = ctx.getPart(MonsterModels.SKELETON_LAYERS[MonsterModels.SKELETON_LAYERS.length - 1]);
		ModelPart head = root.getChild("head");
		for (int i = 0; i < SHARD_COUNT; i++) {
			ModelPart part = head.getChild("shard" + i);
			shards.add(part);
			shardRadius[i] = MathHelper.sqrt(part.pivotX * part.pivotX + part.pivotZ * part.pivotZ);
			shardBaseAngle[i] = (float) MathHelper.atan2(part.pivotZ, part.pivotX);			shardBaseY[i] = part.pivotY;
			shardRoll[i] = part.roll;
		}
		flame = root.getChild("body").getChild("staff").getChild("flame");
		flamePivotX = flame.pivotX;
	}

	private static int tierOf(BountySkeletonEntity entity) {
		return Math.max(0, Math.min(3, entity.getTier()));
	}

	/** Slow orbit + slight vertical bob for the halo shards, wavering tongue of soulfire on the staff. */
	private void animateLegendary(float time) {
		for (int i = 0; i < shards.size(); i++) {
			ModelPart part = shards.get(i);
			float angle = shardBaseAngle[i] + time * ORBIT_SPEED;
			part.pivotX = MathHelper.cos(angle) * shardRadius[i];
			part.pivotZ = MathHelper.sin(angle) * shardRadius[i];
			part.pivotY = shardBaseY[i] + MathHelper.sin(time * BOB_SPEED + i * 0.9f) * 0.55f;
			part.yaw = angle + MathHelper.PI / 2.0f;
			part.roll = shardRoll[i];
		}
		flame.pivotX = flamePivotX + MathHelper.sin(time * 0.6f) * 0.3f;
		flame.pitch = MathHelper.cos(time * 0.45f) * 0.12f;
		flame.roll = MathHelper.sin(time * 0.8f) * 0.12f;
	}

	@Override
	public void render(BountySkeletonEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		int tier = tierOf(entity);
		this.model = models.get(tier);
		if (tier == 3) {
			animateLegendary(entity.age + tickDelta);
		}
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	public Identifier getTexture(BountySkeletonEntity entity) {
		return TEXTURES[tierOf(entity)];
	}
}
