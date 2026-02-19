import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { ToastContainer } from 'react-toastify'; 
import 'react-toastify/dist/ReactToastify.css'; 

import Navbar from "./components/Navbar";
import Login from './pages/Login';
import Signup from './pages/Signup';
import Dashboard from './pages/Dashboard';
import AddExpense from './pages/AddExpense';
import EditExpense from './pages/EditExpense';
import CreateGroup from './pages/CreateGroup'; 
import GroupDetails from './pages/GroupDetails';

function App() {
  return (
    <Router>
      <div className="container">
        <ToastContainer position="top-right" autoClose={3000} theme="light" />

        <Routes>
          {/* AUTH ROUTES */}
          <Route path="/" element={<Login />} />
          <Route path="/signup" element={<Signup />} />
          
          {/* MAIN APP ROUTES */}
          <Route path="/*" element={
            <>
              <Navbar />
              <Routes>
                <Route path="/dashboard" element={<Dashboard />} />
                <Route path="/add-expense" element={<AddExpense />} />
                <Route path="/edit-expense/:id" element={<EditExpense />} />
                <Route path="/create-group" element={<CreateGroup />} />
                
                {/* Redirecting "Groups" to Dashboard for now 
                    Individual groups still use /group/:groupId */}
                <Route path="/groups" element={<GroupDetails />} />
                <Route path="/group/:groupId" element={<GroupDetails />} />

                {/* Redirecting "Expenses" link to AddExpense as requested */}
                <Route path="/expenses" element={<AddExpense />} />
                
                {/* Catch-all: Redirect unknown paths to dashboard */}
                <Route path="*" element={<Navigate to="/dashboard" />} />
              </Routes>
            </>
          } />
        </Routes>
      </div>
    </Router>
  );
}

export default App;