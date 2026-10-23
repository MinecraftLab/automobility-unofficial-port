package io.github.foundationgames.automobility.screen;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** A menu-only barrier against mixing separately delivered selection/part updates. */
public final class GarageReadonlySnapshotBinding {
    public static final String KEY="automobility:garage_readonly";
    private GarageReadonlySnapshotBinding() {}
    public record Token(UUID vehicle,UUID generation) {
        public Token { Objects.requireNonNull(vehicle); Objects.requireNonNull(generation); }
        public static Token fresh(UUID vehicle) { return new Token(vehicle,UUID.randomUUID()); }
    }
    /** Always copies. Call only for virtual readonly slots, never owned inventory or saved entities. */
    public static ItemStack stamp(ItemStack stack,Token token) {
        Objects.requireNonNull(token);
        if(stack.isEmpty()) return ItemStack.EMPTY;
        var result=stack.copy(); var existing=stack.get(DataComponents.CUSTOM_DATA);
        var root=existing==null?new CompoundTag():existing.copyTag(); var binding=new CompoundTag();
        binding.putString("vehicle",token.vehicle().toString()); binding.putString("generation",token.generation().toString());
        root.put(KEY,binding); result.set(DataComponents.CUSTOM_DATA,CustomData.of(root)); return result;
    }
    public static Optional<Token> read(ItemStack stack) {
        if(stack==null||stack.isEmpty()) return Optional.empty();
        var data=stack.get(DataComponents.CUSTOM_DATA); if(data==null) return Optional.empty();
        var binding=data.copyTag().getCompoundOrEmpty(KEY);
        String vehicle=binding.getStringOr("vehicle",""), generation=binding.getStringOr("generation","");
        if(vehicle.length()!=36||generation.length()!=36) return Optional.empty();
        try { return Optional.of(new Token(UUID.fromString(vehicle),UUID.fromString(generation))); }
        catch(IllegalArgumentException error) { return Optional.empty(); }
    }
    public static boolean matches(UUID selected,ItemStack frame,ItemStack wheels,ItemStack engine) {
        if(selected==null) return false;
        var token=read(frame);
        return token.isPresent()&&selected.equals(token.get().vehicle())
                &&token.equals(read(wheels))&&token.equals(read(engine));
    }
}
