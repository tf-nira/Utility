package io.mosip.ondemand_test_utility.service.impl;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import io.mosip.kernel.core.http.ResponseWrapper;
import io.mosip.kernel.core.util.StringUtils;
import io.mosip.ondemand_test_utility.dto.RequestDTO;
import io.mosip.ondemand_test_utility.dto.RequestWrapper;
import io.mosip.ondemand_test_utility.dto.ResponseDTO;
import io.mosip.ondemand_test_utility.service.OndemandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
public class OndemandServiceImpl implements OndemandService {

    @Value("${mosip.ondemand-test-utility.output.path}")
    private String outputFilepath;

    @Value("${mosip.ondemand-test-utility.input.path}")
    private String inputFilepath;

    @Value("${mosip.ondemand-test-utility.ondemand.url}")
    private String ondemandUrl;

    @Autowired(required = true)
    private RestTemplate restTemplate;

    private final Executor executor = Executors.newFixedThreadPool(200);

    private static class NINStatusResult {
        private final String nin;
        private final String rid;

        public NINStatusResult(String nin, String status) {
            this.nin = nin;
            this.rid = status;
        }

        public String getNin() {
            return nin;
        }

        public String getRid() {
            return rid;
        }
    }

    @Override
    public void getRidsFromNIN() throws Exception {

        List<String> ninList = readNINsFromCSV();

        int batchSize = 25;
        List<List<String>> batches = createBatches(ninList, batchSize);

        Path outputPath = Paths.get(outputFilepath, "output.csv");

        try (Writer writer = Files.newBufferedWriter(outputPath); CSVWriter csvWriter = new CSVWriter(writer)) {

            // Write header
            csvWriter.writeNext(new String[] { "NIN", "RID" });
            csvWriter.flush();

            System.out.println("Fetching rids for " + ninList.size() + " NINs in " + batches.size() + " batches");

            for (int i = 0; i < batches.size(); i++) {
                List<String> batch = batches.get(i);
                System.out.println(
                        "Processing batch " + (i + 1) + "/" + batches.size() + " with " + batch.size() + " NINs");

                // Processing batch in parallel
                List<CompletableFuture<NINStatusResult>> futures = batch.stream().map(nin -> CompletableFuture
                        .supplyAsync(() -> fetchRIDforNIN(nin), executor).handle((rid, throwable) -> {
                            if (throwable != null) {
                                System.err.println("Error fetching RID for NIN " + nin + ": " + throwable.getMessage());
                                return new NINStatusResult(nin, "ERROR");
                            }
                            return new NINStatusResult(nin, rid);
                        })).collect(Collectors.toList());

                // Waiting for all futures in the batch to complete
                CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

                try {
                    // Wait for batch completion with timeout
                    allOf.get(2, TimeUnit.MINUTES);

                    // Write results for this batch
                    for (CompletableFuture<NINStatusResult> future : futures) {
                        NINStatusResult result = future.get();
                        csvWriter.writeNext(new String[] { result.getNin(), result.getRid() });
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

        System.out.println("NIN rid check completed successfully");
    }

    private String fetchRIDforNIN (String nin) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(ondemandUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        RequestDTO ninRequest = new RequestDTO();
        ninRequest.setNin(nin);

        RequestWrapper<RequestDTO> request = new RequestWrapper<>();
        request.setId("");
        request.setVersion("");
        request.setRequest(ninRequest);

        HttpEntity<RequestWrapper<RequestDTO>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<ResponseWrapper<ResponseDTO>> responseEntity = restTemplate.exchange(builder.build().toUri(),
                    HttpMethod.POST, entity, new ParameterizedTypeReference<ResponseWrapper<ResponseDTO>>() {
                    });

            if (responseEntity.getBody() == null) {
                return null;
            }

            ResponseWrapper<ResponseDTO> responseWrapper = responseEntity.getBody();

            if (responseWrapper.getErrors() != null && !responseWrapper.getErrors().isEmpty()) {
                boolean noRecordFound = responseWrapper.getErrors().stream()
                        .anyMatch(error -> "IDR-IDC-007".equals(error.getErrorCode()));

                if (noRecordFound) {
                    System.out.println("NIN not found in ID repo: " + nin);
                    return null;
                }
                return null;
            }

            return responseWrapper.getResponse().getRid();

        } catch (RestClientException e) {
            System.err.println("Exception for NIN " + nin + ": " + e.getMessage());
            return null;
        }
    }

    private List<List<String>> createBatches(List<String> list, int batchSize) {
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            int end = Math.min(i + batchSize, list.size());
            batches.add(list.subList(i, end));
        }
        return batches;
    }

    private List<String> readNINsFromCSV() throws IOException, CsvValidationException {
        List<String> ninList = new ArrayList<>();
        File file = new File(inputFilepath);

        try (Reader reader = new FileReader(file); CSVReader csvReader = new CSVReader(reader)) {

            String[] line;
            while ((line = csvReader.readNext()) != null) {
                String nin = line[0].trim();
                if (StringUtils.isNotEmpty(nin)) {
                    ninList.add(nin);
                }
            }
            System.out.println("Total nins read from csv file :: " + ninList.size());
        } catch (Exception e) {
            System.out.println("Exception occured : " + e);
            throw e;
        }
        return ninList;
    }
}
