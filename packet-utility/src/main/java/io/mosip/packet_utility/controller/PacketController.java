package io.mosip.packet_utility.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.mosip.packet_utility.dto.ObjectStoreDto;
import io.mosip.packet_utility.service.ObjectStore;
import io.mosip.packet_utility.service.PacketService;

import java.util.concurrent.CompletableFuture;

@RestController
public class PacketController {

    @Autowired
    private PacketService packetService;
    
    @Autowired
    private ObjectStore objectStore;

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
    
    @GetMapping("/getcentreandopid")
    public ResponseEntity<String>  getPacketCentreAndOperator() throws Exception {
        CompletableFuture.runAsync(() -> {
            try {
                packetService.getPacketCentreAndOperator();

            } catch (Exception e) {
                System.err.println("Error in async batch NIN processing: " +e.getMessage());
                e.printStackTrace();
            }
        });
        return ResponseEntity.ok("centre and operational data extration started.");
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
    
    @GetMapping("/compareData")
    public ResponseEntity<String> compareData() throws Exception {
        CompletableFuture.runAsync(() -> {
            try {
                packetService.comparePacketsFromPacketMgrAndIdRepo();
            } catch (Exception e) {
                System.out.println("Error in async processing:: "+ e);
            }
        });
        return ResponseEntity.ok("Processing started. Check server logs for progress.");
    }


	@PostMapping(path = "/write/{policyId}/{subscriberId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> createDataShare(@RequestBody MultipartFile file,
			@PathVariable("policyId") String policyId, @PathVariable("subscriberId") String subscriberId) throws Exception {
		

		String key = objectStore.writeData(policyId, subscriberId, file);
		return ResponseEntity.status(HttpStatus.OK)
				.body(key);

	}

	/**
	 * Gets the file.
	 *
	 * @param randomShareKey the random share key
	 * @return the file
	 * @throws Exception 
	 */

	@GetMapping(path = "/read/{policyId}/{subscriberId}/{randomShareKey}", consumes = MediaType.ALL_VALUE)
	@ResponseBody
	public ResponseEntity<byte[]> getFile(@PathVariable("policyId") String policyId,
			@PathVariable("subscriberId") String subscriberId, @PathVariable("randomShareKey") String randomShareKey) throws Exception {

		ObjectStoreDto dto=objectStore.readData(policyId, subscriberId, randomShareKey);
		return new ResponseEntity<byte[]>(dto.getFileBytes(),HttpStatus.OK);

	}

}
