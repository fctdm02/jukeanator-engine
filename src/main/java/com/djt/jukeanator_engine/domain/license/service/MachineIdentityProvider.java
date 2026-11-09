package com.djt.jukeanator_engine.domain.license.service;

import java.util.List;

/**
 * Computes this computer's legacy machine ids (the "serial number" a license key is bound to).
 */
public interface MachineIdentityProvider {

  /**
   * Every machine id the legacy client could have used on this computer, in the order a license
   * file should be tested against them. Never null; may be empty.
   */
  List<String> candidateIds();

  /**
   * The machine id to send when provisioning a new license: the legacy choice of the motherboard
   * id when available, otherwise the network-card id. Null if none can be determined.
   */
  String provisioningId();
}
