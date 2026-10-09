package io.mosip.packet_utility.controller;

import io.mosip.packet_utility.dto.NINResultDTO;
import io.mosip.packet_utility.dto.RegIdIdRepoStatusDTO;
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

    @GetMapping("/getNinByRegId")
    public ResponseEntity<NINResultDTO> getNinByRegId(@RequestParam String regId) {
        NINResultDTO result = packetService.getNIN(regId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/checkIdRepoStatus")
    public ResponseEntity<String> checkIdRepoStatus() throws Exception {
        CompletableFuture.runAsync(() -> {
            try {
                packetService.checkIdRepoByRegIdStatus();
            } catch (Exception e) {
                System.err.println("Error in async ID-Repo presence check processing:: " + e);
            }
        });
        return ResponseEntity.ok("ID-Repo presence check started. Check D:\\output\\idrepo-check.csv for the result.");
    }

    @GetMapping("/checkIdRepoByRegId")
    public ResponseEntity<RegIdIdRepoStatusDTO> checkIdRepoByRegId(@RequestParam String regId) {
        RegIdIdRepoStatusDTO result = packetService.checkRegIdInIdRepo(regId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/checkNameInfo")
    public ResponseEntity<String> checkNameInfo() {
        CompletableFuture.runAsync(() -> {
            try {
                packetService.checkNameInfo();
            } catch (Exception e) {
                System.err.println("Error while checking name info: " + e.getMessage());
                e.printStackTrace();
            }
        });
        return ResponseEntity.ok("Name info check started. Check D:\\output\\name-info.csv for the result.");
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

    @GetMapping("/updateResidence")
    public ResponseEntity<String> updateResidence() throws Exception {
        CompletableFuture.runAsync(() -> {
            try {
                packetService.updateResidence();
            } catch (Exception e) {
                System.out.println("Error in async processing:: "+ e);
            }
        });
        return ResponseEntity.ok("Residence update started. Check server logs for progress.");
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

    @GetMapping("/searchResidenceFields")
    public ResponseEntity<String> searchResidenceFields() {
        CompletableFuture.runAsync(() -> {
            try {
                packetService.searchResidenceFields("reg_process.csv");
            } catch (Exception e) {
                System.err.println("Error while searching residence fields: " + e.getMessage());
                e.printStackTrace();
            }
        });
        return ResponseEntity.ok("Residence field search started. Check D:\\output\\residence-fields.csv for the result.");
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

    @GetMapping("/residenceDetails")
    public ResponseEntity<String> getResidenceDetails() throws Exception {
        CompletableFuture.runAsync(() -> {
            try {
                packetService.getApplicantResidence();
            } catch (Exception e) {
                System.out.println("Error in async processing:: " + e);
            }
        });
        return ResponseEntity.ok("Residence details fetch started. Check D:\\output\\applicant_residence.csv for the result.");
    }

    @GetMapping("/residenceCorrection")
    public ResponseEntity<String> getResidenceCorrection() throws Exception {
        CompletableFuture.runAsync(() -> {
            try {
                packetService.getResidenceCorrection();
            } catch (Exception e) {
                System.out.println("Error in async processing:: " + e);
            }
        });
        return ResponseEntity.ok("Residence correction fetch started. Check D:\\output\\residence_correction.csv for the result.");
    }

    @GetMapping("/enrolmentDetails")
    public ResponseEntity<String> getEnrolmentDetails() throws Exception {
        CompletableFuture.runAsync(() -> {
            try {
                packetService.getApplicantEnrolment();
            } catch (Exception e) {
                System.out.println("Error in async processing:: " + e);
            }
        });
        return ResponseEntity.ok("Enrolment details fetch started. Check D:\\output\\applicant_enrolment.csv for the result.");
    }

    @GetMapping("/enrolmentStatus")
    public ResponseEntity<String> checkEnrolmentStatus() throws Exception {
        CompletableFuture.runAsync(() -> {
            try {
                packetService.getEnrolmentStatus();
            } catch (Exception e) {
                System.out.println("Error in async processing:: "+ e);
            }
        });
        return ResponseEntity.ok("Processing started. Check server logs for progress.");

    }
}
