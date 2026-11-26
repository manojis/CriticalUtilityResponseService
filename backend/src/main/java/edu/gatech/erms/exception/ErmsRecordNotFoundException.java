package edu.gatech.erms.exception;

/**
 * @author Manoj.Mohanan Nair
 * @Date 7/17/18
 */
public class ErmsRecordNotFoundException extends ERMSServiceException {

    private static final long serialVersionUID = 1L;

    public static final int STATUS=204;
    public static final String MESSAGE = "No records found for this query";
    public static final String CODE = "no.record.found";


    /**
     * Construct a MedicalRecordNotFoundException with default CODE, MESSAGE and STATUS
     */
    public ErmsRecordNotFoundException()
    {
        super(CODE, MESSAGE, STATUS, null);
    }
    public ErmsRecordNotFoundException(String message)
    {
        super(CODE, message, STATUS, null);
    }
    /**
     * Construct a MedicalRecordNotFoundException with default CODE, MESSAGE, STATUS and the exception passed
     */
    public ErmsRecordNotFoundException(Throwable cause)
    {
        super(CODE, MESSAGE, STATUS, cause);
    }

}
