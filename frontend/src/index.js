import React from 'react';
import ReactDOM from 'react-dom';
import './index.css';
import ERMS from './ERMS';
import { BrowserRouter } from 'react-router-dom';

ReactDOM.render(
  <BrowserRouter>
    <ERMS />
  </BrowserRouter>
  , document.getElementById('root')
);
