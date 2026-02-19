import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../api/api";
import { toast } from 'react-toastify'; // Import for FairNotifications
import 'react-toastify/dist/ReactToastify.css';
import "./Dashboard.css"; 

export default function CreateGroup() {
  const [name, setName] = useState("");
  const [type, setType] = useState("Household"); 
  const [inviteCode, setInviteCode] = useState("");
  const [joiningCode, setJoiningCode] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  // Retrieve user ID from local storage for the API call
  const user = JSON.parse(localStorage.getItem("user"));
  const userId = user?.id;

  // 1. Create a New Group
  const handleCreate = async () => {
    if (!name.trim()) return toast.warn("Please enter a group name");
    if (!userId) return toast.error("User session not found. Please log in again.");

    setLoading(true);
    try {
      const res = await api.post("/groups/create", null, { 
        params: { 
          name: name.trim(),
          type: type,
          userId: userId
        } 
      });
      
      const code = res.data.inviteCode; 
      setInviteCode(code);
      
      toast.success(`Group "${name}" created successfully!`);
    } catch (err) {
      console.error("Create Group Error:", err);
      toast.error(err.response?.data || "Failed to create group.");
    } finally {
      setLoading(false);
    }
  };

  // 2. Join via Existing Code
  const handleJoin = async () => {
    if (!joiningCode.trim()) return toast.warn("Please enter an invite code");
    if (!userId) return toast.error("User session not found. Please log in again.");

    setLoading(true);
    try {
      // Sends the code to your updated joinGroup service
      const res = await api.post("/groups/join", null, { 
        params: { 
          code: joiningCode.trim().toUpperCase(),
          userId: userId
        } 
      });
      
      toast.success(res.data || "Welcome to the group!");
      navigate("/dashboard"); 
    } catch (err) {
      console.error("Join Group Error:", err);
      
      // CAPTURES THE SPECIFIC ERROR FROM YOUR GroupService:
      // e.g., "Invalid invite code. Please check and try again."
      const errorMessage = err.response?.data || "An unexpected error occurred.";
      toast.error(errorMessage);
      
      // Clear input on error to help user retry
      setJoiningCode(""); 
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="dashboard-wrapper">
      <div className="dashboard-content">
        <h2 className="page-title">Manage Groups</h2>
        
        <div className="main-grid">
          
          {/* Create Group Panel */}
          <div className="glass-panel">
            <div className="panel-header">
               <h3>Create New Group</h3>
            </div>
            <p className="text-muted">Start a fresh circle for expenses.</p>
            
            <div className="form-group" style={{ marginTop: "1rem" }}>
              <label className="input-label">Group Name</label>
              <input 
                className="custom-input"
                placeholder="e.g. Flatmates 2024" 
                value={name}
                onChange={e => setName(e.target.value)} 
              />
              
              <label className="input-label" style={{ marginTop: "1rem" }}>Group Type</label>
              <select 
                className="custom-input" 
                value={type} 
                onChange={e => setType(e.target.value)}
                style={{ appearance: "auto", width: "100%" }}
              >
                <option value="Household">🏠 Household</option>
                <option value="Trip">✈️ Trip</option>
                <option value="Team">👥 Team</option>
                <option value="Other">🌟 Other</option>
              </select>

              <button 
                className="primary-btn" 
                onClick={handleCreate}
                disabled={loading}
                style={{ marginTop: "1.5rem", width: "100%" }}
              >
                {loading ? "Processing..." : "Create Group"}
              </button>
            </div>
            
            {inviteCode && (
              <div className="invite-box animate-fade-in" style={{ marginTop: "1.5rem" }}>
                <p>Your Invite Code:</p>
                <h2 style={{ letterSpacing: "2px" }}>{inviteCode}</h2>
                <small>Share this with friends so they can join!</small>
              </div>
            )}
          </div>

          {/* Join Group Panel */}
          <div className="glass-panel">
            <div className="panel-header">
               <h3>Join a Group</h3>
            </div>
            <p className="text-muted">Have a code from a friend?</p>
            <div className="form-group" style={{ marginTop: "1rem" }}>
              <label className="input-label">Invite Code</label>
              <input 
                className="custom-input"
                placeholder="Enter 6-digit code" 
                value={joiningCode}
                onChange={e => setJoiningCode(e.target.value.toUpperCase())} 
              />
              <button 
                className="secondary-btn" 
                onClick={handleJoin}
                disabled={loading}
                style={{ marginTop: "1.5rem", width: "100%" }}
              >
                {loading ? "Joining..." : "Join Group"}
              </button>
            </div>
          </div>

        </div>
        
        <div style={{ textAlign: 'center', marginTop: '2rem' }}>
          <button 
            className="action-link" 
            onClick={() => navigate("/dashboard")}
            style={{ border: "none", background: "none", cursor: "pointer", color: "#6366f1", fontWeight: "600" }}
          >
            ← Back to Dashboard
          </button>
        </div>
      </div>
    </div>
  );
}