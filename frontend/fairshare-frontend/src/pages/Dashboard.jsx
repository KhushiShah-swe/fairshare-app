import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from 'recharts';
import api from "../api/api"; 
import { toast } from "react-toastify";
import "./Dashboard.css";

export default function Dashboard() {
  const [balances, setBalances] = useState({});
  const [activities, setActivities] = useState([]);
  const [memberships, setMemberships] = useState([]); 
  const [loading, setLoading] = useState(true);
  
  // New State for Settle Up Modal
  const [showSettleModal, setShowSettleModal] = useState(false);
  const [settlementInstructions, setSettlementInstructions] = useState([]);

  const navigate = useNavigate();

  const user = JSON.parse(localStorage.getItem("user"));
  const userId = user?.id;

  const fetchDashboardData = async () => {
    try {
      if (!userId) return;
      setLoading(true);
      const [balanceRes, activityRes, groupsRes] = await Promise.all([
        api.get(`/expenses/balance/${userId}`),
        api.get(`/expenses/activity/${userId}`),
        api.get(`/groups/user-groups/${userId}`)
      ]);

      setBalances(balanceRes.data || {});
      setActivities(activityRes.data || []);
      setMemberships(groupsRes.data || []); 
    } catch (err) {
      console.error("Dashboard Fetch Error:", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!userId) {
      navigate("/login");
      return;
    }
    fetchDashboardData();
  }, [userId, navigate]);

  // NEW: Logic to record a settlement in the database
  const handleFinalSettle = async (groupId, amount) => {
    if (window.confirm(`Mark $${amount.toFixed(2)} as paid for this group? This will reset your balance.`)) {
      try {
        await api.post(`/expenses/settle`, {
          groupId: groupId,
          amount: amount,
          payerId: userId
        });
        toast.success("Balance settled successfully!");
        setShowSettleModal(false);
        fetchDashboardData(); // Refresh to show $0.00 balance
      } catch (err) {
        console.error("Settlement Error:", err);
        toast.error("Failed to record settlement.");
      }
    }
  };

  // Logic for Settle Up calculations
  const handleSettleUpClick = () => {
    const instructions = [];
    
    // Iterate through group balances to create actionable items
    Object.entries(balances).forEach(([groupId, balance]) => {
      const groupMatch = memberships.find(m => String(m.group?.id) === String(groupId));
      const groupName = groupMatch ? groupMatch.group.name : "Unknown Group";

      if (balance < 0) {
        instructions.push({
          groupId,
          groupName,
          text: `You owe members in ${groupName}`,
          amount: Math.abs(balance),
          type: 'owe'
        });
      } else if (balance > 0) {
        instructions.push({
          groupId,
          groupName,
          text: `Members in ${groupName} owe you`,
          amount: balance,
          type: 'owed'
        });
      }
    });

    setSettlementInstructions(instructions);
    setShowSettleModal(true);
  };

  const handleDeleteExpense = async (id) => {
    if (window.confirm("Are you sure you want to delete this expense for everyone?")) {
      try {
        await api.delete(`/expenses/delete/${id}`);
        toast.success("Expense deleted");
        fetchDashboardData(); 
      } catch (err) {
        toast.error("Delete failed");
      }
    }
  };

  const getCategoryIcon = (cat) => {
    switch (cat?.toLowerCase()) {
      case 'food': return '🍴';
      case 'travel': return '✈️';
      case 'entertainment': return '🎬';
      case 'shopping': return '🛍️';
      case 'rent': return '🏠';
      case 'health': return '💊';
      default: return '💰';
    }
  };

  const getGroupIcon = (type) => {
    switch (type?.toLowerCase()) {
      case 'household': return '🏠';
      case 'trip': return '✈️';
      case 'team': return '👥';
      default: return '🌟';
    }
  };

  const chartData = Object.entries(balances).map(([key, value]) => {
    const groupMatch = memberships.find(m => String(m.group?.id) === String(key));
    return {
      name: groupMatch ? groupMatch.group.name : `Group ${key}`,
      amount: value,
    };
  });

  const totalOwedToYou = Object.values(balances).filter(v => v > 0).reduce((a, b) => a + b, 0);
  const totalYouOwe = Math.abs(Object.values(balances).filter(v => v < 0).reduce((a, b) => a + b, 0));
  const netBalance = totalOwedToYou - totalYouOwe;

  if (loading) return <div className="loading-spinner">Loading FairShare...</div>;

  return (
    <div className="dashboard-wrapper">
      <div className="dashboard-content">
        
        {/* Stats Section */}
        <section className="stats-container">
          <div className="stat-card total">
            <label>Net Balance</label>
            <h3 className={netBalance >= 0 ? "text-success" : "text-error"}>
              {netBalance >= 0 ? `$${netBalance.toFixed(2)}` : `-$${Math.abs(netBalance).toFixed(2)}`}
            </h3>
          </div>
          <div className="stat-card owed">
            <label>You are owed</label>
            <h3 className="text-success">${totalOwedToYou.toFixed(2)}</h3>
          </div>
          <div className="stat-card owe">
            <label>You owe</label>
            <h3 className="text-error">${totalYouOwe.toFixed(2)}</h3>
          </div>
        </section>

        {/* Balance Distribution Graph */}
        <div className="glass-panel chart-section">
          <h3>Balance Distribution</h3>
          <div style={{ width: '100%', height: 300 }}>
            {chartData.length > 0 ? (
              <ResponsiveContainer>
                <BarChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f0f0f0" />
                  <XAxis dataKey="name" axisLine={false} tickLine={false} />
                  <YAxis axisLine={false} tickLine={false} />
                  <Tooltip 
                    cursor={{fill: 'rgba(0,0,0,0.05)'}}
                    contentStyle={{ borderRadius: '10px', border: 'none', boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }}
                  />
                  <Bar dataKey="amount" radius={[4, 4, 0, 0]}>
                    {chartData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={entry.amount >= 0 ? '#10b981' : '#ef4444'} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            ) : <p className="text-muted text-center">No balance data to display</p>}
          </div>
        </div>

        <div className="main-grid">
          {/* My Groups List */}
          <div className="glass-panel groups-section">
            <div className="panel-header">
              <h3>My Groups</h3>
              <button className="action-link" onClick={() => navigate("/create-group")}>+ New Group</button>
            </div>
            <div className="group-list">
              {memberships.map((member) => {
                const group = member.group;
                if (!group) return null;
                const groupBalance = balances[group.id] ?? 0;

                return (
                  <div key={member.id} className="group-item">
                    <div className="group-info">
                      <div className="group-title-row">
                        <span className="group-icon">{getGroupIcon(group.type)}</span>
                        <strong>{group.name}</strong>
                        <span className="badge-role">{member.role}</span>
                      </div>
                      <p className={groupBalance >= 0 ? 'text-success' : 'text-error'}>
                        {groupBalance >= 0 ? 'Owed' : 'You owe'}: ${Math.abs(groupBalance).toFixed(2)}
                      </p>
                    </div>
                    <div className="group-actions">
                        <small className="invite-label">Code: {group.inviteCode}</small>
                        <button className="btn-view" onClick={() => navigate(`/group/${group.id}`)}>Details</button>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Activity Section */}
          <div className="glass-panel activity-section">
            <h3>Recent Activity</h3>
            <div className="activity-list">
              {activities.length > 0 ? activities.map((act) => (
                <div key={act.id} className="activity-item" style={{ justifyContent: 'space-between' }}>
                  <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
                    <div className="avatar">{getCategoryIcon(act.category)}</div>
                    <div className="details">
                      <p><strong>{act.payer}</strong> paid for <strong>{act.desc}</strong></p>
                      <small className="text-muted">{act.date} • {act.category}</small>
                      <p className="activity-amount">${act.amount.toFixed(2)}</p>
                    </div>
                  </div>
                  
                  <div className="activity-actions" style={{ display: 'flex', gap: '5px' }}>
                    <button 
                      className="icon-btn" 
                      title="Edit"
                      onClick={() => navigate(`/edit-expense/${act.id}`)}
                    >✏️</button>
                    <button 
                      className="icon-btn" 
                      title="Delete"
                      style={{ color: '#ef4444' }}
                      onClick={() => handleDeleteExpense(act.id)}
                    >🗑️</button>
                  </div>
                </div>
              )) : <p className="text-muted">No recent activity</p>}
            </div>
          </div>
        </div>

        {/* Settle Up Overlay / Modal */}
        {showSettleModal && (
          <div className="modal-overlay" onClick={() => setShowSettleModal(false)}>
            <div className="glass-panel modal-content" onClick={e => e.stopPropagation()}>
              <div className="panel-header">
                <h3>Settle Up Summary</h3>
                <button className="icon-btn" onClick={() => setShowSettleModal(false)}>✕</button>
              </div>
              <div className="settle-instructions" style={{ marginTop: '20px' }}>
                {settlementInstructions.length > 0 ? (
                  settlementInstructions.map((ins, i) => (
                    <div key={i} className="activity-item" style={{ marginBottom: '15px', borderBottom: '1px solid #eee', paddingBottom: '10px' }}>
                      <div className="details">
                        <p>{ins.text}</p>
                        <h4 className={ins.type === 'owed' ? 'text-success' : 'text-error'}>
                          {ins.type === 'owed' ? '+' : '-'} ${ins.amount.toFixed(2)}
                        </h4>
                      </div>
                      <div style={{ display: 'flex', gap: '10px' }}>
                        {ins.type === 'owe' && (
                          <button 
                            className="primary-btn sm"
                            onClick={() => handleFinalSettle(ins.groupId, ins.amount)}
                          >
                            Mark Paid
                          </button>
                        )}
                        <button 
                          className="secondary-btn sm" 
                          onClick={() => navigate(`/group/${ins.groupId}`)}
                        >
                          View
                        </button>
                      </div>
                    </div>
                  ))
                ) : (
                  <div style={{ textAlign: 'center', padding: '20px' }}>
                    <p>🎉 You are all settled up in every group!</p>
                  </div>
                )}
              </div>
              <button 
                className="secondary-btn" 
                style={{ width: '100%', marginTop: '10px' }}
                onClick={() => setShowSettleModal(false)}
              >
                Close
              </button>
            </div>
          </div>
        )}

        <div className="quick-actions-bar">
           <button className="primary-btn" onClick={() => navigate("/add-expense")}>Add Expense</button>
           <button className="secondary-btn" onClick={handleSettleUpClick}>Settle Up</button>
        </div>
      </div>
    </div>
  );
}