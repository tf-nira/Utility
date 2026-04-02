package io.mosip.packet_decryptor.dto;

import io.mosip.kernel.core.http.ResponseWrapper;
import io.swagger.annotations.ApiModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@ApiModel(description = "Model representing a Crypto-Manager-Service Response")
public class CryptomanagerResponseDto extends ResponseWrapper<DecryptResponseDto>  {
}
