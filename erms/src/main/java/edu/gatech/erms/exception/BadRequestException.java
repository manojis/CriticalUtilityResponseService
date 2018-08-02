package edu.gatech.erms.exception;

/**
 * @author Manoj.Mohanan Nair
 * @Date 7/17/18
 */
public class BadRequestException extends ERMSServiceException {

    private static final long serialVersionUID = 1L;

    public static final int STATUS=400;
    public static final String MESSAGE = "Missing Required Parameters";
    public static final String CODE = "invalid.input";

    /**
     * Construct a BadRequestException with default CODE, MESSAGE and STATUS
     */
    public BadRequestException()
    {
        super(CODE, MESSAGE, STATUS, null);
    }
    /**
     * Construct a BadRequestException with default CODE, MESSAGE, STATUS and the exception passed
     */
    public BadRequestException(String message)
    {
        super(CODE, message, STATUS, null);
    }
    /**
     * Construct a BadRequestException with default CODE, MESSAGE, STATUS and the exception passed
     */
    public BadRequestException(Throwable cause)
    {
        super(CODE, MESSAGE, STATUS, cause);
    }
}
