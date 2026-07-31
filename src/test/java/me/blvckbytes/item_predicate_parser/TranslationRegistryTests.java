package me.blvckbytes.item_predicate_parser;

import me.blvckbytes.item_predicate_parser.predicate.stringify.PlainStringifier;
import me.blvckbytes.item_predicate_parser.translation.LangKeyedSource;
import me.blvckbytes.item_predicate_parser.translation.keyed.LangKeyedEnchantment;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.MusicInstrument;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TranslationRegistryTests extends ParseTestBase {

  private static final List<LangKeyedEnchantment> ENCHANTMENTS = Registry.ENCHANTMENT.stream().map(LangKeyedEnchantment::new).toList();
  private static final LangKeyedEnchantment UNBREAKING = new LangKeyedEnchantment(Enchantment.UNBREAKING);

  @Test
  public void shouldAppendCollisionPrefixesBetweenSources() {
    new CollisionPrefixCaseBuilder(translationRegistryFactory)
      .withSource(new LangKeyedSource(ENCHANTMENTS, "[Enchantment] "))
      .withSingleSource(UNBREAKING, "[Custom] ")
      .expectResult(UNBREAKING, "[Enchantment] ")
      .expectResult(UNBREAKING, "[Custom] ")
      .execute("unbr");
  }

  @Test
  public void shouldAppendCollisionPrefixesInSameSource() {
    new CollisionPrefixCaseBuilder(translationRegistryFactory)
      .withSource(new LangKeyedSource(List.of(
        UNBREAKING, UNBREAKING
      ), ""))
      .expectResult(UNBREAKING, "[1] ")
      .expectResult(UNBREAKING, "[2] ")
      .execute("unbr");
  }

  @Test
  public void shouldCombineCollisionPrefixes() {
    new CollisionPrefixCaseBuilder(translationRegistryFactory)
      .withSource(new LangKeyedSource(List.of(
        UNBREAKING, UNBREAKING
      ), "[A]"))
      .withSingleSource(UNBREAKING, "[B]")
      .expectResult(UNBREAKING, "[A] [1] ")
      .expectResult(UNBREAKING, "[A] [2] ")
      .expectResult(UNBREAKING, "[B] ")
      .execute("unbr");
  }

  @Test
  public void shouldGenerateMultiPartPredicateForItem() {
    makeGenerateSingleCase(
      new ItemBuilder(Material.DIAMOND_PICKAXE, 5)
        .withEnchantment(Enchantment.UNBREAKING, 3)
        .withEnchantment(Enchantment.EFFICIENCY, 5)
        .withDamage()
        .withName(Component.text("my name")),
      EnumSet.allOf(GeneratingDetail.class),
      "Diamond-Pickaxe Amount 5 Efficiency 5 Unbreaking 3 Enchantment-Count 2 Deterioration 1 \"my name\" Has-Name Repair-Cost 0"
    );
  }

  @Test
  public void shouldGeneratePredicatesForPotions() {
    makeGenerateSingleCase(
      new ItemBuilder(Material.POTION, 2)
        .withEffect(PotionEffectType.FIRE_RESISTANCE, 1, 60)
        .withEffect(PotionEffectType.SPEED, 2, 120),
      EnumSet.allOf(GeneratingDetail.class),
      "Potion Amount 2 Fire-Resistance 1 60 Speed 2 120 Effect-Count 2"
    );
  }

  @Test
  public void shouldGenerateEnchantmentsForABook() {
    makeGenerateSingleCase(
      new ItemBuilder(Material.ENCHANTED_BOOK, 1)
        .withEnchantment(Enchantment.SILK_TOUCH, 1)
        .withEnchantment(Enchantment.MENDING, 1)
        .withEnchantment(Enchantment.FORTUNE, 3)
        .withEnchantment(Enchantment.SHARPNESS, 10)
        .withRepairCost(10),
      EnumSet.allOf(GeneratingDetail.class),
      "Enchanted-Book Amount 1 Fortune 3 Mending Sharpness 10 Silk-Touch Enchantment-Count 4 Repair-Cost >0"
    );
  }

  @Test
  public void shouldNotGenerateNonApplyingDetails() {
    makeGenerateSingleCase(
      new ItemBuilder(Material.QUARTZ_BLOCK, 3),
      EnumSet.allOf(GeneratingDetail.class),
      "Block-of-Quartz Amount 3"
    );
  }

  @Test
  public void shouldGeneratePotionTypePredicates() {
    makeGenerateSingleCase(
      new ItemBuilder(Material.POTION, 1),
      EnumSet.of(GeneratingDetail.POTION_TYPE),
      "Potion"
    );

    makeGenerateSingleCase(
      new ItemBuilder(Material.POTION, 1)
        .withBaseType(PotionType.WATER),
      EnumSet.of(GeneratingDetail.POTION_TYPE),
      "Potion Water-Bottle"
    );

    makeGenerateSingleCase(
      new ItemBuilder(Material.POTION, 1)
        .withBaseType(PotionType.AWKWARD),
      EnumSet.of(GeneratingDetail.POTION_TYPE),
      "Potion Awkward-Potion"
    );

    makeGenerateSingleCase(
      new ItemBuilder(Material.POTION, 1)
        .withBaseType(PotionType.MUNDANE),
      EnumSet.of(GeneratingDetail.POTION_TYPE),
      "Potion Mundane-Potion"
    );

    makeGenerateSingleCase(
      new ItemBuilder(Material.POTION, 1)
        .withBaseType(PotionType.THICK),
      EnumSet.of(GeneratingDetail.POTION_TYPE),
      "Potion Thick-Potion"
    );
  }

  @Test
  public void shouldGenerateMusicInstrumentPredicates() {
    makeGenerateSingleCase(
      new ItemBuilder(Material.GOAT_HORN, 1)
        .withMusicInstrument(MusicInstrument.ADMIRE_GOAT_HORN),
      EnumSet.of(GeneratingDetail.MUSIC_INSTRUMENT),
      "Goat-Horn Admire"
    );

    makeGenerateSingleCase(
      new ItemBuilder(Material.GOAT_HORN, 1)
        .withMusicInstrument(MusicInstrument.CALL_GOAT_HORN),
      EnumSet.of(GeneratingDetail.MUSIC_INSTRUMENT),
      "Goat-Horn Call"
    );

    makeGenerateSingleCase(
      new ItemBuilder(Material.GOAT_HORN, 1)
        .withMusicInstrument(MusicInstrument.DREAM_GOAT_HORN),
      EnumSet.of(GeneratingDetail.MUSIC_INSTRUMENT),
      "Goat-Horn Dream"
    );
  }

  @Test
  public void shouldGenerateDeteriorationPredicates() {
    makeGenerateSingleCase(
      new ItemBuilder(Material.DIAMOND_SHOVEL, 1)
        .withDamage(),
      EnumSet.of(GeneratingDetail.DETERIORATION),
      "Diamond-Shovel Deterioration 1"
    );

    makeGenerateSingleCase(
      new ItemBuilder(Material.DIAMOND_SHOVEL, 1),
      EnumSet.of(GeneratingDetail.DETERIORATION),
      "Diamond-Shovel Deterioration 0 0"
    );
  }

  @Test
  public void shouldGenerateDisplayNamePredicate() {
    makeGenerateSingleCase(
      new ItemBuilder(Material.DIRT, 1)
        .withName(Component.text("My Name").color(NamedTextColor.LIGHT_PURPLE)),
      EnumSet.of(GeneratingDetail.DISPLAY_NAME),
      "Dirt \"My Name\""
    );

    makeGenerateSingleCase(
      new ItemBuilder(Material.DIRT, 1)
        .withName(Component.text("My \" Name")),
      EnumSet.of(GeneratingDetail.DISPLAY_NAME),
      "Dirt \"My \\\" Name\""
    );
  }

  @Test
  public void shouldGenerateHasNamePredicate() {
    makeGenerateSingleCase(
      new ItemBuilder(Material.DIRT, 1)
        .withName(Component.text("My Name")),
      EnumSet.of(GeneratingDetail.HAS_NAME),
      "Dirt Has-Name"
    );
  }

  @Test
  public void shouldGenerateRepairCostPredicate() {
    makeGenerateSingleCase(
      new ItemBuilder(Material.DIAMOND_PICKAXE, 1),
      EnumSet.of(GeneratingDetail.REPAIR_COST),
      "Diamond-Pickaxe Repair-Cost 0"
    );

    makeGenerateSingleCase(
      new ItemBuilder(Material.DIAMOND_PICKAXE, 1)
        .withRepairCost(10),
      EnumSet.of(GeneratingDetail.REPAIR_COST),
      "Diamond-Pickaxe Repair-Cost >0"
    );
  }

  @Test
  public void shouldGenerateMaterialOrChain() {
    makeGenerateOrChainCase(
      List.of(
        new ItemBuilder(Material.DIRT, 1),
        new ItemBuilder(Material.IRON_INGOT, 1),
        new ItemBuilder(Material.DIAMOND_PICKAXE, 1)
      ),
      EnumSet.noneOf(GeneratingDetail.class),
      "Dirt or Iron-Ingot or Diamond-Pickaxe"
    );
  }

  @Test
  public void shouldGenerateMaterialAndAmountOrChain() {
    makeGenerateOrChainCase(
      List.of(
        new ItemBuilder(Material.DIRT, 1),
        new ItemBuilder(Material.IRON_INGOT, 2),
        new ItemBuilder(Material.DIAMOND_PICKAXE, 3)
      ),
      EnumSet.of(GeneratingDetail.AMOUNT),
      "Dirt Amount 1 or Iron-Ingot Amount 2 or Diamond-Pickaxe Amount 3"
    );
  }

  private void makeGenerateSingleCase(ItemBuilder itemBuilder, EnumSet<GeneratingDetail> details, String expectedPredicate) {
    var generatedPredicate = translationRegistry.generatePredicateFor(itemBuilder.build(), details);

    assertNotNull(generatedPredicate);

    var actualPredicateString = PlainStringifier.stringify(generatedPredicate, true);

    assertEquals(expectedPredicate, actualPredicateString);
  }

  private void makeGenerateOrChainCase(List<ItemBuilder> itemBuilders, EnumSet<GeneratingDetail> details, String expectedPredicate) {
    var generatedPredicate = translationRegistry.generateOrChainPredicateFor(itemBuilders.stream().map(ItemBuilder::build).toList(), details);

    assertNotNull(generatedPredicate);

    var actualPredicateString = PlainStringifier.stringify(generatedPredicate, true);

    assertEquals(expectedPredicate, actualPredicateString);
  }
}
