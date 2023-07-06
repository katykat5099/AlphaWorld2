package ca.alphaworld.alphaworld2.data.entity;

import ca.alphaworld.alphaworld2.client.screen.FuelSourceBlockScreenHandler;
import ca.alphaworld.alphaworld2.data.ImplementedInventory;
import ca.alphaworld.alphaworld2.registries.AlphaWorld2BlockEntities;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.RenderUtils;

public class FuelSourceBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory, ImplementedInventory, GeoBlockEntity {
    private AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(3, ItemStack.EMPTY);

    protected final PropertyDelegate propertyDelegate;
    private int fuelTime = 0;
    private int maxFuelTime = 72;

    public FuelSourceBlockEntity(BlockPos pos, BlockState state) {
        super(AlphaWorld2BlockEntities.FUEL_SOURCE_BLOCK, pos, state);
        this.propertyDelegate = new PropertyDelegate() {
            public int get(int index) {
                switch (index){
                    case 0: return FuelSourceBlockEntity.this.fuelTime;
                    case 1: return FuelSourceBlockEntity.this.maxFuelTime;
                    default: return 0;
                }
            }

            public void set(int index, int value) {
                switch (index){
                    case 0: FuelSourceBlockEntity.this.fuelTime = value;
                        break;
                    case 1: FuelSourceBlockEntity.this.maxFuelTime = value;
                        break;
                }
            }

            public int size() {
                return 2;
            }
        };
    }

    @Override
    public double getTick(Object blockEntity) {
        return RenderUtils.getCurrentTick();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private <T extends GeoAnimatable> PlayState predicate(AnimationState<T> tAnimationState) {
        tAnimationState.getController().setAnimation(RawAnimation.begin().then("animation.fuel_source_block.idle", Animation.LoopType.LOOP));
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public DefaultedList<ItemStack> getItems() {
        return this.inventory;
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("gui.text.alphaworld2.fuel_source_block");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new FuelSourceBlockScreenHandler(syncId, inv, this, this.propertyDelegate);
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, inventory);
        nbt.putInt("fuel.progress", fuelTime);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        Inventories.readNbt(nbt, inventory);
        fuelTime = nbt.getInt("fuel.progress");
    }

    private void resetProgress() {
        this.fuelTime = 0;
    }

    public static void tick(World world, BlockPos blockPos, BlockState state, FuelSourceBlockEntity entity) {
        if(world.isClient()){
            return;
        }
        if(hasRecipe(entity)){
            entity.fuelTime++;
            markDirty(world, blockPos, state);
            if(entity.fuelTime >= entity.maxFuelTime){
                craftItem(entity);
            }
        }
        else{
            entity.resetProgress();
            markDirty(world, blockPos, state);
        }
    }

    private static void craftItem(FuelSourceBlockEntity entity) {
        SimpleInventory inventory = new SimpleInventory(entity.size());
        for (int i = 0; i < entity.size(); i++){
            inventory.setStack(i, entity.getStack(i));
        }

        if(hasRecipe(entity)){
            entity.removeStack(1, 1);

            entity.setStack(2, new ItemStack(Items.IRON_INGOT, entity.getStack(2).getCount() + 1));
        }
    }

    private static boolean hasRecipe(FuelSourceBlockEntity entity) {
        SimpleInventory inventory = new SimpleInventory(entity.size());
        for (int i = 0; i < entity.size(); i++){
            inventory.setStack(i, entity.getStack(i));
        }

        boolean hasRawMetalInFirstSlot = entity.getStack(1).getItem() == Items.RAW_IRON;

        return hasRawMetalInFirstSlot && canInsertAmountIntoOutputSlot(inventory) && canInsertItemIntoOutputSlot(inventory, Items.IRON_INGOT);

    }

    private static boolean canInsertItemIntoOutputSlot(SimpleInventory inventory, Item output) {
        return inventory.getStack(2).getItem() == output || inventory.getStack(2).isEmpty();
    }

    private static boolean canInsertAmountIntoOutputSlot(SimpleInventory inventory) {
        return inventory.getStack(2).getMaxCount() > inventory.getStack(2).getCount();
    }
}
