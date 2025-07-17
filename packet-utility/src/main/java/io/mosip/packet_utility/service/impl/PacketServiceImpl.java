package io.mosip.packet_utility.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.enums.CSVReaderNullFieldIndicator;
import com.opencsv.exceptions.CsvValidationException;
import io.mosip.packet_utility.dto.*;
import io.mosip.packet_utility.service.CbeffUtil;
import io.mosip.packet_utility.service.PacketService;
import io.mosip.kernel.biometrics.entities.BIR;
import io.mosip.kernel.biometrics.entities.BiometricRecord;

import io.mosip.kernel.core.util.JsonUtils;
import io.mosip.kernel.core.util.StringUtils;

import org.apache.commons.codec.binary.Base64;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
    public void getNINStatus() throws Exception {
        List<String> ninList = readNINsFromCSV(false);

        int batchSize = 25;
        List<List<String>> batches = createBatches(ninList, batchSize);

        Path outputPath = Paths.get(filepath, "nin_status_report.csv");

        try (Writer writer = Files.newBufferedWriter(outputPath); CSVWriter csvWriter = new CSVWriter(writer)) {

            // Write header
            csvWriter.writeNext(new String[] { "NIN", "STATUS" });
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
            	Map<String, String> bdbBasedOnFinger = cbeffutil.getBDBBasedOnType(Base64.decodeBase64(data), "Face",
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
	       					bdbData=Base64.encodeBase64String(bir.getBdb());
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
            csvWriter.writeNext(new String[] { "RenewalRid", "IdRepoRid","Status" });
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
                csvWriter.writeNext(new String[] { result.getRenewalRid(), result.getIdRepoRid(),result.isStatus() ? "true" : "false" });
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
			result.setStatus(true);
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
