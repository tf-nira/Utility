package io.mosip.packet_utility.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import io.mosip.packet_utility.dto.*;
import io.mosip.packet_utility.dto.ResponseWrapper;
import io.mosip.packet_utility.service.PacketService;
import io.mosip.kernel.core.util.JsonUtils;
import io.mosip.kernel.core.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
public class PacketServiceImpl implements PacketService {

    @Autowired(required = true)
    @Qualifier("selfTokenRestTemplate")
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${mosip.regproc.source}")
    private String source;

    @Value("${mosip.regproc.process}")
    private String process;

    @Value("${io.moisp.packet.manager.search.fields.url}")
    private String searchFieldUrl;

    @Value("${io.moisp.packet.manager.search.multi.fields.url}")
    private String searchMultiFieldUrl;

    @Value("${io.moisp.packet.manager.metaInfo}")
    private String metainfo;

    @Value("${io.moisp.packet.manager.biometrics.url}")
    private String biometricsUrl;

    @Value("${io.moisp.packet.manager.add-or-update-tag.url}")
    private String addOrUpdateTagUrl;

    @Value("${io.mosip.id.repo.fetch.nin-details.url}")
    private String idRepoUrl;

    @Value("${io.mosip.id.repo.update.identity.url}")
    private String updateIdentityUrl;

    @Value("${io.mosip.output.file.path}")
    private String filepath;

    private Boolean allField =true;

    private final Executor executor = Executors.newFixedThreadPool(200);

    @Override
    public void getPacketNIN() throws Exception {
        List<String> ninList = readNINsFromCSV(true);

        int batchSize = 25;
        List<List<String>> batches = createBatches(ninList, batchSize);

        Path outputPath = Paths.get(filepath, "rid-nin.csv");

        try (Writer writer = Files.newBufferedWriter(outputPath); CSVWriter csvWriter = new CSVWriter(writer)) {

            // Write header
            csvWriter.writeNext(new String[]{"REG_ID", "NIN"});
            csvWriter.flush();

            System.out.println("Processing " + ninList.size() + " RIDs in " + batches.size() + " batches");

            for (int i = 0; i < batches.size(); i++) {
                List<String> batch = batches.get(i);
                System.out.println(
                        "Processing batch " + (i + 1) + "/" + batches.size() + " with " + batch.size() + " RIDs");

                // Processing batch in parallel
                List<CompletableFuture<NINResultDTO>> futures = batch.stream().map(rid -> CompletableFuture
                        .supplyAsync(() -> getNIN(rid), executor).handle((ninStatusDTO, throwable) -> {
                            if (throwable != null) {
                                System.err.println("Error checking RID " + rid + ": " + throwable.getMessage());
                            }
                            return ninStatusDTO;
                        })).collect(Collectors.toList());

                // Waiting for all futures in the batch to complete
                CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

                try {
                    // Wait for batch completion with timeout
                    allOf.get(2, TimeUnit.MINUTES);

                    // Write results for this batch
                    for (CompletableFuture<NINResultDTO> future : futures) {
                        NINResultDTO result = future.get();
                        csvWriter.writeNext(new String[]{result.getRid(), result.getNin()});
                    }
                    csvWriter.flush();

                    System.out.println("Completed batch " + (i + 1) + "/" + batches.size());

                } catch (TimeoutException e) {
                    System.err.println("Batch " + (i + 1) + " timed out after 5 minutes");
                } catch (ExecutionException | InterruptedException e) {
                    System.err.println("Error processing batch " + (i + 1) + ": " + e.getMessage());
                }

                // Small delay between batches
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            System.err.println("Error occurred: " + e.getMessage());
            throw e;
        }

        System.out.println("NIN status check completed successfully");
    }

    @Override
    public void getNINStatus() throws Exception {
        List<String> ninList = readNINsFromCSV(false);

        int batchSize = 25;
        List<List<String>> batches = createBatches(ninList, batchSize);

        Path outputPath = Paths.get(filepath, "nin_status_report.csv");

        try (Writer writer = Files.newBufferedWriter(outputPath); CSVWriter csvWriter = new CSVWriter(writer)) {

            // Write header
            csvWriter.writeNext(new String[]{"NIN", "STATUS"});
            csvWriter.flush();

            System.out.println("Processing " + ninList.size() + " NINs in " + batches.size() + " batches");

            for (int i = 0; i < batches.size(); i++) {
                List<String> batch = batches.get(i);
                System.out.println(
                        "Processing batch " + (i + 1) + "/" + batches.size() + " with " + batch.size() + " NIMs");

                // Processing batch in parallel
                List<CompletableFuture<NinStatusDTO>> futures = batch.stream().map(nin -> CompletableFuture
                        .supplyAsync(() -> checkNINExistsAsync(nin), executor).handle((ninStatusDTO, throwable) -> {
                            if (throwable != null) {
                                System.err.println("Error checking NIN " + nin + ": " + throwable.getMessage());
                            }
                            return ninStatusDTO;
                        })).collect(Collectors.toList());

                // Waiting for all futures in the batch to complete
                CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

                try {
                    // Wait for batch completion with timeout
                    allOf.get(2, TimeUnit.MINUTES);

                    // Write results for this batch
                    for (CompletableFuture<NinStatusDTO> future : futures) {
                        NinStatusDTO result = future.get();
                        csvWriter.writeNext(new String[]{result.getNin(), result.getStatus()});
                    }
                    csvWriter.flush();

                    System.out.println("Completed batch " + (i + 1) + "/" + batches.size());

                } catch (TimeoutException e) {
                    System.err.println("Batch " + (i + 1) + " timed out after 5 minutes");
                } catch (ExecutionException | InterruptedException e) {
                    System.err.println("Error processing batch " + (i + 1) + ": " + e.getMessage());
                }

                // Small delay between batches
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            System.err.println("Error occurred: " + e.getMessage());
            throw e;
        }

        System.out.println("NIN status check completed successfully");
    }

    @Override
    public void updateIdentity() throws Exception {
        List<List<String>> ninList = readMultiFieldCSV();

        int batchSize = 25;
        List<List<List<String>>> batches = createMultiFieldBatches(ninList, batchSize);

        Path outputPath = Paths.get(filepath, "update_nins_output.csv");

        try (Writer writer = Files.newBufferedWriter(outputPath); CSVWriter csvWriter = new CSVWriter(writer)) {

            // Write header
            csvWriter.writeNext(new String[]{"NIN", "STATUS"});
            csvWriter.flush();

            System.out.println("Processing " + ninList.size() + " NINs in " + batches.size() + " batches");

            for (int i = 0; i < batches.size(); i++) {
                List<List<String>> batch = batches.get(i);
                System.out.println(
                        "Processing batch " + (i + 1) + "/" + batches.size() + " with " + batch.size() + " NINs");

                // Processing batch in parallel
                List<CompletableFuture<NinStatusDTO>> futures = batch.stream().map(updateDetailsInfo -> CompletableFuture
                        .supplyAsync(() -> updateDetails(updateDetailsInfo), executor).handle((ninStatusDTO, throwable) -> {

                            if (throwable != null) {
                                System.err.println("Error updating NIN " + updateDetailsInfo.get(0) + ": " + throwable.getMessage());
                            }
                            return ninStatusDTO;
                        })).collect(Collectors.toList());

                // Waiting for all futures in the batch to complete
                CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

                try {
                    // Wait for batch completion with timeout
                    allOf.get(2, TimeUnit.MINUTES);

                    // Write results for this batch
                    for (CompletableFuture<NinStatusDTO> future : futures) {
                        NinStatusDTO result = future.get();
                        csvWriter.writeNext(new String[]{result.getNin(), result.getStatus()});
                    }
                    csvWriter.flush();

                    System.out.println("Completed batch " + (i + 1) + "/" + batches.size());

                } catch (TimeoutException e) {
                    System.err.println("Batch " + (i + 1) + " timed out after 5 minutes");
                } catch (ExecutionException | InterruptedException e) {
                    System.err.println("Error processing batch " + (i + 1) + ": " + e.getMessage());
                }

                // Small delay between batches
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            System.err.println("Error occurred: " + e.getMessage());
            throw e;
        }

        System.out.println("NIN update completed successfully");
    }

    public List<String> readNINsFromCSV(boolean getNin) throws IOException, CsvValidationException {

        String fileName = "";
        if (getNin) fileName = "rids.csv";
        else fileName = "nin_status.csv";

        List<String> list = new ArrayList<>();
        Resource resource = new ClassPathResource(fileName);
        Reader reader = new InputStreamReader(resource.getInputStream());

        try (CSVReader csvReader = new CSVReader(reader)) {

            String[] line;
            while ((line = csvReader.readNext()) != null) {
                String nin = line[0].trim();
                if (StringUtils.isNotEmpty(nin)) {
                    list.add(nin);
                }
            }
            System.out.println("Total records read from csv file :: " + list.size());
        } catch (Exception e) {
            System.out.println("Exception occured : " + e);
            throw e;
        }
        return list;
    }

    @Override
    public void addOrUpdateMergedTag() throws Exception {
        List<String> regIds = readNINsFromCSV(true);

        int batchSize = 25;
        List<List<String>> batches = createBatches(regIds, batchSize);
        Path outputPath = Paths.get(filepath, "add_or_update_tag_output.csv");

        try (Writer writer = Files.newBufferedWriter(outputPath); CSVWriter csvWriter = new CSVWriter(writer)) {
            csvWriter.writeNext(new String[]{"REG_ID", "STATUS"});
            csvWriter.flush();

            System.out.println("Processing " + regIds.size() + " RIDs for IS-MERGED tag in " + batches.size() + " batches");

            for (int i = 0; i < batches.size(); i++) {
                List<String> batch = batches.get(i);
                System.out.println("Processing batch " + (i + 1) + "/" + batches.size() + " with " + batch.size() + " RIDs");

                List<CompletableFuture<String[]>> futures = batch.stream()
                        .map(regId -> CompletableFuture.supplyAsync(() -> addOrUpdateMergedTag(regId), executor)
                                .handle((result, throwable) -> {
                                    if (throwable != null) {
                                        System.err.println("Error updating tag for RID " + regId + ": " + throwable.getMessage());
                                        return new String[]{regId, throwable.getMessage()};
                                    }
                                    return result;
                                }))
                        .collect(Collectors.toList());

                CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

                try {
                    allOf.get(2, TimeUnit.MINUTES);

                    for (CompletableFuture<String[]> future : futures) {
                        csvWriter.writeNext(future.get());
                    }
                    csvWriter.flush();

                    System.out.println("Completed batch " + (i + 1) + "/" + batches.size());
                } catch (TimeoutException e) {
                    System.err.println("Batch " + (i + 1) + " timed out after 2 minutes");
                } catch (ExecutionException | InterruptedException e) {
                    System.err.println("Error processing batch " + (i + 1) + ": " + e.getMessage());
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }

                Thread.sleep(1000);
            }
        }

        System.out.println("Packet tag update completed. Output CSV: " + outputPath.toAbsolutePath());
    }

    private String[] addOrUpdateMergedTag(String regId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        RequestWrapper<PacketTagRequestDTO> requestWrapper = new RequestWrapper<>();
        requestWrapper.setId("packet-utility.add-or-update-tag");
        requestWrapper.setVersion("1.0");
        requestWrapper.setRequesttime(LocalDateTime.now(Clock.systemUTC()));
        requestWrapper.setMetadata(new HashMap<>());

        PacketTagRequestDTO packetTagRequestDTO = new PacketTagRequestDTO();
        packetTagRequestDTO.setId(regId);

        Map<String, String> tags = new HashMap<>();
        tags.put("IS-MERGED", "true");
        packetTagRequestDTO.setTags(tags);
        requestWrapper.setRequest(packetTagRequestDTO);

        HttpEntity<RequestWrapper<PacketTagRequestDTO>> entity = new HttpEntity<>(requestWrapper, headers);

        try {
            ResponseEntity<ResponseWrapper<Object>> responseEntity = restTemplate.exchange(addOrUpdateTagUrl,
                    HttpMethod.POST, entity, new ParameterizedTypeReference<ResponseWrapper<Object>>() {
                    });

            ResponseWrapper<Object> responseWrapper = responseEntity.getBody();
            if (responseWrapper != null && responseWrapper.getErrors() != null && !responseWrapper.getErrors().isEmpty()) {
                return new String[]{regId, responseWrapper.getErrors().get(0).getMessage()};
            }

            return new String[]{regId, "SUCCESS"};
        } catch (RestClientException e) {
            System.err.println("Exception while updating tag for RID " + regId + ": " + e.getMessage());
            return new String[]{regId, e.getMessage()};
        }
    }

    @Override
    public void getAge() throws Exception {

        int batchSize = 25;
        String fileName = "pausedApplication.csv";

        List<String[]> inputList = new ArrayList<>();

        // -------- READ INPUT CSV --------
        Resource resource = new ClassPathResource(fileName);

        try (Reader fileReader = new InputStreamReader(resource.getInputStream());
             CSVReader reader = new CSVReader(fileReader)) {

            String[] line;
            while ((line = reader.readNext()) != null) {
                if (line.length < 2) continue;

                String regId = line[0].trim();
                String process = line[1].trim();

                if (StringUtils.isNotEmpty(regId) && StringUtils.isNotEmpty(process)) {
                    inputList.add(new String[]{regId, process});
                }
            }

            System.out.println("Total records read from csv file: " + inputList.size());
        }

        if (inputList.isEmpty()) {
            System.out.println("No records found. Exiting.");
            return;
        }

        // -------- CREATE BATCHES --------
        List<List<String[]>> batches = new ArrayList<>();
        for (int i = 0; i < inputList.size(); i += batchSize) {
            batches.add(inputList.subList(i, Math.min(i + batchSize, inputList.size())));
        }

        // -------- OUTPUT FILES --------
        Path outputDir = Paths.get("D:/output"); // Use D:\output
        Files.createDirectories(outputDir);      // Ensure the folder exists

        Path reprocessablePath = outputDir.resolve("output-reprocessable.csv");
        Path unprocessablePath = outputDir.resolve("output-unprocessable.csv");

        System.out.println("Reprocessable CSV: " + reprocessablePath.toAbsolutePath());
        System.out.println("Unprocessable CSV: " + unprocessablePath.toAbsolutePath());

// -------- WRITE OUTPUT FILES --------
        try (
                Writer writer1 = Files.newBufferedWriter(reprocessablePath);
                CSVWriter reprocessWriter = new CSVWriter(writer1);

                Writer writer2 = Files.newBufferedWriter(unprocessablePath);
                CSVWriter unprocessWriter = new CSVWriter(writer2)
        ) {
            String[] header;

            if (allField) {
                header = new String[]{"reg_id", "process", "date_of_birth",
                        "packet_created_date", "age", "remark"};
            } else {
                header = new String[]{"reg_id"};
            }
            reprocessWriter.writeNext(header);
            unprocessWriter.writeNext(header);

            for (int i = 0; i < batches.size(); i++) {
                List<String[]> batch = batches.get(i);
                System.out.println("Processing batch " + (i + 1) + "/" + batches.size());

                List<CompletableFuture<ApplicationProcessingDTO>> futures = batch.stream()
                        .map(record ->
                                CompletableFuture.supplyAsync(() -> getApplicantAge(record[0], record[1]))
                                        .exceptionally(ex -> {
                                            System.err.println("Error processing reg_id: " + record[0] + " - " + ex.getMessage());
                                            return null;
                                        })
                        ).collect(Collectors.toList());

                CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

                try {
                    allOf.get(2, TimeUnit.MINUTES);

                    // Collect rows for this batch
                    List<String[]> reprocessableRows = new ArrayList<>();
                    List<String[]> unprocessableRows = new ArrayList<>();

                    for (CompletableFuture<ApplicationProcessingDTO> future : futures) {
                        ApplicationProcessingDTO result = future.get();
                        if (result == null) continue;

                        String[] row;

                        if (allField) {
                            row = new String[]{
                                    result.getRegId(),
                                    result.getProcess(),
                                    result.getDateOfBirth() != null ? result.getDateOfBirth().toString() : "",
                                    result.getPacketCreatedDate() != null ? result.getPacketCreatedDate().toString() : "",
                                    result.getApplicantAge(),
                                    result.getRemark()
                            };
                        } else {
                            row = new String[]{
                                    result.getRegId()
                            };
                        }
                        if ("REPROCESSABLE".equalsIgnoreCase(result.getRemark())) {
                            reprocessableRows.add(row);
                        } else {
                            unprocessableRows.add(row);
                        }
                    }

                    // Write after batch is complete
                    for (String[] row : reprocessableRows) reprocessWriter.writeNext(row);
                    for (String[] row : unprocessableRows) unprocessWriter.writeNext(row);

                    reprocessWriter.flush();
                    unprocessWriter.flush();

                    System.out.println("Completed batch " + (i + 1));

                } catch (TimeoutException e) {
                    System.err.println("Batch " + (i + 1) + " timed out after 2 minutes");
                } catch (ExecutionException | InterruptedException e) {
                    System.err.println("Error in batch " + (i + 1) + ": " + e.getMessage());
                }

                Thread.sleep(1000); // optional delay between batches
            }

        } catch (Exception e) {
            System.err.println("Error occurred: " + e.getMessage());
            throw e;
        }

        System.out.println("Processing completed successfully");


    }

        public List<List<String>> readMultiFieldCSV() throws IOException, CsvValidationException {

        List<List<String>> records = new ArrayList<>();
        Resource resource = new ClassPathResource("nin_update.csv");
        Reader reader = new InputStreamReader(resource.getInputStream());

        try (CSVReader csvReader = new CSVReader(reader)) {

            String[] line;
            while ((line = csvReader.readNext()) != null) {
                // Convert array to list and trim each value
                List<String> row = Arrays.stream(line)
                        .map(String::trim)
                        .collect(Collectors.toList());
                records.add(row);
            }

            System.out.println("Total records read from csv file :: " + records.size());

        } catch (Exception e) {
            System.out.println("Exception occurred: " + e);
            throw e;
        }

        return records;
    }

    private List<List<String>> createBatches(List<String> list, int batchSize) {
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            int end = Math.min(i + batchSize, list.size());
            batches.add(list.subList(i, end));
        }
        return batches;
    }

    private List<List<List<String>>> createMultiFieldBatches(List<List<String>> records, int batchSize) {
        List<List<List<String>>> batches = new ArrayList<>();
        for (int i = 0; i < records.size(); i += batchSize) {
            int end = Math.min(i + batchSize, records.size());
            batches.add(records.subList(i, end));
        }
        return batches;
    }

    private List<String[]> readRegProcessCSV(String fileName) throws IOException, CsvValidationException {
        List<String[]> records = new ArrayList<>();
        Resource resource = new ClassPathResource(fileName);

        try (Reader fileReader = new InputStreamReader(resource.getInputStream());
             CSVReader reader = new CSVReader(fileReader)) {

            String[] line;
            while ((line = reader.readNext()) != null) {
                if (line.length < 2) {
                    continue;
                }

                String regId = line[0].trim();
                String recordProcess = line[1].trim();

                if ("reg_id".equalsIgnoreCase(regId) || "process".equalsIgnoreCase(recordProcess)) {
                    continue;
                }

                if (StringUtils.isNotEmpty(regId) && StringUtils.isNotEmpty(recordProcess)) {
                    records.add(new String[]{regId, recordProcess});
                }
            }
        }

        System.out.println("Total records read from " + fileName + " :: " + records.size());
        return records;
    }

    public UpdateRequestDTO createUpdateRequest(List<String> updateDetailsInfo) {
        Identity identity = new Identity();
        identity.setIDSchemaVersion(8.7);
        identity.setNIN(updateDetailsInfo.get(0));

        if (isNotBlank(updateDetailsInfo.get(1))) {
            LocalizedValue surnameValue = new LocalizedValue();
            surnameValue.setLanguage("eng");
            surnameValue.setValue(updateDetailsInfo.get(1));
            identity.setSurname(Collections.singletonList(surnameValue));
        }

        if (isNotBlank(updateDetailsInfo.get(2))) {
            LocalizedValue givenNameValue = new LocalizedValue();
            givenNameValue.setLanguage("eng");
            givenNameValue.setValue(updateDetailsInfo.get(2));
            identity.setGivenName(Collections.singletonList(givenNameValue));
        }

        if (isNotBlank(updateDetailsInfo.get(3))) {
            LocalizedValue otherNamesValue = new LocalizedValue();
            otherNamesValue.setLanguage("eng");
            otherNamesValue.setValue(updateDetailsInfo.get(3));
            identity.setOtherNames(Collections.singletonList(otherNamesValue));
        }


        if (isNotBlank(updateDetailsInfo.get(4))) {
            LocalizedValue genderValue = new LocalizedValue();
            genderValue.setLanguage("eng");
            genderValue.setValue(updateDetailsInfo.get(4));
            identity.setGender(Collections.singletonList(genderValue));
        }

        if (isNotBlank(updateDetailsInfo.get(5))) {
            identity.setDateOfBirth(updateDetailsInfo.get(5));
        }
        if (isNotBlank(updateDetailsInfo.get(6))) {
            LocalizedValue residenceStatusValue = new LocalizedValue();
            residenceStatusValue.setLanguage("eng");
            residenceStatusValue.setValue(updateDetailsInfo.get(6));
            identity.setResidenceStatus(Collections.singletonList(residenceStatusValue));
        }

        RequestData requestData = new RequestData();
        requestData.setRegistrationId(generateRandom10DigitString());
        requestData.setIdentity(identity);

        UpdateRequestDTO updateRequestDto = new UpdateRequestDTO();
        updateRequestDto.setId("mosip.id.update");
        updateRequestDto.setVersion("v1.0");
        updateRequestDto.setRequesttime(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        updateRequestDto.setRequest(requestData);

        return updateRequestDto;
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isNotNull(String value) {
        return value != null;
    }

    public static String generateRandom10DigitString() {
        SecureRandom secureRandom = new SecureRandom();
        long number = 1_000_000_000L + (long) (secureRandom.nextDouble() * 9_000_000_000L);
        return String.valueOf(number);
    }

    public NINResultDTO getNIN(String rid) {
        NINResultDTO ninResultDTO = new NINResultDTO();
        ninResultDTO.setRid(rid);

        String url = searchFieldUrl;
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        FieldRequestDTO fieldRequestDTO = new FieldRequestDTO();
        fieldRequestDTO.setId(rid);
        fieldRequestDTO.setField("NIN");
        fieldRequestDTO.setSource(source);
        fieldRequestDTO.setProcess(process);
        fieldRequestDTO.setBypassCache(true);

        RequestWrapper<FieldRequestDTO> request = new RequestWrapper<>();
        request.setRequest(fieldRequestDTO);

        HttpEntity<RequestWrapper<FieldRequestDTO>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<ResponseWrapper<FieldResponseDTO>> responseEntity = restTemplate.exchange(builder.build().toUri(),
                    HttpMethod.POST, entity, new ParameterizedTypeReference<ResponseWrapper<FieldResponseDTO>>() {
                    });

            ResponseWrapper<FieldResponseDTO> responseWrapper = responseEntity.getBody();

            if (responseWrapper.getErrors() != null && !responseWrapper.getErrors().isEmpty()) {
                System.out.println("NIN for rid : " + rid + " not present in ID repo");
                ninResultDTO.setNin(responseWrapper.getErrors().get(0).getMessage());
                return ninResultDTO;
            }

            FieldResponseDTO fieldResponseDto = objectMapper.readValue(JsonUtils.javaObjectToJsonString(responseWrapper.getResponse()), FieldResponseDTO.class);
            if (fieldResponseDto != null) {
                String nin = fieldResponseDto.getFields().get("NIN");
                ninResultDTO.setNin(nin.toUpperCase());
            }

            return ninResultDTO;

        } catch (RestClientException e) {
            System.err.println("Exception for RID " + rid + ": " + e.getMessage());
            ninResultDTO.setNin(e.getMessage());
            return ninResultDTO;
        } catch (JsonProcessingException | io.mosip.kernel.core.util.exception.JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public NinStatusDTO checkNINExistsAsync(String nin) {
        NinStatusDTO ninStatusDTO = new NinStatusDTO();
        ninStatusDTO.setNin(nin);

        String handle = nin.toLowerCase() + "@nin";
        String url = idRepoUrl + handle;

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url).queryParam("type", "metadata")
                .queryParam("idType", "handle");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        try {
            ResponseEntity<ResponseWrapper<NINStatusResponseDTO>> responseEntity = restTemplate.exchange(builder.build().toUri(),
                    HttpMethod.GET, entity, new ParameterizedTypeReference<ResponseWrapper<NINStatusResponseDTO>>() {
                    });

            ResponseWrapper<NINStatusResponseDTO> responseWrapper = responseEntity.getBody();

            if (responseWrapper.getResponse() != null) {
                ninStatusDTO.setStatus("EXIST_IN_IDREPO");
            }

            if (responseWrapper.getErrors() != null && !responseWrapper.getErrors().isEmpty()) {
                System.out.println("NIN not found in ID repo: " + nin);
                ninStatusDTO.setStatus("NOT_FOUND");
            }

            return ninStatusDTO;

        } catch (RestClientException e) {
            System.err.println("Exception for NIN " + nin + ": " + e.getMessage());
            ninStatusDTO.setStatus(e.getMessage());
            return ninStatusDTO;
        }
    }

    public NinStatusDTO updateDetails(List<String> updateDetailsInfo) {
        NinStatusDTO ninStatusDTO = new NinStatusDTO();
        ninStatusDTO.setNin(updateDetailsInfo.get(0));

        String url = updateIdentityUrl;
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        UpdateRequestDTO updateRequestDTO = createUpdateRequest(updateDetailsInfo);

        HttpEntity<UpdateRequestDTO> entity = new HttpEntity<>(updateRequestDTO, headers);

        try {
            ResponseEntity<ResponseWrapper<UpdateResponseDTO>> responseEntity = restTemplate.exchange(builder.build().toUri(),
                    HttpMethod.PATCH, entity, new ParameterizedTypeReference<ResponseWrapper<UpdateResponseDTO>>() {
                    });

            ResponseWrapper<UpdateResponseDTO> responseWrapper = responseEntity.getBody();

            if (responseWrapper.getErrors() != null && !responseWrapper.getErrors().isEmpty()) {
                System.out.println("NIN not updated in ID repo: " + updateDetailsInfo.get(0));
                ninStatusDTO.setStatus(responseWrapper.getErrors().get(0).getMessage());
                return ninStatusDTO;
            }

            ninStatusDTO.setStatus(responseWrapper.getResponse().getStatus());
            return ninStatusDTO;

        } catch (RestClientException e) {
            System.err.println("Exception for NIN " + updateDetailsInfo.get(0) + ": " + e.getMessage());
            ninStatusDTO.setStatus(e.getMessage());
            return ninStatusDTO;
        }
    }

    @Override
    public void getResidenceStatus() throws Exception {

        List<String> ninList = readNINsFromCSV(true);

        int batchSize = 25;
        List<List<String>> batches = createBatches(ninList, batchSize);

        Path outputPath = Paths.get(filepath, "residence_status_report.csv");

        try (Writer writer = Files.newBufferedWriter(outputPath); CSVWriter csvWriter = new CSVWriter(writer)) {

            // Write header
            csvWriter.writeNext(new String[]{"RID", "NIN","Status"});
            csvWriter.flush();

            System.out.println("Processing " + ninList.size() + " Residence status in " + batches.size() + " batches");

            for (int i = 0; i < batches.size(); i++) {
                List<String> batch = batches.get(i);
                System.out.println(
                        "Processing batch " + (i + 1) + "/" + batches.size() + " with " + batch.size() + " RIDs");

                // Processing batch in parallel
                List<CompletableFuture<RidNinStatusDTO>> futures = batch.stream().map(rid -> CompletableFuture
                        .supplyAsync(() -> getResidence(rid), executor).handle((ridNinStatusDTO, throwable) -> {
                            if (throwable != null) {
                                System.err.println("Error checking Rid " + rid + ": " + throwable.getMessage());
                            }
                            return ridNinStatusDTO;
                        })).collect(Collectors.toList());

                // Waiting for all futures in the batch to complete
                CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

                try {
                    // Wait for batch completion with timeout
                    allOf.get(2, TimeUnit.MINUTES);

                    // Write results for this batch
                    for (CompletableFuture<RidNinStatusDTO> future : futures) {
                        RidNinStatusDTO result = future.get();
                        csvWriter.writeNext(new String[]{result.getRid(),result.getNin(), result.getStatus()});
                    }
                    csvWriter.flush();

                    System.out.println("Completed batch " + (i + 1) + "/" + batches.size());

                } catch (TimeoutException e) {
                    System.err.println("Batch " + (i + 1) + " timed out after 5 minutes");
                } catch (ExecutionException | InterruptedException e) {
                    System.err.println("Error processing batch " + (i + 1) + ": " + e.getMessage());
                }

                // Small delay between batches
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            System.err.println("Error occurred: " + e.getMessage());
            throw e;
        }

        System.out.println("Residence status status check completed successfully");

    }

    public RidNinStatusDTO getResidence(String rid) {
        RidNinStatusDTO ridNinStatusDTO = new RidNinStatusDTO();
        ridNinStatusDTO.setRid(rid);

        String handle = rid;
        String url = idRepoUrl + handle;

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url).queryParam("type", "metadata");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        try {
            ResponseEntity<ResponseWrapper<NINStatusResponseDTO>> responseEntity = restTemplate.exchange(builder.build().toUri(),
                    HttpMethod.GET, entity, new ParameterizedTypeReference<ResponseWrapper<NINStatusResponseDTO>>() {
                    });

            ResponseWrapper<NINStatusResponseDTO> responseWrapper = responseEntity.getBody();

            if (responseWrapper.getResponse() != null) {

                NINStatusResponseDTO response = responseWrapper.getResponse();

                // Convert identity to JSON object
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode identityJson = mapper.valueToTree(response.getIdentity());

                ridNinStatusDTO.setNin(identityJson.get("NIN").asText());

                if (identityJson.hasNonNull("residenceStatus") &&
                        !identityJson.get("residenceStatus").isEmpty()) {
                    ridNinStatusDTO.setStatus("Residence status already present");
                }
                else if (identityJson.hasNonNull("applicantForeignResidenceCountry") &&
                        !identityJson.get("applicantForeignResidenceCountry").isEmpty()) {
                    ridNinStatusDTO.setStatus("Outside Uganda");
                }
                else if (identityJson.hasNonNull("applicantPlaceOfResidenceCounty") &&
                        !identityJson.get("applicantPlaceOfResidenceCounty").isEmpty()) {
                    ridNinStatusDTO.setStatus("In Uganda");
                }
                else {
                    // Default fallback if none of the above fields are present
                    ridNinStatusDTO.setStatus("EXIST_IN_IDREPO");
                }
            }

            else {
                ridNinStatusDTO.setStatus("Does not Exist");
            }

            return ridNinStatusDTO;

        } catch (RestClientException e) {
            System.err.println("Exception for RID " + rid + ": " + e.getMessage());
            ridNinStatusDTO.setStatus(e.getMessage());
            return ridNinStatusDTO;
        }
    }

    private ResponseWrapper<FieldsDTO> callMetaInfo(String regId, String process) {

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(metainfo);

        FieldRequestDTO requestDTO = new FieldRequestDTO();
        requestDTO.setId(regId);
        requestDTO.setProcess(process);
        requestDTO.setSource("MIGRATOR".equalsIgnoreCase(process) ? "DATAMIGRATOR" : source);
        requestDTO.setBypassCache(true);

        RequestWrapper<FieldRequestDTO> wrapper = new RequestWrapper<>();
        wrapper.setRequest(requestDTO);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<RequestWrapper<FieldRequestDTO>> entity =
                new HttpEntity<>(wrapper, headers);

        ResponseEntity<ResponseWrapper<FieldsDTO>> response =
                restTemplate.exchange(
                        builder.build().toUri(),
                        HttpMethod.POST,
                        entity,
                        new ParameterizedTypeReference<ResponseWrapper<FieldsDTO>>() {}
                );

        return response.getBody();
    }
    private FieldsDTO callSearchField(String regId, String process, String fieldId) {

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(searchFieldUrl);

        FieldRequestDTO requestDTO = new FieldRequestDTO();
        requestDTO.setId(regId);
        requestDTO.setField(fieldId);
        requestDTO.setProcess(process);
        requestDTO.setSource("MIGRATOR".equalsIgnoreCase(process) ? "DATAMIGRATOR" : source);
        requestDTO.setBypassCache(true);

        RequestWrapper<FieldRequestDTO> wrapper = new RequestWrapper<>();
        wrapper.setRequest(requestDTO);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<RequestWrapper<FieldRequestDTO>> entity =
                new HttpEntity<>(wrapper, headers);

        ResponseEntity<ResponseWrapper<FieldsDTO>> response =
                restTemplate.exchange(
                        builder.build().toUri(),
                        HttpMethod.POST,
                        entity,
                        new ParameterizedTypeReference<ResponseWrapper<FieldsDTO>>() {}
                );

        return response.getBody() != null ? response.getBody().getResponse() : null;
    }
    private NINStatusResponseDTO callIdRepo(String nin) {

        String url = idRepoUrl + nin.toLowerCase() + "@nin";

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(url)
                .queryParam("type", "metadata")
                .queryParam("idType", "handle");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        ResponseEntity<ResponseWrapper<NINStatusResponseDTO>> response =
                restTemplate.exchange(
                        builder.build().toUri(),
                        HttpMethod.GET,
                        entity,
                        new ParameterizedTypeReference<ResponseWrapper<NINStatusResponseDTO>>() {}
                );

        return response.getBody() != null ? response.getBody().getResponse() : null;
    }

    private NINStatusResponseDTO callIdRepoByApplicationId(String regId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(idRepoUrl + regId)
                .queryParam("type", "metadata");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<ResponseWrapper<NINStatusResponseDTO>> response = restTemplate.exchange(
                    builder.build().toUri(),
                    HttpMethod.GET,
                    new HttpEntity<>(null, headers),
                    new ParameterizedTypeReference<ResponseWrapper<NINStatusResponseDTO>>() {}
            );
            return response.getBody() != null ? response.getBody().getResponse() : null;
        } catch (HttpClientErrorException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                return null;
            }
            throw e;
        }
    }

    private FieldsDTO callSearchFields(String regId, String recordProcess, String[] fields) {
        MultiFieldRequestDTO requestDTO = new MultiFieldRequestDTO();
        requestDTO.setId(regId);
        requestDTO.setFields(fields);
        requestDTO.setProcess(recordProcess);
        requestDTO.setSource("MIGRATOR".equalsIgnoreCase(recordProcess) ? "DATAMIGRATOR" : source);
        requestDTO.setBypassCache(true);

        RequestWrapper<MultiFieldRequestDTO> wrapper = new RequestWrapper<>();
        wrapper.setRequest(requestDTO);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<ResponseWrapper<FieldsDTO>> response = restTemplate.exchange(
                UriComponentsBuilder.fromHttpUrl(searchMultiFieldUrl).build().toUri(),
                HttpMethod.POST,
                new HttpEntity<>(wrapper, headers),
                new ParameterizedTypeReference<ResponseWrapper<FieldsDTO>>() {}
        );
        return response.getBody() != null ? response.getBody().getResponse() : null;
    }

    @Override
    public void searchApplicantFields(String inputFile) throws Exception {
        final int batchSize = 25;
        String[] requestedFields = {"NIN", "surname", "givenName", "otherNames", "gender", "dateOfBirth"};
        List<String[]> records = readRegProcessCSV(inputFile);
        int totalBatches = (records.size() + batchSize - 1) / batchSize;
        Path outputPath = Paths.get(filepath, "applicant-fields.csv");
        Files.createDirectories(outputPath.getParent());

        try (Writer writer = Files.newBufferedWriter(outputPath); CSVWriter csvWriter = new CSVWriter(writer)) {
            csvWriter.writeNext(new String[]{"reg_id", "process", "source", "NIN", "surname", "givenName",
                    "otherNames", "gender", "dateOfBirth", "remark"});

            for (int start = 0; start < records.size(); start += batchSize) {
                List<String[]> batch = records.subList(start, Math.min(start + batchSize, records.size()));
                int batchNumber = (start / batchSize) + 1;
                System.out.println("Processing applicant-fields batch " + batchNumber + "/" + totalBatches
                        + " (" + batch.size() + " records)");
                List<CompletableFuture<String[]>> futures = batch.stream()
                        .map(record -> CompletableFuture.supplyAsync(
                                () -> searchApplicantFields(record, requestedFields), executor))
                        .collect(Collectors.toList());

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(2, TimeUnit.MINUTES);

                for (CompletableFuture<String[]> future : futures) {
                    csvWriter.writeNext(future.get());
                }
                csvWriter.flush();
                System.out.println("Completed applicant-fields batch " + batchNumber + "/" + totalBatches);
            }
        }
    }

    private String[] searchApplicantFields(String[] record, String[] requestedFields) {
        String regId = record[0];
        String recordProcess = record[1];
        Map<String, Object> fields = new HashMap<>();
        String resultSource = "ID_REPO";
        String remark = "";

        try {
            NINStatusResponseDTO idRepoResponse = callIdRepoByApplicationId(regId);
            if (idRepoResponse != null && idRepoResponse.getIdentity() != null) {
                fields.putAll(objectMapper.convertValue(idRepoResponse.getIdentity(), Map.class));
            } else {
                resultSource = "PACKET_MANAGER";
                FieldsDTO packetResponse = callSearchFields(regId, recordProcess, requestedFields);
                if (packetResponse != null && packetResponse.getFields() != null) {
                    fields.putAll(packetResponse.getFields());
                } else {
                    remark = "No record found in ID Repo or Packet Manager";
                }
            }
        } catch (Exception e) {
            resultSource = "ERROR";
            remark = e.getMessage();
        }

        String[] row = new String[10];
        row[0] = regId;
        row[1] = recordProcess;
        row[2] = resultSource;
        for (int i = 0; i < requestedFields.length; i++) {
            row[i + 3] = cleanFieldValue(fields.get(requestedFields[i]));
        }
        row[9] = remark;
        return row;
    }

    private String cleanFieldValue(Object value) {
        if (value == null) {
            return "";
        }

        JsonNode node = objectMapper.valueToTree(value);
        if (node.isTextual()) {
            String text = node.asText().trim();
            if (text.startsWith("[") || text.startsWith("{")) {
                try {
                    node = objectMapper.readTree(text);
                } catch (JsonProcessingException ignored) {
                    // Not JSON text; return the original string below.
                }
            }
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.hasNonNull("value")) {
                    return stripQuotes(item.get("value").asText());
                }
            }
        }
        if (node.hasNonNull("value")) {
            return stripQuotes(node.get("value").asText());
        }
        if (node.isValueNode()) {
            return stripQuotes(node.asText());
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private String stripQuotes(String value) {
        return value == null ? "" : value.replaceAll("^\\\"+|\\\"+$", "");
    }
    public ApplicationProcessingDTO getApplicantAge(String regId, String process) {

        ApplicationProcessingDTO applicationProcessingDTO = new ApplicationProcessingDTO();
        applicationProcessingDTO.setRegId(regId);
        applicationProcessingDTO.setProcess(process);

        ObjectMapper mapper = new ObjectMapper();

        try {


            FieldsDTO metaResponse = callMetaInfo(regId, process).getResponse();
            if (metaResponse != null) {
                ObjectNode identity = mapper.valueToTree(metaResponse.getFields());
                if (identity.get("creationDate") != null) {
                    applicationProcessingDTO.setPacketCreatedDate(identity.get("creationDate").asText());
                }
            }


            if ("NEW".equalsIgnoreCase(process)) {
                FieldsDTO searchResponse =
                        callSearchField(regId, process, "dateOfBirth");

                if (searchResponse != null) {
                    ObjectNode identity = mapper.valueToTree(searchResponse.getFields());
                    if (identity.get("dateOfBirth") != null) {
                        applicationProcessingDTO.setDateOfBirth(identity.get("dateOfBirth").asText());
                    }
                }
            }


            else {

                FieldsDTO ninResponse = callSearchField(regId, process, "NIN");

                if (ninResponse != null) {
                    ObjectNode identity = mapper.valueToTree(ninResponse.getFields());
                    if (identity.get("NIN") != null) {

                        String nin = identity.get("NIN").asText();
                        NINStatusResponseDTO idRepoResponse = callIdRepo(nin);

                        if (idRepoResponse != null) {
                            ObjectNode idRepoIdentity =
                                    mapper.valueToTree(idRepoResponse.getIdentity());

                            if (idRepoIdentity.get("dateOfBirth") != null) {
                                applicationProcessingDTO.setDateOfBirth(
                                        idRepoIdentity.get("dateOfBirth").asText());
                            }
                        }
                    }
                }
            }
            DateTimeFormatter dobFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            LocalDate dob = LocalDate.parse(applicationProcessingDTO.getDateOfBirth(), dobFormatter);
            OffsetDateTime odt = OffsetDateTime.parse(applicationProcessingDTO.getPacketCreatedDate());
            LocalDate packetDate = odt.toLocalDate();
            Period period = Period.between(dob, packetDate);
            double age = period.getYears() + (period.getMonths() / 12.0);
            applicationProcessingDTO.setApplicantAge(String.format("%.2f", age));

            if (age >=16){
                applicationProcessingDTO.setRemark("REPROCESSABLE");
            }
            else
                applicationProcessingDTO.setRemark("UNPROCESSABLE");

        } catch (Exception e) {
            throw new RuntimeException("Error while fetching applicant data", e);
        }

        return applicationProcessingDTO;
    }

    @Override
    public void getPrn() throws Exception {
        int batchSize = 25;
        String fileName = "PrnApplication.csv";

        List<String[]> inputList = new ArrayList<>();

// -------- READ INPUT CSV --------
        Resource resource = new ClassPathResource(fileName);

        try (Reader fileReader = new InputStreamReader(resource.getInputStream());
             CSVReader reader = new CSVReader(fileReader)) {

            String[] line;
            while ((line = reader.readNext()) != null) {
                if (line.length < 2) continue;

                String regId = line[0].trim();
                String process = line[1].trim();

                if (StringUtils.isNotEmpty(regId) && StringUtils.isNotEmpty(process)) {
                    inputList.add(new String[]{regId, process});
                }
            }

            System.out.println("Total records read from csv file: " + inputList.size());
        }

        if (inputList.isEmpty()) {
            System.out.println("No records found. Exiting.");
            return;
        }

// -------- CREATE BATCHES --------
        List<List<String[]>> batches = new ArrayList<>();
        for (int i = 0; i < inputList.size(); i += batchSize) {
            batches.add(inputList.subList(i, Math.min(i + batchSize, inputList.size())));
        }

// -------- OUTPUT FILE --------
        Path outputDir = Paths.get("D:/output");
        Files.createDirectories(outputDir);

        Path outputPath = outputDir.resolve("output-ApplicationPrn.csv");

        System.out.println("Output CSV: " + outputPath.toAbsolutePath());

// -------- WRITE OUTPUT FILE --------
        try (
                Writer writer = Files.newBufferedWriter(outputPath);
                CSVWriter csvWriter = new CSVWriter(writer)
        ) {

            // Header (all fields required)
            String[] header = new String[]{
                    "reg_id",
                    "process",
                    "prn",
                    "operator"
            };
            csvWriter.writeNext(header);

            for (int i = 0; i < batches.size(); i++) {
                List<String[]> batch = batches.get(i);
                System.out.println("Processing batch " + (i + 1) + "/" + batches.size());

                List<CompletableFuture<PrnApplication>> futures = batch.stream()
                        .map(record ->
                                CompletableFuture.supplyAsync(() ->
                                        {
                                            try {
                                                return getApplicantPrn(record[0], record[1]);
                                            } catch (JsonProcessingException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                ).exceptionally(ex -> {
                                    System.err.println("Error processing reg_id: "
                                            + record[0] + " - " + ex.getMessage());
                                    return null;
                                })
                        ).collect(Collectors.toList());

                CompletableFuture<Void> allOf =
                        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

                try {
                    // Wait max 2 minutes per batch
                    allOf.get(2, TimeUnit.MINUTES);

                    for (CompletableFuture<PrnApplication> future : futures) {
                        PrnApplication result = future.get();
                        if (result == null) continue;

                        String[] row = new String[]{
                                result.getRegId(),
                                result.getProcess(),
                                result.getPrn(),
                                result.getOperator()
                        };

                        csvWriter.writeNext(row);
                    }

                    csvWriter.flush();
                    System.out.println("Completed batch " + (i + 1));

                } catch (TimeoutException e) {
                    System.err.println("Batch " + (i + 1) + " timed out after 2 minutes");
                } catch (ExecutionException | InterruptedException e) {
                    System.err.println("Error in batch " + (i + 1) + ": " + e.getMessage());
                }

                Thread.sleep(1000); // optional delay between batches
            }

        } catch (Exception e) {
            System.err.println("Error occurred: " + e.getMessage());
            throw e;
        }

        System.out.println("Processing completed successfully");
    }
    public PrnApplication getApplicantPrn(String regId,String process) throws JsonProcessingException {
        PrnApplication prnApplication = new PrnApplication();
        prnApplication.setRegId(regId);
        prnApplication.setProcess(process);
        ObjectMapper mapper = new ObjectMapper();
        FieldsDTO searchResponse =
                callSearchField(regId, process, "PRNId");
        if (searchResponse != null) {
            ObjectNode identity = mapper.valueToTree(searchResponse.getFields());
            if (identity.get("PRNId") != null) {
                prnApplication.setPrn(identity.get("PRNId").asText());
            }
        }

        FieldsDTO metaResponse = callMetaInfo(regId, process).getResponse();
        if (metaResponse != null) {
            ObjectNode identity = mapper.valueToTree(metaResponse.getFields());
            if (identity.get("operationsData") != null) {
                String operationsDataStr = identity.get("operationsData").asText();

                // Parse the string into JSON array
                ArrayNode operationsArray = (ArrayNode) mapper.readTree(operationsDataStr);

                // Find the officerId value
                for (JsonNode node : operationsArray) {
                    if ("officerId".equals(node.get("label").asText())) {
                        prnApplication.setOperator(node.get("value").asText());
                        break;
                    }
                }
            }
        }


    return prnApplication;
    }

    @Override
    public void extractFaceBiometrics() throws Exception {
        List<String[]> inputList = readRegProcessCSV("reg_process.csv");

        if (inputList.isEmpty()) {
            System.out.println("No records found in reg_process.csv. Exiting.");
            return;
        }

        int batchSize = 10;
        List<List<String[]>> batches = new ArrayList<>();
        for (int i = 0; i < inputList.size(); i += batchSize) {
            batches.add(inputList.subList(i, Math.min(i + batchSize, inputList.size())));
        }

        Path outputDir = Paths.get(filepath, "face-biometrics");
        Files.createDirectories(outputDir);

        Path outputPath = Paths.get(filepath, "face_biometrics_output.csv");

        try (Writer writer = Files.newBufferedWriter(outputPath);
             CSVWriter csvWriter = new CSVWriter(writer)) {

            csvWriter.writeNext(new String[]{"reg_id", "process", "status", "raw_file", "image_file", "remark"});
            csvWriter.flush();

            System.out.println("Processing " + inputList.size() + " biometric records in " + batches.size() + " batches");

            for (int i = 0; i < batches.size(); i++) {
                List<String[]> batch = batches.get(i);
                System.out.println("Processing batch " + (i + 1) + "/" + batches.size());

                List<CompletableFuture<FaceBiometricResultDTO>> futures = batch.stream()
                        .map(record -> CompletableFuture.supplyAsync(
                                () -> extractFaceBiometric(record[0], record[1], outputDir), executor)
                                .exceptionally(ex -> {
                                    FaceBiometricResultDTO result = new FaceBiometricResultDTO();
                                    result.setRegId(record[0]);
                                    result.setProcess(record[1]);
                                    result.setStatus("FAILED");
                                    result.setRemark(ex.getMessage());
                                    return result;
                                }))
                        .collect(Collectors.toList());

                CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

                try {
                    allOf.get(5, TimeUnit.MINUTES);

                    for (CompletableFuture<FaceBiometricResultDTO> future : futures) {
                        FaceBiometricResultDTO result = future.get();
                        csvWriter.writeNext(new String[]{
                                result.getRegId(),
                                result.getProcess(),
                                result.getStatus(),
                                result.getRawFile(),
                                result.getImageFile(),
                                result.getRemark()
                        });
                    }

                    csvWriter.flush();
                    System.out.println("Completed batch " + (i + 1));
                } catch (TimeoutException e) {
                    System.err.println("Batch " + (i + 1) + " timed out after 5 minutes");
                } catch (ExecutionException | InterruptedException e) {
                    System.err.println("Error processing batch " + (i + 1) + ": " + e.getMessage());
                }

                Thread.sleep(1000);
            }
        }

        System.out.println("Face biometric extraction completed. Output CSV: " + outputPath.toAbsolutePath());
    }

    private FaceBiometricResultDTO extractFaceBiometric(String regId, String recordProcess, Path outputDir) {
        FaceBiometricResultDTO result = new FaceBiometricResultDTO();
        result.setRegId(regId);
        result.setProcess(recordProcess);

        try {
            JsonNode response = callBiometrics(regId, recordProcess);
            JsonNode segments = response.path("response").path("segments");

            if (!segments.isArray() || segments.size() == 0) {
                result.setStatus("NO_FACE");
                result.setRemark("No biometric segments found");
                return result;
            }

            JsonNode faceSegment = null;
            for (JsonNode segment : segments) {
                JsonNode types = segment.path("bdbInfo").path("type");
                if (containsText(types, "FACE")) {
                    faceSegment = segment;
                    break;
                }
            }

            if (faceSegment == null) {
                result.setStatus("NO_FACE");
                result.setRemark("Face segment not found");
                return result;
            }

            String bdb = faceSegment.path("bdb").asText(null);
            if (StringUtils.isEmpty(bdb)) {
                result.setStatus("NO_BDB");
                result.setRemark("Face segment has no BDB");
                return result;
            }

            byte[] bdbBytes = Base64.getDecoder().decode(bdb);
            byte[] imageBytes = extractImageBytesFromBdb(bdbBytes);
            String imageExtension = isJpeg2000CodeStream(imageBytes) ? "j2k" : "jp2";

            Path rawFile = outputDir.resolve(regId + "_" + recordProcess + "_face." + imageExtension);
            Files.write(rawFile, imageBytes);

            result.setRawFile(rawFile.toAbsolutePath().toString());

            Path imageFile = tryWritePng(regId, recordProcess, outputDir, imageBytes);
            if (imageFile != null) {
                result.setImageFile(imageFile.toAbsolutePath().toString());
                result.setStatus("SUCCESS");
                result.setRemark("Face image payload extracted from BDB and converted to PNG");
            } else {
                result.setStatus("RAW_SAVED");
                result.setRemark("Face image payload extracted from BDB, but PNG conversion codec is not available in this JVM");
            }

            return result;
        } catch (Exception e) {
            result.setStatus("FAILED");
            result.setRemark(e.getMessage());
            return result;
        }
    }

    private JsonNode callBiometrics(String regId, String recordProcess) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(biometricsUrl);

        BiometricsRequestDTO requestDTO = new BiometricsRequestDTO();
        requestDTO.setId(regId);
        requestDTO.setPerson("individualBiometrics");
        requestDTO.setModalities(new ArrayList<>());
        requestDTO.setSource("MIGRATOR".equalsIgnoreCase(recordProcess) ? "DATAMIGRATOR" : source);
        requestDTO.setProcess(recordProcess);
        requestDTO.setBypassCache(true);

        RequestWrapper<BiometricsRequestDTO> wrapper = new RequestWrapper<>();
        wrapper.setId("mosip.registration.packet.reader");
        wrapper.setVersion("v1");
        wrapper.setRequesttime(LocalDateTime.now(ZoneOffset.UTC));
        wrapper.setMetadata(Collections.emptyMap());
        wrapper.setRequest(requestDTO);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<RequestWrapper<BiometricsRequestDTO>> entity = new HttpEntity<>(wrapper, headers);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                builder.build().toUri(),
                HttpMethod.POST,
                entity,
                JsonNode.class
        );

        return response.getBody();
    }

    private boolean containsText(JsonNode values, String expectedValue) {
        if (values == null || !values.isArray()) {
            return false;
        }

        for (JsonNode value : values) {
            if (expectedValue.equalsIgnoreCase(value.asText())) {
                return true;
            }
        }

        return false;
    }

    private byte[] extractImageBytesFromBdb(byte[] bdbBytes) {
        int jp2Start = indexOf(bdbBytes, new byte[]{0x00, 0x00, 0x00, 0x0C, 0x6A, 0x50, 0x20, 0x20});
        if (jp2Start >= 0) {
            return Arrays.copyOfRange(bdbBytes, jp2Start, bdbBytes.length);
        }

        int ftypStart = indexOf(bdbBytes, new byte[]{0x66, 0x74, 0x79, 0x70, 0x6A, 0x70, 0x32});
        if (ftypStart >= 4) {
            return Arrays.copyOfRange(bdbBytes, ftypStart - 4, bdbBytes.length);
        }

        int codestreamStart = indexOf(bdbBytes, new byte[]{(byte) 0xFF, 0x4F, (byte) 0xFF, 0x51});
        if (codestreamStart >= 0) {
            return Arrays.copyOfRange(bdbBytes, codestreamStart, bdbBytes.length);
        }

        return bdbBytes;
    }

    private int indexOf(byte[] source, byte[] pattern) {
        for (int i = 0; i <= source.length - pattern.length; i++) {
            boolean matched = true;
            for (int j = 0; j < pattern.length; j++) {
                if (source[i + j] != pattern[j]) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return i;
            }
        }

        return -1;
    }

    private boolean isJpeg2000CodeStream(byte[] imageBytes) {
        return imageBytes.length >= 4
                && imageBytes[0] == (byte) 0xFF
                && imageBytes[1] == 0x4F
                && imageBytes[2] == (byte) 0xFF
                && imageBytes[3] == 0x51;
    }

    private Path tryWritePng(String regId, String recordProcess, Path outputDir, byte[] imageBytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (image == null) {
            return null;
        }

        Path imageFile = outputDir.resolve(regId + "_" + recordProcess + "_face.png");
        ImageIO.write(image, "png", imageFile.toFile());
        return imageFile;
    }
}

