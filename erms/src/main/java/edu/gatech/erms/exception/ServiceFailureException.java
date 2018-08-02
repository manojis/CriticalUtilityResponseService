package edu.gatech.erms.exception;

/**
 * @author Manoj.Mohanan Nair
 * @Date 7/17/18
 */
public class ServiceFailureException extends ERMSServiceException{

    private static final long serialVersionUID = 1L;

    public static final int STATUS=500;
    public static final String MESSAGE = "Service Failure Exception occurred";
    public static final String CODE = "service.failure";

    /**
     * Construct a ServiceFailureException with default CODE, MESSAGE and STATUS
     */
    public ServiceFailureException()
    {
        super(CODE, MESSAGE, STATUS, null);
    }
    /**
     * Construct a ServiceFailureException with default CODE, MESSAGE, STATUS and the exception passed
     */
    public ServiceFailureException(Throwable cause)
    {
        super(CODE, MESSAGE, STATUS, cause);
    }

    /**
     * Construct a ServiceFailureException with default CODE, MESSAGE, STATUS and the exception passed
     */
    public ServiceFailureException(String message)
    {
        super(CODE, message, STATUS, null);
    }

    /**
     * Construct a ServiceFailureException with default CODE, MESSAGE, STATUS and the exception passed
     */
    public ServiceFailureException(String message,Throwable cause)
    {
        super(CODE, message, STATUS, cause);
    }
}
