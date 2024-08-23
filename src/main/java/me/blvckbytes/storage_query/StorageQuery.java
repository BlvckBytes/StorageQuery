package me.blvckbytes.storage_query;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Objects;

public class StorageQuery extends JavaPlugin {

  @Override
  public void onEnable() {
    var logger = getLogger();

    var registryGerman = TranslationRegistry.load("/de_de.json", Locale.GERMAN, logger);
    var registryEnglish = TranslationRegistry.load("/en_us.json", Locale.ENGLISH, logger);

    if (registryGerman == null || registryEnglish == null) {
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    var commandExecutor = new StorageQueryCommand(registryGerman, registryEnglish);
    var pluginCommand = Objects.requireNonNull(getCommand("lagersuche"));

    pluginCommand.setExecutor(commandExecutor);
    pluginCommand.setTabCompleter(commandExecutor);
  }
}
