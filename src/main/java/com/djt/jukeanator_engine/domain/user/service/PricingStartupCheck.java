package com.djt.jukeanator_engine.domain.user.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import com.djt.jukeanator_engine.domain.user.dto.CreditPackageDto;
import com.djt.jukeanator_engine.ui.config.JukeANatorUserInterfaceProperties;

/**
 * Logs what a song costs through each way of paying, and warns about pricing mistakes:
 * <ul>
 * <li>an Add Funds package whose credits don't divide evenly by the Web/Mobile song cost, which
 * strands credits that can't buy a song;</li>
 * <li>a Web/Mobile package that is not more expensive per song than the card reader (web payments
 * carry higher fees);</li>
 * <li>a card reader that is cheaper than cash ({@code credit-card-cost-multiplier} below 1).</li>
 * </ul>
 * Only warns -- it never stops startup. Uses this instance's own {@code user-interface.*} settings,
 * read directly rather than through a secured service, since it runs before any user is signed
 * in. See {@code docs/pricing.md}.
 */
public class PricingStartupCheck implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(PricingStartupCheck.class);

  private final JukeANatorUserInterfaceProperties userInterfaceProperties;
  private final List<CreditPackageDto> creditPackages;

  public PricingStartupCheck(JukeANatorUserInterfaceProperties userInterfaceProperties,
      List<CreditPackageDto> creditPackages) {
    this.userInterfaceProperties = userInterfaceProperties;
    this.creditPackages = creditPackages;
  }

  /** The kiosk's card-reader pricing settings, for {@link #analyze}. */
  public record CardPricing(int creditsPerDollar, int fiveDollarBonus, int tenDollarBonus,
      BigDecimal creditCardCostMultiplier) {
  }

  /** What {@link #analyze} found: lines to log at INFO, and warnings. */
  public record Report(List<String> info, List<String> warnings) {
  }

  @Override
  public void run(ApplicationArguments args) {
    JukeANatorUserInterfaceProperties ui = userInterfaceProperties;
    int webSongCost = ui.getWebCostMultiplier(); // a normal play: 1 kiosk credit x the multiplier
    CardPricing card = ui.isEnableCreditCardProcessing()
        ? new CardPricing(ui.getCreditsPerDollar(), ui.getFiveDollarBonusCredits(),
            ui.getTenDollarBonusCredits(), ui.getCreditCardCostMultiplier())
        : null;

    Report report = analyze(creditPackages, webSongCost, card);
    report.info().forEach(log::info);
    report.warnings().forEach(log::warn);
  }

  /**
   * @param webSongCost credits a normal play costs on the Web/Mobile UI
   * @param card the card reader's pricing, or {@code null} when card processing is off
   */
  public static Report analyze(List<CreditPackageDto> packages, int webSongCost,
      CardPricing card) {

    List<String> info = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    CreditPackageDto cheapestWebPackage = null;
    BigDecimal cheapestWebPerSong = null;
    for (CreditPackageDto pkg : packages) {
      int totalCredits = pkg.credits() + pkg.bonusCredits();
      int songs = webSongCost > 0 ? totalCredits / webSongCost : 0;
      int leftover = webSongCost > 0 ? totalCredits % webSongCost : 0;
      if (leftover != 0) {
        warnings.add(String.format(
            "Add Funds package %s: %d credits leaves %d credit(s) that cannot buy a song "
                + "(a song costs %d credits on the Web/Mobile UI). Use a total that divides "
                + "evenly by %d.",
            pkg.id(), totalCredits, leftover, webSongCost, webSongCost));
      }
      if (songs == 0) {
        warnings.add(String.format("Add Funds package %s: %d credits does not buy a single song.",
            pkg.id(), totalCredits));
        continue;
      }
      BigDecimal perSong = perSong(pkg.priceUsd(), songs);
      info.add(String.format("Add Funds package %s: %s for %d credits = %d songs, %s per song",
          pkg.id(), dollars(pkg.priceUsd()), totalCredits, songs, dollars(perSong)));
      if (cheapestWebPerSong == null || perSong.compareTo(cheapestWebPerSong) < 0) {
        cheapestWebPerSong = perSong;
        cheapestWebPackage = pkg;
      }
    }

    if (card != null) {
      if (card.creditCardCostMultiplier().compareTo(BigDecimal.ONE) < 0) {
        warnings.add(String.format(
            "credit-card-cost-multiplier is %s, below 1, so the card reader is cheaper than cash.",
            card.creditCardCostMultiplier().toPlainString()));
      }

      int dearestCardDollars = 0;
      BigDecimal dearestCardPerSong = null;
      for (int dollars : KioskCreditMath.CARD_READER_PRICE_DOLLARS) {
        int songs = KioskCreditMath.creditCardCreditsFor(dollars, card.creditsPerDollar(),
            card.fiveDollarBonus(), card.tenDollarBonus(), card.creditCardCostMultiplier());
        if (songs == 0) {
          warnings.add(String.format("Card reader $%d buys no songs.", dollars));
          continue;
        }
        BigDecimal perSong = perSong(BigDecimal.valueOf(dollars), songs);
        info.add(String.format("Card reader $%d: %d songs, %s per song", dollars, songs,
            dollars(perSong)));
        if (dearestCardPerSong == null || perSong.compareTo(dearestCardPerSong) > 0) {
          dearestCardPerSong = perSong;
          dearestCardDollars = dollars;
        }
      }

      if (cheapestWebPerSong != null && dearestCardPerSong != null
          && cheapestWebPerSong.compareTo(dearestCardPerSong) <= 0) {
        warnings.add(String.format(
            "Add Funds package %s costs %s per song, which is not more than the card reader's "
                + "%s per song (the $%d button). Web payments carry higher fees, so every web "
                + "package should cost more per song than the card reader.",
            cheapestWebPackage.id(), dollars(cheapestWebPerSong), dollars(dearestCardPerSong),
            dearestCardDollars));
      }
    }

    return new Report(List.copyOf(info), List.copyOf(warnings));
  }

  private static BigDecimal perSong(BigDecimal price, int songs) {
    return price.divide(BigDecimal.valueOf(songs), 4, RoundingMode.HALF_UP);
  }

  private static String dollars(BigDecimal amount) {
    return "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }
}
