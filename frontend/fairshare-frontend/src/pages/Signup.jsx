import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import api from "../api/api";
import logo from "../assets/logo.png";

export default function Signup() {
  // Use "name" to match your SignupRequest DTO in Java
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const navigate = useNavigate();

  const handleSignup = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError("");

    try {
      // Sending { name, email, password } to match your com.fairshare.dto.SignupRequest
      await api.post("/auth/signup", { name, email, password });
      
      // Redirect to login on success
      navigate("/", { state: { message: "Account created successfully!" } });
    } catch (err) {
      setError(err.response?.data?.message || "Registration failed. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-container">
      <div className="auth-card animate-fade-in">
        <div className="auth-header">
          <div className="logo-wrapper">
            <img 
              src={logo} 
              alt="FairShare Logo" 
              className="auth-logo" 
              style={{ width: '60px', height: '60px', objectFit: 'contain' }} 
            />
          </div>
          <h1>Create an Account</h1>
          <p>Join FairShare to start managing group expenses.</p>
        </div>

        <form onSubmit={handleSignup} className="auth-form">
          {error && <div className="error-message">⚠️ {error}</div>}
          
          <div className="input-group">
            <label>Full Name</label>
            <input 
              type="text" 
              placeholder="Enter your name" 
              value={name}
              onChange={(e) => setName(e.target.value)} 
              required 
            />
          </div>

          <div className="input-group">
            <label>Email Address</label>
            <input 
              type="email" 
              placeholder="name@company.com" 
              value={email}
              onChange={(e) => setEmail(e.target.value)} 
              required 
            />
          </div>

          <div className="input-group">
            <div className="label-row">
              <label>Password</label>
              <span 
                className="toggle-password" 
                onClick={() => setShowPassword(!showPassword)}
              >
                {showPassword ? "Hide" : "Show"}
              </span>
            </div>
            <input 
              type={showPassword ? "text" : "password"} 
              placeholder="••••••••" 
              value={password}
              onChange={(e) => setPassword(e.target.value)} 
              required 
            />
          </div>

          <button type="submit" className="auth-button" disabled={loading}>
            {loading ? <div className="spinner"></div> : "Sign Up"}
          </button>
        </form>

        <div className="auth-footer">
          <p>Already have an account? <Link to="/" className="accent-link">Sign in</Link></p>
        </div>
      </div>
    </div>
  );
}