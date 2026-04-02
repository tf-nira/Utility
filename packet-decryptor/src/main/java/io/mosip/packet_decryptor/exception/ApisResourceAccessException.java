package io.mosip.packet_decryptor.exception;

import io.mosip.kernel.core.exception.BaseCheckedException;
import io.mosip.packet_decryptor.exception.util.PlatformErrorMessages;

public class ApisResourceAccessException extends BaseCheckedException {
    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = 1L;

    /**
     * Instantiates a new apis resource access exception.
     */
    public ApisResourceAccessException() {
        super();
    }

    /**
     * Instantiates a new apis resource access exception.
     *
     * @param message the message
     */
    public ApisResourceAccessException(String message) {
        super(PlatformErrorMessages.RPR_RCT_UNKNOWN_RESOURCE_EXCEPTION.getCode(), message);
    }

    /**
     * Instantiates a new apis resource access exception.
     *
     * @param message the message
     * @param cause the cause
     */
    public ApisResourceAccessException(String message, Throwable cause) {
        super(PlatformErrorMessages.RPR_RCT_UNKNOWN_RESOURCE_EXCEPTION.getCode(), message, cause);
    }
}
