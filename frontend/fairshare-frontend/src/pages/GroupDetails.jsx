import React, { useEffect, useState, useCallback } from "react";
import { useParams, useNavigate } from "react-router-dom";
import api from "../api/api";
import { toast } from "react-toastify";
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from "recharts";
import "./Dashboard.css"; 

export default function GroupDetails() {
  const { groupId } = useParams();
  const [members, setMembers] = useState([]);
  const [expenses, setExpenses] = useState([]);
  const [groupInfo, setGroupInfo] = useState(null);
  const [loading, setLoading] = useState(true);
  const [currentUserRole, setCurrentUserRole] = useState("MEMBER");
  const [isEditingName, setIsEditingName] = useState(false);
  const [newName, setNewName] = useState("");
  const navigate = useNavigate();

  const user = JSON.parse(localStorage.getItem("user") || "{}");
  const currentUserId = String(user?.id || user?._id || "");

  const fetchDetails = useCallback(async () => {
    try {
      // 1. Fetch Members and Group Info
      const memberRes = await api.get(`/groups/group-members/${groupId}`);
      if (memberRes.data && memberRes.data.length > 0) {
        setMembers(memberRes.data);
        setGroupInfo(memberRes.data[0].group);
        setNewName(memberRes.data[0].group.name);

        const memberRecord = memberRes.data.find(m => 
          String(m.user.id || m.user._id) === currentUserId
        );
        if (memberRecord) setCurrentUserRole(memberRecord.role);
      }

      // 2. Fetch Expenses
      const expenseRes = await api.get(`/expenses/group/${groupId}`);
      setExpenses(expenseRes.data);
    } catch (err) {
      console.error("Error fetching details:", err);
      toast.error("Failed to load group details.");
    } finally {
      setLoading(false);
    }
  }, [groupId, currentUserId]);

  useEffect(() => {
    fetchDetails();
  }, [fetchDetails]);

  // --- SETTINGS: UPDATE GROUP NAME ---
  const handleUpdateName = async () => {
    if (!newName.trim()) {
      toast.error("Name cannot be empty");
      return;
    }
    try {
      const res = await api.put(`/groups/${groupId}/update-name`, null, { 
        params: { newName: newName.trim(), userId: currentUserId } 
      });
      setGroupInfo(res.data);
      setIsEditingName(false);
      toast.success("Group name updated!");
    } catch (err) {
      toast.error(err.response?.data || "Failed to update name.");
    }
  };

  // --- SETTINGS: DELETE GROUP ---
  const handleDeleteGroup = async () => {
    if (window.confirm("CRITICAL: Delete this group? This will erase all history for all members.")) {
      try {
        await api.delete(`/groups/${groupId}/delete`, { 
          params: { userId: currentUserId } 
        });
        toast.success("Group permanently deleted.");
        navigate("/dashboard");
      } catch (err) {
        toast.error(err.response?.data || "Error deleting group.");
      }
    }
  };

  // --- CSV EXPORT ---
  const handleDownloadReport = () => {
    const headers = ["Date", "Description", "Category", "Amount", "Paid By"];
    const rows = expenses.map(exp => [
      new Date(exp.expenseDate || Date.now()).toLocaleDateString(),
      `"${exp.description}"`,
      exp.category || "General",
      exp.amount.toFixed(2),
      getPayerName(exp) // Use the helper to get the real name for the CSV
    ]);
    const csvContent = [headers.join(","), ...rows.map(r => r.join(","))].join("\n");
    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = `${groupInfo?.name}_Report.csv`;
    link.click();
  };

  // --- HELPERS: NAME MAPPING ---
  const getPayerName = (exp) => {
    if (!exp.paidBy) return "Unknown";
    
    // Normalize ID to string (handles object {id: 1} or raw number 1)
    const payerId = String(exp.paidBy.id || exp.paidBy);
    
    if (payerId === currentUserId) return "You";
    
    // Search the members array to find the user with the matching ID
    const foundMember = members.find(m => String(m.user.id || m.user._id) === payerId);
    
    // Return the name if found, otherwise fallback to "User ID"
    return foundMember ? foundMember.user.name : `User ${payerId}`;
  };

  // --- CALCULATE BALANCES ---
  const totalGroupSpending = expenses.reduce((sum, exp) => sum + (exp.amount || 0), 0);
  const fairShare = members.length > 0 ? totalGroupSpending / members.length : 0;

  const balances = members.map(m => {
    const memberId = String(m.user.id || m.user._id);
    let netBalance = 0;

    expenses.forEach(exp => {
      const amount = exp.amount || 0;
      const share = members.length > 0 ? amount / members.length : 0;
      netBalance -= share; // Debit every member their share

      const payerId = String(exp.paidBy?.id || exp.paidBy);
      if (payerId === memberId) {
        netBalance += amount; // Credit the payer back the full amount
      }
    });

    return {
      displayName: memberId === currentUserId ? "You" : (m.user.name || "Member"),
      userId: memberId,
      balance: netBalance
    };
  }).sort((a, b) => b.balance - a.balance);

  const categoryData = expenses.reduce((acc, exp) => {
    const catName = exp.category || "General";
    const existing = acc.find(item => item.name === catName);
    if (existing) { existing.value += exp.amount; } 
    else { acc.push({ name: catName, value: exp.amount }); }
    return acc;
  }, []);

  const COLORS = ["#6366f1", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6", "#ec4899"];
  const isAdmin = currentUserRole === "ADMIN";

  if (loading) return <div className="loading-spinner">Analyzing group finances...</div>;

  return (
    <div className="dashboard-wrapper">
      <div className="dashboard-content">
        
        {/* TOP ACTION BAR */}
        <div style={{ paddingBottom: '20px', display: 'flex', justifyContent: 'space-between' }}>
          <button className="secondary-btn" onClick={() => navigate("/dashboard")}>← Back</button>
          <div style={{ display: 'flex', gap: '10px' }}>
            <button className="secondary-btn" onClick={handleDownloadReport}>📥 Report</button>
            <button className="primary-btn" onClick={() => navigate("/add-expense")}>+ Expense</button>
          </div>
        </div>

        {/* HEADER & EDIT NAME SECTION */}
        <div className="panel-header">
          <div style={{ flex: 1 }}>
            {isEditingName && isAdmin ? (
              <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
                <input 
                  className="custom-input" 
                  value={newName} 
                  onChange={(e) => setNewName(e.target.value)}
                  style={{ fontSize: '1.5rem', fontWeight: 'bold', width: '300px' }}
                  autoFocus
                />
                <button className="primary-btn" onClick={handleUpdateName}>Save</button>
                <button className="secondary-btn" onClick={() => setIsEditingName(false)}>Cancel</button>
              </div>
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <h1>{groupInfo?.name}</h1>
                {isAdmin && (
                  <button className="icon-btn" onClick={() => setIsEditingName(true)} title="Rename Group">✏️</button>
                )}
              </div>
            )}
            <p className="text-muted">Invite Code: <strong style={{color: '#2563eb'}}>{groupInfo?.inviteCode}</strong></p>
          </div>
          <span className={`badge-role ${isAdmin ? 'admin' : ''}`}>{currentUserRole}</span>
        </div>

        {/* STATS ROW */}
        <div className="group-stats-row">
          <div className="total-spending-card">
            <div className="spending-content">
              <label>Group Total Spending</label>
              <div className="amount-wrapper">
                <span className="currency-symbol">$</span>
                <h1>{totalGroupSpending.toLocaleString(undefined, { minimumFractionDigits: 2 })}</h1>
              </div>
              <div className="spending-footer">
                <strong>Fair Share:</strong> ${fairShare.toFixed(2)} / person
              </div>
            </div>
          </div>

          <div className="glass-panel chart-panel">
            <div style={{ width: '100%', height: '180px' }}>
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={categoryData} cx="50%" cy="50%" innerRadius={45} outerRadius={65} paddingAngle={5} dataKey="value">
                    {categoryData.map((_, index) => <Cell key={index} fill={COLORS[index % COLORS.length]} />)}
                  </Pie>
                  <Tooltip formatter={(val) => `$${val.toFixed(2)}`} />
                </PieChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>

        <div className="main-grid">
          {/* LEFT: HISTORY */}
          <div className="glass-panel">
            <h3>Expense History</h3>
            <div className="expense-list" style={{ marginTop: '1rem' }}>
              {expenses.length === 0 ? <p className="text-muted">No expenses found.</p> : expenses.map((exp) => (
                <div key={exp.id} className="activity-item" style={{ justifyContent: 'space-between' }}>
                  <div>
                    <strong>{exp.description}</strong>
                    <p className="text-muted">Paid by {getPayerName(exp)} • ${exp.amount?.toFixed(2)}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* RIGHT: BALANCES & SETTINGS */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <div className="glass-panel">
              <h3>Balance Summary</h3>
              <div className="settle-list" style={{ marginTop: '1rem' }}>
                {balances.map(b => (
                  <div key={b.userId} style={{ display: 'flex', justifyContent: 'space-between', padding: '12px 0', borderBottom: '1px solid #f1f5f9' }}>
                    <span style={{fontWeight: b.userId === currentUserId ? '700' : '400'}}>{b.displayName}</span>
                    <span style={{ fontWeight: '700', color: b.balance >= 0 ? '#10b981' : '#ef4444' }}>
                      {b.balance >= 0 ? `+ $${b.balance.toFixed(2)}` : `- $${Math.abs(b.balance).toFixed(2)}`}
                    </span>
                  </div>
                ))}
              </div>
            </div>

            <div className="glass-panel">
              <h3>Group Settings</h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginTop: '1rem' }}>
                {isAdmin && (
                  <button className="secondary-btn" onClick={() => setIsEditingName(true)}>✏️ Rename Group</button>
                )}
                <button className="secondary-btn" onClick={() => navigate("/dashboard")}>Exit to Dashboard</button>
                {isAdmin && (
                  <button 
                    className="secondary-btn" 
                    onClick={handleDeleteGroup}
                    style={{ color: '#ef4444', backgroundColor: '#fef2f2', marginTop: '10px' }}
                  >
                    🗑️ Delete Group
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}