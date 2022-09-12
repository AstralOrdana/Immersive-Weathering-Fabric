package com.ordana.immersive_weathering.data.fluid_generators;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ordana.immersive_weathering.data.position_tests.PositionRuleTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class SelfFluidGenerator implements IFluidGenerator {

    public static final Codec<SelfFluidGenerator> CODEC = RecordCodecBuilder.<SelfFluidGenerator>create(
            instance -> instance.group(
                    Registry.FLUID.byNameCodec().fieldOf("fluid").forGetter(SelfFluidGenerator::getFluid),
                    FluidType.CODEC.optionalFieldOf("fluid_type", FluidType.BOTH).forGetter(SelfFluidGenerator::getFluidType),
                    BlockState.CODEC.fieldOf("generate").forGetter(SelfFluidGenerator::getGrowth),
                    AdjacentBlocks.CODEC.fieldOf("adjacent_blocks").forGetter(SelfFluidGenerator::getAdjacentBlocksCondition),
                    PositionRuleTest.CODEC.optionalFieldOf("additional_target_check").forGetter(SelfFluidGenerator::getPositionTests),
                    Codec.INT.optionalFieldOf("priority", 0).forGetter(SelfFluidGenerator::getPriority)
            ).apply(instance, SelfFluidGenerator::new));

    public static final IFluidGenerator.Type<SelfFluidGenerator> TYPE = new Type<>(CODEC, "target_self");

    private final Fluid fluid;
    private final FluidType fluidType;
    private final BlockState growth;
    private final Optional<PositionRuleTest> positionTests;
    private final int priority;
    private final AdjacentBlocks adjacentBlocksCondition;

    public SelfFluidGenerator(Fluid fluid,FluidType fluidType, BlockState growth,
                              AdjacentBlocks adjacentBlocks,
                              Optional<PositionRuleTest> positionRuleTests, int priority) {
        this.fluid = fluid;
        this.fluidType = fluidType;
        this.growth = growth;
        this.adjacentBlocksCondition = adjacentBlocks;
        this.positionTests = positionRuleTests;
        this.priority = priority;
    }

    @Override
    public FluidType getFluidType() {
        return fluidType;
    }

    @Override
    public Type<?> getType() {
        return TYPE;
    }

    @Override
    public Fluid getFluid() {
        return fluid;
    }

    public BlockState getGrowth() {
        return growth;
    }

    public Optional<PositionRuleTest> getPositionTests() {
        return positionTests;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    public AdjacentBlocks getAdjacentBlocksCondition() {
        return adjacentBlocksCondition;
    }

    @Override
    public Optional<BlockPos> tryGenerating(List<Direction> possibleFlowDir, BlockPos pos, Level level, Map<Direction, BlockState> neighborCache) {

        if(!adjacentBlocksCondition.isMet(possibleFlowDir, pos, level, neighborCache, positionTests))return Optional.empty();

        if (pos != null) {
            level.setBlockAndUpdate(pos, this.growth);
            return Optional.of(pos);
        }
        return Optional.empty();
    }


    public static class AdjacentBlocks {

        public static final Codec<AdjacentBlocks> CODEC = RecordCodecBuilder.<AdjacentBlocks>create(
                instance -> instance.group(
                        RuleTest.CODEC.listOf().optionalFieldOf("sides", List.of()).forGetter(a -> a.sidesBlocks),
                        RuleTest.CODEC.listOf().optionalFieldOf("any", List.of()).forGetter(a -> a.anyBlocks),
                        RuleTest.CODEC.optionalFieldOf("up").forGetter(a -> Optional.ofNullable(a.upBlock)),
                        RuleTest.CODEC.optionalFieldOf("down").forGetter(a -> Optional.ofNullable(a.downBlock))

                ).apply(instance, AdjacentBlocks::new)).comapFlatMap(arg -> {
            if (arg.sidesBlocks.isEmpty() && arg.anyBlocks.isEmpty() && arg.upBlock == null && arg.downBlock == null) {
                return DataResult.error("Adjacent Blocks must contain at least one predicate");
            }
            return DataResult.success(arg);
        }, Function.identity());

        private final List<RuleTest> anyBlocks;
        private final List<RuleTest> sidesBlocks;
        private final RuleTest upBlock;
        private final RuleTest downBlock;

        public AdjacentBlocks(List<RuleTest> sidesBlocks,
                              List<RuleTest> anyBlocks,
                              Optional<RuleTest> upBlock,
                              Optional<RuleTest> downBlock) {
            this.sidesBlocks = sidesBlocks;
            this.anyBlocks = anyBlocks;
            this.upBlock = upBlock.orElse(null);
            this.downBlock = downBlock.orElse(null);

        }

        public boolean isMet(List<Direction> possibleFlowDir, BlockPos pos, Level level,
                             Map<Direction, BlockState> neighborCache, Optional<PositionRuleTest> extraCheck) {

            Holder<Biome> b = extraCheck.isPresent() ? level.getBiome(pos) : null;
            for(var r : anyBlocks){
                boolean atLeastOnceSuccess = false;

                for (var d : Direction.values()) {
                    BlockPos side = pos.relative(d);
                    BlockState state = neighborCache.computeIfAbsent(d, p -> level.getBlockState(side));
                    if (r.test(state, level.random)) {
                            if(b != null && !extraCheck.get().test(b, side, level)) continue;
                        atLeastOnceSuccess = true;
                        break;
                    }
                }
                if(!atLeastOnceSuccess)return false;
            }

            for(var r : sidesBlocks){
                boolean atLeastOnceSuccess = false;
                for (var d : possibleFlowDir) {
                    if (d.getAxis().isHorizontal()) {
                        BlockPos side = pos.relative(d);
                        BlockState state = neighborCache.computeIfAbsent(d, p -> level.getBlockState(side));
                        if (r.test(state, level.random)) {
                            if(b != null && !extraCheck.get().test(b, side, level)) continue;
                            atLeastOnceSuccess = true;
                            break;
                        }
                    }
                }
                if(!atLeastOnceSuccess)return false;
            }

            if(upBlock != null){
                Direction d = Direction.UP;
                BlockPos target = pos.relative(d);
                BlockState state = neighborCache.computeIfAbsent(d, p -> level.getBlockState(target));
                if (!upBlock.test(state, level.random)) return false;
                if(b != null && !extraCheck.get().test(b, pos, level)) return false;
            }

            if(downBlock != null){
                Direction d = Direction.DOWN;
                BlockPos target = pos.relative(d);
                BlockState state = neighborCache.computeIfAbsent(d, p -> level.getBlockState(target));
                if (!downBlock.test(state, level.random)) return false;
                if(b != null && !extraCheck.get().test(b, target, level)) return false;
            }

            return true;
        }

    }
}