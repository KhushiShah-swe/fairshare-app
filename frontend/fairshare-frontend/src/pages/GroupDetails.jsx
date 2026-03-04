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

  // ✅ default role is MEMBER, but we will derive correctly from backend
  const [currentUserRole, setCurrentUserRole] = useState("MEMBER");
  const [isEditingName, setIsEditingName] = useState(false);
  const [newName, setNewName] = useState("");

  // ✅ expenseId -> { splitType, participants, percentages }
  const [splitsByExpense, setSplitsByExpense] = useState({});

  // ✅ RECEIPT FEATURE STATES
  // expenseId -> true/false (does receipt exist)
  const [receiptExistsByExpense, setReceiptExistsByExpense] = useState({});
  // upload UI state
  const [showReceiptModal, setShowReceiptModal] = useState(false);
  const [receiptExpenseTarget, setReceiptExpenseTarget] = useState(null); // expense object
  const [receiptFile, setReceiptFile] = useState(null);
  const [receiptUploading, setReceiptUploading] = useState(false);

  const user = JSON.parse(localStorage.getItem("user") || "{}");
  const currentUserId = String(user?.id || user?._id || "");

  const normalizeRole = (role) => String(role || "MEMBER").trim().toUpperCase();
  const isAdmin = normalizeRole(currentUserRole) === "ADMIN";

  // ✅ Handles BOTH member shapes:
  // - m.user.id
  // - m.userId (if backend sends plain userId)
  const getMemberUserIdStr = (m) => {
    const id = m?.user?.id ?? m?.user?._id ?? m?.userId ?? m?.user_id;
    return id == null ? "" : String(id);
  };

  // ✅ Handles BOTH role shapes:
  // - m.role
  // - m.userRole (some backends)
  const getMemberRole = (m) => normalizeRole(m?.role ?? m?.userRole ?? "MEMBER");

  // =========================
  // RECEIPT API CONTRACT (IMPORTANT)
  // Backend endpoints this UI expects:
  //
  // 1) Upload/Replace receipt:
  //    POST /api/expenses/{expenseId}/receipt
  //    Content-Type: multipart/form-data
  //    FormData field name: "file"
  //
  // 2) View/download receipt:
  //    GET /api/expenses/{expenseId}/receipt
  //    returns the file as binary (image/pdf)
  //
  // 3) Check if receipt exists (optional but recommended):
  //    GET /api/expenses/{expenseId}/receipt/exists
  //    returns: { exists: true/false }
  //
  // If (3) is NOT implemented, UI will still work:
  // - "View Receipt" will try GET /receipt and show error if none
  // - After upload, we mark it as exists locally
  // =========================

  const fetchDetails = useCallback(async () => {
    setLoading(true);
    try {
      // 1) Members + group info
      const memberRes = await api.get(`/groups/group-members/${groupId}`);

      if (Array.isArray(memberRes.data) && memberRes.data.length > 0) {
        setMembers(memberRes.data);

        const g = memberRes.data[0]?.group;
        setGroupInfo(g || null);
        setNewName(g?.name || "");

        // ✅ IMPORTANT FIX:
        // Find the membership record for the current user using robust id extraction
        const memberRecord = memberRes.data.find(
          (m) => getMemberUserIdStr(m) === currentUserId
        );

        // ✅ IMPORTANT FIX:
        // If role is null/empty in DB for new groups, treat creator as ADMIN
        // (still shows admin UI even if role wasn't saved properly)
        let role = getMemberRole(memberRecord);

        // If backend didn't save role, infer:
        // if current user is the first member record AND group exists => likely creator/admin
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

      // 2) Expenses
      const expenseRes = await api.get(`/expenses/group/${groupId}`);
      const expList = Array.isArray(expenseRes.data) ? expenseRes.data : [];
      setExpenses(expList);

      // 3) ✅ Fetch split info for each expense (percentage/equal participants)
      const splitMap = {};
      await Promise.all(
        expList.map(async (exp) => {
          if (!exp?.id) return;
          try {
            // GET /api/expenses/{id}/splits
            const sres = await api.get(`/expenses/${exp.id}/splits`);
            splitMap[exp.id] = sres.data;
          } catch {
            // fallback handled in calculations
          }
        })
      );
      setSplitsByExpense(splitMap);

      // 4) ✅ Receipt existence check (optional endpoint)
      const receiptMap = {};
      await Promise.all(
        expList.map(async (exp) => {
          if (!exp?.id) return;
          try {
            // Optional endpoint. If not implemented, it will fail and we ignore.
            const r = await api.get(`/expenses/${exp.id}/receipt/exists`);
            receiptMap[exp.id] = !!r?.data?.exists;
          } catch {
            // ignore; UI will still allow upload + view attempt
          }
        })
      );
      if (Object.keys(receiptMap).length > 0) {
        setReceiptExistsByExpense((prev) => ({ ...prev, ...receiptMap }));
      }
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
        params: { newName: newName.trim(), userId: currentUserId },
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

  // --- HELPERS ---
  const getPayerName = (exp) => {
    if (!exp?.paidBy) return "Unknown";
    const payerId = String(exp.paidBy?.id || exp.paidBy);

    if (payerId === currentUserId) return "You";

    const foundMember = members.find((m) => getMemberUserIdStr(m) === payerId);

    return foundMember ? foundMember?.user?.name || foundMember?.name : `User ${payerId}`;
  };

  const handleDownloadReport = () => {
    const headers = ["Date", "Description", "Category", "Amount", "Paid By"];
    const rows = expenses.map((exp) => [
      new Date(exp.expenseDate || Date.now()).toLocaleDateString(),
      `"${exp.description || ""}"`,
      exp.category || "General",
      Number(exp.amount || 0).toFixed(2),
      getPayerName(exp),
    ]);
    const csvContent = [headers.join(","), ...rows.map((r) => r.join(","))].join("\n");
    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = `${groupInfo?.name || "Group"}_Report.csv`;
    link.click();
  };

  // ✅ core fix: how much does member owe for this expense?
  const getOwedAmountForMember = (exp, memberIdStr) => {
    const total = Number(exp?.amount || 0);
    if (!total) return 0;

    const splitData = splitsByExpense?.[exp.id];

    // PERCENTAGE
    if (
      splitData?.splitType &&
      String(splitData.splitType).toUpperCase() === "PERCENTAGE" &&
      splitData?.percentages
    ) {
      const pct = Number(splitData.percentages[String(memberIdStr)] || 0);
      return total * (pct / 100);
    }

    // EQUAL among selected participants (only if this member is included)
    if (Array.isArray(splitData?.participants) && splitData.participants.length > 0) {
      const participantIds = splitData.participants.map((x) => String(x));
      if (!participantIds.includes(String(memberIdStr))) return 0;
      return total / splitData.participants.length;
    }

    // fallback: equal among all group members (old behavior)
    return members.length > 0 ? total / members.length : 0;
  };

  // --- CALCULATIONS (FIXED) ---
  const totalGroupSpending = expenses.reduce((sum, exp) => sum + Number(exp?.amount || 0), 0);
  const fairShare = members.length > 0 ? totalGroupSpending / members.length : 0;

  const balances = members
    .map((m) => {
      const memberId = getMemberUserIdStr(m);
      let netBalance = 0;

      expenses.forEach((exp) => {
        // debit what member owes
        netBalance -= getOwedAmountForMember(exp, memberId);

        // credit payer the full amount
        const payerId = String(exp?.paidBy?.id || exp?.paidBy || "");
        if (payerId && payerId === memberId) {
          netBalance += Number(exp?.amount || 0);
        }
      });

      return {
        displayName: memberId === currentUserId ? "You" : m?.user?.name || "Member",
        userId: memberId,
        balance: netBalance,
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

  // =========================
  // RECEIPT ACTIONS
  // =========================

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

    // Basic client-side type check
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

      // optional cleanup later (won't break if ignored)
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      toast.error(err.response?.data?.error || err.response?.data || "No receipt found for this expense.");
    }
  };

  if (loading) return <div className="loading-spinner">Analyzing group finances...</div>;

  return (
    <div className="dashboard-wrapper">
      <div className="dashboard-content">
        {/* TOP ACTION BAR */}
        <div style={{ paddingBottom: "20px", display: "flex", justifyContent: "space-between" }}>
          <button className="secondary-btn" onClick={() => navigate("/dashboard")}>
            ← Back
          </button>
          <div style={{ display: "flex", gap: "10px" }}>
            <button className="secondary-btn" onClick={handleDownloadReport}>
              📥 Report
            </button>
            <button className="primary-btn" onClick={() => navigate("/add-expense")}>
              + Expense
            </button>
          </div>
        </div>

        {/* HEADER */}
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
              Invite Code:{" "}
              <strong style={{ color: "#2563eb" }}>{groupInfo?.inviteCode || "-"}</strong>
            </p>
          </div>

          {/* ✅ Always show role */}
          <span className={`badge-role ${isAdmin ? "admin" : ""}`}>
            {normalizeRole(currentUserRole)}
          </span>
        </div>

        {/* STATS */}
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
          {/* LEFT */}
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

                        {/* ✅ Receipt actions */}
                        <div style={{ display: "flex", gap: "10px", marginTop: "8px", flexWrap: "wrap" }}>
                          <button
                            className="secondary-btn"
                            onClick={() => openReceiptModal(exp)}
                            style={{ padding: "8px 10px" }}
                            title="Attach image/PDF receipt"
                          >
                            🧾 {hasReceipt ? "Replace Receipt" : "Attach Receipt"}
                          </button>

                          <button
                            className="secondary-btn"
                            onClick={() => viewReceipt(exp.id)}
                            style={{ padding: "8px 10px" }}
                            title="View attached receipt"
                          >
                            👁️ View Receipt
                          </button>
                        </div>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </div>

          {/* RIGHT */}
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

      {/* ✅ Receipt Upload Modal */}
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
    </div>
  );
}