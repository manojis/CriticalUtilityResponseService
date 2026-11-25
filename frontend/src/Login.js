import React, { Component } from 'react';
import axios from 'axios';
import TextField from '@material-ui/core/TextField';
import Button from '@material-ui/core/Button';
import Paper from '@material-ui/core/Paper';
import Grid from '@material-ui/core/Grid';

class Login extends Component {

  constructor(props){
    super(props);

    this.state = {
      username : '',
      password: ''
    }
  }

  handleLogin = () => {

    // console.log(this.props.childProps)

    var apiBaseUrl = "http://localhost:8090/api/login";

    var payload = {
      username: this.state.username,
      password: this.state.password
    }
    axios.post(apiBaseUrl, payload)
    .then( (res) => {
      if (res.status === 200 && res.data['success'] === 'true') {
        this.props.childProps.userHasAuthenticated(true);
        this.props.props.history.push('/')
        sessionStorage.setItem("username", res.data['username'])
      } else {
        alert('Username or Password is incorrect!');
      }
    }).catch( (error) => {
      console.log(error)
    })
  }


  handleChange = name => event => {
    this.setState({
      [name]: event.target.value,
    });
  };

  render() {
    return (
      <div style={{margin: "auto",width: "30%"}}>
        <h1>Login</h1>
        <Paper style={{border: "3px solid green", padding: "10px", boxShadow: "0 19px 38px rgba(0,0,0,0.30), 0 15px 12px rgba(0,0,0,0.22)"
          }}>
          
          <h1>ERMS</h1> 
          <h3>Emergency Resource Management System</h3>
          <Grid container spacing={16}> 
            <Grid item xs={12}>
              <TextField
                style = {{width: "60%"}}
                id="username"
                label="Username"
                value={this.state.username}
                onChange={this.handleChange('username')}
                margin="normal"
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                style = {{width: "60%"}}
                id="password"
                label="Password"
                value={this.state.password}
                onChange={this.handleChange('password')}
                margin="normal"
              />
            </Grid>
            <Grid item xs={12}>
              <Button style={{marginTop:"2rem"}} variant="contained" color="primary" size="large" onClick={() => this.handleLogin()}>
                Submit
              </Button>
            </Grid>
          </Grid>
        </Paper>
      </div>
    );
  }
}

export default Login;
