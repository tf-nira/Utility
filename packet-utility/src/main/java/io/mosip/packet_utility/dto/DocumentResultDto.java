package io.mosip.packet_utility.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class DocumentResultDto {
    private Map<String, Object> identityDocuments;
    private List<DocumentDto> documents;
}