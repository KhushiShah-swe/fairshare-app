import React from "react";
import { useNavigate } from "react-router-dom";
import logo from "../assets/logo.png";
import "./Navbar.css"; 

export default function Navbar() {
  const navigate = useNavigate();
  const user = JSON.parse(localStorage.getItem("user"));

  const handleLogout = () => {
    if (window.confirm("Are you sure you want to logout?")) {
      localStorage.removeItem("user");
      navigate("/");
    }
  };

  return (
    <nav className="navbar">
      <div className="nav-container">
        {/* Pushed to the LEFT corner */}
        <div className="nav-left" onClick={() => navigate("/dashboard")}>
          <img src={logo} alt="FairShare" className="nav-logo" />
          <span className="brand-name">FairShare</span>
        </div>
        
        {/* Pushed to the RIGHT corner */}
        <div className="nav-right">
          {user && (
            <button className="logout-btn" onClick={handleLogout}>
              Logout
            </button>
          )}
        </div>
      </div>
    </nav>
  );
}