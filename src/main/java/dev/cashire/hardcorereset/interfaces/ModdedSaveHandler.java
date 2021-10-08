package dev.cashire.hardcorereset.interfaces;

import net.minecraft.nbt.NbtCompound;

public interface ModdedSaveHandler {
  NbtCompound loadPlayerData(String uuid);
  
}
