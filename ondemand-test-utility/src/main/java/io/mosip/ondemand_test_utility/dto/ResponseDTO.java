package io.mosip.ondemand_test_utility.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ResponseDTO {
    private String rid;
    private Map<String, String> demographics;
    private Map<String, String> documents;
    private String status;
}
