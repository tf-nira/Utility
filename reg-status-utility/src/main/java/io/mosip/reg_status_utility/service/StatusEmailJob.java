package io.mosip.reg_status_utility.service;

import io.mosip.reg_status_utility.dto.StatusCodeCountProjection;
import io.mosip.reg_status_utility.repository.RegistrationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Time;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatusEmailJob {

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private EmailService emailService;

    @Value("#{'${recipient.emails}'.split(',')}")
    private List<String> recipients;

    private Map<String, Long> prevCount;

    @Scheduled(cron = "${mosip.send.status.cron.expression}")
    public void sendStatusReport () {
        registrationRepository.updateStatusCodes();
        List<StatusCodeCountProjection> data = registrationRepository.getStatusCodeCount();

        Map<String, Long> newCount = data.stream()
                .collect(Collectors.toMap(
                        StatusCodeCountProjection::getStatusCode,
                        StatusCodeCountProjection::getCount
                ));

        Map<String, Long> diffCount = computeDifference(newCount);

        StringBuilder emailBody = prepareEmailBody(newCount, diffCount, data.get(0).getCurrentDate(), data.get(0).getCurrentTime());
        emailService.sendEmail(recipients, "Registration Status Report Production " + data.get(0).getCurrentTime(), emailBody.toString());
    }

    public Map<String, Long> computeDifference (Map<String, Long> data) {
        Map<String, Long> diffCount = new HashMap<>();

        if (prevCount != null) {
            for (String key: data.keySet()) {
                long val1 = data.getOrDefault(key, 0L);
                long val2 = prevCount.getOrDefault(key, 0L);
                diffCount.put(key, val1-val2);
            }
        }
        prevCount = data;

        return diffCount;
    }

    public StringBuilder prepareEmailBody (Map<String, Long> count, Map<String, Long> diffCount, Date date, Time time) {
        StringBuilder body = new StringBuilder();

        body.append("<h3>Status Count Report</h3>");
        body.append("<p>Current Date: ").append(date).append("<br>");
        body.append("Current Time: ").append(time).append("</p>");
        body.append("<table border='1' cellpadding='5' cellspacing='0'>");
        body.append("<tr><th>Status Code</th><th>Count</th><th>Difference</th></tr>");

        for (String key : count.keySet()) {
            Long currentCount = count.getOrDefault(key, 0L);
            Long diff = diffCount.getOrDefault(key, 0L);

            body.append("<tr><td>").append(key).append("</td>")
                    .append("<td>").append(currentCount).append("</td>")
                    .append("<td>").append(diff).append("</td></tr>");
        }

        body.append("</table>");

        return body;
    }
}
