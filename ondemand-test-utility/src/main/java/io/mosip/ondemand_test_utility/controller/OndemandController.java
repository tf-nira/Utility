package io.mosip.ondemand_test_utility.controller;

import io.mosip.ondemand_test_utility.service.OndemandService;
import org.apache.tomcat.util.codec.binary.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
public class OndemandController {

    @Autowired
    private OndemandService ondemandService;

    @GetMapping("/getrids")
    public ResponseEntity<String> getRids() {
        CompletableFuture.runAsync(() -> {
            try {
                ondemandService.getRidsFromNIN();
            } catch (Exception e) {
                System.out.println("Error in async processing:: "+ e);
            }
        });
        return ResponseEntity.ok("Processing started. Check server logs for progress.");
    }

}
