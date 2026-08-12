package io.mosip.packet_utility.controller;

import io.mosip.packet_utility.service.PacketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/addOrUpdateTag")
    public ResponseEntity<String> addOrUpdateMergedTag() throws Exception {
        CompletableFuture.runAsync(() -> {
            try {
                packetService.addOrUpdateMergedTag();
            } catch (Exception e) {
                System.out.println("Error in async processing:: " + e);
            }
        });
        return ResponseEntity.ok("Packet tag update started. Check server logs for progress.");
    }

    @GetMapping("/residenceStatus")
    public ResponseEntity<String> checkResidenceStatus() throws Exception {
        CompletableFuture.runAsync(() -> {
            try {
                packetService.getResidenceStatus();
            } catch (Exception e) {
                System.out.println("Error in async processing:: "+ e);
            }
        });
        return ResponseEntity.ok("Processing started. Check server logs for progress.");

    }

    @GetMapping("/getAge")
    public ResponseEntity<String> getAge() throws Exception {
        CompletableFuture.runAsync(() -> {
            try {
                packetService.getAge();
            } catch (Exception e) {
                System.out.println("Error in async processing:: "+ e);
            }
        });
        return ResponseEntity.ok("Processing started. Check server logs for progress.");
    }
    @GetMapping("/getPrn")
    public ResponseEntity<String> getPrn() throws Exception {
        CompletableFuture.runAsync(() -> {
            try {
                packetService.getPrn();
            } catch (Exception e) {
                System.out.println("Error in async processing:: "+ e);
            }
        });
        return ResponseEntity.ok("Processing started. Check server logs for progress.");
    }

    @GetMapping("/extractFaceBiometrics")
    public ResponseEntity<String> extractFaceBiometrics() throws Exception {
        CompletableFuture.runAsync(() -> {
            try {
                packetService.extractFaceBiometrics();
            } catch (Exception e) {
                System.out.println("Error in async processing:: " + e);
            }
        });
        return ResponseEntity.ok("Face biometric extraction started. Check server logs for progress.");
    }

    @GetMapping("/searchApplicantFields")
    public ResponseEntity<String> searchApplicantFields(
            @RequestParam(defaultValue = "reg_process.csv") String inputFile) {
        CompletableFuture.runAsync(() -> {
            try {
                packetService.searchApplicantFields(inputFile);
            } catch (Exception e) {
                System.err.println("Error while searching applicant fields: " + e.getMessage());
                e.printStackTrace();
            }
        });
        return ResponseEntity.ok("Applicant field search started. Check D:\\output\\applicant-fields.csv for the result.");
    }

    @GetMapping("/extractDocuments")
    public ResponseEntity<String> extractDocuments(
            @RequestParam(defaultValue = "reg_process.csv") String inputFile) {
        CompletableFuture.runAsync(() -> {
            try {
                packetService.extractDocuments(inputFile);
            } catch (Exception e) {
                System.err.println("Error while extracting documents: " + e.getMessage());
                e.printStackTrace();
            }
        });
        return ResponseEntity.ok("Document extraction started. Check D:\\output\\documents folder for the result.");
    }

    @GetMapping("/facilityDetails")
    public ResponseEntity<String> getFacilityDetails() throws Exception {
        CompletableFuture.runAsync(() -> {
            try {
                packetService.getFacilityDetails();
            } catch (Exception e) {
                System.out.println("Error in async processing:: " + e);
            }
        });
        return ResponseEntity.ok("Processing started. Check server logs for progress.");
    }
}
