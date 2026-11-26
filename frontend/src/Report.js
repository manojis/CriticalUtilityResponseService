import React, {Component} from 'react';
import axios from 'axios';

import Table from '@material-ui/core/Table';
import TableBody from '@material-ui/core/TableBody';
import TableCell from '@material-ui/core/TableCell';
import TableHead from '@material-ui/core/TableHead';
import TableRow from '@material-ui/core/TableRow';
import Paper from '@material-ui/core/Paper';

import { Link } from 'react-router-dom';
import Button from '@material-ui/core/Button';
import Divider from '@material-ui/core/Divider';
import Grid from '@material-ui/core/Grid';


const gridStyle = {
  paddingTop: "2rem"
}

class Search extends Component {

  constructor(props){
    super(props);

    this.state = {
      username : sessionStorage.getItem('username'),
      esfs: [],
      totalESF: 0,
      totalAvailable: 0
    }

    this.fetchESFSLIST = this.fetchESFSLIST.bind(this);
    this.fetchTotalESFS = this.fetchTotalESFS.bind(this);
    this.fetchTotalAvailable = this.fetchTotalAvailable.bind(this);
  }
  
  componentWillMount() {    
    setTimeout(() => {
      this.fetchESFSLIST();
    }, 400); 
    setTimeout(() =>{
      this.fetchTotalESFS();
    }, 800);
    setTimeout(() => {
      this.fetchTotalAvailable();
    }, 1200);
  }

  fetchESFSLIST() {
    var apiBaseUrl = "http://localhost:8090/api/report?username=" + this.state.username;

    axios.get(apiBaseUrl)
    .then( (res) => {
      this.setState({
        esfs: res.data
      })
    }).catch( (error) => {
      console.log(error)
    }) 
  }

  fetchTotalESFS() {
    var apiBaseUrl = "http://localhost:8090/api/total/esfs?username=" + this.state.username;

    axios.get(apiBaseUrl)
    .then( (res) => {
      this.setState({
        totalESF: res.data
      })
    }).catch( (error) => {
      console.log(error)
    }) 
  }

  fetchTotalAvailable() {
    var apiBaseUrl = "http://localhost:8090/api/total/available?username=" + this.state.username;

    axios.get(apiBaseUrl)
    .then( (res) => {
      this.setState({
        totalAvailable: res.data
      })
    }).catch( (error) => {
      console.log(error)
    }) 
  }

  render() {

    return(
      <div style={{width:"60%"}}>
        <Paper style={{marginTop: "2rem", border: "3px solid green", padding: "10px", boxShadow: "0 19px 38px rgba(0,0,0,0.30), 0 15px 12px rgba(0,0,0,0.22)"}}>
      
        <h1 style={{textAlign:"center"}}>Resource Report by Primary Emergency Support Function</h1>
          <Divider />

          <Table style={{marginTop:"2rem"}}>
            <TableHead>
              <TableRow style={{background: "#4DCCBD"}} >
                <TableCell>ESF#</TableCell>
                <TableCell>Primary Emergency Support Function</TableCell>
                <TableCell>Total Resources</TableCell>
                <TableCell>Resource in use</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {this.state.esfs.map(data => {
                return (
                  <TableRow key={data.primary_esf}>
                    <TableCell>{data.primary_esf}</TableCell>
                    <TableCell component="th" scope="row">
                      {data.esf_description}
                    </TableCell>
                    <TableCell>{data.total_resources}</TableCell>
                    <TableCell>{data.resources_in_use}</TableCell>
                  </TableRow>
                );
              })}
              <TableRow style={{background: "#D6FFF6"}} >
                <TableCell></TableCell>
                <TableCell>Total</TableCell>
                <TableCell>{this.state.totalESF}</TableCell>
                <TableCell>{this.state.totalAvailable}</TableCell>
              </TableRow>
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

export default Search;
