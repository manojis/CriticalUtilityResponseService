import React, {Component} from 'react';

import axios from 'axios';

import { withStyles } from '@material-ui/core/styles';
import Table from '@material-ui/core/Table';
import TableBody from '@material-ui/core/TableBody';
import TableCell from '@material-ui/core/TableCell';
import TableHead from '@material-ui/core/TableHead';
import TableRow from '@material-ui/core/TableRow';
import Paper from '@material-ui/core/Paper';
import Button from '@material-ui/core/Button';
import Divider from '@material-ui/core/Divider';
import { Link } from 'react-router-dom';
import Grid from '@material-ui/core/Grid';


const gridStyle = {
  paddingTop: "2rem"
}

const styles = theme => ({
  root: {
    width: '100%',
    marginTop: theme.spacing.unit * 3,
    overflowX: 'auto',
  },
  table: {
    minWidth: 700,
  },
});

class ResourceStatus extends Component {
  constructor(props){
    super(props);

    this.state = {
      username: sessionStorage.getItem('username'),
      resources_in_use: [],
      resources_requested_by_me: [],
      resources_received_by_me: []
    }

    this.fetchResourceReceivedByMe = this.fetchResourceReceivedByMe.bind(this);
    this.fetchResourcesInUse = this.fetchResourcesInUse.bind(this);
    this.fetchResourcesRequestedByMe = this.fetchResourcesRequestedByMe.bind(this);
    this.returnAction = this.returnAction.bind(this);
    this.cancelAction = this.cancelAction.bind(this);
    this.rejectAction = this.rejectAction.bind(this);
    this.deployAction = this.deployAction.bind(this);
  }

  componentDidMount() {
    setTimeout(() => {
      this.fetchResourcesInUse();
    }, 400); 
    setTimeout(() =>{
      this.fetchResourcesRequestedByMe();
    }, 800);
    setTimeout(() => {
      this.fetchResourceReceivedByMe();
    }, 1200);
  }

  fetchResourceReceivedByMe() {
    var apiBaseUrl = "http://localhost:8090/api//recievedrequest?username=" + this.state.username;

    axios.get(apiBaseUrl)
    .then( (res) => {
      console.log(res)
      this.setState({
        resources_received_by_me : res.data
      })
    }).catch( (error) => {
      console.log(error)
    })
  }

  fetchResourcesInUse() {
    var apiBaseUrl = "http://localhost:8090/api/requestresource";

    var payload = {
      username: this.state.username
    }
    axios.post(apiBaseUrl, payload)
    .then( (res) => {
      console.log(res)
      this.setState({
        resources_in_use : res.data
      })
    }).catch( (error) => {
      console.log(error)
    })
  }

  fetchResourcesRequestedByMe() {
    var apiBaseUrl = "http://localhost:8090/api/requestresource";

    var payload = {
      requestedBy_username: this.state.username
    }
    axios.post(apiBaseUrl, payload)
    .then( (res) => {
      console.log(res)
      this.setState({
        resources_requested_by_me : res.data
      })
    }).catch( (error) => {
      console.log(error)
    })
  }

  returnAction(resource_id, incident_id){

    var apiBaseUrl = "http://localhost:8090/api/requestaction";

    var payload = {
      resource_id: resource_id,
      incident_id: incident_id,
      action: "return"
    }
    axios.post(apiBaseUrl, payload)
    .then( (res) => {
      alert('Resource has been returned successfully')
    }).catch( (error) => {
      console.log(error)
    })
  }

  cancelAction(resource_id, incident_id) {
    var apiBaseUrl = "http://localhost:8090/api/requestaction";

    var payload = {
      resource_id: resource_id,
      incident_id: incident_id,
      action: "cancel"
    }
    axios.post(apiBaseUrl, payload)
    .then( (res) => {
      alert('Resource has been canceled')
    }).catch( (error) => {
      console.log(error)
    })
  }

  rejectAction(resource_id, incident_id) {
    var apiBaseUrl = "http://localhost:8090/api/requestaction";

    var payload = {
      resource_id: resource_id,
      incident_id: incident_id,
      action: "reject",
      actionStatus: "reject"
    }
    axios.post(apiBaseUrl, payload)
    .then( (res) => {
      alert('Resource has been rejected!')
    }).catch( (error) => {
      console.log(error)
    })
  }

  deployAction(resource_id, incident_id) {
    var apiBaseUrl = "http://localhost:8090/api/requestaction";

    var payload = {
      resource_id: resource_id,
      incident_id: incident_id,
      action: "deploy"
    }
    axios.post(apiBaseUrl, payload)
    .then( (res) => {
      alert('Resource has been deployed')
    }).catch( (error) => {
      console.log(error)
    })
  }
  

  render(){
    const { classes } = this.props;
    return (
      <div style={{width:"82%"}}>
        <Paper style={{marginTop: "2rem", border: "3px solid green", padding: "10px", boxShadow: "0 19px 38px rgba(0,0,0,0.30), 0 15px 12px rgba(0,0,0,0.22)"}}>
  
          <h1 style={{textAlign:"center"}}>Resource Status</h1>
          <Divider />
  
          <br/><br/>
          <h2>Resources in use</h2>
            <Table className={classes.table}>
              <TableHead>
                <TableRow style={{background: "#4DCCBD"}}>
                  <TableCell>ID</TableCell>
                  <TableCell>Resource Name</TableCell>
                  <TableCell>Incident</TableCell>
                  <TableCell>Owner</TableCell>
                  <TableCell>Start Date</TableCell>
                  <TableCell>Return By</TableCell>
                  <TableCell className = "float-right">Action</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {this.state.resources_in_use.map(n => {
                  return (
                    <TableRow key={n.resource_id}>
                      <TableCell component="th" scope="row">
                        {n.resource_id}
                      </TableCell>
                      <TableCell>{n.resource_name}</TableCell>
                      <TableCell>{n.description}</TableCell>
                      <TableCell>{n.username}</TableCell>
                      <TableCell>{n.deploy_date}</TableCell>
                      <TableCell>{n.estimated_return_date}</TableCell>
                      <TableCell className = "float-right">
                        <Button variant="contained" size="large" onClick={() => this.returnAction(n.resource_id, n.incident_id)}>
                        Return
                        </Button>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
  
            <br/><br/>
  
            <h2> Resources requested by me </h2>
            <Table className={classes.table}>
              <TableHead>
                <TableRow style={{background: "#4DCCBD"}}>
                  <TableCell>ID</TableCell>
                  <TableCell>Resource Name</TableCell>
                  <TableCell>Incident</TableCell>
                  <TableCell>Owner</TableCell>
                  <TableCell>Return By</TableCell>
                  <TableCell className = "float-right">Action</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {this.state.resources_requested_by_me.map(n => {
                  return (
                    <TableRow key={n.resource_id}>
                      <TableCell component="th" scope="row">
                        {n.resource_id}
                      </TableCell>
                      <TableCell>{n.resource_name}</TableCell>
                      <TableCell>{n.description}</TableCell>
                      <TableCell>{n.requestedBy_username}</TableCell>
                      <TableCell>{n.estimated_return_date}</TableCell>
                      <TableCell className = "float-right">
                        <Button variant="contained" size="large" onClick={() => this.cancelAction(n.resource_id, n.incident_id)}>
                          Cancel
                        </Button>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
  
            <br/><br/>
  
              <h2> Resource requests received by me </h2>
              <Table className={classes.table}>
                <TableHead>
                  <TableRow style={{background: "#4DCCBD"}}>
                    <TableCell>ID</TableCell>
                    <TableCell>Resource Name</TableCell>
                    <TableCell>Incident</TableCell>
                    <TableCell>Owner</TableCell>
                    <TableCell>Return By</TableCell>
                    <TableCell className = "float-right">Action</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {this.state.resources_received_by_me.map(n => {
                    return (
                      <TableRow key={n.resource_id}>
                        <TableCell component="th" scope="row">
                          {n.resource_id}
                        </TableCell>
                        <TableCell>{n.resource_name}</TableCell>
                        <TableCell>{n.description}</TableCell>
                        <TableCell>{n.requestedBy_username}</TableCell>
                        <TableCell>{n.estimated_return_date}</TableCell>
                        <TableCell className = "float-right">
                        <div>{
                          (n.is_available===true) ? (
                            <div>
                              <Button variant="contained" size="large" onClick={() => this.deployAction(n.resource_id, n.incident_id)}>
                                Deploy
                              </Button>                         
                              <Button variant="contained" size="large" onClick={() => this.rejectAction(n.resource_id, n.incident_id)}>
                                Reject
                              </Button>
                            </div>
                          ) : <div> 
                            <Button variant="contained" size="large" onClick={() => this.rejectAction(n.resource_id, n.incident_id)}>
                                Reject
                              </Button>
                          </div>}
                        </div>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
              <Grid style={gridStyle} align="right" container spacing={24}>
                <Grid item xs={12}>
                  <Link to={'/'} >
                    <Button style={{marginRight:"2em"}} variant="contained" size="large">
                      Exit
                    </Button>
                  </Link>
                </Grid>
              </Grid>
          </Paper>
      </div>
    );
  }
}


export default withStyles(styles)(ResourceStatus);
