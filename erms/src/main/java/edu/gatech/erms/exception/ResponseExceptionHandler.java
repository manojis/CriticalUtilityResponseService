package edu.gatech.erms.exception;

/*import org.slf4j.Logger;
import org.slf4j.LoggerFactory;*/
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author Manoj.Mohanan Nair
 * @Date 7/17/18
 */
@ControllerAdvice
public class ResponseExceptionHandler {

    /*private static final Logger log = LoggerFactory.getLogger(ResponseExceptionHandler.class);*/

    ErrorResponse errorResponse = null;

    @ExceptionHandler({ NumberFormatException.class ,TypeMismatchException.class})
    @ResponseBody
    public ResponseEntity handleInvalidInput(Throwable t) {

        errorResponse = new ErrorResponse();
        errorResponse.setCode("invalid.input");
        errorResponse.setMessage("Input is not a valid data");
        return errorResponse(errorResponse,t, HttpStatus.OK);
    }

    @ExceptionHandler({ BadRequestException.class })
    @ResponseBody
    public ResponseEntity handleBadRequest(Throwable t) {

        errorResponse = new ErrorResponse();
        errorResponse.setCode("invalid.input");
        errorResponse.setMessage(t.getMessage());

        return errorResponse(errorResponse,t, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({ ErmsRecordNotFoundException.class })
    @ResponseBody
    public ResponseEntity handleDataNotFound(Exception ex) {

        errorResponse = new ErrorResponse();
        errorResponse.setCode(ErmsRecordNotFoundException.CODE);
        errorResponse.setMessage(ErmsRecordNotFoundException.MESSAGE);
        return errorResponse(errorResponse,ex, HttpStatus.NO_CONTENT);
    }

    @ExceptionHandler({ GatewayException.class })
    @ResponseBody
    public ResponseEntity handleGatewayFailure(Exception ex) {
        errorResponse = new ErrorResponse();
        errorResponse.setCode(GatewayException.CODE);
        errorResponse.setMessage(GatewayException.MESSAGE);
        return errorResponse(errorResponse,ex, HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler({ ServiceFailureException.class })
    @ResponseBody
    public ResponseEntity handleServiceFailure(Exception ex) {
        errorResponse = new ErrorResponse();
        errorResponse.setCode(ServiceFailureException.CODE);
        errorResponse.setMessage(ServiceFailureException.MESSAGE);
        return errorResponse(errorResponse,ex, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler({ ERMSServiceException.class })
    @ResponseBody
    public ResponseEntity handleCustomExceptions(Exception ex) {
        errorResponse = new ErrorResponse();
        errorResponse.setCode("service.failure");
        errorResponse.setMessage(ex.getMessage());
        return errorResponse(errorResponse,ex, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Catch all for any other exceptions...
     */
    @ExceptionHandler({ Exception.class })
    @ResponseBody
    public ResponseEntity<?> handleAnyException(Exception e) {
        errorResponse = new ErrorResponse();
        errorResponse.setCode("service.failure");
        errorResponse.setMessage(e.getMessage());
        return errorResponse(errorResponse,e, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    protected ResponseEntity<ErrorResponse> errorResponse(ErrorResponse errorResp,
                                                          Throwable throwable,HttpStatus status) {
        if (null != errorResp) {
            /*log.error("error caught: " + errorResp.getMessage(), throwable);*/

            return response(errorResp, status);
        } else {
            /*log.error("unknown error caught in RESTController, {} "+ status);*/
            return response(null, status);
        }
    }

    protected <T> ResponseEntity<T> response(T body, HttpStatus status) {
        /*log.debug("Responding with a status of {} "+ status);*/
        return new ResponseEntity<>(body, new HttpHeaders(), status);
    }

}
