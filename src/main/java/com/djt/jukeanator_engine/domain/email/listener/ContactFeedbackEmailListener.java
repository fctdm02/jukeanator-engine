package com.djt.jukeanator_engine.domain.email.listener;

import java.util.Objects;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import com.djt.jukeanator_engine.domain.common.security.SecurityContextPropagatingRunnable;
import com.djt.jukeanator_engine.domain.common.security.SystemPrincipal;
import com.djt.jukeanator_engine.domain.email.service.EmailService;
import com.djt.jukeanator_engine.domain.licenseserver.event.ContactFeedbackSubmittedEvent;

/**
 * Emails the public website's contact form off the request thread ({@code @Async}) so a slow SMTP
 * send never delays the visitor's redirect to the thank-you page. The form is anonymous, so the
 * {@link EmailService} call runs as the system principal ({@code ServiceSecurityAspect} rejects
 * unauthenticated service calls).
 */
public class ContactFeedbackEmailListener {

  private final EmailService emailService;

  public ContactFeedbackEmailListener(EmailService emailService) {
    this.emailService = Objects.requireNonNull(emailService);
  }

  @Async
  @EventListener
  public void onContactFeedbackSubmitted(ContactFeedbackSubmittedEvent event) {
    new SecurityContextPropagatingRunnable(() -> emailService.sendContactFeedbackEmail(
        event.recipients(), event.visitorEmail(), event.comments()),
        SystemPrincipal.SystemAuthenticationToken.INSTANCE).run();
  }
}
