package me.blvckbytes.item_predicate_parser;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.MusicInstrument;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.Objects;

public class ItemBuilder {

  private final ItemStack item;
  private final ItemMeta meta;

  public ItemBuilder(Material material, int amount) {
    this.item = new ItemStack(material, amount);
    this.meta = Objects.requireNonNull(item.getItemMeta());
  }

  public ItemBuilder withBaseType(PotionType type) {
    if (!(meta instanceof PotionMeta potionMeta))
      throw new IllegalStateException("Cannot add potion-effect to a non-potion item");

    potionMeta.setBasePotionType(type);
    return this;
  }

  public ItemBuilder withEffect(PotionEffectType type, int level, int duration) {
    if (!(meta instanceof PotionMeta potionMeta))
      throw new IllegalStateException("Cannot add potion-effect to a non-potion item");

    potionMeta.addCustomEffect(new PotionEffect(type, duration, level - 1), true);
    return this;
  }

  public ItemBuilder withEnchantment(Enchantment enchantment, int level) {
    if (meta instanceof EnchantmentStorageMeta enchantmentStorageMeta) {
      enchantmentStorageMeta.addStoredEnchant(enchantment, level, true);
      return this;
    }

    meta.addEnchant(enchantment, level, true);
    return this;
  }

  public ItemBuilder withRepairCost(int repairCost) {
    if (!(meta instanceof Repairable repairable))
      throw new IllegalStateException("Cannot set repair-cost on a non-repairable item");

    repairable.setRepairCost(repairCost);
    return this;
  }

  public ItemBuilder withDamage() {
    if (!(meta instanceof Damageable damageable))
      throw new IllegalStateException("Cannot set damage on a non-damageable item");

    damageable.setDamage(item.getType().getMaxDurability() / 2);
    return this;
  }

  public ItemBuilder withMusicInstrument(MusicInstrument instrument) {
    if (!(meta instanceof MusicInstrumentMeta musicInstrumentMeta))
      throw new IllegalStateException("Cannot set instrument on a non-music-instrument item");

    musicInstrumentMeta.setInstrument(instrument);
    return this;
  }

  public ItemBuilder withName(Component name) {
    meta.displayName(name);
    return this;
  }

  public ItemStack build() {
    item.setItemMeta(meta);
    return item;
  }
}
