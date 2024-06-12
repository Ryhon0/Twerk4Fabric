package xyz.ryhon.twerk4fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BuddingAmethystBlock;
import net.minecraft.block.Degradable;
import net.minecraft.block.Fertilizable;
import net.minecraft.block.SpreadableBlock;
import net.minecraft.block.SugarCaneBlock;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.random.RandomSplitter;
import static net.minecraft.server.command.CommandManager.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

public class Twerk4Fabric implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("twerk4fabric");

	public static int range = 4;
	public static ArrayList<Block> ignoredBlocks = new ArrayList<>();

	@Override
	public void onInitialize() {
		try {
			loadConfig();
		} catch (Exception e) {
			LOGGER.error("Failed to load config", e);
		}

		CommandRegistrationCallback.EVENT
				.register((dispatcher, registryAccess,
						environment) -> dispatcher.register(literal("reloadtwerk4fabric")
								.requires(source -> environment.integrated || source.hasPermissionLevel(2))
								.executes(context -> {
									try {
										loadConfig();
									} catch (Exception e) {
										context.getSource().sendFeedback(
												() -> Text.literal("Failed to reload config")
														.formatted(Formatting.RED),
												false);
										context.getSource().sendFeedback(
												() -> Text.literal(e.toString()).formatted(Formatting.RED),
												false);
										return 1;
									}
									context.getSource()
											.sendFeedback(() -> Text.literal("Config reloaded sucessfully"), false);
									context.getSource().sendFeedback(() -> Text.literal("Range: " + range), false);
									context.getSource().sendFeedback(() -> Text.literal("Ignored blocks:"), false);
									if (ignoredBlocks.isEmpty())
										context.getSource().sendFeedback(() -> Text.literal("    None"), false);
									else {
										for (Block b : ignoredBlocks) {
											Item item = b.asItem();
											if (item != null) {
												context.getSource().sendFeedback(
														() -> Text.literal("    ")
																.append(Text.translatable(item.getTranslationKey())
																		.styled(s -> s.withHoverEvent(
																				new HoverEvent(
																						HoverEvent.Action.SHOW_ITEM,
																						new HoverEvent.ItemStackContent(
																								item.getDefaultStack()))))),
														false);
											}
											else
											{
												context.getSource().sendFeedback(
														() -> Text.literal("    ")
															.append(Text.translatable(b.getTranslationKey())),
														false);
											}
										}
									}

									return 0;

								})));
	}

	public static void Twerk(ServerPlayerEntity player) {
		if (player.isSpectator())
			return;

		BlockPos pos = player.getBlockPos();
		ServerWorld world = (ServerWorld) player.getWorld();

		ArrayList<BlockPos> toIterate = new ArrayList<>();
		ArrayList<BlockState> oldStates = new ArrayList<>();
		for (BlockPos ip : BlockPos.iterateOutwards(pos, range, 1, range)) {
			BlockPos bp = new BlockPos(ip);
			BlockState state = world.getBlockState(bp);
			if (!state.isAir()) {
				toIterate.add(bp);
				oldStates.add(state);
			}
		}

		ArrayList<BlockPos> ignoredPos = new ArrayList<>();
		for (int i = 0; i < toIterate.size(); i++) {
			BlockPos p = toIterate.get(i);
			if (ignoredPos.indexOf(p) != -1)
				continue;

			BlockState b = world.getBlockState(p);
			if (ignoredBlocks.contains(b.getBlock()))
				continue;

			// State changed, skip
			if (oldStates.get(i) != b)
				continue;

			boolean affected = false;
			if (b.getBlock() instanceof SpreadableBlock s) {
				for (BlockPos dp : BlockPos.iterateOutwards(p.down(), 1, 2, 1)) {
					if (world.getBlockState(dp).isOf(Blocks.DIRT) &&
							world.getLightLevel(dp.up()) >= 9 &&
							SpreadableBlock.canSpread(b, world, dp)) {
						affected = true;
						break;
					}
				}
				if (affected)
					b.randomTick(world, p, player.getRandom());
			} else if (b.getBlock() instanceof Degradable d) {
				Optional<BlockState> res = d.getDegradationResult(b);
				if (res.isPresent() && res.get() != b) {
					d.tryDegrade(b, world, p, player.getRandom());
					affected = true;
				}
			} else if (b.getBlock() instanceof Fertilizable f) {
				if (f.isFertilizable(world, p, b) && f.canGrow(world, player.getRandom(), p, b)) {
					f.grow(world, player.getRandom(), p, b);
					affected = true;
				}
			} else if (b.getBlock() instanceof SugarCaneBlock s) {
				BlockPos bottomPos = p;
				while (world.getBlockState(bottomPos.down()).getBlock() == s)
					bottomPos = bottomPos.down();

				BlockPos topPos = bottomPos;
				while (world.getBlockState(topPos.up()).getBlock() == s)
					topPos = topPos.up();

				ignoredPos.addAll(ImmutableList.copyOf(BlockPos.iterate(bottomPos, topPos)));

				if (topPos.getY() - bottomPos.getY() < 2 && world.isAir(topPos.up())) {
					world.setBlockState(topPos.up(), s.getDefaultState());
					ignoredPos.add(topPos.up());
					affected = true;
				}
			} else if (b.getBlock() instanceof BuddingAmethystBlock a) {
				for (int j = 0; j < 6; j++) {
					b.randomTick(world, p, new RiggedRandom() {
						@Override
						public int nextInt(int bound) {
							if (bound == Direction.values().length)
								return player.getRandom().nextInt(bound);
							return super.nextInt(bound);
						}
					});
				}
				affected = true;
			} else {
				b.randomTick(world, pos, player.getRandom());
			}

			if (affected) {
				world.playSound(null, p, SoundEvents.ITEM_BONE_MEAL_USE, SoundCategory.BLOCKS, 0.25f, 1.5f);
				for (ServerPlayerEntity viewer : world.getServer().getPlayerManager().getPlayerList()) {
					Vec3d center = p.toCenterPos();
					world.spawnParticles(viewer, ParticleTypes.HAPPY_VILLAGER, true, center.x, center.y, center.z, 10,
							0.25f, 0.25f, 0.25f, 1);
				}
			}
		}
	}

	public static class RiggedRandom implements Random {
		@Override
		public boolean nextBoolean() {
			return true;
		}

		@Override
		public double nextDouble() {
			return 0f;
		}

		@Override
		public float nextFloat() {
			return 0f;
		}

		@Override
		public double nextGaussian() {
			return 0;
		}

		@Override
		public int nextInt() {
			return 0;
		}

		@Override
		public int nextInt(int bound) {
			return 0;
		}

		@Override
		public long nextLong() {
			return 0;
		}

		@Override
		public RandomSplitter nextSplitter() {
			return null;
		}

		@Override
		public void setSeed(long seed) {
		}

		@Override
		public Random split() {
			return this;
		}
	}

	static Path configDir = FabricLoader.getInstance().getConfigDir().resolve("twerk4fabric");
	static Path configFile = configDir.resolve("config.json");

	static void loadConfig() throws Exception {
		Files.createDirectories(configDir);
		JsonObject jo = new JsonObject();
		if (Files.exists(configFile)) {
			String str = Files.readString(configFile);
			jo = (JsonObject) JsonParser.parseString(str);
		}

		if (jo.has("range"))
			range = jo.get("range").getAsInt();
		else
			range = 4;

		ignoredBlocks = new ArrayList<>();
		if (jo.has("ignoredBlocks")) {
			for (JsonElement e : jo.get("ignoredBlocks").getAsJsonArray()) {
				Identifier id = Identifier.splitOn(e.getAsString(), ':');
				Block b = Registries.BLOCK.get(id);
				if (b == Blocks.AIR && !id.toString().equals(Registries.BLOCK.getId(Blocks.AIR).toString()))
					throw new Exception("Could not find block with ID " + id.toString());
				ignoredBlocks.add(b);
			}
		} else {
			ignoredBlocks.add(Blocks.SHORT_GRASS);
			ignoredBlocks.add(Blocks.TALL_GRASS);
		}

		saveConfig();

	}

	static void saveConfig() throws IOException {
		JsonObject jo = new JsonObject();

		jo.add("range", new JsonPrimitive(range));

		JsonArray ignoredArr = new JsonArray();
		for (Block b : ignoredBlocks) {
			Identifier id = Registries.BLOCK.getId(b);
			ignoredArr.add(new JsonPrimitive(id.toString()));
		}
		jo.add("ignoredBlocks", ignoredArr);

		Files.createDirectories(configDir);
		Files.writeString(configFile, new Gson().toJson(jo));
	}
}