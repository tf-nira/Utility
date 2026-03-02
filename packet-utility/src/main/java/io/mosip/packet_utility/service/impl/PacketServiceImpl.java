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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

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
import java.util.Collections;
import java.util.List;
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

    @Value("${io.moisp.packet.manager.metaInfo}")
    private String metainfo;

    @Value("${io.mosip.id.repo.fetch.nin-details.url}")
    private String idRepoUrl;

    @Value("${io.mosip.id.repo.update.identity.url}")
    private String updateIdentityUrl;

    @Value("${io.mosip.output.file.path}")
    private String filepath;

    private Boolean allField =false;

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

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url).queryParam("type", "all")
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
            int age = Period.between(dob, packetDate).getYears();
            applicationProcessingDTO.setApplicantAge(String.valueOf(age));
            if (age<2 || age >70){
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
}

