package io.github.foundationgames.automobility.automobile.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.AutomobileEngine;
import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import io.github.foundationgames.automobility.automobile.AutomobileWheel;
import io.github.foundationgames.automobility.automobile.attachment.front.FrontAttachment;
import io.github.foundationgames.automobility.automobile.attachment.rear.RearAttachment;
import io.github.foundationgames.automobility.item.AutomobileEngineItem;
import io.github.foundationgames.automobility.item.AutomobileFrameItem;
import io.github.foundationgames.automobility.item.AutomobileWheelItem;
import io.github.foundationgames.automobility.item.AutomobilityItems;
import io.github.foundationgames.automobility.item.FrontAttachmentItem;
import io.github.foundationgames.automobility.item.RearAttachmentItem;
import io.github.foundationgames.automobility.automobile.render.obj.ObjModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

public class AutomobileItemSpecialRenderer implements SpecialModelRenderer<AutomobileItemSpecialRenderer.Argument> {
    private final Kind kind;

    public AutomobileItemSpecialRenderer(Kind kind) {
        this.kind = kind;
    }

    @Override
    public void submit(Argument argument, PoseStack pose, SubmitNodeCollector submitter, int light, int overlay, boolean foil, int color) {
        if (argument == null) {
            return;
        }

        if (argument.automobile != null) {
            pose.pushPose();
            pose.scale(argument.scale, argument.scale, argument.scale);
            AutomobileRenderer.render(pose, submitter, light, overlay, 0, argument.automobile);
            pose.popPose();
            return;
        }

        var model = AutomobileModels.getModel(argument.modelId);
        if (model instanceof BaseModel base || model instanceof ObjModel) {
            pose.pushPose();
            pose.translate(0.5, 0, 0.5);
            pose.scale(argument.scale, -argument.scale, -argument.scale);
            if (model instanceof BaseModel base) {
                submitter.submitModel(base, new BaseModel.RenderState(0), pose, base.renderType(argument.texture), light, overlay, color, null, 0, null);
            } else if (model instanceof ObjModel obj) {
                obj.submit(pose, submitter, obj.renderType(argument.texture), light, overlay, color);
            }
            pose.popPose();
        }
    }

    @Override
    public void getExtents(Consumer<Vector3fc> consumer) {
        consumer.accept(new Vector3f(-1.5f, -1.5f, -1.5f));
        consumer.accept(new Vector3f(1.5f, 1.5f, 1.5f));
    }

    @Override
    public @Nullable Argument extractArgument(ItemStack stack) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }

        var registries = level.registryAccess();
        return switch (this.kind) {
            case AUTOMOBILE -> {
                var data = stack.get(AutomobilityItems.COMPONENT_AUTOMOBILE_DATA.require());
                if (data == null) {
                    yield null;
                }

                var frame = registries.lookupOrThrow(AutomobileFrame.REGISTRY).get(data.frame()).map(ref -> ref.value()).orElse(AutomobileFrame.EMPTY);
                var wheel = registries.lookupOrThrow(AutomobileWheel.REGISTRY).get(data.wheel()).map(ref -> ref.value()).orElse(AutomobileWheel.EMPTY);
                var engine = registries.lookupOrThrow(AutomobileEngine.REGISTRY).get(data.engine()).map(ref -> ref.value()).orElse(AutomobileEngine.EMPTY);
                if (frame.isEmpty() || wheel.isEmpty() || engine.isEmpty()) {
                    yield null;
                }

                float wheelDist = frame.model().lengthPx() / 16;
                yield Argument.automobile(new SimpleItemAutomobile(frame, engine, wheel), 1 / (wheelDist * 0.77f));
            }
            case FRAME -> {
                if (stack.getItem() instanceof AutomobileFrameItem item) {
                    var component = item.getComponent(stack, registries);
                    if (!component.isEmpty()) {
                        yield Argument.component(component.model().modelId(), component.model().texture(), 1 / ((component.model().lengthPx() / 16) * 0.77f));
                    }
                }
                yield null;
            }
            case WHEEL -> {
                if (stack.getItem() instanceof AutomobileWheelItem item) {
                    var component = item.getComponent(stack, registries);
                    if (!component.isEmpty()) {
                        yield Argument.component(component.model().modelId(), component.model().texture(), 6 / component.model().radius());
                    }
                }
                yield null;
            }
            case ENGINE -> {
                if (stack.getItem() instanceof AutomobileEngineItem item) {
                    var component = item.getComponent(stack, registries);
                    if (!component.isEmpty()) {
                        yield Argument.component(component.model().modelId(), component.model().texture(), 1);
                    }
                }
                yield null;
            }
            case FRONT_ATTACHMENT -> {
                if (stack.getItem() instanceof FrontAttachmentItem item) {
                    var component = item.getComponent(stack, registries);
                    if (!component.isEmpty()) {
                        yield Argument.component(component.model().modelId(), component.model().texture(), component.model().scale());
                    }
                }
                yield null;
            }
            case REAR_ATTACHMENT -> {
                if (stack.getItem() instanceof RearAttachmentItem item) {
                    var component = item.getComponent(stack, registries);
                    if (!component.isEmpty()) {
                        yield Argument.component(component.model().modelId(), component.model().texture(), 1);
                    }
                }
                yield null;
            }
        };
    }

    public record Argument(@Nullable RenderableAutomobile automobile, Identifier modelId, Identifier texture, float scale) {
        public static Argument automobile(RenderableAutomobile automobile, float scale) {
            return new Argument(automobile, Automobility.rl("empty"), BaseModel.TEXTURE_SOLID, scale);
        }

        public static Argument component(Identifier modelId, Identifier texture, float scale) {
            return new Argument(null, modelId, texture, scale);
        }
    }

    public enum Kind implements StringRepresentable {
        AUTOMOBILE("automobile"),
        FRAME("frame"),
        WHEEL("wheel"),
        ENGINE("engine"),
        FRONT_ATTACHMENT("front_attachment"),
        REAR_ATTACHMENT("rear_attachment");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    public record Unbaked(Kind kind) implements SpecialModelRenderer.Unbaked<Argument> {
        public static final MapCodec<Unbaked> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Kind.CODEC.fieldOf("kind").forGetter(Unbaked::kind)
        ).apply(inst, Unbaked::new));

        @Override
        public SpecialModelRenderer<Argument> bake(BakingContext context) {
            return new AutomobileItemSpecialRenderer(kind);
        }

        @Override
        public MapCodec<? extends SpecialModelRenderer.Unbaked<Argument>> type() {
            return CODEC;
        }
    }

    private record SimpleItemAutomobile(
            AutomobileFrame frame,
            AutomobileEngine engine,
            AutomobileWheel wheel
    ) implements RenderableAutomobile {
        @Override
        public AutomobileFrame getFrame() {
            return frame;
        }

        @Override
        public AutomobileEngine getEngine() {
            return engine;
        }

        @Override
        public AutomobileWheel getWheels() {
            return wheel;
        }

        @Override
        public @Nullable RearAttachment getRearAttachment() {
            return null;
        }

        @Override
        public @Nullable FrontAttachment getFrontAttachment() {
            return null;
        }

        @Override
        public float getAutomobileYaw(float tickDelta) {
            return 0;
        }

        @Override
        public float getRearAttachmentYaw(float tickDelta) {
            return 0;
        }

        @Override
        public float getWheelAngle(float tickDelta) {
            return 0;
        }

        @Override
        public float getSteering(float tickDelta) {
            return 0;
        }

        @Override
        public float getSuspensionBounce(float tickDelta) {
            return 0;
        }

        @Override
        public boolean engineRunning() {
            return false;
        }

        @Override
        public int getBoostTimer() {
            return 0;
        }

        @Override
        public int getTurboCharge() {
            return 0;
        }

        @Override
        public long getTime() {
            return 0;
        }

        @Override
        public boolean automobileOnGround() {
            return true;
        }

        @Override
        public boolean debris() {
            return false;
        }

        @Override
        public Vector3f debrisColor() {
            return new Vector3f();
        }
    }
}
