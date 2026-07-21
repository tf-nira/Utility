package io.mosip.reg_status_utility.service;

import io.mosip.reg_status_utility.dto.StatusCodeCountProjection;
import io.mosip.reg_status_utility.repository.RegistrationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
public class OpenCrvsJob {

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private EmailService emailService;

    @Value("#{'${recipient.emails.pvt}'.split(',')}")
    private List<String> recipients;

    @Scheduled(cron = "${mosip.update.crvs.cron.expression}")
    public void sendStatusReport () {
       int updatedCount= registrationRepository.updateOpenCrvs();
        String emailBody = String.format(
                "<html>" +
                        "<body style='font-family: Arial, sans-serif;'>" +
                        "<h3>Open CRVS Update Summary</h3>" +
                        "<p>The Open CRVS records have been updated successfully.</p>" +
                        "<table style='border-collapse: collapse;'>" +
                        "<tr>" +
                        "<td style='padding:8px; border:1px solid #ccc;'><strong>Updated Records</strong></td>" +
                        "<td style='padding:8px; border:1px solid #ccc;'>%d</td>" +
                        "</tr>" +
                        "</table>" +
                        "</body>" +
                        "</html>",
                updatedCount
        );

        emailService.sendEmail(recipients, "Open CRVS Updated count", emailBody);
    }
}
