package edu.gatech.erms.rest;


import edu.gatech.erms.model.*;
import edu.gatech.erms.service.ErmsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


@RestController
@EnableAutoConfiguration
@RequestMapping("/api")
public class ErmsRestService {

    /*private static final Logger logger = LoggerFactory.getLogger(MRSearchServiceImpl.class);*/

    @Autowired
    ErmsService service;

    @CrossOrigin
    @Scope("request")
    @RequestMapping(value = "/login", method = RequestMethod.POST, produces = { "application/json" })
    public ResponseEntity ermslogin (
            @RequestBody User user){


        Map<String,String> response = new HashMap<String, String>();

        String username = user.getUserName();
        String pwd = user.getPassword();

        boolean result = service.verifyUser(username,pwd);

        if(!result){
            response.put("success", String.valueOf(result));
            /*return new ResponseEntity(response, HttpStatus.OK);*/
        } else {
            response.put("success", String.valueOf(result));
            response.put("username", username);
        }
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @CrossOrigin
    @Scope("request")
    @RequestMapping(value = "/user/{username}", method = RequestMethod.GET, produces = { "application/json" })
    public ResponseEntity getUser(@PathVariable String username){
        User users = service.getUserDetails(username);
        return new ResponseEntity(users, HttpStatus.OK);
    }

    @CrossOrigin
    @Scope("request")
    @RequestMapping(value = "/esfs", method = RequestMethod.GET, produces = { "application/json" })
    public ResponseEntity getesfs () {

        HashMap<Integer,String> response = new HashMap<>();
        response = service.getEsfsDetails();
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @CrossOrigin
    @Scope("request")
    @RequestMapping(value = "/units", method = RequestMethod.GET, produces = { "application/json" })
    public ResponseEntity getCostTypes () {

        HashMap<String,String> response = new HashMap<>();
        response = service.getCostTypes();
        return new ResponseEntity(response, HttpStatus.OK);
    }


    @CrossOrigin
    @Scope("request")
    @RequestMapping(value = "/resource", method = RequestMethod.POST, produces = { "application/json" })
    public ResponseEntity addResource (
            @RequestBody Resource resource) {

        Map<String,String> response = new HashMap<String, String>();
        response.put("message",service.addResource(resource));
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @CrossOrigin
    @Scope("request")
    @RequestMapping(value = "/declarations", method = RequestMethod.GET, produces = { "application/json" })
    public ResponseEntity getDeclarations () {

        HashMap<String,String> response = new HashMap<>();
        response = service.getAllDeclarations();
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @CrossOrigin
    @Scope("request")
    @RequestMapping(value = "/incidentcount", method = RequestMethod.GET, produces = { "application/json" })
    public ResponseEntity getIncidentTypeCount (
            @RequestParam(value="abbr") String declaration_abbr) {

        HashMap<String,String> response = new HashMap<>();
        response.put("next_available_incident_id", declaration_abbr + "-" + String.valueOf(service.getIncidentTypeCount(declaration_abbr) + 1));
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @CrossOrigin
    @Scope("request")
    @RequestMapping(value = "/incident", method = RequestMethod.POST, produces = { "application/json" })
    public ResponseEntity addIncident (
            @RequestBody Incident incident) {

        Map<String,String> response = new HashMap<String, String>();
        response.put("message",service.addIncident(incident));
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @CrossOrigin
    @Scope("request")
    @RequestMapping(value = "/incident", method = RequestMethod.GET, produces = { "application/json" })
    public ResponseEntity getAllIncidents(
            @RequestParam(value = "username") String username) {

        HashMap<String,String> response = new HashMap<>();
        response = service.getAllIncident(username);

        return new ResponseEntity(response, HttpStatus.OK);
    }

    @CrossOrigin
    @Scope("request")
    @RequestMapping(value = "/resource", method = RequestMethod.GET, produces = { "application/json" })
    public ResponseEntity getAllResource(
            @RequestParam(value = "keyword") String keyword,
            @RequestParam(value = "esf") int esf,
            @RequestParam(value = "location") int distance,
            @RequestParam(value = "incidentid") String incidentId){

        ArrayList<SearchResource> response = new ArrayList<>();
        response = service.getAllResource(keyword,esf,distance,incidentId);

        return new ResponseEntity(response,HttpStatus.OK);
    }

    @CrossOrigin
    @Scope("request")
    @RequestMapping(value = "/report", method = RequestMethod.GET, produces = { "application/json" })
    public ResponseEntity getReport(
            @RequestParam(value = "username") String username){

        ArrayList<Report> response = new ArrayList<>();
        response = service.getReport(username);

        return new ResponseEntity(response,HttpStatus.OK);
    }

    @CrossOrigin
    @Scope("request")
    @RequestMapping(value = "/total/esfs", method = RequestMethod.GET, produces = { "application/json" })
    public ResponseEntity getTotalESFS(
            @RequestParam(value = "username") String username){

        int response = 0;
        response = service.getTotalESF(username);

        return new ResponseEntity(response,HttpStatus.OK);
    }

    @CrossOrigin
    @Scope("request")
    @RequestMapping(value = "/total/available", method = RequestMethod.GET, produces = { "application/json" })
    public ResponseEntity getTotalAvailable(
            @RequestParam(value = "username") String username){

        int response = 0;
        response = service.getTotalAvailable(username);

        return new ResponseEntity(response,HttpStatus.OK);
    }

    @CrossOrigin
    @Scope("request")
    @RequestMapping(value = "/requestaction", method = RequestMethod.POST, produces = { "application/json" })
    public ResponseEntity intiateAction (
            @RequestBody ResourceAction resourceaction) {

        Map<String,String> response = new HashMap<String, String>();

        boolean output = service.actionRequest((resourceaction));
        String message = "";
        System.out.println("value: "+output);
        if(output){
            message = "Resource requested successfully";
        }else{
            message = "Resource request denied. Please Try again with another Resource!!";
        }

        response.put("success", String.valueOf(output));
        response.put("message", message);

        return new ResponseEntity(response, HttpStatus.OK);
    }

    @CrossOrigin
    @Scope("request")
    @RequestMapping(value = "/requestresource", method = RequestMethod.POST, produces = { "application/json" })
    public ResponseEntity requestResourceByUser (
            @RequestBody ResourceAction resourceaction) {

        ArrayList<ResourceAction> outputData = new ArrayList<>();
        outputData = service.getResDetailsByUser(resourceaction);

        return new ResponseEntity(outputData, HttpStatus.OK);
    }

    @CrossOrigin
    @Scope("request")
    @RequestMapping(value = "/recievedrequest", method = RequestMethod.GET, produces = { "application/json" })
    public ResponseEntity receivedRequestByUser (
            @RequestParam(value = "username") String username) {

        ArrayList<ResourceAction> outputData = new ArrayList<>();
        outputData = service.getreceivedRequestByUser(username);

        return new ResponseEntity(outputData, HttpStatus.OK);
    }



}
