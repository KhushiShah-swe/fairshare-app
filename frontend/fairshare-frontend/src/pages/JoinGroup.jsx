export default function JoinGroup() {
  const [code, setCode] = useState("");
  const [error, setError] = useState("");
  const navigate = useNavigate();

  const join = async () => {
    setError(""); 
    try {
      // res.data will be "Joined group successfully! Let's start sharing."
      const res = await api.post("/groups/join", null, { params: { code } });
      
      alert(res.data); 
      navigate("/dashboard"); 
    } catch (err) {
      // Pulls the specific message from your Java RuntimeException
      const message = err.response?.data || "Invalid invite code or you are already a member.";
      setError(message);
    }
  };

  return (
    <div className="dashboard-wrapper">
      <div className="glass-panel" style={{ maxWidth: "400px", margin: "4rem auto", textAlign: "center" }}>
        <h3>Join a Group</h3>
        {/* UPDATED: Changed 'digit' to 'character' to avoid confusion */}
        <p className="text-muted">Enter the 6-character invite code shared with you.</p>
        
        <div style={{ display: "flex", flexDirection: "column", gap: "1.2rem", marginTop: "1.5rem" }}>
          <input 
            className="custom-input"
            // type="text" is implicit, but ensures alphanumeric entry
            type="text" 
            style={{ textTransform: "uppercase", textAlign: "center", fontSize: "1.2rem", letterSpacing: "2px" }}
            placeholder="EXAMPL" // UPDATED: Purely alpha placeholder to show it's allowed
            maxLength={6}
            value={code}
            onChange={e => setCode(e.target.value.toUpperCase())} 
          />
          
          {error && <p className="text-error" style={{ fontSize: "0.9rem" }}>{error}</p>}
          
          <button className="primary-btn" onClick={join}>
            Join Group
          </button>
          
          <button className="action-link" onClick={() => navigate("/dashboard")}>
            Cancel
          </button>
        </div>
      </div>
    </div>
  );
}