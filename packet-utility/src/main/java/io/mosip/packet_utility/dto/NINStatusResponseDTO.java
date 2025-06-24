package io.mosip.packet_utility.dto;

import lombok.Data;

import java.util.List;

@Data
public class NINStatusResponseDTO {
    private String entity;
    private Object identity;
    private List<Object> documents;
    private String status;
    private List<Object> cardDetails;
}
