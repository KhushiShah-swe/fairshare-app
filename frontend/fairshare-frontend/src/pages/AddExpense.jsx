import { useState, useEffect, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import api from "../api/api";
import "./AddExpense.css";

export default function AddExpense() {
  const [amount, setAmount] = useState("");
  const [desc, setDesc] = useState("");
  const [groupId, setGroupId] = useState("");
  const [groups, setGroups] = useState([]);

  // Sprint 1 fields
  const [category, setCategory] = useState("General");
  const [expenseDate, setExpenseDate] = useState(
    new Date().toISOString().split("T")[0]
  );
  const [notes, setNotes] = useState("");

  // Sprint 2 fields
  const [splitType, setSplitType] = useState("EQUAL"); // EQUAL | PERCENTAGE
  const [members, setMembers] = useState([]); // group members: [{ user: {id, name...}}]
  const [selectedMemberIds, setSelectedMemberIds] = useState([]); // subset selection
  const [percentages, setPercentages] = useState({}); // { [userId]: number }

  // ✅ Sprint 3: Receipt upload (image/pdf)
  const [receiptFile, setReceiptFile] = useState(null);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const navigate = useNavigate();

  const user = JSON.parse(localStorage.getItem("user"));
  const userId = user?.id;

  const categories = [
    "General",
    "Food",
    "Travel",
    "Entertainment",
    "Shopping",
    "Rent",
    "Health",
  ];

  // helper: percent sum for UI
  const percentageSum = useMemo(() => {
    return Object.values(percentages).reduce((acc, v) => acc + (Number(v) || 0), 0);
  }, [percentages]);

  useEffect(() => {
    if (!userId) {
      navigate("/login");
      return;
    }

    const fetchGroups = async () => {
      try {
        const res = await api.get(`/groups/user-groups/${userId}`);
        const userGroups = res.data.map((m) => m.group);
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

  // When group changes, fetch members and default-select all
  useEffect(() => {
    if (!groupId) return;

    const fetchMembers = async () => {
      try {
        const memberRes = await api.get(`/groups/group-members/${groupId}`);
        setMembers(memberRes.data || []);

        const allIds = (memberRes.data || []).map((m) => m.user.id);
        setSelectedMemberIds(allIds);

        // Initialize percentages evenly (only matters if user switches to PERCENTAGE)
        const evenPct = allIds.length > 0 ? 100 / allIds.length : 0;
        const pctMap = {};
        allIds.forEach((id) => (pctMap[id] = Number(evenPct.toFixed(2))));
        setPercentages(pctMap);
      } catch (err) {
        console.error("Error fetching group members:", err);
        setMembers([]);
        setSelectedMemberIds([]);
        setPercentages({});
      }
    };

    fetchMembers();
  }, [groupId]);

  const toggleMember = (memberId) => {
    setSelectedMemberIds((prev) => {
      const exists = prev.includes(memberId);
      const next = exists ? prev.filter((id) => id !== memberId) : [...prev, memberId];

      // Keep percentages map in sync
      setPercentages((pPrev) => {
        const pNext = { ...pPrev };
        if (!exists) {
          // add default 0 for newly selected member
          pNext[memberId] = pNext[memberId] ?? 0;
        } else {
          // remove percentage for unselected member
          delete pNext[memberId];
        }
        return pNext;
      });

      return next;
    });
  };

  const handlePercentageChange = (userIdForPct, value) => {
    setPercentages((prev) => ({
      ...prev,
      [userIdForPct]: value === "" ? "" : Number(value),
    }));
  };

  // ✅ Receipt validation (optional file)
  const validateReceipt = () => {
    if (!receiptFile) return ""; // optional
    const type = receiptFile.type || "";
    const isImage = type.startsWith("image/");
    const isPdf = type === "application/pdf";
    if (!isImage && !isPdf) return "Receipt must be an image or a PDF.";
    // optional size limit (example 10MB)
    const maxBytes = 10 * 1024 * 1024;
    if (receiptFile.size > maxBytes) return "Receipt file is too large (max 10MB).";
    return "";
  };

  const validate = () => {
    if (!groupId) return "Please select a group.";
    if (!desc || desc.trim().length === 0) return "Please enter a title.";
    if (!amount || Number(amount) <= 0) return "Amount must be greater than zero.";
    if (!expenseDate) return "Please select a date.";
    if (!selectedMemberIds || selectedMemberIds.length === 0)
      return "Please select at least one participant.";

    const receiptErr = validateReceipt();
    if (receiptErr) return receiptErr;

    if (splitType === "PERCENTAGE") {
      // all selected should have a number
      for (const uid of selectedMemberIds) {
        const val = percentages[uid];
        if (val === "" || val === undefined || val === null || isNaN(Number(val))) {
          return `Please enter a percentage for userId ${uid}.`;
        }
        if (Number(val) < 0) return "Percentages cannot be negative.";
      }

      const sum = selectedMemberIds.reduce(
        (acc, uid) => acc + (Number(percentages[uid]) || 0),
        0
      );

      if (Math.abs(sum - 100) > 0.01) {
        return `Percentages must sum to 100. Current sum: ${sum.toFixed(2)}`;
      }
    }

    return "";
  };

  const handleAddExpense = async (e) => {
    e.preventDefault();

    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    setLoading(true);
    setError("");

    try {
      const payload = {
        amount: parseFloat(amount),
        description: desc,
        groupId: parseInt(groupId),
        paidBy: userId,
        participants: selectedMemberIds, // Sprint 2: subset supported
        category,
        expenseDate,
        notes: notes.trim(),
        splitType, // "EQUAL" | "PERCENTAGE"
      };

      if (splitType === "PERCENTAGE") {
        // Backend expects Map<String, Double> (JSON keys are strings)
        const pctPayload = {};
        selectedMemberIds.forEach((uid) => {
          pctPayload[String(uid)] = Number(percentages[uid] || 0);
        });
        payload.percentages = pctPayload;
      }

      // ✅ Add expense first (existing endpoint)
      const addRes = await api.post("/expenses/add", payload);

      // ✅ Then upload receipt (optional) to:
      // POST /api/expenses/{expenseId}/receipt (multipart/form-data, field "file")
      // IMPORTANT: backend must return expenseId OR something like { expenseId: X }.
      // We try common shapes safely:
      const maybeExpenseId =
        addRes?.data?.expenseId ??
        addRes?.data?.id ??
        addRes?.data?.expense?.id ??
        null;

      if (receiptFile) {
        if (!maybeExpenseId) {
          // If your backend only returns {message: "..."} then we can't attach.
          // You must update backend to return expenseId.
          throw new Error(
            "Receipt upload needs expenseId from /expenses/add response. Update backend to return it."
          );
        }

        const formData = new FormData();
        formData.append("file", receiptFile);

        await api.post(`/expenses/${maybeExpenseId}/receipt`, formData, {
          headers: { "Content-Type": "multipart/form-data" },
        });
      }

      navigate("/dashboard");
    } catch (err) {
      console.error(err);
      setError(
        err?.message ||
          err?.response?.data?.error ||
          err?.response?.data ||
          "Failed to add expense. Check if you have group members."
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="dashboard-wrapper">
      <div
        className="auth-card animate-fade-in"
        style={{ maxWidth: "650px", margin: "2rem auto" }}
      >
        <div className="auth-header">
          <h1>Add New Expense</h1>
          <p>
            Choose who participates and how the expense is split (Equal or Percentage).
          </p>
        </div>

        <form onSubmit={handleAddExpense} className="auth-form">
          {error && <div className="error-message">⚠️ {error}</div>}

          {/* Title */}
          <div className="input-group">
            <label>Title</label>
            <input
              placeholder="e.g. Pizza Night"
              value={desc}
              onChange={(e) => setDesc(e.target.value)}
              required
            />
          </div>

          <div className="row" style={{ display: "flex", gap: "15px" }}>
            {/* Amount */}
            <div className="input-group" style={{ flex: 1 }}>
              <label>Total Amount ($)</label>
              <input
                type="number"
                step="0.01"
                placeholder="0.00"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                required
              />
            </div>

            {/* Date */}
            <div className="input-group" style={{ flex: 1 }}>
              <label>Date</label>
              <input
                type="date"
                value={expenseDate}
                onChange={(e) => setExpenseDate(e.target.value)}
                required
              />
            </div>
          </div>

          <div className="row" style={{ display: "flex", gap: "15px" }}>
            {/* Group Selection */}
            <div className="input-group" style={{ flex: 1 }}>
              <label>Group</label>
              <select
                className="custom-select"
                value={groupId}
                onChange={(e) => setGroupId(e.target.value)}
                required
              >
                <option value="" disabled>
                  Select Group
                </option>
                {groups.map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.name}
                  </option>
                ))}
              </select>
            </div>

            {/* Category Selection */}
            <div className="input-group" style={{ flex: 1 }}>
              <label>Category</label>
              <select value={category} onChange={(e) => setCategory(e.target.value)}>
                {categories.map((cat) => (
                  <option key={cat} value={cat}>
                    {cat}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Split Type */}
          <div className="input-group">
            <label>Split Type</label>
            <select
              className="custom-select"
              value={splitType}
              onChange={(e) => setSplitType(e.target.value)}
            >
              <option value="EQUAL">Equal Split</option>
              <option value="PERCENTAGE">Percentage Split</option>
            </select>
            <small style={{ color: "#64748b" }}>
              Equal splits the amount evenly among selected participants. Percentage uses
              custom shares totaling 100%.
            </small>
          </div>

          {/* Participants Selection */}
          <div className="input-group">
            <label>Split Among (Select Members)</label>

            {members.length === 0 ? (
              <div className="info-box">
                <small>No members found for this group.</small>
              </div>
            ) : (
              <div
                style={{
                  border: "1px solid #e2e8f0",
                  borderRadius: "10px",
                  padding: "10px",
                  maxHeight: "220px",
                  overflowY: "auto",
                }}
              >
                {members.map((m) => {
                  const mid = m.user.id;
                  const displayName =
                    m.user.name ||
                    m.user.username ||
                    m.user.email ||
                    `User ${mid}`;

                  const checked = selectedMemberIds.includes(mid);

                  return (
                    <div
                      key={mid}
                      style={{
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "space-between",
                        padding: "8px 6px",
                        borderBottom: "1px solid #f1f5f9",
                        gap: "10px",
                      }}
                    >
                      <label style={{ display: "flex", alignItems: "center", gap: "10px" }}>
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={() => toggleMember(mid)}
                        />
                        <span>{displayName}</span>
                      </label>

                      {/* Percentage input only in percentage mode AND if selected */}
                      {splitType === "PERCENTAGE" && checked && (
                        <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                          <input
                            type="number"
                            step="0.01"
                            min="0"
                            value={percentages[mid] ?? ""}
                            onChange={(e) => handlePercentageChange(mid, e.target.value)}
                            style={{
                              width: "90px",
                              padding: "6px 8px",
                              borderRadius: "8px",
                              border: "1px solid #e2e8f0",
                            }}
                          />
                          <span>%</span>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}

            {splitType === "PERCENTAGE" && (
              <div className="info-box" style={{ marginTop: "10px" }}>
                <small>
                  Percentage total: <b>{percentageSum.toFixed(2)}%</b> (must be 100%)
                </small>
              </div>
            )}
          </div>

          {/* ✅ Receipt Upload */}
          <div className="input-group">
            <label>Attach Receipt (Optional)</label>
            <input
              type="file"
              accept="image/*,application/pdf"
              onChange={(e) => setReceiptFile(e.target.files?.[0] || null)}
            />
            <small style={{ color: "#64748b" }}>
              Upload an image or PDF. This will be linked to the expense after it is created.
            </small>

            {receiptFile && (
              <div className="info-box" style={{ marginTop: "10px" }}>
                <small>
                  Selected: <b>{receiptFile.name}</b> ({Math.round(receiptFile.size / 1024)} KB)
                </small>
              </div>
            )}
          </div>

          {/* Notes */}
          <div className="input-group">
            <label>Notes (Optional)</label>
            <textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Add more details about this expense..."
              style={{
                width: "100%",
                borderRadius: "8px",
                padding: "10px",
                border: "1px solid #e2e8f0",
                minHeight: "80px",
                fontFamily: "inherit",
              }}
            />
          </div>

          <div className="info-box">
            <small>
              FairShare will calculate and store splits based on your selected members and split
              type.
            </small>
          </div>

          <button type="submit" className="auth-button" disabled={loading || groups.length === 0}>
            {loading ? "Calculating Splits..." : "Add Expense"}
          </button>

          <button
            type="button"
            className="secondary-btn"
            style={{
              width: "100%",
              marginTop: "10px",
              background: "white",
              color: "#64748b",
              border: "1px solid #e2e8f0",
            }}
            onClick={() => navigate("/dashboard")}
          >
            Cancel
          </button>
        </form>
      </div>
    </div>
  );
}