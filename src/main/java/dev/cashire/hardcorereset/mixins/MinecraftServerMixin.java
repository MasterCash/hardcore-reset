package dev.cashire.hardcorereset.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import dev.cashire.hardcorereset.interfaces.ModdedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTask;
import net.minecraft.util.thread.ReentrantThreadExecutor;
import net.minecraft.world.WorldSaveHandler;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin extends ReentrantThreadExecutor<ServerTask> implements ModdedServer{

  @Shadow
  protected WorldSaveHandler saveHandler;
  
  public MinecraftServerMixin(String string) {
    super(string);
  }

  @Override
  public WorldSaveHandler getSaveHandler() {
    return this.saveHandler;
  }
}
