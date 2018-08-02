import React, { Component } from 'react';
import {Switch, Route, Redirect, withRouter} from 'react-router-dom';
import styled from 'styled-components';
import Resource from './Resource';
import Menu from './Menu';
import Login from './Login';
import Incident from './Incident';
import Resource_status from './Resource_status';
import Search from './Search';
import Report from './Report';
import NotFound from './NotFound';

export const Container = styled.div`
    display: flex;
    flex-direction: column;
    align-items: center;
    margin: auto;
    width: 100%;
    min-height: 80vh;
`

class ERMS extends Component {

    constructor(props){
        super(props);

        this.state = {
            isAuthenticated: false
        }

        this.userHasAuthenticated = this.userHasAuthenticated.bind(this);
    }

    componentWillMount(){

        if (sessionStorage.getItem('username')){
            this.setState({
                isAuthenticated: true
            })
        }
    }

    userHasAuthenticated = auth => {
        this.setState({ isAuthenticated: auth });
    }

    render() {

        const childProps = {
            isAuthenticated: this.state.isAuthenticated,
            userHasAuthenticated: this.userHasAuthenticated
        };

        const PrivateRoute = ({ component: Component, ...rest }) => (
            <Route {...rest} render={(props) => (
                this.state.isAuthenticated === true
                ? <Component {...props} />
                : <Redirect to='/login' />
            )} />
        )

        return(
            <Container>
                <Switch>
                    <Route exact path='/login' render={ () => <Login props={this.props} childProps={childProps} />} />
                    <PrivateRoute exact path='/' component={Menu} />
                    <PrivateRoute exact path='/resource' component={Resource} />
                    <PrivateRoute exact path='/incident' component={Incident} />
                    <PrivateRoute exact path='/resource_status' component={Resource_status} />
                    <PrivateRoute exact path='/search' component={Search} />
                    <PrivateRoute exact path='/report' component={Report} />
                    <Route component={NotFound} />
                </Switch>
            </Container>
        )
    }
}

export default withRouter(ERMS);