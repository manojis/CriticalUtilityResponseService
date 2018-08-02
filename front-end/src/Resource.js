import React, {Component} from 'react';
import axios from 'axios';

import MenuItem from '@material-ui/core/MenuItem';
import Select from '@material-ui/core/Select';
import InputLabel from '@material-ui/core/InputLabel';
import Button from '@material-ui/core/Button';
import { Link } from 'react-router-dom';
import Paper from '@material-ui/core/Paper';

import Grid from '@material-ui/core/Grid';
import Divider from '@material-ui/core/Divider';
import Input from '@material-ui/core/Input';

import Resource_validators from './Resource_validators';


const gridStyle = {
  paddingTop: "2rem"
}

class Resource extends Component {

  constructor(props){
    super(props);

    this.state = {
      username: sessionStorage.getItem('username'),
      owner: '',
      resource_name: '',
      primary_esf: '',
      additional_esfs: [],
      model: '',
      capabilities: '',
      latitude : '',
      longitude: '',
      max_distance: '',
      cost: '',
      unit: '',
      list_of_esfs: [],
      list_of_units: [],
      errors: {},
    }

    // Set of validators for signin form
    this.validators = Resource_validators;

    // This resets our form when navigating between views
    this.resetValidators();

    this.onSave = this.onSave.bind(this);
    this.fetchESFs = this.fetchESFs.bind(this);
    this.fetchUnits = this.fetchUnits.bind(this);
    this.fetchUserDetails = this.fetchUserDetails.bind(this);
    this.validate = this.validate.bind(this);
    this.handleChange = this.handleChange.bind(this);

    // Validation related methods
    this.displayValidationErrors = this.displayValidationErrors.bind(this);
    this.updateValidators = this.updateValidators.bind(this);
    this.resetValidators = this.resetValidators.bind(this);
    this.isFormValid = this.isFormValid.bind(this);
  }

  componentWillMount() {
    setTimeout(() => {
      this.fetchUserDetails();
    }, 400); 
    setTimeout(() =>{
      this.fetchESFs();
    }, 800);
    setTimeout(() => {
      this.fetchUnits();
    }, 1200);
  }

  fetchUserDetails() {
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
            owner: res.data['job_title']
          })
        } else if (res.data['user_Type'] ==='Company'){
          this.setState({
            owner: res.data['headquarter']
          })
        } else if (res.data['user_Type'] ==='Municipality'){
          this.setState({
            owner: res.data['municipality_category']
          })
        } else if (res.data['user_Type'] ==='GovernmentAgency'){
          this.setState({
            owner: res.data['agency_name_local_office']
          })
        }
      }
    })
    .catch( (error) => {
      console.log(error)
    })
  }

  fetchESFs() {
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

  fetchUnits() {
    var apiBaseUrl = "http://localhost:8090/api/units";

    axios.get(apiBaseUrl)
    .then( (res) => {
      var result = Object.keys(res.data).map( key => {
        return {key:key, value:res.data[key]}
      });
      this.setState({
        list_of_units: result
      })
    }).catch( (error) => {
      console.log(error)
    })
  }

  handleChange = name => event => {
    this.setState({
      [name]: event.target.value,
    });
    var validated_inputs = ["resource_name", "longitude", "latitude", "cost"];
    if (validated_inputs.indexOf(name)>=0)
      {this.updateValidators(name, event.target.value);}
  };

  handleSelect = event => {
    this.setState({ [event.target.name]: event.target.value });
    var validated_selections = ["primary_esf", "unit"];
    if (validated_selections.indexOf(event.target.name)>=0)
      {this.updateValidators(event.target.name, event.target.value);}
  };

  validate() {

    let errors = {};

    let err = false;

    if (this.state.resource_name === '') {
      err = true;
      errors['resource_name'] = 'Required'
    }

    if (this.state.primary_esf === '') {
      err = true;
      errors['primary_esf'] = 'Required'
    }

    this.setState({errors: errors});

    return err;
  }


  onSave() {

    const err = this.validate();

    if (err === false) {
      var apiBaseUrl = "http://localhost:8090/api/resource";
      var payload = {
        username: this.state.username,
        resource_name: this.state.resource_name,
        model: this.state.model,
        cost: this.state.cost,
        longitude: this.state.longitude,
        latitude: this.state.latitude,
        max_distance: this.state.max_distance,
        primary_esf: this.state.primary_esf,
        additional_esf: this.state.additional_esfs,
        capabilities: this.state.capabilities.split('\n'),
        unit: this.state.unit,
        is_available: 1
      }
      console.log(payload)
      axios.post(apiBaseUrl, payload)
      .then( (res) => {
        alert('Resource Added Successfully');
        this.setState({
          resource_name: '',
          primary_esf: '',
          additional_esfs: [],
          model: '',
          capabilities: '',
          latitude : '',
          longitude: '',
          max_distance: '',
          cost: '',
          unit: ''
        })
      }).catch( (error) => {
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

          <h1 style={{textAlign:"center"}}>Add New Resource</h1>
          <h2 style={{textAlign:"center"}}> New Resource Info </h2>

          <Divider />
          <form style={{paddingTop:"2em"}}>
            <Grid style={gridStyle} container spacing={24}>
              <Grid item xs={1}>
              </Grid>
              <Grid item xs={3}>
                Resource ID
              </Grid>
              <Grid item xs={7}>
                (auto assigned)
              </Grid>
            </Grid>
            <Grid style={gridStyle} container spacing={24}>
              <Grid item xs={1}>
              </Grid>
              <Grid item xs={3}>
                Owner
              </Grid>
              <Grid item xs={7}>
                {this.state.owner}
              </Grid>
            </Grid>
            <Grid style={gridStyle} container spacing={24}>
              <Grid item xs={1}>
              </Grid>
              <Grid item xs={3}>
                Resource Name
              </Grid>
              <Grid item xs={7}>
                <Input
                  style = {{width: "60%"}}
                  id="resource_name"
                  label="Resource Name"
                  value={this.state.resource_name}
                  onChange={this.handleChange('resource_name')}
                />
                <div style={{color: "red"}}>{ this.displayValidationErrors('resource_name') }</div>
              </Grid>
            </Grid>
            <Grid style={gridStyle} container spacing={24}>
              <Grid item xs={1}>
              </Grid>
              <Grid item xs={3}>
                Primary ESF
              </Grid>
              <Grid item xs={7}>
                <Select
                    style = {{width: "60%"}}
                    value={this.state.primary_esf}
                    onChange={this.handleSelect}
                    inputProps={{
                      name: 'primary_esf',
                      id: 'priamry_esf',
                    }}
                  >
                    {this.state.list_of_esfs.map(option => (
                      <MenuItem key={option.key} value={option.key}>
                        {"#" + option.key + " " + option.value}
                      </MenuItem>
                    ))}
                </Select>
                <div style={{color: "red"}}>{ this.displayValidationErrors('primary_esf') }</div>
                <span style={{color: "red"}}>{this.state.errors["primary_esf"]}</span>
              </Grid>
            </Grid>
            <Grid style={gridStyle} container spacing={24}>
              <Grid item xs={1}>
              </Grid>
              <Grid item xs={3}>
                Additional ESFs
              </Grid>
              <Grid item xs={7}>
                <Select
                  style = {{width: "60%"}}
                  multiple
                  value={this.state.additional_esfs}
                  onChange={this.handleSelect}
                  inputProps={{
                    name: 'additional_esfs',
                    id: 'additional_esfs',
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
                Model
              </Grid>
              <Grid item xs={7}>
                <Input
                  style = {{width: "60%"}}
                  id="model"
                  label="Model"
                  value={this.state.model}
                  onChange={this.handleChange('model')}
                />
                </Grid>
            </Grid>
            <Grid style={gridStyle} container spacing={24}>
              <Grid item xs={1}>
              </Grid>
              <Grid item xs={3}>
                Capabilities
              </Grid>
              <Grid item xs={7}>
                <Input
                  style = {{width: "60%"}}
                  id="capabilities"
                  label="Capabilities"
                  multiline
                  rowsMax="4"
                  value={this.state.capabilities}
                  onChange={this.handleChange('capabilities')}
                />
              </Grid>
            </Grid>
            <Grid style={gridStyle} container spacing={24}>
              <Grid item xs={1}>
              </Grid>
              <Grid item xs={3}>
                Home Location
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
                  <div style={{color: "red"}}> { this.displayValidationErrors('latitude') }</div>
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
                  <div style={{color: "red"}}> { this.displayValidationErrors('longitude') }</div>
                </Grid>
              </Grid>
            </Grid>
            <Grid style={gridStyle} container spacing={24}>
              <Grid item xs={1}>
              </Grid>
              <Grid item xs={3}>
                Max Distance
              </Grid>
              <Grid item xs={7}>
                <Input
                  style = {{width: "60%"}}
                  id="max_distance"
                  label="Max Distance"
                  value={this.state.max_distance}
                  onChange={this.handleChange('max_distance')}
                /> kilometers
              </Grid>
            </Grid>
            <Grid style={gridStyle} container spacing={24}>
              <Grid item xs={1}>
              </Grid>
              <Grid item xs={3}>
                  Cost
              </Grid>
              <Grid item xs={7}>
                $ <Input
                  style = {{width: "30%"}}
                  id="cost"
                  label="Cost"
                  value={this.state.cost}
                  onChange={this.handleChange('cost')}
                />
                <div style={{color: "red"}}> { this.displayValidationErrors('cost') }</div>
                <InputLabel htmlFor="unit"> per </InputLabel>
                <Select
                  style = {{width: "20%"}}
                  value={this.state.unit}
                  onChange={this.handleSelect}
                  inputProps={{
                    name: 'unit',
                    id: 'unit',
                  }}
                >
                  {this.state.list_of_units.map(option => (
                    <MenuItem key={option.key} value={option.key}>
                      {option.value}
                    </MenuItem>
                  ))}
                </Select>
                <div style={{color: "red"}}>{ this.displayValidationErrors('unit') }</div>
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
                  disabled = {!this.isFormValid()} onClick={this.onSave}>
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

export default Resource;
