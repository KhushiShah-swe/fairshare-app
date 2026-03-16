import React, { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Cell,
} from "recharts";
import api from "../api/api";
import { toast } from "react-toastify";
import "./Dashboard.css";

export default function Dashboard() {
  const [balances, setBalances] = useState({});
  const [activities, setActivities] = useState([]);
  const [memberships, setMemberships] = useState([]);
  const [loading, setLoading] = useState(true);

  const [showSettleModal, setShowSettleModal] = useState(false);
  const [settlementInstructions, setSettlementInstructions] = useState([]);

  const navigate = useNavigate();

  const user = JSON.parse(localStorage.getItem("user"));
  const userId = user?.id;
  const userName = user?.name || "there";

  const fetchDashboardData = async () => {
    try {
      if (!userId) return;
      setLoading(true);

      const [balanceRes, activityRes, groupsRes] = await Promise.all([
        api.get(`/expenses/balance/${userId}`),
        api.get(`/expenses/activity/${userId}`),
        api.get(`/groups/user-groups/${userId}`),
      ]);

      const membershipsData = groupsRes.data || [];

      const activeGroupIds = new Set(
        membershipsData
          .filter((m) => m?.group?.id != null)
          .map((m) => String(m.group.id))
      );

      const rawBalances = balanceRes.data || {};
      const filteredBalances = Object.fromEntries(
        Object.entries(rawBalances).filter(([groupId]) =>
          activeGroupIds.has(String(groupId))
        )
      );

      const rawActivities = activityRes.data || [];

      const filteredActivities = rawActivities.filter((act) => {
        const actGroupId = act.groupId || act.group?.id || act.group_id;
        if (!actGroupId) return true;
        return activeGroupIds.has(String(actGroupId));
      });

      setBalances(filteredBalances);
      setActivities(filteredActivities);
      setMemberships(membershipsData);
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

  const handleFinalSettle = async (groupId, amount) => {
    if (
      window.confirm(
        `Mark $${amount.toFixed(2)} as paid for this group? This will reset your balance.`
      )
    ) {
      try {
        await api.post(`/expenses/settle`, {
          groupId,
          amount,
          payerId: userId,
        });
        toast.success("Balance settled successfully!");
        setShowSettleModal(false);
        fetchDashboardData();
      } catch (err) {
        console.error("Settlement Error:", err);
        toast.error("Failed to record settlement.");
      }
    }
  };

  const getCategoryIcon = (cat) => {
    switch (cat?.toLowerCase()) {
      case "food":
        return "🍴";
      case "travel":
        return "✈️";
      case "entertainment":
        return "🎬";
      case "shopping":
        return "🛍️";
      case "rent":
        return "🏠";
      case "health":
        return "💊";
      default:
        return "💰";
    }
  };

  const getGroupIcon = (type) => {
    switch (type?.toLowerCase()) {
      case "household":
        return "🏠";
      case "trip":
        return "✈️";
      case "team":
        return "👥";
      default:
        return "🌟";
    }
  };

  const groupNameMap = useMemo(() => {
    const map = {};
    memberships.forEach((m) => {
      if (m?.group?.id != null) {
        map[String(m.group.id)] = m.group.name;
      }
    });
    return map;
  }, [memberships]);

  const memberNameMap = useMemo(() => {
    const map = {};

    memberships.forEach((m) => {
      if (m?.user?.id != null) {
        map[String(m.user.id)] =
          m.user.name ||
          m.user.fullName ||
          m.user.username ||
          m.user.email ||
          `User ${m.user.id}`;
      }

      if (Array.isArray(m?.group?.members)) {
        m.group.members.forEach((member) => {
          if (member?.id != null) {
            map[String(member.id)] =
              member.name ||
              member.fullName ||
              member.username ||
              member.email ||
              `User ${member.id}`;
          } else if (member?.user?.id != null) {
            map[String(member.user.id)] =
              member.user.name ||
              member.user.fullName ||
              member.user.username ||
              member.user.email ||
              `User ${member.user.id}`;
          }
        });
      }
    });

    return map;
  }, [memberships]);

  const resolvePayerName = (act) => {
    return (
      act.payerName ||
      act.paidByName ||
      act.actorName ||
      act.userName ||
      (act.payerId != null ? memberNameMap[String(act.payerId)] : null) ||
      (act.userId != null ? memberNameMap[String(act.userId)] : null) ||
      act.payer ||
      "Unknown User"
    );
  };

  const handleSettleUpClick = () => {
    const instructions = [];

    Object.entries(balances).forEach(([groupId, balance]) => {
      const groupName = groupNameMap[String(groupId)] || "Unknown Group";

      if (balance < 0) {
        instructions.push({
          groupId,
          groupName,
          text: `You owe members in ${groupName}`,
          amount: Math.abs(balance),
          type: "owe",
        });
      } else if (balance > 0) {
        instructions.push({
          groupId,
          groupName,
          text: `Members in ${groupName} owe you`,
          amount: balance,
          type: "owed",
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
        console.error(err);
        toast.error("Delete failed");
      }
    }
  };

  const chartData = Object.entries(balances)
    .filter(([groupId]) => groupNameMap[String(groupId)])
    .map(([groupId, value]) => ({
      groupId,
      name: groupNameMap[String(groupId)],
      amount: Number(value) || 0,
    }));

  const totalOwedToYou = Object.values(balances)
    .filter((v) => Number(v) > 0)
    .reduce((a, b) => a + Number(b), 0);

  const totalYouOwe = Math.abs(
    Object.values(balances)
      .filter((v) => Number(v) < 0)
      .reduce((a, b) => a + Number(b), 0)
  );

  const netBalance = totalOwedToYou - totalYouOwe;
  const totalGroups = memberships.length;
  const totalActivities = activities.length;

  if (loading) return <div className="loading-spinner">Loading FairShare...</div>;

  return (
    <div className="dashboard-wrapper">
      <div className="dashboard-content">
        <section className="hero-banner">
          <div className="hero-copy">
            <p className="hero-eyebrow">FairShare Dashboard</p>
            <h1>Welcome back, {userName}</h1>
            <p className="hero-subtext">
              Track what you are owed, what you owe, and manage your group expenses in one place.
            </p>
          </div>

          <div className="hero-pills">
            <div className="hero-pill">
              <span className="hero-pill-label">Groups</span>
              <strong>{totalGroups}</strong>
            </div>
            <div className="hero-pill">
              <span className="hero-pill-label">Recent Activity</span>
              <strong>{totalActivities}</strong>
            </div>
          </div>
        </section>

        <section className="stats-container">
          <div className="stat-card total">
            <div className="stat-icon">📊</div>
            <label>Net Balance</label>
            <h3 className={netBalance >= 0 ? "text-success" : "text-error"}>
              {netBalance >= 0
                ? `$${netBalance.toFixed(2)}`
                : `-$${Math.abs(netBalance).toFixed(2)}`}
            </h3>
            <p className="stat-helper">
              {netBalance >= 0 ? "Overall, you are in the positive." : "You currently owe more overall."}
            </p>
          </div>

          <div className="stat-card owed">
            <div className="stat-icon">💸 </div>
            <label>You Are Owed</label>
            <h3 className="text-success">${totalOwedToYou.toFixed(2)}</h3>
            <p className="stat-helper">Money others owe you across your active groups.</p>
          </div>

          <div className="stat-card owe">
            <div className="stat-icon">🚨</div>
            <label>You Owe</label>
            <h3 className="text-error">${totalYouOwe.toFixed(2)}</h3>
            <p className="stat-helper">Your current payable amount across active groups.</p>
          </div>
        </section>

        <div className="glass-panel chart-section chart-gradient-card">
          <div className="chart-header">
            <div>
              <h3>Balance Distribution</h3>
              <p className="chart-subtitle">
                Green bars show money owed to you. Red bars show money you owe.
              </p>
            </div>
            <div className="chart-badges">
              <span className="mini-badge positive">Owed</span>
              <span className="mini-badge negative">You owe</span>
            </div>
          </div>

          <div style={{ width: "100%", height: 320 }}>
            {chartData.length > 0 ? (
              <ResponsiveContainer>
                <BarChart data={chartData} barCategoryGap={28}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#eef2f7" />
                  <XAxis dataKey="name" axisLine={false} tickLine={false} />
                  <YAxis axisLine={false} tickLine={false} />
                  <Tooltip
                    cursor={{ fill: "rgba(15, 23, 42, 0.04)" }}
                    contentStyle={{
                      borderRadius: "14px",
                      border: "none",
                      boxShadow: "0 12px 24px rgba(0,0,0,0.10)",
                      background: "rgba(255,255,255,0.96)",
                    }}
                  />
                  <Bar dataKey="amount" radius={[10, 10, 0, 0]}>
                    {chartData.map((entry, index) => (
                      <Cell
                        key={`cell-${index}`}
                        fill={entry.amount >= 0 ? "#10b981" : "#ef4444"}
                      />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <p className="text-muted text-center">No balance data to display</p>
            )}
          </div>
        </div>

        <div className="main-grid">
          <div className="glass-panel groups-section">
            <div className="panel-header">
              <div>
                <h3>My Groups</h3>
                <p className="panel-subtext">Jump into your shared expense spaces.</p>
              </div>
              <button className="action-link" onClick={() => navigate("/create-group")}>
                + New Group
              </button>
            </div>

            <div className="group-list">
              {memberships.length > 0 ? (
                memberships.map((member) => {
                  const group = member.group;
                  if (!group) return null;

                  const groupBalance = balances[group.id] ?? 0;

                  return (
                    <div key={member.id} className="group-item">
                      <div className="group-info">
                        <div className="group-title-row">
                          <span className="group-icon-bubble">{getGroupIcon(group.type)}</span>
                          <div>
                            <strong>{group.name}</strong>
                            <div className="group-meta-row">
                              <span className="badge-role">{member.role}</span>
                              <span className="invite-label">Code: {group.inviteCode}</span>
                            </div>
                          </div>
                        </div>

                        <div
                          className={`group-balance-chip ${
                            groupBalance >= 0 ? "chip-positive" : "chip-negative"
                          }`}
                        >
                          {groupBalance >= 0 ? "Owed" : "You owe"} • $
                          {Math.abs(groupBalance).toFixed(2)}
                        </div>
                      </div>

                      <div className="group-actions">
                        <button
                          className="btn-view"
                          onClick={() => navigate(`/group/${group.id}`)}
                        >
                          Details
                        </button>
                      </div>
                    </div>
                  );
                })
              ) : (
                <div className="empty-state-card">
                  <div className="empty-state-icon">👥</div>
                  <h4>No groups yet</h4>
                  <p>Create your first group and start splitting expenses beautifully.</p>
                  <button className="primary-btn" onClick={() => navigate("/create-group")}>
                    Create Group
                  </button>
                </div>
              )}
            </div>
          </div>

          <div className="glass-panel activity-section">
            <div className="panel-header">
              <div>
                <h3>Recent Activity</h3>
                <p className="panel-subtext">Latest expense actions from your active groups.</p>
              </div>
            </div>

            <div className="activity-list">
              {activities.length > 0 ? (
                activities.map((act) => (
                  <div key={act.id} className="activity-item">
                    <div className="activity-left">
                      <div className="avatar">{getCategoryIcon(act.category)}</div>

                      <div className="details">
                        <p>
                          <strong>{resolvePayerName(act)}</strong> paid for{" "}
                          <strong>{act.desc || act.description || "expense"}</strong>
                        </p>
                        <small className="text-muted">
                          {act.date} {act.category ? `• ${act.category}` : ""}
                          {act.groupId && groupNameMap[String(act.groupId)]
                            ? ` • ${groupNameMap[String(act.groupId)]}`
                            : ""}
                        </small>
                        <p className="activity-amount">
                          ${Number(act.amount || 0).toFixed(2)}
                        </p>
                      </div>
                    </div>

                    <div className="activity-actions">
                      <button
                        className="icon-btn"
                        title="Edit"
                        onClick={() => navigate(`/edit-expense/${act.id}`)}
                      >
                        ✏️
                      </button>
                      <button
                        className="icon-btn danger"
                        title="Delete"
                        onClick={() => handleDeleteExpense(act.id)}
                      >
                        🗑️
                      </button>
                    </div>
                  </div>
                ))
              ) : (
                <div className="empty-state-card compact">
                  <div className="empty-state-icon">🧾</div>
                  <h4>No recent activity</h4>
                  <p>Your expense actions will appear here once you start adding entries.</p>
                </div>
              )}
            </div>
          </div>
        </div>

        {showSettleModal && (
          <div className="modal-overlay" onClick={() => setShowSettleModal(false)}>
            <div className="settle-modal-card" onClick={(e) => e.stopPropagation()}>
              <div className="panel-header">
                <div>
                  <h3>Settle Up Summary</h3>
                  <p className="panel-subtext">
                    Review your balances and mark payments as completed.
                  </p>
                </div>
                <button className="icon-btn" onClick={() => setShowSettleModal(false)}>
                  ✕
                </button>
              </div>

              <div className="settle-instructions">
                {settlementInstructions.length > 0 ? (
                  settlementInstructions.map((ins, i) => (
                    <div key={i} className="settle-row">
                      <div className="settle-row-left">
                        <p className="settle-title">{ins.text}</p>
                        <h4 className={ins.type === "owed" ? "text-success" : "text-error"}>
                          {ins.type === "owed" ? "+" : "-"} ${ins.amount.toFixed(2)}
                        </h4>
                      </div>

                      <div className="settle-row-actions">
                        {ins.type === "owe" && (
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
                  <div className="empty-state-card compact">
                    <div className="empty-state-icon">🎉</div>
                    <h4>You are all settled up</h4>
                    <p>No pending balances across your active groups.</p>
                  </div>
                )}
              </div>

              <button
                className="secondary-btn settle-close-btn"
                onClick={() => setShowSettleModal(false)}
              >
                Close
              </button>
            </div>
          </div>
        )}

        <div className="quick-actions-bar">
          <button className="primary-btn" onClick={() => navigate("/add-expense")}>
            Add Expense
          </button>
          <button className="secondary-btn" onClick={handleSettleUpClick}>
            Settle Up
          </button>
        </div>
      </div>
    </div>
  );
}