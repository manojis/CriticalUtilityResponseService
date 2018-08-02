package edu.gatech.erms.exception;

/**
 * @author Manoj.Mohanan Nair
 * @Date 7/17/18
 */
public class ERMSServiceException extends RuntimeException {

    private String code;
    private int status;
    public String getCode() {
        return code;
    }
    public void setCode(String code) {
        this.code = code;
    }
    public int getStatus() {
        return status;
    }
    public void setStatus(int status) {
        this.status = status;
    }

    public ERMSServiceException(Throwable e){
        super(e);
    }
    /**
     * Construct a ERMSServiceException
     *
     * @param code
     *          a dotted notation error code (e.g. no data.not.found)
     * @param message
     *          a description message of the error
     * @param status
     *          a hint as to the type of HTTP status to generate
     */
    public ERMSServiceException(String code, String message, int status, Throwable cause)
    {
        super(message, cause);
        this.code = code;
        this.status = status;
    }
}
