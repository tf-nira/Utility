package io.mosip.reg_status_utility.service;

import io.mosip.reg_status_utility.dto.StatusCodeCountProjection;
import io.mosip.reg_status_utility.repository.mvs.MvsRepository;
import io.mosip.reg_status_utility.repository.RegistrationRepository;
import io.mosip.reg_status_utility.repository.ida.IdaRepository;
import io.mosip.reg_status_utility.repository.print.CardDetailRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TemplateStatusEmailJob {

    private final EmailService emailService;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private CardDetailRepository cardDetailRepository;

    @Autowired
    private IdaRepository idaRepository;

    @Autowired
    private MvsRepository mvsRepository;

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

            // Build placeholder values from query results
            Map<String, String> placeholderValues = new HashMap<>();
            populatePlaceholders(placeholderValues);

            // Replace all placeholders in the template
            for (Map.Entry<String, String> entry : placeholderValues.entrySet()) {
                emailBody = emailBody.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }

            emailService.sendEmail(recipients, "Application Status Report", emailBody);
        } catch (IOException e) {
            log.error("Unable to read the status email template or recipients file", e);
        }
    }

    private String fmt(long number) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(0);
        return nf.format(number);
    }

    private String fmtDate(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
    }

    private Map<String, Long> toMap(List<StatusCodeCountProjection> list) {
        return list.stream()
                .collect(Collectors.toMap(
                        StatusCodeCountProjection::getStatusCode,
                        StatusCodeCountProjection::getCount
                ));
    }

    private long getCount(Map<String, Long> map, String key) {
        return map.getOrDefault(key, 0L);
    }

    private long getSingleCount(List<StatusCodeCountProjection> list) {
        return list.isEmpty() ? 0 : list.get(0).getCount();
    }

    private Map<String, Long> toCountMap(List<StatusCodeCountProjection> list) {
        return list.stream().collect(Collectors.toMap(
                projection -> projection.getStatusCode() == null ? "" : projection.getStatusCode().toUpperCase(Locale.ROOT),
                StatusCodeCountProjection::getCount,
                Long::sum
        ));
    }

    private void populatePlaceholders(Map<String, String> values) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate dayBeforeYesterday = today.minusDays(2);

        // Format: yyyy-MM-dd% (matches cr_dtimes LIKE '2026-07-16%')
        String datePattern1 = yesterday.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "%";
        String datePattern2 = dayBeforeYesterday.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "%";

        try {
            // Keep repository access sequential because JPA/Hibernate repository calls are not thread-safe here.
            List<StatusCodeCountProjection> r1 = registrationRepository.getStatusCodeCount();
            List<StatusCodeCountProjection> r2c = registrationRepository.getProcessTypeCountCumulative();
            List<StatusCodeCountProjection> r2d1 = registrationRepository.getProcessTypeCountByDate(datePattern1);
            List<StatusCodeCountProjection> r2d2 = registrationRepository.getProcessTypeCountByDate(datePattern2);
            List<StatusCodeCountProjection> r4c = registrationRepository.getProcessedCountCumulative();
            List<StatusCodeCountProjection> r4d1 = registrationRepository.getProcessedCountByDate(datePattern1);
            List<StatusCodeCountProjection> r4d2 = registrationRepository.getProcessedCountByDate(datePattern2);
            List<StatusCodeCountProjection> r3d1 = registrationRepository.getMAStatsByDate(datePattern1);
            List<StatusCodeCountProjection> r3d2 = registrationRepository.getMAStatsByDate(datePattern2);
            List<StatusCodeCountProjection> r3t = registrationRepository.getMAStatsTotal();
            List<StatusCodeCountProjection> rHoldFailed1 = registrationRepository.getHoldAndFailedByDate(datePattern1);
            List<StatusCodeCountProjection> rHoldFailed2 = registrationRepository.getHoldAndFailedByDate(datePattern2);
            List<StatusCodeCountProjection> r5d1 = cardDetailRepository.getPrintingCountByDate(datePattern1);
            List<StatusCodeCountProjection> r5d2 = cardDetailRepository.getPrintingCountByDate(datePattern2);
            List<StatusCodeCountProjection> r5t = cardDetailRepository.getPrintingCountTotal();
            List<StatusCodeCountProjection> r6d1 = idaRepository.getIDAStoredCountByDate(datePattern1);
            List<StatusCodeCountProjection> r6d2 = idaRepository.getIDAStoredCountByDate(datePattern2);
            List<StatusCodeCountProjection> r6t = idaRepository.getIDAStoredCountTotal();
            List<StatusCodeCountProjection> rMvsd1 = mvsRepository.getInqueueCountByDate(datePattern1);
            List<StatusCodeCountProjection> rMvsd2 = mvsRepository.getInqueueCountByDate(datePattern2);
            List<StatusCodeCountProjection> rMvst = mvsRepository.getInqueueCountTotal();

            // ---- REPORT 1: ALL APPLICATIONS ----
            Map<String, Long> countMap = toMap(r1);
            long processingCount = getCount(countMap, "PROCESSING") + getCount(countMap, "RESUMABLE");
            long onHoldCount = getCount(countMap, "ON_HOLD");
            long reprocessCount = getCount(countMap, "REPROCESS");
            long reprocessFailedCount = getCount(countMap, "REPROCESS_FAILED");
            long rejectedCount = getCount(countMap, "REJECTED");
            long pausedCount = getCount(countMap, "PAUSED_FOR_ADDITIONAL_INFO");
            long failedCount = getCount(countMap, "FAILED");
            long processedCount = getCount(countMap, "PROCESSED");
            long total = processingCount + onHoldCount + reprocessCount + reprocessFailedCount
                    + rejectedCount + pausedCount + failedCount + processedCount;
            values.put("AA_TOTAL_APPLICATIONS", fmt(total));
            values.put("AA_TOTAL_TODAY", fmt(total));
            values.put("AA_TOTAL_YESTERDAY", fmt(total));
            values.put("AA_TOTAL_CUMULATIVE", fmt(total));
            values.put("AA_PROCESSING", fmt(processingCount));
            values.put("AA_ON_HOLD", fmt(onHoldCount));
            values.put("AA_REPROCESS", fmt(reprocessCount));
            values.put("AA_REPROCESS_FAILED", fmt(reprocessFailedCount));
            values.put("AA_REJECTED", fmt(rejectedCount));
            values.put("AA_PAUSED_FOR_ADDITIONAL_INFO", fmt(pausedCount));
            values.put("AA_FAILED", fmt(failedCount));
            values.put("AA_PROCESSED", fmt(processedCount));
            values.put("AA_PROCESSED_TODAY", fmt(processedCount));
            values.put("AA_PROCESSED_YESTERDAY", fmt(processedCount));
            values.put("AA_PROCESSED_TOTAL", fmt(processedCount));

            // ---- REPORT 2: STATISTICS BY SERVICE TYPE ----
            values.put("SST_DATE1", fmtDate(yesterday));
            values.put("SST_DATE2", fmtDate(dayBeforeYesterday));
            values.put("SST_TODAY", fmtDate(yesterday));
            values.put("SST_YESTERDAY", fmtDate(dayBeforeYesterday));

            Map<String, Long> cumulativeMap = toMap(r2c);
            Map<String, Long> date1Map = toMap(r2d1);
            Map<String, Long> date2Map = toMap(r2d2);

            values.put("MA_CURRENT_DATE", fmtDate(yesterday));
            values.put("MA_PREVIOUS_DATE", fmtDate(dayBeforeYesterday));
            values.put("APP_TODAY", fmtDate(yesterday));
            values.put("APP_YESTERDAY", fmtDate(dayBeforeYesterday));
            values.put("PRT_TODAY", fmtDate(yesterday));
            values.put("PRT_YESTERDAY", fmtDate(dayBeforeYesterday));
            values.put("IDA_DATE1", fmtDate(yesterday));
            values.put("IDA_DATE2", fmtDate(dayBeforeYesterday));

            long totalCumulative = sumSelectedServiceTypeRows(cumulativeMap);
            long totalDate1 = sumSelectedServiceTypeRows(date1Map);
            long totalDate2 = sumSelectedServiceTypeRows(date2Map);
            values.put("SST_TOTAL", fmt(totalCumulative));
            values.put("SST_TOTAL_DATE1", fmt(totalDate1));
            values.put("SST_TOTAL_DATE2", fmt(totalDate2));

            putProcessTypeValues(values, cumulativeMap, date1Map, date2Map,
                    "NEW", "SST_NEW_REGISTRATION_TOTAL", "SST_NEW_REGISTRATION_DATE1", "SST_NEW_REGISTRATION_DATE2");
            putProcessTypeAliases(values, cumulativeMap, date1Map, date2Map,
                    "NEW", "SST_NEW_REGISTRATION_TOTAL", "SST_NEW_REGISTRATION_DATE1", "SST_NEW_REGISTRATION_DATE2");

            putProcessTypeValues(values, cumulativeMap, date1Map, date2Map,
                    "RENEWAL", "SST_RENEWAL_TOTAL", "SST_RENEWAL_DATE1", "SST_RENEWAL_DATE2");
            putProcessTypeAliases(values, cumulativeMap, date1Map, date2Map,
                    "RENEWAL", "SST_RENEWAL_TOTAL", "SST_RENEWAL_DATE1", "SST_RENEWAL_DATE2");

            putProcessTypeValues(values, cumulativeMap, date1Map, date2Map,
                    "FIRSTID", "SST_GET_FIRST_ID_TOTAL", "SST_GET_FIRST_ID_DATE1", "SST_GET_FIRST_ID_DATE2");
            putProcessTypeAliases(values, cumulativeMap, date1Map, date2Map,
                    "FIRSTID", "SST_GET_FIRST_ID_TOTAL", "SST_GET_FIRST_ID_DATE1", "SST_GET_FIRST_ID_DATE2");

            putProcessTypeValues(values, cumulativeMap, date1Map, date2Map,
                    "LOST", "SST_LOST_TOTAL", "SST_LOST_DATE1", "SST_LOST_DATE2");
            putProcessTypeAliases(values, cumulativeMap, date1Map, date2Map,
                    "LOST", "SST_LOST_TOTAL", "SST_LOST_DATE1", "SST_LOST_DATE2");

            putProcessTypeValues(values, cumulativeMap, date1Map, date2Map,
                    "UPDATE", "SST_CHANGE_OF_PARTICULAR_TOTAL", "SST_CHANGE_OF_PARTICULAR_DATE1", "SST_CHANGE_OF_PARTICULAR_DATE2");
            putProcessTypeAliases(values, cumulativeMap, date1Map, date2Map,
                    "UPDATE", "SST_CHANGE_OF_PARTICULAR_TOTAL", "SST_CHANGE_OF_PARTICULAR_DATE1", "SST_CHANGE_OF_PARTICULAR_DATE2");

            putProcessTypeValues(values, cumulativeMap, date1Map, date2Map,
                    "BIOMETRIC_CORRECTION", "SST_BIOMETRIC_CORRECTION_TOTAL", "SST_BIOMETRIC_CORRECTION_DATE1", "SST_BIOMETRIC_CORRECTION_DATE2");
            putProcessTypeAliases(values, cumulativeMap, date1Map, date2Map,
                    "BIOMETRIC_CORRECTION", "SST_BIOMETRIC_CORRECTION_TOTAL", "SST_BIOMETRIC_CORRECTION_DATE1", "SST_BIOMETRIC_CORRECTION_DATE2");

            putProcessTypeValues(values, cumulativeMap, date1Map, date2Map,
                    "Alien New Registration", "SST_ALIEN_NEW_REGISTRATION_TOTAL", "SST_ALIEN_NEW_REGISTRATION_DATE1", "SST_ALIEN_NEW_REGISTRATION_DATE2");
            putProcessTypeAliases(values, cumulativeMap, date1Map, date2Map,
                    "Alien New Registration", "SST_ALIEN_NEW_REGISTRATION_TOTAL", "SST_ALIEN_NEW_REGISTRATION_DATE1", "SST_ALIEN_NEW_REGISTRATION_DATE2");

            putProcessTypeValues(values, cumulativeMap, date1Map, date2Map,
                    "CRVS_NEW", "SST_CRVS_NEW_TOTAL", "SST_CRVS_NEW_DATE1", "SST_CRVS_NEW_DATE2");
            putProcessTypeAliases(values, cumulativeMap, date1Map, date2Map,
                    "CRVS_NEW", "SST_CRVS_NEW_TOTAL", "SST_CRVS_NEW_DATE1", "SST_CRVS_NEW_DATE2");

            // ---- REPORT 4: PROCESSED PACKETS ----
            Map<String, Long> processedTotalMap = toCountMap(r4c);
            Map<String, Long> processedTodayMap = toCountMap(r4d1);
            Map<String, Long> processedYesterdayMap = toCountMap(r4d2);

            long processedTodayTotal = processedTodayMap.values().stream().mapToLong(Long::longValue).sum();
            long processedYesterdayTotal = processedYesterdayMap.values().stream().mapToLong(Long::longValue).sum();
            long processedCumulativeTotal = processedTotalMap.values().stream().mapToLong(Long::longValue).sum();

            values.put("APP_PROCESSED_TODAY", fmt(processedTodayTotal));
            values.put("APP_PROCESSED_YESTERDAY", fmt(processedYesterdayTotal));
            values.put("APP_PROCESSED_TOTAL", fmt(processedCumulativeTotal));

            values.put("APP_MIGRATION_TODAY", fmt(getCount(processedTodayMap, "MIGRATOR")));
            values.put("APP_MIGRATION_YESTERDAY", fmt(getCount(processedYesterdayMap, "MIGRATOR")));
            values.put("APP_MIGRATION_TOTAL", fmt(getCount(processedTotalMap, "MIGRATOR")));

            values.put("APP_RENEWAL_TODAY", fmt(getCount(processedTodayMap, "RENEWAL")));
            values.put("APP_RENEWAL_YESTERDAY", fmt(getCount(processedYesterdayMap, "RENEWAL")));
            values.put("APP_RENEWAL_TOTAL", fmt(getCount(processedTotalMap, "RENEWAL")));

            values.put("APP_NEW_TODAY", fmt(getCount(processedTodayMap, "NEW")));
            values.put("APP_NEW_YESTERDAY", fmt(getCount(processedYesterdayMap, "NEW")));
            values.put("APP_NEW_TOTAL", fmt(getCount(processedTotalMap, "NEW")));

            values.put("APP_COP_TODAY", fmt(getCount(processedTodayMap, "UPDATE")));
            values.put("APP_COP_YESTERDAY", fmt(getCount(processedYesterdayMap, "UPDATE")));
            values.put("APP_COP_TOTAL", fmt(getCount(processedTotalMap, "UPDATE")));

            values.put("APP_LOST_TODAY", fmt(getCount(processedTodayMap, "LOST")));
            values.put("APP_LOST_YESTERDAY", fmt(getCount(processedYesterdayMap, "LOST")));
            values.put("APP_LOST_TOTAL", fmt(getCount(processedTotalMap, "LOST")));

            values.put("APP_FIRST_ID_TODAY", fmt(getCount(processedTodayMap, "FIRSTID")));
            values.put("APP_FIRST_ID_YESTERDAY", fmt(getCount(processedYesterdayMap, "FIRSTID")));
            values.put("APP_FIRST_ID_TOTAL", fmt(getCount(processedTotalMap, "FIRSTID")));

            values.put("APP_BIOMETRIC_CORRECTION_TODAY", fmt(getCount(processedTodayMap, "BIOMETRIC_CORRECTION")));
            values.put("APP_BIOMETRIC_CORRECTION_YESTERDAY", fmt(getCount(processedYesterdayMap, "BIOMETRIC_CORRECTION")));
            values.put("APP_BIOMETRIC_CORRECTION_TOTAL", fmt(getCount(processedTotalMap, "BIOMETRIC_CORRECTION")));

            values.put("APP_ALIEN_NEW_TODAY", fmt(getCount(processedTodayMap, "ALIEN NEW REGISTRATION")));
            values.put("APP_ALIEN_NEW_YESTERDAY", fmt(getCount(processedYesterdayMap, "ALIEN NEW REGISTRATION")));
            values.put("APP_ALIEN_NEW_TOTAL", fmt(getCount(processedTotalMap, "ALIEN NEW REGISTRATION")));

            values.put("APP_CRVS_NEW_TODAY", fmt(getCount(processedTodayMap, "CRVS_NEW")));
            values.put("APP_CRVS_NEW_YESTERDAY", fmt(getCount(processedYesterdayMap, "CRVS_NEW")));
            values.put("APP_CRVS_NEW_TOTAL", fmt(getCount(processedTotalMap, "CRVS_NEW")));

            // ---- REPORT 3: MA STATISTICS ----
            values.put("CURRENT_DATE", fmtDate(yesterday));
            values.put("PREVIOUS_DATE", fmtDate(dayBeforeYesterday));

            Map<String, Long> maTodayMap = toMap(r3d1);
            Map<String, Long> maYesterdayMap = toMap(r3d2);
            Map<String, Long> maTotalMap = toMap(r3t);
            Map<String, Long> holdFailedTodayMap = toMap(rHoldFailed1);
            Map<String, Long> holdFailedYesterdayMap = toMap(rHoldFailed2);
            long mvsToday = getSingleCount(rMvsd1);
            long mvsYesterday = getSingleCount(rMvsd2);
            long mvsTotal = getSingleCount(rMvst);

            long totalToday = processedTodayTotal
                    + getCount(holdFailedTodayMap, "ON_HOLD")
                    + getCount(maTodayMap, "INQUEUE")
                    + mvsToday
                    + getCount(holdFailedTodayMap, "FAILED");

            long totalYesterday = processedYesterdayTotal
                    + getCount(holdFailedYesterdayMap, "ON_HOLD")
                    + getCount(maYesterdayMap, "INQUEUE")
                    + mvsYesterday
                    + getCount(holdFailedYesterdayMap, "FAILED");

            long processedReportCumulative = processedCumulativeTotal
                    + getCount(maTotalMap, "INQUEUE")
                    + mvsTotal;

            values.put("APP_TOTAL_TODAY", fmt(totalToday));
            values.put("APP_TOTAL_YESTERDAY", fmt(totalYesterday));
            values.put("APP_TOTAL", fmt(processedReportCumulative));
            values.put("MA_TOTAL_CURRENT", fmt(getCount(maTodayMap, "APPROVED")
                    + getCount(maTodayMap, "INQUEUE")
                    + getCount(maTodayMap, "REJECTED")));
            values.put("MA_TOTAL_PREVIOUS", fmt(getCount(maYesterdayMap, "APPROVED")
                    + getCount(maYesterdayMap, "INQUEUE")
                    + getCount(maYesterdayMap, "REJECTED")));
            values.put("MA_TOTAL_CUMULATIVE", fmt(processedReportCumulative));
            values.put("MA_APPROVED_CURRENT", fmt(getCount(maTodayMap, "APPROVED")));
            values.put("MA_APPROVED_PREVIOUS", fmt(getCount(maYesterdayMap, "APPROVED")));
            values.put("MA_INQUEUE_CURRENT", fmt(getCount(maTodayMap, "INQUEUE")));
            values.put("MA_INQUEUE_PREVIOUS", fmt(getCount(maYesterdayMap, "INQUEUE")));
            values.put("MA_REJECTED_CURRENT", fmt(getCount(maTodayMap, "REJECTED")));
            values.put("MA_REJECTED_PREVIOUS", fmt(getCount(maYesterdayMap, "REJECTED")));
            values.put("APP_MA_INQUEUE_TODAY", fmt(getCount(maTodayMap, "INQUEUE")));
            values.put("APP_MA_INQUEUE_YESTERDAY", fmt(getCount(maYesterdayMap, "INQUEUE")));
            values.put("APP_MA_INQUEUE_TOTAL", fmt(getCount(maTotalMap, "INQUEUE")));
            values.put("APP_ON_HOLD_TODAY", fmt(getCount(holdFailedTodayMap, "ON_HOLD")));
            values.put("APP_ON_HOLD_YESTERDAY", fmt(getCount(holdFailedYesterdayMap, "ON_HOLD")));
            values.put("APP_FAILED_TODAY", fmt(getCount(holdFailedTodayMap, "FAILED")));
            values.put("APP_FAILED_YESTERDAY", fmt(getCount(holdFailedYesterdayMap, "FAILED")));

            // ---- REPORT 5: PRINTING ----
            values.put("PRT_SENT_TO_PERSO_TODAY", fmt(getSingleCount(r5d1)));
            values.put("PRT_SENT_TO_PERSO_YESTERDAY", fmt(getSingleCount(r5d2)));
            values.put("PRT_SENT_TO_PERSO_TOTAL", fmt(getSingleCount(r5t)));

            // ---- REPORT 6: STORED IN IDA ----
            values.put("IDA_DATE1", fmtDate(yesterday));
            values.put("IDA_DATE2", fmtDate(dayBeforeYesterday));
            long idaStoredDate1 = getCount(processedTodayMap, "MIGRATOR") + getCount(processedTodayMap, "NEW");
            long idaStoredDate2 = getCount(processedYesterdayMap, "MIGRATOR") + getCount(processedYesterdayMap, "NEW");
            values.put("IDA_STORED_DATE1", fmt(idaStoredDate1));
            values.put("IDA_STORED_DATE2", fmt(idaStoredDate2));
            values.put("IDA_STORED_TOTAL", fmt(getSingleCount(r6t)));
            values.put("APP_MVS_INQUEUE_TODAY", fmt(getSingleCount(rMvsd1)));
            values.put("APP_MVS_INQUEUE_YESTERDAY", fmt(getSingleCount(rMvsd2)));
            values.put("APP_MVS_INQUEUE_TOTAL", fmt(getSingleCount(rMvst)));

        } catch (Exception e) {
            log.error("Error executing parallel queries", e);
            throw new RuntimeException("Failed to fetch data for email", e);
        }
    }

    private void putProcessTypeValues(Map<String, String> values,
                                       Map<String, Long> cumulativeMap,
                                       Map<String, Long> date1Map,
                                       Map<String, Long> date2Map,
                                       String processType,
                                       String totalKey, String date1Key, String date2Key) {
        values.put(totalKey, fmt(getCount(cumulativeMap, processType)));
        values.put(date1Key, fmt(getCount(date1Map, processType)));
        values.put(date2Key, fmt(getCount(date2Map, processType)));
    }

    private void putProcessTypeAliases(Map<String, String> values,
                                       Map<String, Long> cumulativeMap,
                                       Map<String, Long> date1Map,
                                       Map<String, Long> date2Map,
                                       String processType,
                                       String totalKey, String date1Key, String date2Key) {
        values.put(totalKey, fmt(getCount(cumulativeMap, processType)));
        values.put(date1Key, fmt(getCount(date1Map, processType)));
        values.put(date2Key, fmt(getCount(date2Map, processType)));
    }

    private long sumSelectedProcessTypes(Map<String, Long> map) {
        return getCount(map, "NEW")
                + getCount(map, "RENEWAL")
                + getCount(map, "FIRSTID")
                + getCount(map, "LOST")
                + getCount(map, "UPDATE")
                + getCount(map, "BIOMETRIC_CORRECTION")
                + getCount(map, "Alien New Registration")
                + getCount(map, "CRVS_NEW");
    }

    private long sumSelectedServiceTypeRows(Map<String, Long> map) {
        return getCount(map, "NEW")
                + getCount(map, "RENEWAL")
                + getCount(map, "FIRSTID")
                + getCount(map, "LOST")
                + getCount(map, "UPDATE")
                + getCount(map, "BIOMETRIC_CORRECTION")
                + getCount(map, "Alien New Registration")
                + getCount(map, "CRVS_NEW");
    }
}
