package com.djt.jukeanator_engine.domain.licenseserver.event;

import java.util.List;

/**
 * Published by {@code LegacyFrontController} when a visitor submits the public website's contact
 * form ({@code command=feedback}). {@code recipients} comes from {@code
 * license-server.feedback-recipients}; {@code visitorEmail} may be blank.
 */
public record ContactFeedbackSubmittedEvent(List<String> recipients, String visitorEmail,
    String comments) {

  public ContactFeedbackSubmittedEvent {
    recipients = recipients == null ? List.of() : List.copyOf(recipients);
  }
}
