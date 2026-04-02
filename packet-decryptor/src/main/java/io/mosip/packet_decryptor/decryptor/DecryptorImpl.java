package io.mosip.packet_decryptor.decryptor;

import io.mosip.kernel.core.exception.ServiceError;
import io.mosip.kernel.core.http.RequestWrapper;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.packet_decryptor.constant.CryptomanagerConstant;
import io.mosip.packet_decryptor.dto.CryptomanagerRequestDto;
import io.mosip.packet_decryptor.dto.CryptomanagerResponseDto;
import io.mosip.packet_decryptor.exception.ApisResourceAccessException;
import io.mosip.packet_decryptor.exception.PacketDecryptionFailureException;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;

@Component
public class DecryptorImpl implements Decryptor{
    private static final Logger logger = LoggerFactory.getLogger(DecryptorImpl.class);

    @Value("${registration.processor.application.id}")
    private String applicationId;

    @Value("${crypto.PrependThumbprint.enable}")
    private boolean isPrependThumbprintEnabled;

    @Value("${mosip.kernel.cryptomanager.url}")
    private String cryptomanagerUrl;

    @Autowired
    private Environment env;

    @Autowired(required = true)
    @Qualifier("selfTokenRestTemplate")
    private RestTemplate restTemplate;

    private static final String DECRYPT_SERVICE_ID = "mosip.registration.processor.crypto.decrypt.id";
    private static final String REG_PROC_APPLICATION_VERSION = "mosip.registration.processor.application.version";
    private static final String DATETIME_PATTERN = "mosip.registration.processor.datetime.pattern";

    private static final String DECRYPTION_SUCCESS = "Decryption success";
    private static final String DECRYPTION_FAILURE = "Virus scan decryption failed for registrationId ";
    private static final String IO_EXCEPTION = "Exception while reading packet inputStream";
    private static final String DATE_TIME_EXCEPTION = "Error while parsing packet timestamp";

    /*
     * (non-Javadoc)
     *
     * @see
     * io.mosip.registration.processor.core.spi.decryptor.Decryptor#decrypt(java.io.
     * InputStream, java.lang.String)
     */
    @Override
    public InputStream decrypt(String id, String refId, InputStream packetStream)
            throws PacketDecryptionFailureException, ApisResourceAccessException {
        InputStream outstream = null;
        logger.debug(id, "Decryptor::decrypt()::entry");
        try {
            byte[] packet = IOUtils.toByteArray(packetStream);
            CryptomanagerRequestDto cryptomanagerRequestDto = new CryptomanagerRequestDto();
            cryptomanagerRequestDto.setPrependThumbprint(isPrependThumbprintEnabled);
            io.mosip.kernel.core.http.RequestWrapper<CryptomanagerRequestDto> request = new RequestWrapper<>();
            cryptomanagerRequestDto.setApplicationId(applicationId);
            cryptomanagerRequestDto.setReferenceId(refId);
            logger.info(id,
                    "Size = " + packet.length);
            byte[] nonce = Arrays.copyOfRange(packet, 0, CryptomanagerConstant.GCM_NONCE_LENGTH);
            byte[] aad = Arrays.copyOfRange(packet, CryptomanagerConstant.GCM_NONCE_LENGTH,
                    CryptomanagerConstant.GCM_NONCE_LENGTH + CryptomanagerConstant.GCM_AAD_LENGTH);
            byte[] encryptedData = Arrays.copyOfRange(packet, CryptomanagerConstant.GCM_NONCE_LENGTH + CryptomanagerConstant.GCM_AAD_LENGTH,
                    packet.length);
            cryptomanagerRequestDto.setAad(CryptoUtil.encodeToURLSafeBase64(aad));
            cryptomanagerRequestDto.setSalt(CryptoUtil.encodeToURLSafeBase64(nonce));
            cryptomanagerRequestDto.setData(CryptoUtil.encodeToURLSafeBase64(encryptedData));
            cryptomanagerRequestDto.setTimeStamp(DateUtils.getUTCCurrentDateTime());

            request.setId(env.getProperty(DECRYPT_SERVICE_ID));
            request.setMetadata(null);
            request.setRequest(cryptomanagerRequestDto);
            DateTimeFormatter format = DateTimeFormatter.ofPattern(env.getProperty(DATETIME_PATTERN));
            LocalDateTime localdatetime = LocalDateTime
                    .parse(DateUtils.getUTCCurrentDateTimeString(env.getProperty(DATETIME_PATTERN)), format);
            request.setRequesttime(localdatetime);
            request.setVersion(env.getProperty(REG_PROC_APPLICATION_VERSION));
            CryptomanagerResponseDto response;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<RequestWrapper<CryptomanagerRequestDto>> entity = new HttpEntity<>(request, headers);
            ResponseEntity<CryptomanagerResponseDto> responseEntity = restTemplate.exchange(cryptomanagerUrl, HttpMethod.POST, entity, CryptomanagerResponseDto.class);
            response = responseEntity.getBody();


            if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                ServiceError error = response.getErrors().get(0);
                logger.error(id, "Virus scan decryption failed for registrationId " + error.getMessage());
                throw new PacketDecryptionFailureException(error.getMessage());
            }
            byte[] decryptedPacket =null;
            try {
                decryptedPacket= CryptoUtil.decodeURLSafeBase64(response.getResponse().getData());
            } catch (IllegalArgumentException exception) {
                decryptedPacket= CryptoUtil.decodePlainBase64(response.getResponse().getData());
            }
            outstream = new ByteArrayInputStream(decryptedPacket);

            logger.debug(id, "Decryptor::decrypt()::exit");
            logger.info(id, "Decryption success");
        } catch (IOException e) {
            logger.error(id, "Exception while reading packet inputStream" + e.getMessage());
            throw new PacketDecryptionFailureException("Exception while reading packet inputStream" + e.getMessage());
        } catch (DateTimeParseException e) {
            logger.error(id, "Error while parsing packet timestamp" + e.getMessage());
            throw new PacketDecryptionFailureException("Error while parsing packet timestamp" + e.getMessage());
        } catch (RestClientException e) {
            logger.error(id, "Internal Error occurred " + e.getMessage());
            throw new ApisResourceAccessException("Error calling cryptomanager", e);
        }
        return outstream;
    }
}
