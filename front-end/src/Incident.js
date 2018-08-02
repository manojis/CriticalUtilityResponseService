import React, {Component} from 'react';
import axios from 'axios';

import MenuItem from '@material-ui/core/MenuItem';
import Select from '@material-ui/core/Select';
import Button from '@material-ui/core/Button';
import Paper from '@material-ui/core/Paper';
import Divider from '@material-ui/core/Divider';
import { Link } from 'react-router-dom';
import Grid from '@material-ui/core/Grid';
import Input from '@material-ui/core/Input';

import Incident_validators from './Incident_validators';


const gridStyle = {
  paddingTop: "2rem"
}

class Incident extends Component {

  constructor(props){
    super(props);
    var today = new Date();
    var date = today.getFullYear() + '-' + (today.getMonth() + 1) + '-' + today.getDate();


    this.state = {
      username: sessionStorage.getItem('username'),
      incident_id: '',
      declaration_abbreviation: '',
      date:date,
      description: '',
      latitude : '',
      longitude: '',
      list_of_declarations: []
    }

    // Set of validators for signin form
    this.validators = Incident_validators;

    // This resets our form when navigating between views
    this.resetValidators();

    this.onSave = this.onSave.bind(this);

    // Validation related methods
    this.displayValidationErrors = this.displayValidationErrors.bind(this);
    this.updateValidators = this.updateValidators.bind(this);
    this.resetValidators = this.resetValidators.bind(this);
    this.isFormValid = this.isFormValid.bind(this);
  }

  componentDidMount() {
    this.setState({ username: sessionStorage.getItem('username')})
    this.fetchDeclarations();
  }

  fetchDeclarations() {
    var apiBaseUrl = "http://localhost:8090/api/declarations";

    axios.get(apiBaseUrl)
    .then( (res) => {

      var result = Object.keys(res.data).map( key => {
        return {key:key, value:res.data[key]}
      });

      this.setState({
        list_of_declarations : result
      })
    }).catch( (error) => {
      console.log(error)
    })
  }

  handleChange = name => event => {
    this.setState({
      [name]: event.target.value,
    });
    var validated_inputs = ["date", "description", "latitude", "longitude"];
    if (validated_inputs.indexOf(name)>=0)
      {this.updateValidators(name, event.target.value);}
  };

  handleSelect = event => {
    this.setState({ [event.target.name]: event.target.value });
    var validated_selections = ["declaration_abbreviation"];
    if (validated_selections.indexOf(event.target.name)>=0)
      {this.updateValidators(event.target.name, event.target.value);}
  };

  onSave() {
    const valid = this.isFormValid();

    console.log(valid)

    if (valid) {
      axios.get("http://localhost:8090/api/incidentcount?abbr=" + this.state.declaration_abbreviation)
      .then((res) => {
        var next_available_incident_id = res.data['next_available_incident_id'];
        var apiBaseUrl = "http://localhost:8090/api/incident";
        var payload = {
          incident_id: next_available_incident_id,
          username: this.state.username,
          incident_date: this.state.date,
          description: this.state.description,
          longitude: this.state.longitude,
          latitude: this.state.latitude,
          declaration_abbreviation: this.state.declaration_abbreviation
        }
        axios.post(apiBaseUrl, payload)
        .then( (res) => {
          alert('Incident Added Successfully');
          this.setState({
            incident_id: '',
            declaration_abbreviation: '',
            description: '',
            latitude : '',
            longitude: '',
          })
        }).catch( (error) => {
          console.log(error)
        })
      })
      .catch( (error) => {
        console.log(error)
      })
    } else {
      alert('Please fill out all the required fields correctly');
    }
  }

  /**
   * This function updates the state of the validator for the specified validator
   */
  updateValidators(fieldName, value) {
    this.validators[fieldName].errors = [];
    this.validators[fieldName].state = value;
    this.validators[fieldName].valid = true;
    this.validators[fieldName].rules.forEach((rule) => {
      if (rule.test instanceof RegExp) {
        if (!rule.test.test(value)) {
          this.validators[fieldName].errors.push(rule.message);
          this.validators[fieldName].valid = false;
        }
      } else if (typeof rule.test === 'function') {
        if (!rule.test(value)) {
          this.validators[fieldName].errors.push(rule.message);
          this.validators[fieldName].valid = false;
        }
      }
    });
  }

  // This function resets all validators for this form to the default state
  resetValidators() {
    Object.keys(this.validators).forEach((fieldName) => {
      this.validators[fieldName].errors = [];
      this.validators[fieldName].state = '';
      this.validators[fieldName].valid = false;
      this.validators["date"].valid = true;
    });
  }

  // This function displays the validation errors for a given input field
  displayValidationErrors(fieldName) {
    const validator = this.validators[fieldName];
    const result = '';
    if (validator && !validator.valid) {
      const errors = validator.errors.map((info, index) => {
        return <span className="error" key={index}>* {info}</span>;
      });

      return (
        <div className="col s12 row">
          {errors}
        </div>
      );
    }
    return result;
  }

  // This method checks to see if the validity of all validators are true
  isFormValid() {
    let status = true;
    Object.keys(this.validators).forEach((field) => {
      if (!this.validators[field].valid) {
        status = false;
      }
    });
    return status;
  }


  render() {

    return(
      <div style={{width:"60%"}}>
        <Paper style={{marginTop: "2rem", border: "3px solid green", padding: "10px", boxShadow: "0 19px 38px rgba(0,0,0,0.30), 0 15px 12px rgba(0,0,0,0.22)"}}>

          <h1 style={{textAlign:"center"}}>Add New Incident</h1>
          <h2 style={{textAlign:"center"}}>New Incident Info</h2>

          <Divider />
          <form style={{paddingTop:"2em"}}>
            <Grid style={gridStyle} container spacing={24}>
              <Grid item xs={1}>
              </Grid>
              <Grid item xs={3}>
                Declaration
              </Grid>
              <Grid item xs={7}>
                <Select
                  style={{width: "60%"}}
                  value={this.state.declaration_abbreviation}
                  onChange={this.handleSelect}
                  inputProps={{
                    name: 'declaration_abbreviation',
                    id: 'declaration_abbreviation',
                  }}
                >
                  {this.state.list_of_declarations.map(option => (
                    <MenuItem key={option.key} value={option.key}>
                      {"(" + option.key + ") " + option.value}
                    </MenuItem>
                  ))}
                </Select>
                <div style={{color: "red"}}>{ this.displayValidationErrors('declaration_abbreviation') }</div>
              </Grid>
            </Grid>
            <Grid style={gridStyle} container spacing={24}>
              <Grid item xs={1}>
              </Grid>
              <Grid item xs={3}>
                Incident ID
              </Grid>
              <Grid item xs={7}>
                (auto assigned)
              </Grid>
            </Grid>
            <Grid style={gridStyle} container spacing={24}>
              <Grid item xs={1}>
              </Grid>
              <Grid item xs={3}>
                Date
              </Grid>
              <Grid item xs={7}>
                <Input
                  style={{width: "60%"}}
                  id="date"
                  label="Date"
                  multiline
                  rowsMax="4"
                  value={this.state.date}
                  onChange={this.handleChange('date')}
                />
                <div style={{color: "red"}}>{ this.displayValidationErrors('date') }</div>
              </Grid>
            </Grid>
            <Grid style={gridStyle} container spacing={24}>
              <Grid item xs={1}>
              </Grid>
              <Grid item xs={3}>
                Description
              </Grid>
              <Grid item xs={7}>
                <Input
                  style={{width: "60%"}}
                  id="description"
                  label="Description"
                  value={this.state.description}
                  onChange={this.handleChange('description')}
                />
                <div style={{color: "red"}}>{ this.displayValidationErrors('description') }</div>
              </Grid>
            </Grid>
            <Grid style={gridStyle} container spacing={24}>
              <Grid item xs={1}>
              </Grid>
              <Grid item xs={3}>
                Location
              </Grid>
              <Grid item xs={7}>
                <Grid item xs={3}>
                  Latitude
                </Grid>
                <Grid item xs={4}>
                  <Input
                    style = {{width: "100%"}}
                    id="latitude"
                    label="Latitude"
                    value={this.state.latitude}
                    onChange={this.handleChange('latitude')}
                  />
                  <div style={{color: "red"}}>{ this.displayValidationErrors('latitude') }</div>
                </Grid>
                <br />
                <Grid item xs={3}>
                  Longitude
                </Grid>
                <Grid item xs={4}>
                  <Input
                    style = {{width: "100%"}}
                    id="longitude"
                    label="Longitude"
                    value={this.state.longitude}
                    onChange={this.handleChange('longitude')}
                  />
                  <div style={{color: "red"}}>{ this.displayValidationErrors('longitude') }</div>
                </Grid>
              </Grid>
            </Grid>
            <Grid style={gridStyle} align="right" container spacing={24}>
              <Grid item xs={12}>
                <Link to={'/'} >
                  <Button style={{marginRight:"2em"}} variant="contained" size="large">
                    Cancel
                  </Button>
                </Link>
                <Button style={{marginRight:"2em"}} variant="contained" color="primary" size="large"
                  disabled = {!this.isFormValid()}  onClick={this.onSave}>
                  Save
                </Button>
              </Grid>
            </Grid>
          </form>
        </Paper>
      </div>
    );
  }

}

export default Incident;
