package com.djt.jukeanator_engine.domain.user.dto;
import java.math.BigDecimal;
public record CreditPackageDto(String id, int credits, int bonusCredits, BigDecimal priceUsd, String badge) {}
