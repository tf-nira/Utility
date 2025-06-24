package io.mosip.packet_utility.controller;

import io.mosip.packet_utility.service.PacketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
public class PacketController {

    @Autowired
    private PacketService packetService;

    @GetMapping("/getnin")
    public ResponseEntity<String>  getNIN() throws Exception {
        CompletableFuture.runAsync(() -> {
            try {
                packetService.getPacketNIN();

            } catch (Exception e) {
                System.err.println("Error in async batch NIN processing: " +e.getMessage());
                e.printStackTrace();
            }
        });
        return ResponseEntity.ok("NIN extraction started with batch processing.");
    }

    @GetMapping("/ninstatus")
    public ResponseEntity<String> checkNINStatus() throws Exception {
        CompletableFuture.runAsync(() -> {
            try {
                packetService.getNINStatus();
            } catch (Exception e) {
                System.out.println("Error in async processing:: "+ e);
            }
        });
        return ResponseEntity.ok("Processing started. Check server logs for progress.");

    }

    @GetMapping("/updateDetails")
    public ResponseEntity<String> updateDetails() throws Exception {
        CompletableFuture.runAsync(() -> {
            try {
                packetService.updateIdentity();
            } catch (Exception e) {
                System.out.println("Error in async processing:: "+ e);
            }
        });
        return ResponseEntity.ok("Processing started. Check server logs for progress.");
    }
}
