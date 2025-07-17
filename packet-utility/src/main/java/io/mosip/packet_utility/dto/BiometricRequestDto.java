package io.mosip.packet_utility.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class BiometricRequestDto {

    private String id;
    private String person;
    private List<String> modalities;
    private String source;
    private String process;
    private boolean bypassCache;
}
