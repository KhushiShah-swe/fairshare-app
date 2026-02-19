import { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import api from "../api/api";
import "./AddExpense.css"; 

export default function EditExpense() {
  const { id } = useParams(); 
  const navigate = useNavigate();

  // Updated state to include new fields
  const [amount, setAmount] = useState("");
  const [desc, setDesc] = useState("");
  const [category, setCategory] = useState("General");
  const [expenseDate, setExpenseDate] = useState("");
  const [notes, setNotes] = useState("");
  
  const [groupId, setGroupId] = useState("");
  const [participants, setParticipants] = useState([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  const user = JSON.parse(localStorage.getItem("user"));
  const userId = user?.id;

  const categories = ["General", "Food", "Travel", "Entertainment", "Shopping", "Rent", "Health"];

  useEffect(() => {
    const fetchExpenseData = async () => {
      try {
        const res = await api.get(`/expenses/${id}`);
        const exp = res.data;
        
        setAmount(exp.amount);
        setDesc(exp.description);
        setGroupId(exp.groupId);
        setCategory(exp.category || "General");
        setNotes(exp.notes || "");
        
        // Ensure date is formatted for HTML input (YYYY-MM-DD)
        if (exp.expenseDate) {
          setExpenseDate(exp.expenseDate);
        }

        const memberRes = await api.get(`/groups/group-members/${exp.groupId}`);
        const memberIds = memberRes.data.map(m => m.user.id);
        setParticipants(memberIds);
        
        setLoading(false);
      } catch (err) {
        console.error("Error loading expense:", err);
        setError("Failed to load expense details.");
        setLoading(false);
      }
    };

    fetchExpenseData();
  }, [id]);

  const handleUpdate = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError("");

    try {
      const payload = {
        amount: parseFloat(amount),
        description: desc.trim(),
        groupId: parseInt(groupId),
        paidBy: userId,
        participants: participants,
        category: category,
        expenseDate: expenseDate,
        notes: notes.trim()
      };

      await api.put(`/expenses/edit/${id}`, payload);
      navigate(-1); 
    } catch (err) {
      setError("Update failed. Please try again.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async () => {
    if (window.confirm("Are you sure you want to delete this expense?")) {
      try {
        await api.delete(`/expenses/delete/${id}`);
        navigate(-1);
      } catch (err) {
        setError("Delete failed.");
      }
    }
  };

  if (loading) return <div className="loading">Loading expense data...</div>;

  return (
    <div className="dashboard-wrapper">
      <div className="auth-card animate-fade-in" style={{ maxWidth: '600px', margin: '2rem auto' }}>
        <div className="auth-header">
          <h1>Edit Expense</h1>
          <p>Update the transaction details.</p>
        </div>

        <form onSubmit={handleUpdate} className="auth-form">
          {error && <div className="error-message">⚠️ {error}</div>}

          {/* Title / Description */}
          <div className="input-group">
            <label>Title</label>
            <input 
              type="text"
              placeholder="e.g., Dinner at Mario's"
              value={desc}
              onChange={e => setDesc(e.target.value)} 
              required 
            />
          </div>

          <div className="row" style={{ display: 'flex', gap: '15px' }}>
            {/* Amount */}
            <div className="input-group" style={{ flex: 1 }}>
              <label>Amount ($)</label>
              <input 
                type="number" 
                step="0.01"
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

          <div className="input-group">
            <label>Category</label>
            <select value={category} onChange={e => setCategory(e.target.value)}>
              {categories.map(cat => (
                <option key={cat} value={cat}>{cat}</option>
              ))}
            </select>
          </div>

          <div className="input-group">
            <label>Notes (Optional)</label>
            <textarea 
              value={notes} 
              onChange={e => setNotes(e.target.value)}
              placeholder="Add more details..."
              style={{ width: '100%', borderRadius: '8px', padding: '10px', border: '1px solid #e2e8f0', minHeight: '80px' }}
            />
          </div>

          <div className="info-box" style={{ background: '#f8fafc', padding: '10px', borderRadius: '8px', marginBottom: '15px' }}>
             <small>Splitting between {participants.length} group members.</small>
          </div>

          <button type="submit" className="auth-button" disabled={submitting}>
            {submitting ? "Updating..." : "Save Changes"}
          </button>

          <button 
            type="button" 
            className="delete-btn-outline"
            style={{ 
              width: '100%', 
              marginTop: '10px', 
              background: '#fee2e2', 
              color: '#dc2626', 
              border: 'none',
              padding: '0.75rem',
              borderRadius: '8px',
              fontWeight: 'bold',
              cursor: 'pointer'
            }}
            onClick={handleDelete}
          >
            Delete Expense
          </button>
          
          <button 
            type="button" 
            className="secondary-btn" 
            style={{ width: '100%', marginTop: '10px', background: 'white', color: '#64748b', border: '1px solid #cbd5e1' }}
            onClick={() => navigate(-1)}
          >
            Cancel
          </button>
        </form>
      </div>
    </div>
  );
}