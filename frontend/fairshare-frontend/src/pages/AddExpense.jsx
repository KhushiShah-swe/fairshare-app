import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import api from "../api/api";
import "./AddExpense.css";

export default function AddExpense() {
  const [amount, setAmount] = useState("");
  const [desc, setDesc] = useState("");
  const [groupId, setGroupId] = useState("");
  const [groups, setGroups] = useState([]);
  
  // New Fields
  const [category, setCategory] = useState("General");
  const [expenseDate, setExpenseDate] = useState(new Date().toISOString().split('T')[0]); // Default to today
  const [notes, setNotes] = useState("");
  
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const navigate = useNavigate();

  const user = JSON.parse(localStorage.getItem("user"));
  const userId = user?.id;

  const categories = ["General", "Food", "Travel", "Entertainment", "Shopping", "Rent", "Health"];

  useEffect(() => {
    if (!userId) {
      navigate("/login");
      return;
    }

    const fetchGroups = async () => {
      try {
        const res = await api.get(`/groups/user-groups/${userId}`);
        const userGroups = res.data.map(m => m.group);
        setGroups(userGroups);
        
        if (userGroups.length > 0 && !groupId) {
          setGroupId(userGroups[0].id);
        }
      } catch (err) {
        console.error("Error fetching groups:", err);
      }
    };

    fetchGroups();
  }, [userId, navigate, groupId]);

  const handleAddExpense = async (e) => {
    e.preventDefault();
    if (!groupId) {
        setError("Please select a group.");
        return;
    }

    setLoading(true);
    setError("");

    try {
      // Fetch all members of the chosen group
      const memberRes = await api.get(`/groups/group-members/${groupId}`);
      const allMemberIds = memberRes.data.map(member => member.user.id);

      const payload = {
        amount: parseFloat(amount),
        description: desc,
        groupId: parseInt(groupId),
        paidBy: userId, 
        participants: allMemberIds,
        // New payload fields
        category: category,
        expenseDate: expenseDate,
        notes: notes.trim()
      };

      await api.post("/expenses/add", payload);
      navigate("/dashboard");
    } catch (err) {
      console.error(err);
      setError("Failed to add expense. Check if you have group members.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="dashboard-wrapper">
      <div className="auth-card animate-fade-in" style={{ maxWidth: '600px', margin: '2rem auto' }}>
        <div className="auth-header">
          <h1>Add New Expense</h1>
          <p>The total will be split equally among all group members.</p>
        </div>

        <form onSubmit={handleAddExpense} className="auth-form">
          {error && <div className="error-message">⚠️ {error}</div>}

          {/* Title */}
          <div className="input-group">
            <label>Title</label>
            <input 
              placeholder="e.g. Pizza Night" 
              value={desc}
              onChange={e => setDesc(e.target.value)} 
              required 
            />
          </div>

          <div className="row" style={{ display: 'flex', gap: '15px' }}>
            {/* Amount */}
            <div className="input-group" style={{ flex: 1 }}>
              <label>Total Amount ($)</label>
              <input 
                type="number" 
                step="0.01"
                placeholder="0.00" 
                value={amount}
                onChange={e => setAmount(e.target.value)} 
                required 
              />
            </div>

            {/* Date */}
            <div className="input-group" style={{ flex: 1 }}>
              <label>Date</label>
              <input 
                type="date" 
                value={expenseDate}
                onChange={e => setExpenseDate(e.target.value)} 
                required 
              />
            </div>
          </div>

          <div className="row" style={{ display: 'flex', gap: '15px' }}>
             {/* Group Selection */}
            <div className="input-group" style={{ flex: 1 }}>
              <label>Group</label>
              <select 
                className="custom-select"
                value={groupId} 
                onChange={e => setGroupId(e.target.value)}
                required
              >
                <option value="" disabled>Select Group</option>
                {groups.map(g => (
                  <option key={g.id} value={g.id}>{g.name}</option>
                ))}
              </select>
            </div>

            {/* Category Selection */}
            <div className="input-group" style={{ flex: 1 }}>
              <label>Category</label>
              <select 
                value={category} 
                onChange={e => setCategory(e.target.value)}
              >
                {categories.map(cat => (
                  <option key={cat} value={cat}>{cat}</option>
                ))}
              </select>
            </div>
          </div>

          {/* Notes */}
          <div className="input-group">
            <label>Notes (Optional)</label>
            <textarea 
              value={notes} 
              onChange={e => setNotes(e.target.value)}
              placeholder="Add more details about this expense..."
              style={{ 
                width: '100%', 
                borderRadius: '8px', 
                padding: '10px', 
                border: '1px solid #e2e8f0', 
                minHeight: '80px',
                fontFamily: 'inherit'
              }}
            />
          </div>

          <div className="info-box">
             <small>FairShare will automatically calculate splits for all group members.</small>
          </div>

          <button type="submit" className="auth-button" disabled={loading || groups.length === 0}>
            {loading ? "Calculating Splits..." : "Add Expense"}
          </button>
          
          <button 
            type="button" 
            className="secondary-btn" 
            style={{ width: '100%', marginTop: '10px', background: 'white', color: '#64748b', border: '1px solid #e2e8f0' }}
            onClick={() => navigate("/dashboard")}
          >
            Cancel
          </button>
        </form>
      </div>
    </div>
  );
}