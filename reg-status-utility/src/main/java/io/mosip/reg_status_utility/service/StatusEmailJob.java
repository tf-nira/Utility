package io.mosip.reg_status_utility.service;

import io.mosip.reg_status_utility.dto.CredentialProjection;
import io.mosip.reg_status_utility.dto.StatusCodeCountProjection;
import io.mosip.reg_status_utility.repository.CredentialRepository;
import io.mosip.reg_status_utility.repository.RegistrationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
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
    private CredentialRepository credentialRepository;

    @Autowired
    private EmailService emailService;

    @Value("#{'${recipient.emails}'.split(',')}")
    private List<String> recipients;

    private Map<String, Long> prevCount;

    private static final String STATE_FILE_PATH = "reg_status_prev_count.txt";

    @PostConstruct
    public void init() {
        this.prevCount = loadStateFromFile();
        if (this.prevCount == null) {
            this.prevCount = new HashMap<>();
        }
    }

    @Transactional("credentialTransactionManager")
    @Scheduled(cron = "${mosip.send.status.cron.expression}")
    public void sendStatusReport () {
        System.out.println("Abhay Dev Gautam");
        List<CredentialProjection> results = credentialRepository.findAllCredentialIdAndStatusCode();

        for(CredentialProjection row : results){
            System.out.println("Credential ID : " + row.getCredentialId() + "Status Code :" + row.getStatusCode());
        }
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
        saveStateToFile(prevCount);

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

    private static final Logger log = LoggerFactory.getLogger(StatusEmailJob.class);

    private void saveStateToFile(Map<String, Long> mapToSave) {
        Path path = Paths.get(STATE_FILE_PATH);
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = Files.createTempFile(parent != null ? parent : Paths.get("."), "reg_state", ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8, StandardOpenOption.WRITE)) {
                for (Map.Entry<String, Long> e : mapToSave.entrySet()) {
                    writer.write(e.getKey() + "=" + e.getValue());
                    writer.newLine();
                }
                writer.flush();
            }
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("Failed to save state to file {}", STATE_FILE_PATH, e);
        }
    }

    private Map<String, Long> loadStateFromFile() {
        Path path = Paths.get(STATE_FILE_PATH);
        if (!Files.exists(path)) {
            log.info("No previous state file found at {}", STATE_FILE_PATH);
            return null;
        }

        Map<String, Long> map = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("=", 2);
                if (parts.length != 2) {
                    log.warn("Skipping malformed line in state file: {}", line);
                    continue;
                }
                String key = parts[0].trim();
                String valStr = parts[1].trim();
                try {
                    long value = Long.parseLong(valStr);
                    map.put(key, value);
                } catch (NumberFormatException nfe) {
                    log.warn("Skipping line with invalid number '{}': {}", valStr, line);
                }
            }
        } catch (IOException e) {
            log.error("Failed to load state from file {}", STATE_FILE_PATH, e);
            return null;
        }
        return map;
    }
}
