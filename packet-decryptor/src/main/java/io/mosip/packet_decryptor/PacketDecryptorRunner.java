package io.mosip.packet_decryptor;

import io.mosip.packet_decryptor.decryptor.Decryptor;
import io.mosip.packet_decryptor.exception.ApisResourceAccessException;
import io.mosip.packet_decryptor.exception.PacketDecryptionFailureException;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Packet Decryptor Runner - Decrypts MOSIP packets
 * 
 * Architecture:
 * 1. Read encrypted packet file from ZIP
 * 2. Convert InputStream to byte[] (like PacketKeeper does)
 * 3. Decrypt using Decryptor service (calls Cryptomanager)
 * 4. Decrypt returns InputStream of decrypted ZIP
 * 5. Extract and save all entries from decrypted ZIP
 */
@Component
public class PacketDecryptorRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(PacketDecryptorRunner.class);
    private static final String PACKET_ENTRY_NAME = "packet.zip";

    @Autowired
    private Decryptor decryptor;

    @Value("${packet.input.zip.path}")
    private String zipFilePath;

    @Value("${packet.output.path}")
    private String outputFilePath;

    @Value("${packet.id}")
    private String id;

    @Value("${packet.refId}")
    private String refId;

    @Override
    public void run(String... args) {
        logger.info("========================================");
        logger.info("Starting MOSIP Packet Decryption");
        logger.info("========================================");
        logger.info("Input ZIP: {}", zipFilePath);
        logger.info("Output Path: {}", outputFilePath);
        logger.info("Packet ID: {}", id);
        logger.info("Reference ID: {}", refId);

        if (zipFilePath == null || zipFilePath.isEmpty()) {
            logger.error("ERROR: Packet input path not configured in application.properties");
            return;
        }

        Path zipPath = Paths.get(zipFilePath);

        // Validate input file
        if (!Files.exists(zipPath)) {
            logger.error("ERROR: Input ZIP file not found: {}", zipFilePath);
            logger.error("Current working directory: {}", System.getProperty("user.dir"));
            return;
        }

        if (!Files.isRegularFile(zipPath)) {
            logger.error("ERROR: Path is not a file: {}", zipFilePath);
            return;
        }

        try {
            long fileSize = Files.size(zipPath);
            logger.info("Input file size: {} bytes", fileSize);
            decryptPacket(zipPath);
        } catch (Exception e) {
            logger.error("ERROR: Decryption failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Decrypt packet following PacketKeeper pattern:
     * 1. Find "packet" entry in wrapper ZIP
     * 2. Read as InputStream
     * 3. Convert to byte array (encrypted packet bytes)
     * 4. Decrypt using Decryptor service
     * 5. Process decrypted ZIP entries
     * 
     * @param zipPath Path to wrapper ZIP containing encrypted packet
     * @throws Exception if error occurs
     */
    private void decryptPacket(Path zipPath) throws Exception {
        // Step 1: Open wrapper ZIP and find encrypted packet entry
        logger.info("Step 1: Reading encrypted packet from wrapper ZIP...");

        try (FileInputStream fis = new FileInputStream(zipPath.toFile());
             ZipInputStream zis = new ZipInputStream(fis)) {

            ZipEntry entry;
            boolean packetFound = false;

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                logger.debug("  Found ZIP entry: {}", entryName);

                if (PACKET_ENTRY_NAME.equals(entryName)) {
                    logger.info("  ✓ Found encrypted packet entry");
                    packetFound = true;

                    // Step 2: Read encrypted packet InputStream into byte array
                    logger.info("Step 2: Converting InputStream to byte array (encrypted packet)...");
                    byte[] encryptedSubPacket = IOUtils.toByteArray(zis);
                    logger.info("  ✓ Read {} bytes from encrypted packet", encryptedSubPacket.length);

                    // Step 3: Decrypt
                    logger.info("Step 3: Decrypting packet...");
                    logger.info("  Calling decrypt() with ID: {} and RefId: {}", id, refId);

                    try {
                        ByteArrayInputStream bais = new ByteArrayInputStream(encryptedSubPacket);
                        InputStream decryptedStream = decryptor.decrypt(id, refId, bais);
                        logger.info("  ✓ Decryption successful");

                        // Step 4: Process decrypted packet
                        logger.info("Step 4: Processing decrypted packet...");
                        processDecryptedPacketZip(decryptedStream);

                    } catch (ApisResourceAccessException e) {
                        logger.error("  ✗ API Error: {}", e.getMessage(), e);
                        throw new Exception("Decryption failed - API error: " + e.getMessage(), e);
                    } catch (PacketDecryptionFailureException e) {
                        logger.error("  ✗ Decryption Error: {}", e.getMessage(), e);
                        throw new Exception("Decryption failed: " + e.getMessage(), e);
                    }

                    break;
                }
                zis.closeEntry();
            }

            if (!packetFound) {
                logger.error("✗ ERROR: 'packet' entry not found in wrapper ZIP");
                logger.error("  Available entries:");
                // Re-open and list entries
                try (FileInputStream fis2 = new FileInputStream(zipPath.toFile());
                     ZipInputStream zis2 = new ZipInputStream(fis2)) {
                    ZipEntry debugEntry;
                    while ((debugEntry = zis2.getNextEntry()) != null) {
                        logger.error("    - {}", debugEntry.getName());
                    }
                }
                throw new Exception("Packet entry not found in ZIP");
            }
        }

        logger.info("========================================");
        logger.info("Decryption completed successfully!");
        logger.info("========================================");
    }

    /**
     * Process the decrypted packet which is a ZIP file containing multiple entries
     * 
     * Expected entries in the decrypted ZIP:
     * - ID.json - Identity data (JSON)
     * - EVIDENCE.json - Evidence type packet (optional)
     * - BIOMETRIC_DATA.json - Metadata (JSON)
     * - audit - Audit logs (JSON array)
     * - {document_filename}.pdf - Documents (PDF)
     * - {document_filename}.png - Documents (PNG)
     * - {biometric_filename}.xml - Biometric data (CBEFF XML)
     * - {hash_sequence_filename} - Hash sequence for integrity
     * 
     * @param decryptedStream InputStream containing the decrypted ZIP file
     * @throws IOException if an I/O error occurs
     */
    private void processDecryptedPacketZip(InputStream decryptedStream) throws IOException {
        logger.info("Step 4a: Buffering decrypted stream...");
        
        // Read entire stream into byte array for ZIP processing
        ByteArrayOutputStream decryptedBuffer = new ByteArrayOutputStream();
        IOUtils.copy(decryptedStream, decryptedBuffer);
        byte[] decryptedBytes = decryptedBuffer.toByteArray();
        logger.info("  ✓ Decrypted packet size: {} bytes", decryptedBytes.length);
        
        // Create output directory
        Path outputDir = Paths.get(outputFilePath);
        Files.createDirectories(outputDir);
        Path extractionDir = outputDir.resolve(id + "_extracted");
        Files.createDirectories(extractionDir);
        logger.info("Step 4b: Created output directory: {}", extractionDir);
        
        // Process decrypted ZIP file
        logger.info("Step 4c: Extracting decrypted packet entries...");
        
        ByteArrayInputStream bais = new ByteArrayInputStream(decryptedBytes);
        ZipInputStream decryptedZis = new ZipInputStream(bais);
        
        try {
            ZipEntry zipEntry;
            int fileCount = 0;
            int dirCount = 0;
            
            while ((zipEntry = decryptedZis.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) {
                    dirCount++;
                    logger.debug("  Skipping directory: {}", zipEntry.getName());
                    continue;
                }
                
                fileCount++;
                
                // Create output path for this entry
                Path entryPath = extractionDir.resolve(zipEntry.getName());
                Files.createDirectories(entryPath.getParent());
                
                // Extract entry
                try (FileOutputStream fos = new FileOutputStream(entryPath.toFile())) {
                    IOUtils.copy(decryptedZis, fos);
                }
                
                // Log extracted entry
                String entryType = getEntryType(zipEntry.getName());
                logger.info("  ✓ Extracted {}: {} ({} bytes)", 
                    entryType, zipEntry.getName(), zipEntry.getSize());
            }
            
            logger.info("Step 4d: Extraction complete");
            logger.info("  ✓ Extracted {} files, {} directories", fileCount, dirCount);
            logger.info("  ✓ Output location: {}", extractionDir);
            
        } finally {
            decryptedZis.close();
        }
    }
    
    /**
     * Determine entry type for logging
     */
    private String getEntryType(String entryName) {
        if (entryName.contains(".json")) {
            return "JSON";
        } else if (entryName.endsWith(".pdf") || entryName.endsWith(".png") || entryName.endsWith(".jpg")) {
            return "Document";
        } else if (entryName.endsWith(".xml")) {
            return "Biometric";
        } else {
            return "File";
        }
    }
}
