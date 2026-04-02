package io.mosip.packet_decryptor.exception;

public class PacketDecryptionFailureException extends RuntimeException {
    public PacketDecryptionFailureException(String message) {
        super(message);
    }
}
