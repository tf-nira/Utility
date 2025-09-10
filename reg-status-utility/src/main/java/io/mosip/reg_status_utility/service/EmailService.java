package io.mosip.reg_status_utility.service;

import com.fasterxml.jackson.core.type.TypeReference;
import io.mosip.kernel.core.http.RequestWrapper;
import io.mosip.kernel.core.http.ResponseWrapper;
import io.mosip.reg_status_utility.dto.AuthenticationRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Service
public class EmailService {

    @Autowired(required = true)
    @Qualifier("selfTokenRestTemplate")
    private RestTemplate restTemplate;

    @Value("${mosip.auth.url}")
    private String authenticationUrl;

    @Value("${mosip.iam.adapter.appid}")
    private String appId;

    @Value("${mosip.iam.adapter.clientid}")
    private String clientId;

    @Value("${mosip.iam.adapter.clientsecret}")
    private String clientSecret;

    @Value("${mosip.email.notification.url}")
    private String emailNotificationUrl;

    public String getAuthToken () {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");

            AuthenticationRequest request = new AuthenticationRequest();
            request.setAppId(appId);
            request.setClientId(clientId);
            request.setSecretKey(clientSecret);

            RequestWrapper<AuthenticationRequest> authRequest = new RequestWrapper<>();
            authRequest.setRequest(request);

            HttpEntity<RequestWrapper<AuthenticationRequest>> entity = new HttpEntity<>(authRequest, headers);

            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(authenticationUrl);

            ResponseEntity<String> response = restTemplate.postForEntity(builder.build().toUri(),
                    entity, String.class);

            if (response.getBody() == null) {
                log.error("Failed to authenticate. Status code: " + response.getStatusCodeValue());
            }

            log.info("User authenticated successfully");

            return response.getHeaders().getFirst("authorization");
        } catch (Exception e) {
            log.error("Failed to authenticate, {}", e);
            return null;
        }
    }

    public void sendEmail(List<String> to, String subject, String body) {
        String authToken = getAuthToken();
        log.info("Sending email notification");
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(emailNotificationUrl)
                    .queryParam("mailTo", String.join(",", to))
                    .queryParam("mailSubject", subject)
                    .queryParam("mailContent", body);

            LinkedMultiValueMap<String, Object> params = new LinkedMultiValueMap<>();
            params.add("attachments", null);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Cookie", "Authorization=" + authToken);

            HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = new HttpEntity<>(params, headers);

            ResponseEntity<ResponseWrapper> responseEntity = restTemplate.exchange(
                    builder.build().toUri(),
                    HttpMethod.POST,
                    requestEntity,
                    ResponseWrapper.class
            );

            if (responseEntity.getBody() == null) {
                log.error("Failed to send email notification. Status code: {}", responseEntity.getStatusCodeValue());
            }

            ResponseWrapper<?> responseWrapper = responseEntity.getBody();

            if (responseWrapper.getErrors() != null && !responseWrapper.getErrors().isEmpty()) {
                log.error("Email notification error: {}", responseWrapper.getErrors().get(0));
            }

            log.info("Email sent successfully");

        } catch (Exception e) {
            log.error("Failed to send email notification, {}", e.toString());
        }
    }
}
