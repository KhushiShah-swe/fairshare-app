import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import api from "../api/api";
import logo from "../assets/logo.png";

export default function Login() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const navigate = useNavigate();

  const handleLogin = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError("");

    try {
      const response = await api.post("/auth/login", { email, password });
      localStorage.setItem("user", JSON.stringify(response.data));
      navigate("/dashboard");
    } catch (err) {
      setError(err.response?.data?.message || "Invalid email or password.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-container">
      <div className="auth-card animate-fade-in">
        {/* Everything inside this header is the ONLY place the logo will appear */}
        <div className="auth-header">
          <div className="logo-wrapper">
            <img 
              src={logo} 
              alt="FairShare Logo" 
              className="auth-logo" 
              style={{ width: '60px', height: '60px', objectFit: 'contain' }} 
            />
          </div>
          <h1>Ready to settle up?</h1>
          <p>Manage your shared expenses effortlessly.</p>
        </div>

        <form onSubmit={handleLogin} className="auth-form">
          {error && <div className="error-message">⚠️ {error}</div>}
          
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
            {loading ? <div className="spinner"></div> : "Sign In"}
          </button>
        </form>

        <div className="auth-footer">
          <p>Don't have an account? <Link to="/signup" className="accent-link">Create an account</Link></p>
        </div>
      </div>
    </div>
  );
}