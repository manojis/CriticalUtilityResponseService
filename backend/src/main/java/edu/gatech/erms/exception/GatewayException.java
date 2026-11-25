package edu.gatech.erms.exception;

/**
 * @author Manoj.Mohanan Nair
 * @Date 7/17/18
 */
public class GatewayException extends ERMSServiceException {

    private static final long serialVersionUID = 1L;

    public static final int STATUS=502;
    public static final String MESSAGE = "Retrieving from Service failed";
    public static final String CODE = "internal.service.failure";

    /**
     * Construct a GatewayException with default CODE, MESSAGE and STATUS
     */
    public GatewayException()
    {
        super(CODE, MESSAGE, STATUS, null);
    }
    /**
     * Construct a GatewayException with default CODE, MESSAGE, STATUS and the exception passed
     */
    public GatewayException(Throwable cause)
    {
        super(CODE, MESSAGE, STATUS, cause);
    }
}
