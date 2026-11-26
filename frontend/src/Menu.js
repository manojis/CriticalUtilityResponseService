import React, { Component } from 'react';
import axios from 'axios';

import MenuList from '@material-ui/core/MenuList';
import MenuItem from '@material-ui/core/MenuItem';
import Paper from '@material-ui/core/Paper';
import ListItemIcon from '@material-ui/core/ListItemIcon';
import ListItemText from '@material-ui/core/ListItemText';
import Add from '@material-ui/icons/Add';
import Search from '@material-ui/icons/Search';
import Assignment from '@material-ui/icons/Assignment'
import AssignmentLate from '@material-ui/icons/AssignmentLate';

import { Link } from 'react-router-dom';
import Button from '@material-ui/core/Button';
import Grid from '@material-ui/core/Grid';
import Divider from '@material-ui/core/Divider';

class Menu extends Component {

  constructor(props){
    super(props);

    this.state = {
      username: sessionStorage.getItem('username'),
      name: ''
    }

    this.exitApp = this.exitApp.bind(this);
  }

  componentDidMount() {
    var apiBaseUrl = "http://localhost:8090/api/user/";

    axios.get(apiBaseUrl + this.state.username)
    .then( (res) => {
      if (res.status === 200) {
        console.log(res)
        this.setState({
          name: res.data['name']
        })

        if (res.data['user_Type'] ==='Individual'){
          this.setState({
            user_info: res.data['job_title']
          })
        } else if (res.data['user_Type'] ==='Company'){
          this.setState({
            user_info: res.data['headquarter']
          })
        } else if (res.data['user_Type'] ==='Municipality'){
          this.setState({
            user_info: res.data['municipality_category']
          })
        } else if (res.data['user_Type'] ==='GovernmentAgency'){
          this.setState({
            user_info: res.data['agency_name_local_office']
          })
        }
      }
    }).catch( (error) => {
      console.log(error)
    })
  }

  exitApp() {

    sessionStorage.removeItem('username');
    return (
      this.props.history.push('/login')
    );
  }

  render() {
    return (
      <div style={{width:"60%"}}>
        <Paper style={{marginTop: "2rem", border: "3px solid green", padding: "10px", boxShadow: "0 19px 38px rgba(0,0,0,0.30), 0 15px 12px rgba(0,0,0,0.22)"}}>
          <Grid container spacing={16}> 
            <Grid item xs={4}>
              <h1 style={{textAlign:"left", paddingLeft: "2em"}}> ERMS </h1>
            </Grid>
            <Grid item xs={4}>
              <h1 style={{textAlign:"left", paddingLeft: "1em"}}> Main Menu </h1>
            </Grid>
            <Grid item xs={4}>
              <h3 style={{textAlign:"right", paddingRight: "2em"}}> {this.state.name} </h3>
              <h3 style={{textAlign:"right", paddingRight: "2em"}}> {this.state.user_info} </h3>
            </Grid>
            <Grid item xs={12}>
              <Divider />
            </Grid>
            <Grid item xs={4}>
            </Grid>
            <Grid item xs={4}>
              <MenuList>
                <Link to={'/resource'}>
                  <MenuItem >
                    <ListItemIcon>
                      <Add />
                    </ListItemIcon>
                    <ListItemText inset primary="Add Resource" />
                  </MenuItem>
                </Link>
                <Link to={'/incident'}>
                  <MenuItem >
                    <ListItemIcon>
                      <Add />
                    </ListItemIcon>
                    <ListItemText inset primary="Add Emergency Incident" />
                  </MenuItem>
                </Link>
                <Link to={'/search'}>
                  <MenuItem >
                    <ListItemIcon>
                      <Search />
                    </ListItemIcon>
                    <ListItemText inset primary="Search Resources" />
                  </MenuItem>
                </Link>
                <Link to={'/resource_status'} >
                  <MenuItem >
                    <ListItemIcon>
                      <AssignmentLate />
                    </ListItemIcon>
                    <ListItemText inset primary="Resource Status" />
                  </MenuItem>
                </Link>
                <Link to={'/report'} >
                  <MenuItem >
                    <ListItemIcon>
                      <Assignment />
                    </ListItemIcon>
                    <ListItemText inset primary="Resource Report" />
                  </MenuItem>
                </Link>
              </MenuList>
            </Grid>
            <Grid item xs={10}>
            </Grid>
            <Grid item xs={2}>
              <Button variant="contained" size="large" onClick={this.exitApp}>
                Exit
              </Button>
            </Grid>
          </Grid>
          
        </Paper>

      </div>
    );
  }
}

export default Menu;
