package dev.cashire.hardcorereset;

import static com.google.common.collect.ImmutableList.of;

import dev.cashire.cashconfig.Config;
import dev.cashire.cashconfig.items.ConfigBoolean;
import dev.cashire.cashconfig.items.ConfigGroup;
import dev.cashire.cashconfig.items.ConfigNumber;
import dev.cashire.cashconfig.items.ConfigString;
import dev.cashire.hardcorereset.commands.CommandBuilder;
import dev.cashire.hardcorereset.interfaces.ModdedSaveHandler;
import dev.cashire.hardcorereset.interfaces.ModdedServer;

import java.io.File;
import java.time.LocalDate;
import java.util.HashSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.level.ServerWorldProperties;

/**
 * Entry point for Server Side logic
 */
public class Main implements DedicatedServerModInitializer {
  // Config File
  private static Config CONFIG = new Config("hardcore-reset.json");
  // 
  private static Logger LOGGER = LogManager.getLogger("hardcore-reset");

  @Override
  public void onInitializeServer() {
    CONFIG.readFile();
    setupConfig();

    CommandRegistrationCallback.EVENT.register(CommandBuilder::register);

    ServerEntityEvents.ENTITY_LOAD.register((entity, serverWorld) -> {
      if(entity instanceof ServerPlayerEntity player) {
        var uuid = player.getUuidAsString();
        updatePlayer(uuid, player.isAlive() && !player.isSpectator());
      }

    });

    ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
      if (entity instanceof ServerPlayerEntity player) {
        var uuid = player.getUuid().toString();
        updatePlayer(uuid, player.isAlive() && !player.isSpectator());
      }

    });

    ServerPlayerEvents.AFTER_RESPAWN.register((player, other, isTrue) -> {
        var uuid = player.getUuidAsString();
        updatePlayer(uuid, player.isAlive() && !player.isSpectator());
    });

    // check for unregistered players
    ServerLifecycleEvents.SERVER_STARTING.register((server) -> {
      if(CONFIG.getItem("initialized").asBoolean().getValue()) {
        return;
      }

      var moddedServer = (ModdedServer) server;
      var saveHandler = moddedServer.getSaveHandler();
      for (var id : saveHandler.getSavedPlayerIds()) {
        var nbt = ((ModdedSaveHandler) saveHandler).loadPlayerData(id);
        if(nbt == null) {
          continue;
        }

        // get player data
        ConfigGroup playerData;
        ConfigBoolean isAlive;
        if(!CONFIG.hasItem("players." + id)) {
          var dateItem = new ConfigString("lastSeen",LocalDate.now().toString());
          isAlive = new ConfigBoolean("isAlive", true);
          playerData = new ConfigGroup(id, of(isAlive, dateItem));
          CONFIG.addItem("players", playerData);
        } else {
          playerData = CONFIG.getItem("players." + id).asGroup();
          isAlive = playerData.getItem("isAlive").asBoolean();
          var dateItem = playerData.getItem("lastSeen");
          dateItem.asString().setValue(LocalDate.now().toString());
        }

        // grab data from nbt
        var health = nbt.getFloat("Health");
        var gameMode = nbt.getInt("playerGameType");
        isAlive.setValue(health > 0 && gameMode != 3);
      } 
      CONFIG.setItem(new ConfigBoolean("initialized", true));
    });

    // when stopping see if we should shutdown.
    ServerLifecycleEvents.SERVER_STOPPED.register((server) -> {
      var alivePlayers = playersAlive(server);
      var forceDelete = isForceDelete(); 

      if (alivePlayers > 0 || forceDelete) {
        if (forceDelete) {
          LOGGER.warn("Server was force Reset");
        }
        var worldNames = new HashSet<String>();
        for (var world : server.getWorlds()) {
          var prop = world.getLevelProperties();
          if(prop instanceof ServerWorldProperties serProps) {
            var name = serProps.getLevelName();
            worldNames.add(name);
          }
        }
        for (var name : worldNames) {
          var files = FabricLoader.getInstance().getGameDir().toFile().listFiles((file) -> {
            return file.getName().startsWith(name) && file.isDirectory();
          });
          for (var file : files) {
            LOGGER.info("file: \"" + file.getName() + "\"" + " was marked to be deleted");
            deleteFile(file);
            
          }
        }
        CONFIG.getItem("players").asGroup().setValue(of());
        clearForceDelete();
      }

      CONFIG.saveFile();
    });
  }

  private static boolean isForceDelete() {
    var forceDelete = CONFIG.getItem("forceDelete");
    if(forceDelete == null) return false;
    return forceDelete.asBoolean().getValue();
  }

  private static void clearForceDelete() {
    CONFIG.removeItem("forceDelete");
  }

  public static void setForceDelete() {
    CONFIG.setItem(new ConfigBoolean("forceDelete", true));
  }

  public static int playersAlive(MinecraftServer server) {
    updateConfig();
    int numPlayers = 0;
    if(server.isHardcore()) {
      for (var playerData : CONFIG.getItem("players").asGroup().getValue()) {
        if (playerData.asGroup().getItem("isAlive").asBoolean().getValue()) {
          ++numPlayers;
        }
      }
    }
    return numPlayers;
  }

  private static void deleteFile(File file) {
    file.deleteOnExit();
    if(file.isDirectory()) {
      for (var subFile : file.listFiles()) {
        deleteFile(subFile);
      }
    }
  }

  private static void setupConfig() {
    if(!CONFIG.hasItem("players")) {
      CONFIG.addItem(new ConfigGroup("players"));
    }

    if(!CONFIG.hasItem("initialized")) {
      CONFIG.addItem(new ConfigBoolean("initialized"));
    }

    if(!CONFIG.hasItem("daysSinceActive")) {
      CONFIG.addItem(new ConfigNumber("daysSinceActive", 30));
    }
    CONFIG.saveFile();
  }

  private static void updateConfig() {
    var daysSinceActive = CONFIG.getItem("daysSinceActive").asNumber().getValue();
    var players = CONFIG.getItem("players").asGroup();
    for (var playerData : players.getValue()) {
      var lastSeen = playerData.asGroup().getItem("lastSeen").asString().getValue();
      var lastSeenDate = LocalDate.parse(lastSeen);
      var cutOff = LocalDate.now().minusDays(daysSinceActive.longValue());
      if (lastSeenDate.isBefore(cutOff)) {
        players.removeItem(playerData.getKey());
      }
    }
  }

  private static void updatePlayer(String uuid, boolean isAlive) {
    var players = CONFIG.getItem("players").asGroup();
    if(!players.hasItem(uuid)) {
      var playerData = new ConfigGroup(uuid, of(
        new ConfigString("lastSeen", LocalDate.now().toString()),
        new ConfigBoolean("isAlive", isAlive)
      ));
      players.addItem(playerData);
    } else {
      var playerData = players.getItem(uuid).asGroup();
      playerData.getItem("isAlive").asBoolean().setValue(isAlive);
      playerData.getItem("lastSeen").asString().setValue(LocalDate.now().toString());
    }
    CONFIG.saveFile();
  }
}
