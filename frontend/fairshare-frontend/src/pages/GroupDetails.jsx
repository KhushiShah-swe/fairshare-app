import React, { useEffect, useState, useCallback } from "react";
import { useParams, useNavigate } from "react-router-dom";
import api from "../api/api";
import { toast } from "react-toastify";
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from "recharts";
import "./Dashboard.css";

export default function GroupDetails() {
  const { groupId } = useParams();
  const navigate = useNavigate();

  const [members, setMembers] = useState([]);
  const [expenses, setExpenses] = useState([]);
  const [groupInfo, setGroupInfo] = useState(null);
  const [loading, setLoading] = useState(true);

  const [currentUserRole, setCurrentUserRole] = useState("MEMBER");
  const [isEditingName, setIsEditingName] = useState(false);
  const [newName, setNewName] = useState("");

  const [splitsByExpense, setSplitsByExpense] = useState({});

  const [receiptExistsByExpense, setReceiptExistsByExpense] = useState({});
  const [showReceiptModal, setShowReceiptModal] = useState(false);
  const [receiptExpenseTarget, setReceiptExpenseTarget] = useState(null);
  const [receiptFile, setReceiptFile] = useState(null);
  const [receiptUploading, setReceiptUploading] = useState(false);

  const [settlementPlan, setSettlementPlan] = useState([]);
  const [loadingSettlement, setLoadingSettlement] = useState(false);

  const [zelleInfoByUser, setZelleInfoByUser] = useState({});
  const [zelleFormByUser, setZelleFormByUser] = useState({});

  const [showZelleQrModal, setShowZelleQrModal] = useState(false);
  const [zelleQrTargetUserId, setZelleQrTargetUserId] = useState(null);
  const [zelleQrFile, setZelleQrFile] = useState(null);
  const [zelleQrUploading, setZelleQrUploading] = useState(false);

  const user = JSON.parse(localStorage.getItem("user") || "{}");
  const currentUserId = String(user?.id || user?._id || "");

  const normalizeRole = (role) => String(role || "MEMBER").trim().toUpperCase();
  const isAdmin = normalizeRole(currentUserRole) === "ADMIN";

  const getMemberUserIdStr = (m) => {
    const id = m?.user?.id ?? m?.user?._id ?? m?.userId ?? m?.user_id;
    return id == null ? "" : String(id);
  };

  const getMemberRole = (m) => normalizeRole(m?.role ?? m?.userRole ?? "MEMBER");

  const fetchDetails = useCallback(async () => {
    setLoading(true);
    try {
      const memberRes = await api.get(`/groups/group-members/${groupId}`);

      if (Array.isArray(memberRes.data) && memberRes.data.length > 0) {
        setMembers(memberRes.data);

        const g = memberRes.data[0]?.group;
        setGroupInfo(g || null);
        setNewName(g?.name || "");

        const memberRecord = memberRes.data.find(
          (m) => getMemberUserIdStr(m) === currentUserId
        );

        let role = getMemberRole(memberRecord);

        if (
          (!memberRecord || !memberRecord?.role) &&
          getMemberUserIdStr(memberRes.data[0]) === currentUserId
        ) {
          role = "ADMIN";
        }

        setCurrentUserRole(role);
      } else {
        setMembers([]);
        setGroupInfo(null);
        setNewName("");
        setCurrentUserRole("MEMBER");
      }

      const expenseRes = await api.get(`/expenses/group/${groupId}`);
      const rawExpenses = Array.isArray(expenseRes.data) ? expenseRes.data : [];

      const uniqueExpenses = Array.from(
        new Map(rawExpenses.map((exp) => [String(exp.id), exp])).values()
      );

      setExpenses(uniqueExpenses);

      const splitMap = {};
      await Promise.all(
        uniqueExpenses.map(async (exp) => {
          if (!exp?.id) return;
          try {
            const sres = await api.get(`/expenses/${exp.id}/splits`);
            splitMap[exp.id] = sres.data;
          } catch {
            // ignore fallback
          }
        })
      );
      setSplitsByExpense(splitMap);

      const receiptMap = {};
      await Promise.all(
        uniqueExpenses.map(async (exp) => {
          if (!exp?.id) return;
          try {
            const r = await api.get(`/expenses/${exp.id}/receipt/info`);
            receiptMap[exp.id] = !!r?.data?.hasReceipt;
          } catch {
            receiptMap[exp.id] = false;
          }
        })
      );
      setReceiptExistsByExpense(receiptMap);
    } catch (err) {
      console.error("Error fetching details:", err);
      toast.error("Failed to load group details.");
    } finally {
      setLoading(false);
    }
  }, [groupId, currentUserId]);

  const fetchSettlementPlan = async () => {
    try {
      setLoadingSettlement(true);
      const res = await api.get(`/expenses/group/${groupId}/settlement-plan`);
      setSettlementPlan(res.data || []);
    } catch (err) {
      console.error("Settlement error", err);
      setSettlementPlan([]);
    } finally {
      setLoadingSettlement(false);
    }
  };

  const fetchZelleInfo = async () => {
    try {
      const map = {};
      const formMap = {};

      await Promise.all(
        members.map(async (m) => {
          const uid = getMemberUserIdStr(m);
          if (!uid) return;

          try {
            const res = await api.get(`/auth/${uid}/zelle`);
            map[uid] = res.data;
            formMap[uid] = {
              zelleEmail: res.data?.zelleEmail || "",
              zellePhone: res.data?.zellePhone || "",
            };
          } catch {
            map[uid] = null;
            formMap[uid] = {
              zelleEmail: "",
              zellePhone: "",
            };
          }
        })
      );

      setZelleInfoByUser(map);
      setZelleFormByUser(formMap);
    } catch (err) {
      console.error("Zelle info error", err);
    }
  };

  useEffect(() => {
    fetchDetails();
  }, [fetchDetails]);

  useEffect(() => {
    if (members.length > 0) {
      fetchSettlementPlan();
      fetchZelleInfo();
    }
  }, [members]);

  const handleUpdateName = async () => {
    if (!newName.trim()) {
      toast.error("Name cannot be empty");
      return;
    }
    try {
      const res = await api.put(`/groups/${groupId}/update-name`, null, {
        params: { newName: newName.trim(), userId: currentUserId },
      });
      setGroupInfo(res.data);
      setIsEditingName(false);
      toast.success("Group name updated!");
    } catch (err) {
      toast.error(err.response?.data || "Failed to update name.");
    }
  };

  const handleDeleteGroup = async () => {
    if (
      window.confirm(
        "CRITICAL: Delete this group? This will erase all history for all members."
      )
    ) {
      try {
        await api.delete(`/groups/${groupId}/delete`, {
          params: { userId: currentUserId },
        });
        toast.success("Group permanently deleted.");
        navigate("/dashboard");
      } catch (err) {
        toast.error(err.response?.data || "Error deleting group.");
      }
    }
  };

  const handleClearDebts = async () => {
    if (!window.confirm("Mark all debts as settled?")) return;

    try {
      await api.post(`/expenses/group/${groupId}/clear-debts`);
      toast.success("All balances cleared.");
      await fetchDetails();
      await fetchSettlementPlan();
    } catch (err) {
      console.error(err);
      toast.error("Failed to clear debts.");
    }
  };

  const exportBalanceSheet = async () => {
    try {
      const res = await api.get(`/expenses/group/${groupId}/export/csv`, {
        responseType: "blob",
      });

      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement("a");
      link.href = url;
      link.download = `group_${groupId}_balance_sheet.csv`;
      link.click();
      setTimeout(() => window.URL.revokeObjectURL(url), 60000);
    } catch (err) {
      console.error(err);
      toast.error("Export failed.");
    }
  };

  const handleZelleFieldChange = (userId, field, value) => {
    setZelleFormByUser((prev) => ({
      ...prev,
      [String(userId)]: {
        ...(prev[String(userId)] || {}),
        [field]: value,
      },
    }));
  };

  const saveZelleInfo = async (userId) => {
    const form = zelleFormByUser[String(userId)] || {};
    try {
      await api.put(`/auth/${userId}/zelle`, null, {
        params: {
          zelleEmail: form.zelleEmail || "",
          zellePhone: form.zellePhone || "",
        },
      });

      toast.success("Zelle info saved.");
      await fetchZelleInfo();
    } catch (err) {
      console.error(err);
      toast.error("Failed to save Zelle info.");
    }
  };

  const openZelleQrModal = (userId) => {
    setZelleQrTargetUserId(String(userId));
    setZelleQrFile(null);
    setShowZelleQrModal(true);
  };

  const closeZelleQrModal = () => {
    setShowZelleQrModal(false);
    setZelleQrTargetUserId(null);
    setZelleQrFile(null);
    setZelleQrUploading(false);
  };

  const uploadZelleQr = async () => {
    if (!zelleQrTargetUserId) {
      toast.error("Invalid user selected.");
      return;
    }

    if (!zelleQrFile) {
      toast.error("Please select a Zelle QR image.");
      return;
    }

    const type = zelleQrFile.type || "";
    if (!type.startsWith("image/")) {
      toast.error("Only image files are allowed for Zelle QR.");
      return;
    }

    setZelleQrUploading(true);
    try {
      const formData = new FormData();
      formData.append("file", zelleQrFile);

      await api.post(`/auth/${zelleQrTargetUserId}/zelle/qr`, formData, {
        headers: { "Content-Type": "multipart/form-data" },
      });

      toast.success("Zelle QR uploaded successfully.");
      await fetchZelleInfo();
      closeZelleQrModal();
    } catch (err) {
      console.error(err);
      toast.error(err.response?.data?.error || "Failed to upload Zelle QR.");
      setZelleQrUploading(false);
    }
  };

  const getDisplayNameByUserId = (userId) => {
    const uid = String(userId);
    const found = members.find((m) => getMemberUserIdStr(m) === uid);
    if (!found) return `User ${uid}`;
    if (uid === currentUserId) return "You";
    return found?.user?.name || found?.name || found?.user?.email || `User ${uid}`;
  };

  const getPayerName = (exp) => {
    if (!exp?.paidBy) return "Unknown";
    const payerId = String(exp.paidBy?.id || exp.paidBy);
    if (payerId === currentUserId) return "You";

    const foundMember = members.find((m) => getMemberUserIdStr(m) === payerId);
    return foundMember
      ? foundMember?.user?.name || foundMember?.name || foundMember?.user?.email || `User ${payerId}`
      : `User ${payerId}`;
  };

  const getOwedAmountForMember = (exp, memberIdStr) => {
    const total = Number(exp?.amount || 0);
    if (!total) return 0;

    const splitData = splitsByExpense?.[exp.id];

    if (
      splitData?.splitType &&
      String(splitData.splitType).toUpperCase() === "PERCENTAGE" &&
      splitData?.percentages
    ) {
      const pct = Number(splitData.percentages[String(memberIdStr)] || 0);
      return total * (pct / 100);
    }

    if (Array.isArray(splitData?.participants) && splitData.participants.length > 0) {
      const participantIds = splitData.participants.map((x) => String(x));
      if (!participantIds.includes(String(memberIdStr))) return 0;
      return total / splitData.participants.length;
    }

    return members.length > 0 ? total / members.length : 0;
  };

  const getSplitSummary = (exp) => {
    const splitData = splitsByExpense?.[exp.id];
    if (!splitData) return "Split: All group members";

    const splitTypeLabel =
      String(splitData.splitType || "EQUAL").toUpperCase() === "PERCENTAGE"
        ? "Percentage"
        : "Equal";

    if (
      String(splitData.splitType || "").toUpperCase() === "PERCENTAGE" &&
      splitData.percentages
    ) {
      const lines = Object.entries(splitData.percentages).map(([uid, pct]) => {
        return `${getDisplayNameByUserId(uid)} (${Number(pct).toFixed(0)}%)`;
      });
      return `Split Type: ${splitTypeLabel} • Split Among: ${lines.join(", ")}`;
    }

    if (Array.isArray(splitData?.participants) && splitData.participants.length > 0) {
      const names = splitData.participants.map((uid) => getDisplayNameByUserId(uid));
      return `Split Type: ${splitTypeLabel} • Split Among: ${names.join(", ")}`;
    }

    return `Split Type: ${splitTypeLabel}`;
  };

  const totalGroupSpending = expenses.reduce((sum, exp) => sum + Number(exp?.amount || 0), 0);
  const fairShare = members.length > 0 ? totalGroupSpending / members.length : 0;

  const pairwiseDebts = [];
  expenses.forEach((exp) => {
    const payerId = String(exp?.paidBy?.id || exp?.paidBy || "");
    if (!payerId) return;

    const splitData = splitsByExpense?.[exp.id];
    let participantIds = [];

    if (Array.isArray(splitData?.participants) && splitData.participants.length > 0) {
      participantIds = splitData.participants.map(String);
    } else if (splitData?.percentages && Object.keys(splitData.percentages).length > 0) {
      participantIds = Object.keys(splitData.percentages).map(String);
    } else {
      participantIds = members.map((m) => getMemberUserIdStr(m));
    }

    participantIds.forEach((participantId) => {
      if (participantId === payerId) return;

      const owedAmount = getOwedAmountForMember(exp, participantId);
      if (owedAmount > 0) {
        pairwiseDebts.push({
          expenseId: exp.id,
          expenseDesc: exp.description,
          from: participantId,
          to: payerId,
          amount: owedAmount,
        });
      }
    });
  });

  const balances = members
    .map((m) => {
      const memberId = getMemberUserIdStr(m);

      let balance = 0;

      pairwiseDebts.forEach((debt) => {
        if (String(debt.to) === memberId) {
          balance += Number(debt.amount || 0);
        }
        if (String(debt.from) === memberId) {
          balance -= Number(debt.amount || 0);
        }
      });

      return {
        displayName:
          memberId === currentUserId
            ? "You"
            : m?.user?.name || m?.name || m?.user?.email || "Member",
        userId: memberId,
        balance,
      };
    })
    .sort((a, b) => b.balance - a.balance);

  const categoryData = expenses.reduce((acc, exp) => {
    const catName = exp?.category || "General";
    const amt = Number(exp?.amount || 0);
    const existing = acc.find((item) => item.name === catName);
    if (existing) existing.value += amt;
    else acc.push({ name: catName, value: amt });
    return acc;
  }, []);

  const COLORS = ["#6366f1", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6", "#ec4899"];

  const openReceiptModal = (expense) => {
    setReceiptExpenseTarget(expense);
    setReceiptFile(null);
    setShowReceiptModal(true);
  };

  const closeReceiptModal = () => {
    setShowReceiptModal(false);
    setReceiptExpenseTarget(null);
    setReceiptFile(null);
    setReceiptUploading(false);
  };

  const uploadReceipt = async () => {
    if (!receiptExpenseTarget?.id) {
      toast.error("Invalid expense selected.");
      return;
    }
    if (!receiptFile) {
      toast.error("Please select an image or PDF file.");
      return;
    }

    const type = receiptFile.type || "";
    const isImage = type.startsWith("image/");
    const isPdf = type === "application/pdf";
    if (!isImage && !isPdf) {
      toast.error("Only image files or PDF receipts are allowed.");
      return;
    }

    setReceiptUploading(true);
    try {
      const formData = new FormData();
      formData.append("file", receiptFile);

      await api.post(`/expenses/${receiptExpenseTarget.id}/receipt`, formData, {
        headers: { "Content-Type": "multipart/form-data" },
      });

      toast.success("Receipt uploaded!");
      setReceiptExistsByExpense((prev) => ({ ...prev, [receiptExpenseTarget.id]: true }));
      closeReceiptModal();
    } catch (err) {
      console.error(err);
      toast.error(err.response?.data?.error || err.response?.data || "Failed to upload receipt.");
      setReceiptUploading(false);
    }
  };

  const viewReceipt = async (expenseId) => {
    if (!expenseId) return;

    try {
      const res = await api.get(`/expenses/${expenseId}/receipt`, {
        responseType: "blob",
      });

      const blob = res.data;
      const contentType = res.headers?.["content-type"] || blob.type || "application/octet-stream";

      const url = URL.createObjectURL(new Blob([blob], { type: contentType }));
      window.open(url, "_blank", "noopener,noreferrer");
      setTimeout(() => URL.revokeObjectURL(url), 60000);
    } catch (err) {
      toast.error(err.response?.data?.error || err.response?.data || "No receipt found for this expense.");
    }
  };

  const viewZelleQr = async (userId) => {
    try {
      const baseUrl = api?.defaults?.baseURL || "";
      window.open(`${baseUrl}/auth/${userId}/zelle/qr`, "_blank", "noopener,noreferrer");
    } catch (err) {
      console.error(err);
      toast.error("Unable to open Zelle QR.");
    }
  };

  if (loading) return <div className="loading-spinner">Analyzing group finances...</div>;

  return (
    <div className="dashboard-wrapper">
      <div className="dashboard-content">
        <div style={{ paddingBottom: "20px", display: "flex", justifyContent: "space-between" }}>
          <button className="secondary-btn" onClick={() => navigate("/dashboard")}>
            ← Back
          </button>
          <div style={{ display: "flex", gap: "10px", flexWrap: "wrap" }}>
            <button className="secondary-btn" onClick={exportBalanceSheet}>
              📄 Export Balance Sheet
            </button>
            <button className="primary-btn" onClick={() => navigate("/add-expense")}>
              + Expense
            </button>
          </div>
        </div>

        <div className="panel-header">
          <div style={{ flex: 1 }}>
            {isEditingName && isAdmin ? (
              <div style={{ display: "flex", gap: "10px", alignItems: "center" }}>
                <input
                  className="custom-input"
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                  style={{ fontSize: "1.5rem", fontWeight: "bold", width: "300px" }}
                  autoFocus
                />
                <button className="primary-btn" onClick={handleUpdateName}>
                  Save
                </button>
                <button className="secondary-btn" onClick={() => setIsEditingName(false)}>
                  Cancel
                </button>
              </div>
            ) : (
              <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
                <h1>{groupInfo?.name || "Group"}</h1>
                {isAdmin && (
                  <button
                    className="icon-btn"
                    onClick={() => setIsEditingName(true)}
                    title="Rename Group"
                  >
                    ✏️
                  </button>
                )}
              </div>
            )}

            <p className="text-muted">
              Invite Code: <strong style={{ color: "#2563eb" }}>{groupInfo?.inviteCode || "-"}</strong>
            </p>
          </div>

          <span className={`badge-role ${isAdmin ? "admin" : ""}`}>
            {normalizeRole(currentUserRole)}
          </span>
        </div>

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
            <div style={{ width: "100%", height: "180px" }}>
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={categoryData}
                    cx="50%"
                    cy="50%"
                    innerRadius={45}
                    outerRadius={65}
                    paddingAngle={5}
                    dataKey="value"
                  >
                    {categoryData.map((_, index) => (
                      <Cell key={index} fill={COLORS[index % COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip formatter={(val) => `$${Number(val).toFixed(2)}`} />
                </PieChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>

        <div className="main-grid">
          <div className="glass-panel">
            <h3>Expense History</h3>
            <div className="expense-list" style={{ marginTop: "1rem" }}>
              {expenses.length === 0 ? (
                <p className="text-muted">No expenses found.</p>
              ) : (
                expenses.map((exp) => {
                  const hasReceipt = !!receiptExistsByExpense?.[exp.id];

                  return (
                    <div
                      key={exp.id}
                      className="activity-item"
                      style={{ justifyContent: "space-between", alignItems: "center", gap: "12px" }}
                    >
                      <div>
                        <strong>{exp.description}</strong>
                        <p className="text-muted">
                          Paid by {getPayerName(exp)} • ${Number(exp.amount || 0).toFixed(2)}
                        </p>

                        <p className="text-muted" style={{ fontSize: "0.92rem", marginTop: "4px" }}>
                          {getSplitSummary(exp)}
                        </p>

                        <div style={{ display: "flex", gap: "10px", marginTop: "8px", flexWrap: "wrap" }}>
                          <button
                            className="secondary-btn"
                            onClick={() => openReceiptModal(exp)}
                            style={{ padding: "8px 10px" }}
                            title="Attach image/PDF receipt"
                          >
                            🧾 {hasReceipt ? "Replace Receipt" : "Attach Receipt"}
                          </button>

                          {hasReceipt && (
                            <button
                              className="secondary-btn"
                              onClick={() => viewReceipt(exp.id)}
                              style={{ padding: "8px 10px" }}
                              title="View attached receipt"
                            >
                              👁️ View Receipt
                            </button>
                          )}
                        </div>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
            <div className="glass-panel">
              <h3>Balance Summary</h3>
              <div className="settle-list" style={{ marginTop: "1rem" }}>
                {balances.map((b) => (
                  <div
                    key={b.userId}
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      padding: "12px 0",
                      borderBottom: "1px solid #f1f5f9",
                    }}
                  >
                    <span style={{ fontWeight: b.userId === currentUserId ? "700" : "400" }}>
                      {b.displayName}
                    </span>
                    <span
                      style={{
                        fontWeight: "700",
                        color: b.balance >= 0 ? "#10b981" : "#ef4444",
                      }}
                    >
                      {b.balance >= 0
                        ? `+ $${b.balance.toFixed(2)}`
                        : `- $${Math.abs(b.balance).toFixed(2)}`}
                    </span>
                  </div>
                ))}
              </div>
            </div>

            <div className="glass-panel">
              <h3>Who Owes Whom</h3>
              <div style={{ marginTop: "1rem" }}>
                {pairwiseDebts.length === 0 ? (
                  <p className="text-muted">No pending settlements.</p>
                ) : (
                  pairwiseDebts.map((debt, index) => (
                    <div
                      key={`${debt.expenseId}-${debt.from}-${debt.to}-${index}`}
                      style={{
                        display: "flex",
                        justifyContent: "space-between",
                        padding: "12px 0",
                        borderBottom: "1px solid #f1f5f9",
                      }}
                    >
                      <div>
                        <span>
                          <strong>{getDisplayNameByUserId(debt.from)}</strong> owes{" "}
                          <strong>{getDisplayNameByUserId(debt.to)}</strong>
                        </span>
                        <div className="text-muted" style={{ fontSize: "0.9rem", marginTop: "2px" }}>
                          For: {debt.expenseDesc || "Expense"}
                        </div>
                      </div>
                      <span style={{ fontWeight: "700", color: "#ef4444" }}>
                        ${debt.amount.toFixed(2)}
                      </span>
                    </div>
                  ))
                )}
              </div>
            </div>

            <div className="glass-panel">
              <h3>Settlement Plan</h3>

              <div style={{ marginTop: "1rem" }}>
                {loadingSettlement ? (
                  <p className="text-muted">Calculating settlements...</p>
                ) : settlementPlan.length === 0 ? (
                  <p className="text-muted">Everyone is settled 🎉</p>
                ) : (
                  settlementPlan.map((s, index) => (
                    <div
                      key={index}
                      style={{
                        display: "flex",
                        justifyContent: "space-between",
                        gap: "10px",
                        padding: "12px 0",
                        borderBottom: "1px solid #f1f5f9",
                      }}
                    >
                      <span>
                        <strong>{s.fromName}</strong> pays <strong>{s.toName}</strong>
                      </span>

                      <span style={{ fontWeight: "700", color: "#ef4444" }}>
                        ${Number(s.amount).toFixed(2)}
                      </span>
                    </div>
                  ))
                )}
              </div>

              <div style={{ marginTop: "16px", display: "flex", gap: "10px", flexWrap: "wrap" }}>
                <button className="primary-btn" onClick={handleClearDebts}>
                  ✔ Clear All Debts
                </button>

                <button className="secondary-btn" onClick={exportBalanceSheet}>
                  📄 Export Balance Sheet
                </button>
              </div>
            </div>

            <div className="glass-panel">
              <h3>Zelle Payment Info</h3>
              <div style={{ marginTop: "1rem" }}>
                {members.length === 0 ? (
                  <p className="text-muted">No members found.</p>
                ) : (
                  members.map((m) => {
                    const uid = getMemberUserIdStr(m);
                    const displayName = getDisplayNameByUserId(uid);
                    const zelle = zelleInfoByUser?.[uid];
                    const form = zelleFormByUser?.[uid] || {
                      zelleEmail: "",
                      zellePhone: "",
                    };

                    return (
                      <div
                        key={uid}
                        style={{
                          padding: "14px 0",
                          borderBottom: "1px solid #f1f5f9",
                          display: "flex",
                          flexDirection: "column",
                          gap: "10px",
                        }}
                      >
                        <div style={{ fontWeight: "700", fontSize: "1rem" }}>{displayName}</div>

                        <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                          <input
                            className="custom-input"
                            placeholder="Zelle Email"
                            value={form.zelleEmail}
                            onChange={(e) =>
                              handleZelleFieldChange(uid, "zelleEmail", e.target.value)
                            }
                          />

                          <input
                            className="custom-input"
                            placeholder="Zelle Phone"
                            value={form.zellePhone}
                            onChange={(e) =>
                              handleZelleFieldChange(uid, "zellePhone", e.target.value)
                            }
                          />
                        </div>

                        <div style={{ display: "flex", gap: "10px", flexWrap: "wrap" }}>
                          <button
                            className="secondary-btn"
                            onClick={() => saveZelleInfo(uid)}
                          >
                            💾 Save Zelle Info
                          </button>

                          {zelle?.hasZelleQr && (
                            <button
                              className="secondary-btn"
                              onClick={() => viewZelleQr(uid)}
                            >
                              📱 View Zelle QR
                            </button>
                          )}

                          <button
                            className="secondary-btn"
                            onClick={() => openZelleQrModal(uid)}
                          >
                            {zelle?.hasZelleQr ? "Replace Zelle QR" : "Upload Zelle QR"}
                          </button>
                        </div>

                        {(zelle?.zelleEmail || zelle?.zellePhone || zelle?.hasZelleQr) && (
                          <div className="text-muted" style={{ fontSize: "0.9rem" }}>
                            {zelle?.zelleEmail && <div>Saved Email: {zelle.zelleEmail}</div>}
                            {zelle?.zellePhone && <div>Saved Phone: {zelle.zellePhone}</div>}
                            {zelle?.hasZelleQr && <div>QR uploaded</div>}
                          </div>
                        )}
                      </div>
                    );
                  })
                )}
              </div>
            </div>

            <div className="glass-panel">
              <h3>Group Settings</h3>
              <div style={{ display: "flex", flexDirection: "column", gap: "10px", marginTop: "1rem" }}>
                {isAdmin && (
                  <button className="secondary-btn" onClick={() => setIsEditingName(true)}>
                    ✏️ Rename Group
                  </button>
                )}
                <button className="secondary-btn" onClick={() => navigate("/dashboard")}>
                  Exit to Dashboard
                </button>
                {isAdmin && (
                  <button
                    className="secondary-btn"
                    onClick={handleDeleteGroup}
                    style={{ color: "#ef4444", backgroundColor: "#fef2f2", marginTop: "10px" }}
                  >
                    🗑️ Delete Group
                  </button>
                )}
              </div>

              {!isAdmin && (
                <p className="text-muted" style={{ marginTop: "10px" }}>
                  Only admins can rename/delete this group.
                </p>
              )}
            </div>
          </div>
        </div>
      </div>

      {showReceiptModal && (
        <div
          style={{
            position: "fixed",
            inset: 0,
            background: "rgba(0,0,0,0.45)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            zIndex: 9999,
            padding: "16px",
          }}
          onClick={closeReceiptModal}
        >
          <div
            className="glass-panel"
            style={{ width: "100%", maxWidth: "520px" }}
            onClick={(e) => e.stopPropagation()}
          >
            <h3 style={{ marginBottom: "8px" }}>Attach Receipt</h3>
            <p className="text-muted" style={{ marginBottom: "14px" }}>
              Expense: <strong>{receiptExpenseTarget?.description || "-"}</strong>
            </p>

            <input
              className="custom-input"
              type="file"
              accept="image/*,application/pdf"
              onChange={(e) => setReceiptFile(e.target.files?.[0] || null)}
            />

            <p className="text-muted" style={{ marginTop: "10px" }}>
              Allowed: images (JPG/PNG/etc.) or PDF.
            </p>

            <div style={{ display: "flex", gap: "10px", marginTop: "16px" }}>
              <button className="secondary-btn" onClick={closeReceiptModal} disabled={receiptUploading}>
                Cancel
              </button>
              <button className="primary-btn" onClick={uploadReceipt} disabled={receiptUploading}>
                {receiptUploading ? "Uploading..." : "Upload Receipt"}
              </button>
            </div>
          </div>
        </div>
      )}

      {showZelleQrModal && (
        <div
          style={{
            position: "fixed",
            inset: 0,
            background: "rgba(0,0,0,0.45)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            zIndex: 9999,
            padding: "16px",
          }}
          onClick={closeZelleQrModal}
        >
          <div
            className="glass-panel"
            style={{ width: "100%", maxWidth: "520px" }}
            onClick={(e) => e.stopPropagation()}
          >
            <h3 style={{ marginBottom: "8px" }}>Upload Zelle QR</h3>
            <p className="text-muted" style={{ marginBottom: "14px" }}>
              Member: <strong>{getDisplayNameByUserId(zelleQrTargetUserId)}</strong>
            </p>

            <input
              className="custom-input"
              type="file"
              accept="image/*"
              onChange={(e) => setZelleQrFile(e.target.files?.[0] || null)}
            />

            <p className="text-muted" style={{ marginTop: "10px" }}>
              Allowed: image files only.
            </p>

            <div style={{ display: "flex", gap: "10px", marginTop: "16px" }}>
              <button className="secondary-btn" onClick={closeZelleQrModal} disabled={zelleQrUploading}>
                Cancel
              </button>
              <button className="primary-btn" onClick={uploadZelleQr} disabled={zelleQrUploading}>
                {zelleQrUploading ? "Uploading..." : "Upload Zelle QR"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}