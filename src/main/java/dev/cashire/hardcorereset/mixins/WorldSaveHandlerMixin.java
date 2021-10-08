package dev.cashire.hardcorereset.mixins;

import java.io.File;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import dev.cashire.hardcorereset.interfaces.ModdedSaveHandler;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.WorldSaveHandler;

@Mixin(WorldSaveHandler.class)
public abstract class WorldSaveHandlerMixin implements ModdedSaveHandler {
  @Shadow
  private File playerDataDir;

  @Override
  public NbtCompound loadPlayerData(String uuid) {
    NbtCompound nbtCompound = null;

    try {
      File file = new File(this.playerDataDir, uuid + ".dat");
      if (file.exists() && file.isFile()) {
        nbtCompound = NbtIo.readCompressed(file);
      }
    } catch (Exception e) {
    }
    return nbtCompound;
  }
}
