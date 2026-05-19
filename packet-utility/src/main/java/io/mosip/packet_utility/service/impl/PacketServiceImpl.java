package io.mosip.packet_utility.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.enums.CSVReaderNullFieldIndicator;
import com.opencsv.exceptions.CsvValidationException;
import io.mosip.biometrics.util.ConvertRequestDto;
import io.mosip.biometrics.util.face.FaceEncoder;
import io.mosip.image.compressor.sdk.impl.ImageCompressorSDKV2;
import io.mosip.kernel.biometrics.constant.BiometricType;
import io.mosip.kernel.biometrics.constant.ProcessedLevelType;
import io.mosip.kernel.biometrics.entities.*;
import io.mosip.kernel.biometrics.model.Response;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.packet_utility.dto.*;
import io.mosip.packet_utility.service.CbeffUtil;
import io.mosip.packet_utility.service.PacketService;

import javax.imageio.ImageIO;
import org.w3c.dom.*;

import java.awt.image.BufferedImage;
import java.io.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
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

    @Value("${io.moisp.packet.manager.search.metainfo.url}")
    private String metaInfoUrl;

    @Value("${io.mosip.id.repo.fetch.nin-details.url}")
    private String idRepoUrl;

    @Value("${io.mosip.id.repo.update.identity.url}")
    private String updateIdentityUrl;

    @Value("${io.mosip.output.file.path}")
    private String filepath;
    
    @Value("${io.mosip.packet.manager.get.biomterics.url}")
    private String getBiometricsUrl;


    private final Executor executor = Executors.newFixedThreadPool(200);
    
	@Autowired
	private CbeffUtil cbeffutil;

    @Autowired
    private ImageCompressorSDKV2 imageCompressorSDK;

    @Override
    public void getPacketNIN() throws Exception {
        List<String> ninList = readNINsFromCSV(true);

        int batchSize = 25;
        List<List<String>> batches = createBatches(ninList, batchSize);

        Path outputPath = Paths.get(filepath, "rid-nin.csv");

        try (Writer writer = Files.newBufferedWriter(outputPath); CSVWriter csvWriter = new CSVWriter(writer)) {

            // Write header
            csvWriter.writeNext(new String[] { "REG_ID", "NIN" });
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
                        csvWriter.writeNext(new String[] { result.getRid(), result.getNin() });
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
    public void getPacketCentreAndOperator() throws Exception {
        List<String> ninList = readNINsFromCSV(true);

        int batchSize = 25;
        List<List<String>> batches = createBatches(ninList, batchSize);

        Path outputPath = Paths.get(filepath, "cntr-opid.csv");

        try (Writer writer = Files.newBufferedWriter(outputPath); CSVWriter csvWriter = new CSVWriter(writer)) {

            // Write header
            csvWriter.writeNext(new String[] { "RID","CENTRE", "OPERATORID","SUPERVISORID" });
            csvWriter.flush();

            System.out.println("Processing " + ninList.size() + " RIDs in " + batches.size() + " batches");

            for (int i = 0; i < batches.size(); i++) {
                List<String> batch = batches.get(i);
                System.out.println(
                        "Processing batch " + (i + 1) + "/" + batches.size() + " with " + batch.size() + " RIDs");

                // Processing batch in parallel
                List<CompletableFuture<CenterResultDTO>> futures = batch.stream().map(rid -> CompletableFuture
                        .supplyAsync(() -> getcentrandid(rid), executor).handle((centerResultDTO, throwable) -> {
                            if (throwable != null) {
                                System.err.println("Error checking RID " + rid + ": " + throwable.getMessage());
                            }
                            return centerResultDTO;
                        })).collect(Collectors.toList());

                // Waiting for all futures in the batch to complete
                CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

                try {
                    // Wait for batch completion with timeout
                    allOf.get(2, TimeUnit.MINUTES);

                    // Write results for this batch
                    for (CompletableFuture<CenterResultDTO> future : futures) {
                        CenterResultDTO result = future.get();
                        csvWriter.writeNext(new String[] { result.getRid(), result.getCntr() , result.getOpid() , result.getSupid() });
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

        System.out.println("center and operational data extraction completed successfully");
    }

@Override
public void getNINStatus() throws Exception {
    List<String> ninList = readNINsFromCSV(false);

    int batchSize = 25;
    List<List<String>> batches = createBatches(ninList, batchSize);

    Path outputPath = Paths.get(filepath, "nin_status_report.csv");

    try (Writer writer = Files.newBufferedWriter(outputPath); CSVWriter csvWriter = new CSVWriter(writer)) {

        csvWriter.writeNext(new String[] { "NIN", "STATUS" });
        csvWriter.flush();

        System.out.println("Processing " + ninList.size() + " NINs in " + batches.size() + " batches");

        for (int i = 0; i < batches.size(); i++) {
            List<String> batch = batches.get(i);
            System.out.println("Processing batch " + (i + 1) + "/" + batches.size() + " with " + batch.size() + " NIMs");

            List<CompletableFuture<NinStatusDTO>> futures = batch.stream().map(nin -> CompletableFuture
                    .supplyAsync(() -> checkNINExistsAsync(nin, filepath), executor) // ✅ pass filepath
                    .handle((ninStatusDTO, throwable) -> {
                        if (throwable != null) {
                            System.err.println("Error checking NIN " + nin + ": " + throwable.getMessage());
                        }
                        return ninStatusDTO;
                    })).collect(Collectors.toList());

            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

            try {
                allOf.get(2, TimeUnit.MINUTES);

                for (CompletableFuture<NinStatusDTO> future : futures) {
                    NinStatusDTO result = future.get();
                    csvWriter.writeNext(new String[] { result.getNin(), result.getStatus() });
                }
                csvWriter.flush();

                System.out.println("Completed batch " + (i + 1) + "/" + batches.size());

            } catch (TimeoutException e) {
                System.err.println("Batch " + (i + 1) + " timed out after 5 minutes");
            } catch (ExecutionException | InterruptedException e) {
                System.err.println("Error processing batch " + (i + 1) + ": " + e.getMessage());
            }

            Thread.sleep(1000);
        }

    } catch (Exception e) {
        System.err.println("Error occurred: " + e.getMessage());
        throw e;
    }

    System.out.println("NIN status check completed successfully");
}

    @Override
    public void updateIdentity() throws  Exception {
        List<List<String>> ninList = readMultiFieldCSV();

        int batchSize = 25;
        List<List<List<String>>> batches = createMultiFieldBatches(ninList, batchSize);

        Path outputPath = Paths.get(filepath, "update_nins_output.csv");

        try (Writer writer = Files.newBufferedWriter(outputPath); CSVWriter csvWriter = new CSVWriter(writer)) {

            // Write header
            csvWriter.writeNext(new String[] { "NIN", "STATUS" });
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
                        csvWriter.writeNext(new String[] { result.getNin(), result.getStatus() });
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

    public List<String> readNINsFromCSV (boolean getNin) throws IOException, CsvValidationException {

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
        identity.setIDSchemaVersion(8.4);
        identity.setNIN(updateDetailsInfo.get(0));

        if (isNotNull(updateDetailsInfo.get(1))) {
            LocalizedValue surnameValue = new LocalizedValue();
            surnameValue.setLanguage("eng");
            surnameValue.setValue(updateDetailsInfo.get(1));
            identity.setSurname(Collections.singletonList(surnameValue));
        }

        if (isNotNull(updateDetailsInfo.get(2))) {
            LocalizedValue givenNameValue = new LocalizedValue();
            givenNameValue.setLanguage("eng");
            givenNameValue.setValue(updateDetailsInfo.get(2));
            identity.setGivenName(Collections.singletonList(givenNameValue));
        }

        if (isNotNull(updateDetailsInfo.get(3))) {
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
        long number = 1_000_000_000L + (long)(secureRandom.nextDouble() * 9_000_000_000L);
        return String.valueOf(number);
    }

    public NINResultDTO getNIN (String rid) {
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

    public CenterResultDTO  getcentrandid (String rid) {
        CenterResultDTO centerResultDTO = new CenterResultDTO();
        centerResultDTO.setRid(rid);
        //process needs to be changed accordingly
        InfoDto fieldDto = new InfoDto(rid, source, process, false);
        RequestWrapper<InfoDto> request = new RequestWrapper<>();
        request.setId(rid);
        request.setVersion("v1");
        request.setRequesttime(DateUtils.getUTCCurrentDateTime());
        request.setRequest(fieldDto);
        String url = metaInfoUrl;
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RequestWrapper<InfoDto>> entity = new HttpEntity<>(request, headers);
        try {
            ResponseEntity<ResponseWrapper<FieldResponseDTO>> responseEntity = restTemplate.exchange(builder.build().toUri(),
                    HttpMethod.POST, entity, new ParameterizedTypeReference<ResponseWrapper<FieldResponseDTO>>() {
                    });

            ResponseWrapper<FieldResponseDTO> responseWrapper = responseEntity.getBody();

            if (responseWrapper.getErrors() != null && !responseWrapper.getErrors().isEmpty()) {
                System.out.println("rid : " + rid + " packetmanager failed");
                centerResultDTO.setCntr(responseWrapper.getErrors().get(0).getMessage());
                centerResultDTO.setOpid(responseWrapper.getErrors().get(0).getMessage());
                centerResultDTO.setSupid(responseWrapper.getErrors().get(0).getMessage());
                return centerResultDTO;
            }

            FieldResponseDTO fieldResponseDto = objectMapper.readValue(JsonUtils.javaObjectToJsonString(responseWrapper.getResponse()), FieldResponseDTO.class);
            if (fieldResponseDto != null) {
//                String cntr = fieldResponseDto.getFields().get("operationsData");
//
//                String opid = fieldResponseDto.getFields().get("officerId");
//                centerResultDTO.setCntr(cntr);
//                centerResultDTO.setOpid(opid);
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode rootNode = objectMapper.valueToTree(fieldResponseDto.getFields());

// Extract officerId directly from operationsData
                JsonNode operationsDataNode = rootNode.get("operationsData");
                String opid = null;
                String supid = null;
                if (operationsDataNode != null && operationsDataNode.isTextual()) {
                    try {
                        // Parse the text content as JSON
                        String operationsDataText = operationsDataNode.asText();
                        JsonNode parsedOperationsData = objectMapper.readTree(operationsDataText);

                        // Now iterate through the parsed JSON array
                        if (parsedOperationsData.isArray()) {
                            for (JsonNode item : parsedOperationsData) {
                                if ("officerId".equals(item.get("label").asText())) {
                                    opid = item.get("value").asText();
                                    break;
                                }
                            }
                        }
                        if (parsedOperationsData.isArray()) {
                            for (JsonNode item : parsedOperationsData) {
                                if ("supervisorId".equals(item.get("label").asText())) {
                                    supid = item.get("value").asText();
                                    break;
                                }
                            }
                        }
                    } catch (JsonProcessingException e) {
                        System.err.println("Error parsing operationsData JSON: " + e.getMessage());
                    }
                }

// Extract centerId directly from metadata
                JsonNode metadataNode = rootNode.get("metaData");
                String cntr = null;
                if (metadataNode != null && metadataNode.isTextual()) {
                    try {
                        // Parse the text content as JSON
                        String metaDataText = metadataNode.asText();
                        JsonNode parsedOperationsData2 = objectMapper.readTree(metaDataText);

                        // Now iterate through the parsed JSON array
                        if (parsedOperationsData2.isArray()) {
                            for (JsonNode item : parsedOperationsData2) {
                                if ("centerId".equals(item.get("label").asText())) {
                                    cntr = item.get("value").asText();
                                    break;
                                }
                            }
                        }
                    } catch (JsonProcessingException e) {
                        System.err.println("Error parsing operationsData JSON: " + e.getMessage());
                    }
                }

// Set DTO
//                centerResultDTO.setOpid(opid);
//                centerResultDTO.setSupid(supid);
//                centerResultDTO.setCntr(cntr);
                centerResultDTO.setOpid(opid != null ? opid.toLowerCase() : "null");
                centerResultDTO.setSupid(supid != null ? supid.toLowerCase() : "null");
                centerResultDTO.setCntr(cntr != null ? cntr : "null");


            }

            return centerResultDTO;

        } catch (RestClientException e) {
            System.err.println("Exception for RID " + rid + ": " + e.getMessage());
            centerResultDTO.setCntr(e.getMessage());
            centerResultDTO.setOpid(e.getMessage());
            return centerResultDTO;
        } catch (JsonProcessingException | io.mosip.kernel.core.util.exception.JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }


public NinStatusDTO checkNINExistsAsync(String nin, String baseOutputPath) {

    NinStatusDTO ninStatusDTO = new NinStatusDTO();
    ninStatusDTO.setNin(nin);

    String handle = nin.toLowerCase() + "@nin";
    String url = idRepoUrl + handle;

    UriComponentsBuilder builder =
            UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("type", "all")
                    .queryParam("idType", "handle");

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> entity = new HttpEntity<>(null, headers);

    try {
        Path ninFolder = Paths.get(baseOutputPath, nin);
        Files.createDirectories(ninFolder);

        ResponseEntity<ResponseWrapper<NINStatusResponseDTO>> responseEntity =
                restTemplate.exchange(
                        builder.build().toUri(),
                        HttpMethod.GET,
                        entity,
                        new ParameterizedTypeReference<ResponseWrapper<NINStatusResponseDTO>>() {});

        ResponseWrapper<NINStatusResponseDTO> responseWrapper = responseEntity.getBody();

        String data = null;

        if (responseWrapper != null && responseWrapper.getResponse() != null) {

            Object identity = responseWrapper.getResponse().getIdentity();
            if (identity != null) {
                writeDemographicInfo(ninFolder, identity, nin);
            }

            List<Documents> docs = responseWrapper.getResponse().getDocuments();
            for (Documents doc : docs) {
                if ("individualBiometrics".equalsIgnoreCase(doc.getCategory())) {
                    data = doc.getValue();
                    break;
                }
            }
        }

        if (data != null) {

            byte[] decodedBytes = Base64.getUrlDecoder().decode(data);

            Map<String, String> faceMap = cbeffutil.getBDBBasedOnType(decodedBytes, "Face", null);

            if (faceMap != null && !faceMap.isEmpty()) {

                String faceBdbBase64 = faceMap.values().iterator().next();
                byte[] faceBdb = Base64.getDecoder().decode(faceBdbBase64);


                byte[] jp2Bytes = extractImageFromIso19794(faceBdb);
                if (jp2Bytes != null) {
                    BufferedImage rawImage = ImageIO.read(new ByteArrayInputStream(jp2Bytes));
                    if (rawImage != null) {
                        File rawFile = ninFolder.resolve("raw_face.jpg").toFile();
                        ImageIO.write(rawImage, "JPEG", rawFile);
                        System.out.println(" [" + nin + "] raw_face.jpg written");
                    }
                }

                // Re-encode into fresh ISO container for SDK
                ConvertRequestDto requestDto = new ConvertRequestDto();
                requestDto.setModality("Face");
                requestDto.setPurpose("REGISTRATION");
                requestDto.setVersion("ISO19794_5_2011");
                requestDto.setImageType(0);
                requestDto.setInputBytes(jp2Bytes);
                byte[] reEncodedIso = FaceEncoder.convertFaceImageToISO(requestDto);

                RegistryIDType format = new RegistryIDType();
                format.setOrganization("MOSIP");
                format.setType("8");

                BDBInfo bdbInfo = new BDBInfo();
                bdbInfo.setType(Collections.singletonList(BiometricType.FACE));
                bdbInfo.setSubtype(new ArrayList<>());
                bdbInfo.setLevel(ProcessedLevelType.RAW);
                bdbInfo.setFormat(format);

                BIR bir = new BIR.BIRBuilder()
                        .withBdb(reEncodedIso)
                        .withBdbInfo(bdbInfo)
                        .build();

                BiometricRecord biometricRecord = new BiometricRecord();
                biometricRecord.setSegments(Collections.singletonList(bir));

                Response<BiometricRecord> response =
                        imageCompressorSDK.extractTemplate(
                                biometricRecord,
                                Arrays.asList(BiometricType.FACE),
                                new HashMap<>());

                if (response != null && response.getStatusCode() == 200) {

                    byte[] compressedBdb = response.getResponse().getSegments().get(0).getBdb();

                    System.out.println("[" + nin + "] Oriiginal  : " + reEncodedIso.length + " bytes");
                    System.out.println("[" + nin + "] Compresed: " + compressedBdb.length + " bytes");
                    System.out.println("[" + nin + "] Reduction : "
                            + (100 - (compressedBdb.length * 100 / reEncodedIso.length)) + "%");


                    byte[] compressedJp2 = extractImageFromIso19794(compressedBdb);
                    if (compressedJp2 != null) {
                        BufferedImage compressedImage = ImageIO.read(new ByteArrayInputStream(compressedJp2));
                        if (compressedImage != null) {
                            File compressedFile = ninFolder.resolve("compressed_face.jpg").toFile();
                            ImageIO.write(compressedImage, "JPEG", compressedFile);
                            System.out.println(" [" + nin + "] compressed_face.jpg written");
                        } else {
                            Files.write(ninFolder.resolve("compressed_face.jp2"), compressedJp2);
                            System.out.println(" [" + nin + "] compressed_face.jp2 written (fallback)");
                        }
                    } else {
                        BufferedImage compressedImage = ImageIO.read(new ByteArrayInputStream(compressedBdb));
                        if (compressedImage != null) {
                            File compressedFile = ninFolder.resolve("compressed_face.jpg").toFile();
                            ImageIO.write(compressedImage, "JPEG", compressedFile);
                            System.out.println(" [" + nin + "] compressed_face.jpg written (direct)");
                        }
                    }

                } else {
                    System.out.println("SDK error for [" + nin + "]: "
                            + (response != null ? response.getStatusCode() : "null"));
                }

                ninStatusDTO.setStatus("FACE_FOUND");

            } else {
                System.out.println(" Face NOT found for: " + nin);
                ninStatusDTO.setStatus("FACE_NOT_FOUND");
            }

        } else {
            ninStatusDTO.setStatus("NO_BIOMETRICS");
        }

        if (responseWrapper != null &&
                responseWrapper.getErrors() != null &&
                !responseWrapper.getErrors().isEmpty()) {
            System.out.println("NIN not found: " + nin);
            ninStatusDTO.setStatus("NOT_FOUND");
        }

    } catch (Exception e) {
        System.err.println("Exception for NIN " + nin + ": " + e.getMessage());
        ninStatusDTO.setStatus("ERROR");
    }

    return ninStatusDTO;
}

    private byte[] extractImageFromIso19794(byte[] faceRecord) {

        for (int i = 0; i < faceRecord.length - 3; i++) {

            // JPEG2000 JP2 file format signature
            if ((faceRecord[i] & 0xFF) == 0x00
                    && (faceRecord[i + 1] & 0xFF) == 0x00
                    && (faceRecord[i + 2] & 0xFF) == 0x00
                    && (faceRecord[i + 3] & 0xFF) == 0x0C) {
                // verify next 4 bytes are "jP  " (6A 50 20 20)
                if (i + 7 < faceRecord.length
                        && (faceRecord[i + 4] & 0xFF) == 0x6A
                        && (faceRecord[i + 5] & 0xFF) == 0x50
                        && (faceRecord[i + 6] & 0xFF) == 0x20
                        && (faceRecord[i + 7] & 0xFF) == 0x20) {
                    byte[] jp2 = new byte[faceRecord.length - i];
                    System.arraycopy(faceRecord, i, jp2, 0, jp2.length);
                    System.out.println(" JPEG2000 (JP2) found at offset: " + i);
                    return jp2;
                }
            }

            // JPEG2000 codestream signature (no JP2 container)
            if ((faceRecord[i] & 0xFF) == 0xFF
                    && (faceRecord[i + 1] & 0xFF) == 0x4F
                    && (faceRecord[i + 2] & 0xFF) == 0xFF
                    && (faceRecord[i + 3] & 0xFF) == 0x51) {
                byte[] j2k = new byte[faceRecord.length - i];
                System.arraycopy(faceRecord, i, j2k, 0, j2k.length);
                System.out.println(" JPEG2000 (J2K codestream) found at offset: " + i);
                return j2k;
            }
        }

        System.out.println(" No image found in ISO 19794-5 record");
        return null;
    }

    private void writeDemographicInfo(Path ninFolder, Object identity, String nin) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(identity);
            JsonNode node = mapper.readTree(json);
            String surname    = getLanguageValue(node, "surname");
            String givenName  = getLanguageValue(node, "givenName");
            String otherNames = getLanguageValue(node, "otherName");
            String dob        = getNodeValue(node, "dateOfBirth");
            String gender     = getLanguageValue(node, "gender");
            String content = String.join(System.lineSeparator(),
                    "NIN           : " + nin,
                    "Surname       : " + surname,
                    "Given Name    : " + givenName,
                    "Other Names   : " + otherNames,
                    "Date of Birth : " + dob,
                    "Gender        : " + gender
            );

            Files.write(ninFolder.resolve(nin+"_demographic_info.txt"),
                    content.getBytes(StandardCharsets.UTF_8));

            System.out.println(" [" + nin + "] demographic_info.txt written");

        } catch (Exception e) {
            System.err.println(" [" + nin + "] Failed to write demographic info: " + e.getMessage());
        }
    }
    private String getLanguageValue(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        if (fieldNode != null && fieldNode.isArray() && fieldNode.size() > 0) {
            JsonNode first = fieldNode.get(0);
            if (first.has("value")) {
                return first.get("value").asText("");
            }
        }
        if (fieldNode != null && fieldNode.isTextual()) {
            return fieldNode.asText("");
        }
        return "";
    }

    private String getNodeValue(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        if (fieldNode != null) {
            return fieldNode.asText("");
        }
        return "";
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
	public String  getDetailsFromIdRepo(String rid) {
		    String  bdbdata=null;
	        String url = idRepoUrl + rid;
            
	        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url).queryParam("type", "all");

	        HttpHeaders headers = new HttpHeaders();
	        headers.setContentType(MediaType.APPLICATION_JSON);
	        HttpEntity<String> entity = new HttpEntity<>(null, headers);

	        try {
	            ResponseEntity<ResponseWrapper<NINStatusResponseDTO>> responseEntity = restTemplate.exchange(builder.build().toUri(),
	                    HttpMethod.GET, entity, new ParameterizedTypeReference<ResponseWrapper<NINStatusResponseDTO>>() {
	                    });

	            ResponseWrapper<NINStatusResponseDTO> responseWrapper = responseEntity.getBody();
	            String data = null;
	            if (responseWrapper.getResponse() != null) {
	            	List<Documents> docs=responseWrapper.getResponse().getDocuments();
	            	for (int i = 0; i < docs.size(); i++) {
	    				if (docs.get(i).getCategory().equalsIgnoreCase("individualBiometrics")) {
	    					data = docs.get(i).getValue();
	    					break;
	    				}
	    			}

	            }
            if(data!=null) {
            	Map<String, String> bdbBasedOnFinger = cbeffutil.getBDBBasedOnType(Base64.getDecoder().decode(data), "Face",
        				null);
            	 bdbdata = bdbBasedOnFinger.values().iterator().next();
            
            }
	           

	        } catch (RestClientException e) {
	            System.err.println("Exception for rid " + rid + ": " + e.getMessage());
	          
	            return bdbdata;
	        } catch (Exception e) {
	        	 System.err.println("Exception for rid " + rid + ": " + e.getMessage());
		          
		            return bdbdata;
			}
			return bdbdata;
		
	}

	@Override
	public String getDetailsFromPacketManager(String rid) {
		   String bdbData=null;
		   List<String> modalities=new ArrayList<String>();
		   modalities.add("Face");
		  BiometricRequestDto fieldDto = new BiometricRequestDto(rid, "individualBiometrics", modalities, source, process, false);
		   String url = getBiometricsUrl;
	        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);

	        HttpHeaders headers = new HttpHeaders();
	        headers.setContentType(MediaType.APPLICATION_JSON);
	        
	        RequestWrapper<BiometricRequestDto> request = new RequestWrapper<>();
	        request.setRequest(fieldDto);

	        HttpEntity<RequestWrapper<BiometricRequestDto>> entity = new HttpEntity<>(request, headers);

	        try {
	            ResponseEntity<ResponseWrapper<BiometricRecord>> responseEntity = restTemplate.exchange(builder.build().toUri(),
	                    HttpMethod.POST, entity, new ParameterizedTypeReference<ResponseWrapper<BiometricRecord>>() {
	                    });

	            ResponseWrapper<BiometricRecord> responseWrapper = responseEntity.getBody();
	            if (responseWrapper.getErrors() != null && !responseWrapper.getErrors().isEmpty()) {
	                System.out.println("Error for rid : " + rid + " from packet manager");
	                return bdbData;
	            }

	            BiometricRecord biometricRecord = objectMapper.readValue(JsonUtils.javaObjectToJsonString(responseWrapper.getResponse()), BiometricRecord.class);
	            for (BIR bir : biometricRecord.getSegments()) {
	                if(bir.getBdbInfo().getType() != null) {
	       				if(bir.getBdb()!=null) {
                            bdbData = Base64.getEncoder().encodeToString(bir.getBdb());
	       				}
	                }
	       		}
	        } catch (RestClientException e) {
	        	 System.err.println("Exception for rid " + rid + ": " + e.getMessage());
		   
		            return bdbData;
	        } catch (JsonMappingException e) {
	        	System.err.println("Exception for rid " + rid + ": " + e.getMessage());
	 		   
	            return bdbData;
			} catch (JsonProcessingException e) {
				System.err.println("Exception for rid " + rid + ": " + e.getMessage());
				   
	            return bdbData;
			} catch (io.mosip.kernel.core.util.exception.JsonProcessingException e) {
				System.err.println("Exception for rid " + rid + ": " + e.getMessage());
				   
	            return bdbData;
			}
		     return bdbData;
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void comparePacketsFromPacketMgrAndIdRepo() throws IOException {
        Resource resource = new ClassPathResource("dataToCompare.csv");
        List<RegistrationIdDto> dataFromCsv = readDataFromCSV(resource.getInputStream());  
        Path outputPath = Paths.get(filepath, "rids_compare_result.csv");
        try (Writer writer = Files.newBufferedWriter(outputPath); CSVWriter csvWriter = new CSVWriter(writer)) {
            csvWriter.writeNext(new String[] { "RenewalRid", "IdRepoRid","Status","Error" });
            csvWriter.flush();
            List<CompletableFuture<CompareDataResultDto>> futures = dataFromCsv.stream()
            	    .map(data -> CompletableFuture
            	            .supplyAsync(() -> {
            	                return compareData(data.getRenewalRid(), data.getIdRepoRid());
            	            }, executor) 
            	            .handle((resultDto, throwable) -> {
            	                if (throwable != null) {
            	                    System.err.println("Error checking RID " + data.getRenewalRid() + ": " + throwable.getMessage());
            	                    return null; 
            	                }
            	                return resultDto; 
            	            }))
            	    .collect(Collectors.toList());
            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allOf.join();
            for (CompletableFuture<CompareDataResultDto> future : futures) {
            	CompareDataResultDto result = future.get();
                csvWriter.writeNext(new String[] { result.getRenewalRid(), result.getIdRepoRid(),result.isStatus() ? "true" : "false",result.isError() ? "true" : "false" });
            }
            csvWriter.flush();

        }catch (Exception e) {
        	System.err.println("Error checking RID "  + e.getMessage());
       
		}        
	}
	
	private CompareDataResultDto compareData(String renewalRid, String IdRepoRid) {
		CompareDataResultDto result = new CompareDataResultDto();
		result.setIdRepoRid(IdRepoRid);
		result.setRenewalRid(renewalRid);
	     String dataFromIdrepo=getDetailsFromIdRepo(IdRepoRid);
	     String dataFromPacketManager=getDetailsFromPacketManager(renewalRid);
		if(dataFromIdrepo ==null || dataFromPacketManager== null) {
			result.setError(true);
		}else {
           if(dataFromIdrepo.equalsIgnoreCase(dataFromPacketManager)) {
        	   result.setStatus(true);
                }
           else {
               result.setStatus(false);
            }
		}
		System.out.println("RENEWAL rid  migration rid and status "  + result.getRenewalRid()+result.getIdRepoRid()+result.isStatus());
		return result;
	}
	
	
	
	private List<RegistrationIdDto> readDataFromCSV(InputStream inputStream){
		List<RegistrationIdDto> data = new ArrayList<>();
		try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(inputStream))) {

            CSVReader reader = new CSVReaderBuilder(fileReader)
                    .withSkipLines(0) // Start reading from the first line
                    .withFieldAsNull(CSVReaderNullFieldIndicator.EMPTY_SEPARATORS) // Treat empty fields as null
                    .build();
            // 
            String[] header = reader.readNext();
            if (header == null) {
                throw new IOException("CSV file is empty or has no header.");
            }
            
            Map<String, Integer> headerMap = new java.util.HashMap<>();
            for (int i = 0; i < header.length; i++) {
                headerMap.put(header[i].trim().toLowerCase(), i);
            }

            
            Integer renewalRidIndex = headerMap.get("renewalrid");
            Integer idRepoRidIndex = headerMap.get("idreporid");

            if (renewalRidIndex == null || idRepoRidIndex == null) {
                throw new IOException("Required columns 'RenewalRid' or 'IdRepoRid' not found in the CSV header.");
            }

            String[] line;
            while ((line = reader.readNext()) != null) {
                try {
                	RegistrationIdDto dataDto = new RegistrationIdDto();
                	dataDto.setRenewalRid(line[renewalRidIndex]);
                    dataDto.setIdRepoRid(line[idRepoRidIndex]);
                    data.add(dataDto);
                } catch (NumberFormatException e) {
                    System.err.println("Skipping row due to invalid age format: " + Arrays.toString(line) + " - " + e.getMessage());
                } catch (ArrayIndexOutOfBoundsException e) {
                    System.err.println("Skipping row due to malformed CSV line (not enough columns): " + Arrays.toString(line) + " - " + e.getMessage());
                }
            }
        } catch (IOException | CsvValidationException e) {
            throw new RuntimeException("Failed to parse CSV file: " + e.getMessage(), e);
        }
		return data;
	}
}
