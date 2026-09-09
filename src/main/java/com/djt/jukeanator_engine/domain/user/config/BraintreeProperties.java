package com.djt.jukeanator_engine.domain.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Properties bound to the {@code braintree:} YAML prefix. Credentials are always sourced from
 * environment variables in {@code application.yml} (never hardcoded) — see that file's
 * {@code braintree:} block.
 */
@Validated
@ConfigurationProperties(prefix = "braintree")
public class BraintreeProperties {

  private String environment = "sandbox"; // sandbox | production
  private String merchantId;
  private String publicKey;
  private String privateKey;

  public String getEnvironment() {
    return environment;
  }

  public void setEnvironment(String environment) {
    this.environment = environment;
  }

  public String getMerchantId() {
    return merchantId;
  }

  public void setMerchantId(String merchantId) {
    this.merchantId = merchantId;
  }

  public String getPublicKey() {
    return publicKey;
  }

  public void setPublicKey(String publicKey) {
    this.publicKey = publicKey;
  }

  public String getPrivateKey() {
    return privateKey;
  }

  public void setPrivateKey(String privateKey) {
    this.privateKey = privateKey;
  }
}
