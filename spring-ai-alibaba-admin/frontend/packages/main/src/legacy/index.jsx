import React from 'react';
import ReactDOM from 'react-dom/client';
import { HashRouter } from 'react-router-dom';
import 'antd/dist/reset.css'; // Ant Design styles
import App from './App';
import './styles/index.css';
import './styles/tailwind.css';
// Initialize global error handling
import './utils/requestInterceptors';

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  // <React.StrictMode>
    <HashRouter>
      <App />
    </HashRouter>
  // </React.StrictMode>
);
