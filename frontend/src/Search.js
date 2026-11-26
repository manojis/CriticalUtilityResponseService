import React, {Component} from 'react';
import axios from 'axios';

import Select from '@material-ui/core/Select';
import MenuItem from '@material-ui/core/MenuItem';
import Button from '@material-ui/core/Button';
import { Link } from 'react-router-dom';

import Table from '@material-ui/core/Table';
import TableBody from '@material-ui/core/TableBody';
import TableCell from '@material-ui/core/TableCell';
import TableHead from '@material-ui/core/TableHead';
import TableRow from '@material-ui/core/TableRow';
import Paper from '@material-ui/core/Paper';
import Divider from '@material-ui/core/Divider';
import Grid from '@material-ui/core/Grid';
import Input from '@material-ui/core/Input';

import Modal from '@material-ui/core/Modal';
import { withStyles } from '@material-ui/core/styles';

const gridStyle = {
  paddingTop: "2rem"
}

function getModalStyle() {
  const top = 50;
  const left = 50;

  return {
    top: `${top}%`,
    left: `${left}%`,
    transform: `translate(-${top}%, -${left}%)`,
  };
}

const styles = theme => ({
  paper: {
    position: 'absolute',
    width: theme.spacing.unit * 50,
    backgroundColor: theme.palette.background.paper,
    boxShadow: theme.shadows[5],
    padding: theme.spacing.unit * 4,
  },
});

class Search extends Component {

  constructor(props){
    super(props);

    this.state = {
      username: sessionStorage.getItem('username'),
      keyword: '',
      esf: '',
      location: '',
      incident: '',
      fireResults: false,
      fireIncidentResults: false,
      showSearch: true,
      list_of_esfs: [],
      list_of_incidents: [],
      list_of_results: [],
      open: false,
      expected_return_date: '',
      savedData: {},
      selectedAction: '',
      actionStatus: ''
    }

    this.onSearch = this.onSearch.bind(this);
    this.onClose = this.onClose.bind(this);
    this.fetchESFS = this.fetchESFS.bind(this);
    this.fetchIncidents = this.fetchIncidents.bind(this);
    this.handleDeploy= this.handleDeploy.bind(this);
    this.handleRequest = this.handleRequest.bind(this);
    this.handleOpen = this.handleOpen.bind(this);
    this.handleClose = this.handleClose.bind(this);
  }

  componentWillMount() {
    setTimeout(() => {
      this.fetchESFS();
    }, 400); 
    setTimeout(() =>{
      this.fetchIncidents();
    }, 800);
  }

  fetchESFS() {
    var apiBaseUrl = "http://localhost:8090/api/esfs";

    axios.get(apiBaseUrl)
    .then( (res) => {

      var result = Object.keys(res.data).map( key => {
        return {key:key, value:res.data[key]}
      });

      this.setState({
        list_of_esfs : result
      })
    }).catch( (error) => {
      console.log(error)
    })
  }

  fetchIncidents() {
    var apiBaseUrl = "http://localhost:8090/api/incident?username=" + this.state.username;

    axios.get(apiBaseUrl)
    .then( (res) => {

      var result = Object.keys(res.data).map( key => {
        return {key:key, value:res.data[key]}
      });

      this.setState({
        list_of_incidents : result
      })
    }).catch( (error) => {
      console.log(error)
    })
  }

  handleChange = name => event => {
    this.setState({
      [name]: event.target.value,
    });
  };

  handleSelect = event => {
    this.setState({ [event.target.name]: event.target.value });
  };

  onSearch() {

    var loc = '';
    var esf = '';
    if (this.state.location === ''){
      loc = '-1';
    } else {
      loc = this.state.location;
    }
    if (this.state.esf === ''){
      esf = '-1';
    } else {
      esf = this.state.esf;
    }

    if (this.state.location !== '' && this.state.incident === '') {
      return alert('Please select an incident')
    }
    var apiBaseUrl = "http://localhost:8090/api/resource?keyword=" + this.state.keyword + "&location=" + loc + "&incidentid=" + this.state.incident + "&esf=" + esf;

    axios.get(apiBaseUrl)
    .then( (res) => {
      this.setState({
        list_of_results: res.data
      })
    }).catch( (error) => {
      console.log(error)
    })

    if (this.state.incident !== ''){
      this.setState({
        fireIncidentResults: true,
        showSearch: false
      })
    } else {
      this.setState({
        fireResults: true,
        showSearch: false
      })
    }
  }

  onClose() {
    this.setState({
      fireResults: false,
      fireIncidentResults: false,
      showSearch: true
    })
  }

  handleDeploy() {

  }
  
  handleRequest(resource_id, incident_id, resource_name, requestedBy_username, next_available, action, actionStatus) {

    this.setState({
      open: false
    })

    var apiBaseUrl = "http://localhost:8090/api/requestaction";
    var payload = {
      resource_id: resource_id,
      incident_id: incident_id,
      resource_name: resource_name,  
      username: this.state.username,
      requestedBy_username: requestedBy_username,  
      next_available: next_available,
      action: action,
      actionStatus: actionStatus
    }
    console.log(payload)
    axios.post(apiBaseUrl, payload)
    .then( (res) => {
      alert(res.data['message'])
    }).catch( (error) => {
      console.log(error)
    })
  }

  handleOpen = () => {
    this.setState({ open: true });
  };

  handleClose = () => {
    this.setState({ open: false });
  };

  render() {

    const { classes } = this.props;

    return(
      <div style={{width:"60%"}}>

        { this.state.showSearch && (
          <Paper style={{marginTop: "2rem", border: "3px solid green", padding: "10px", boxShadow: "0 19px 38px rgba(0,0,0,0.30), 0 15px 12px rgba(0,0,0,0.22)"}}>
        
          <h1 style={{textAlign:"center"}}>Search Resource</h1>
          <Divider />
          <form style={{paddingTop:"2em"}}>
            <Grid style={gridStyle} container spacing={24}> 
              <Grid item xs={1}>
              </Grid>
              <Grid item xs={3}>
                Keyword
              </Grid>
              <Grid item xs={7}>
                <Input
                  style={{width:"60%"}}
                  id="keyword"
                  label="Keyword"
                  value={this.state.keyword}
                  onChange={this.handleChange('keyword')}
                />
              </Grid>
            </Grid>
            <Grid style={gridStyle} container spacing={24}> 
              <Grid item xs={1}>
              </Grid>
              <Grid item xs={3}>
                ESF
              </Grid>
              <Grid item xs={7}>
                <Select
                  style={{width:"60%"}}
                  value={this.state.esf}
                  onChange={this.handleSelect}
                  inputProps={{
                    name: 'esf',
                    id: 'esf',
                  }}
                >
                  {this.state.list_of_esfs.map(option => (
                    <MenuItem key={option.key} value={option.key}>
                      {"#" + option.key + " " + option.value}
                    </MenuItem>
                  ))}
                </Select>
              </Grid>
            </Grid>
            <Grid style={gridStyle} container spacing={24}> 
              <Grid item xs={1}>
              </Grid>
              <Grid item xs={3}>
                Location Within
              </Grid>
              <Grid item xs={7}>
                <Input
                  style={{width:"20%"}}
                  id="Location"
                  label="Location Within"
                  value={this.state.location}
                  onChange={this.handleChange('location')}
                /> Kilometers of incident
              </Grid>
            </Grid>  
            <Grid style={gridStyle} container spacing={24}> 
              <Grid item xs={1}>
              </Grid>
              <Grid item xs={3}>
                Incident
              </Grid>
              <Grid item xs={7}>
                <Select
                  style={{width:"60%"}}
                  value={this.state.incident}
                  onChange={this.handleSelect}
                  inputProps={{
                    name: 'incident',
                    id: 'incident',
                  }}
                >
                  {this.state.list_of_incidents.map(option => (
                    <MenuItem key={option.key} value={option.key}>
                      {"(" + option.key + ") " + option.value}
                    </MenuItem>
                  ))}
                </Select>
              </Grid>
            </Grid>   
            <Grid style={gridStyle} align="right" container spacing={24}> 
              <Grid item xs={12}>
                <Link to={'/'} >
                  <Button style={{marginRight:"2em"}} variant="contained" size="large">
                    Cancel
                  </Button>
                </Link>
                <Button style={{marginRight:"2em"}} variant="contained" color="primary" size="large" onClick={this.onSearch}>
                  Search
                </Button>
              </Grid>
            </Grid>
          </form>
        </Paper>
        )}

        { this.state.fireIncidentResults && (
           <div style={{width:"100%"}}>

             <Paper style={{marginTop: "2rem", border: "3px solid green", padding: "10px", boxShadow: "0 19px 38px rgba(0,0,0,0.30), 0 15px 12px rgba(0,0,0,0.22)"}}>
             <h1 style={{textAlign:"center"}}>Results</h1>
              <Divider />
              <Table style={{overflowX:'auto'}}>
                <TableHead>
                  <TableRow style={{background: "#4DCCBD"}}>
                    <TableCell>ID</TableCell>
                    <TableCell>Name</TableCell>
                    <TableCell>Owner</TableCell>
                    <TableCell>Cost</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Next Available</TableCell>
                    <TableCell>Distance</TableCell>
                    <TableCell>Action</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {this.state.list_of_results.map(data => {
                    console.log(data)
                    return (
                      <TableRow key={data.resource_id}>
                        <TableCell><div>{data.resource_id}</div></TableCell>
                        <TableCell><div>{data.resource_name}</div></TableCell>
                        <TableCell><div>{data.username}</div></TableCell>
                        <TableCell><div>{data.cost}/{data.unit}</div></TableCell>
                        <TableCell><div>{data.is_available ? 'AVAILABLE' : 'NOT AVAILABLE'}</div></TableCell>
                        <TableCell><div>{data.next_available}</div></TableCell>
                        <TableCell><div>{data.distance}</div></TableCell>
                        <TableCell><div>{
                          (data.username === this.state.username && data.is_available===true) ? (
                            <Button variant="contained" size="large" onClick={() => 
                              this.setState({
                                open: true,
                                savedData: data,
                                selectedAction: 'Deploy',
                                actionStatus: 'Accept'
                                })  
                            } >
                              Deploy
                            </Button>
                          ) : <div></div>
                          
                        }</div>
                        <div>{
                          (data.username !== this.state.username) ? (
                            <Button variant="contained" size="large" onClick={() => 
                                this.setState({
                                  open: true,
                                  savedData: data,
                                  selectedAction: 'Request',
                                  actionStatus: ''
                                  })  
                              } >
                              Request
                            </Button>
                          ) : <div> </div>}
                        </div>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
              <Grid style={{marginTop: "1em"}} align="right" container spacing={24}> 
                <Grid item xs={12}>
                    <Button style={{marginRight:"2em"}} variant="contained" size="large" onClick={this.onClose}>
                      Cancel
                    </Button>
                </Grid>
              </Grid>
            </Paper>
          </div>
        )}
        
        { this.state.fireResults && (
           <div style={{width:"100%"}}>

             <Paper style={{marginTop: "2rem", border: "3px solid green", padding: "10px", boxShadow: "0 19px 38px rgba(0,0,0,0.30), 0 15px 12px rgba(0,0,0,0.22)"}}>
             <h1 style={{textAlign:"center"}}>Results</h1>
              <Divider />
              <Table style={{overflowX:'auto'}}>
                <TableHead>
                  <TableRow style={{background: "#4DCCBD"}}>
                    <TableCell>ID</TableCell>
                    <TableCell>Name</TableCell>
                    <TableCell>Owner</TableCell>
                    <TableCell>Cost</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Next Available</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {this.state.list_of_results.map(data => {
                    return (
                      <TableRow key={data.resource_id}>
                        <TableCell><div>{data.resource_id}</div></TableCell>
                        <TableCell><div>{data.resource_name}</div></TableCell>
                        <TableCell><div>{data.username}</div></TableCell>
                        <TableCell><div>{data.cost}/{data.unit}</div></TableCell>
                        <TableCell><div>{data.is_available}</div></TableCell>
                        <TableCell><div>{data.next_available}</div></TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
              <Grid style={{marginTop: "1em"}} align="right" container spacing={24}> 
                <Grid item xs={12}>
                    <Button style={{marginRight:"2em"}} variant="contained" size="large" onClick={this.onClose}>
                      Cancel
                    </Button>
                </Grid>
              </Grid>
            </Paper>
          </div>
        )}
        <Modal
          open={this.state.open}
          onClose={this.handleClose}
        >
          <div style={getModalStyle()} className={classes.paper}>
            <Grid style={gridStyle} container spacing={24}> 
              <Grid item xs={1}>
              </Grid>
              <Grid item xs={3}>
                Expected Return Date
              </Grid>
              <Grid item xs={7}>
                <Input
                  style={{width:"100%"}}
                  id="expected_return_date"
                  label="YYYY-MM-dd"
                  value={this.state.expected_return_date}
                  onChange={this.handleChange('expected_return_date')}
                />
              </Grid>
              <Grid item xs={12}>
                <Button style={{marginRight:"2em"}} variant="contained" size="large" onClick={() => this.handleRequest(this.state.savedData.resource_id, this.state.incident, this.state.savedData.resource_name, this.state.username, this.state.expected_return_date, this.state.selectedAction, this.state.actionStatus)}>
                    Ok
                  </Button>
              </Grid>
            </Grid> 
          </div>
        </Modal>
      </div>
    );
  }

}

export default withStyles(styles)(Search);
