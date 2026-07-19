package io.mosip.reg_status_utility.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TemplateStatusEmailJob {

    private final EmailService emailService;

    @Value("classpath:status mail html template.txt")
    private Resource statusMailTemplate;

    @Value("classpath:recipients.txt")
    private Resource recipientsResource;

    public TemplateStatusEmailJob(EmailService emailService) {
        this.emailService = emailService;
    }

    @Scheduled(cron = "${mosip.template.status.email.cron.expression}")
    public void sendTemplateStatusEmail() {
        try {
            // Read recipients from file at runtime (no restart needed)
            String recipientsContent;
            try (InputStream is = recipientsResource.getInputStream()) {
                recipientsContent = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            List<String> recipients = Arrays.stream(recipientsContent.split(","))
                    .map(String::trim)
                    .filter(email -> !email.isEmpty())
                    .collect(Collectors.toList());

            if (recipients.isEmpty()) {
                log.warn("No recipients found in recipients.txt. Skipping email.");
                return;
            }

            // Read email body from template
            String emailBody;
            try (InputStream inputStream = statusMailTemplate.getInputStream()) {
                emailBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            emailService.sendEmail(recipients, "Application Status Report", emailBody);
        } catch (IOException e) {
            log.error("Unable to read the status email template or recipients file", e);
        }
    }
}