import { useState, useEffect, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import api from "../api/api";
import "./AddExpense.css";

export default function AddExpense() {
  const [amount, setAmount] = useState("");
  const [desc, setDesc] = useState("");
  const [groupId, setGroupId] = useState("");
  const [groups, setGroups] = useState([]);

  const [category, setCategory] = useState("General");
  const [expenseDate, setExpenseDate] = useState(
    new Date().toISOString().split("T")[0]
  );
  const [notes, setNotes] = useState("");

  const [splitType, setSplitType] = useState("EQUAL");
  const [members, setMembers] = useState([]);
  const [selectedMemberIds, setSelectedMemberIds] = useState([]);
  const [percentages, setPercentages] = useState({});

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

  const percentageSum = useMemo(() => {
    return Object.values(percentages).reduce((acc, v) => acc + (Number(v) || 0), 0);
  }, [percentages]);

  const perHeadAmount = useMemo(() => {
    const total = Number(amount || 0);
    if (!selectedMemberIds.length || !total) return 0;
    if (splitType !== "EQUAL") return 0;
    return total / selectedMemberIds.length;
  }, [amount, selectedMemberIds, splitType]);

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

  useEffect(() => {
    if (!groupId) return;

    const fetchMembers = async () => {
      try {
        const memberRes = await api.get(`/groups/group-members/${groupId}`);
        setMembers(memberRes.data || []);

        const allIds = (memberRes.data || []).map((m) => m.user.id);
        setSelectedMemberIds(allIds);

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

      setPercentages((pPrev) => {
        const pNext = { ...pPrev };
        if (!exists) {
          pNext[memberId] = pNext[memberId] ?? 0;
        } else {
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

  const validateReceipt = () => {
    if (!receiptFile) return "";
    const type = receiptFile.type || "";
    const isImage = type.startsWith("image/");
    const isPdf = type === "application/pdf";
    if (!isImage && !isPdf) return "Receipt must be an image or a PDF.";
    const maxBytes = 10 * 1024 * 1024;
    if (receiptFile.size > maxBytes) return "Receipt file is too large (max 10MB).";
    return "";
  };

  const validate = () => {
    if (!groupId) return "Please select a group.";
    if (!desc || desc.trim().length === 0) return "Please enter a title.";
    if (!amount || Number(amount) <= 0) return "Amount must be greater than zero.";
    if (!expenseDate) return "Please select a date.";
    if (!selectedMemberIds || selectedMemberIds.length === 0) {
      return "Please select at least one participant.";
    }

    const receiptErr = validateReceipt();
    if (receiptErr) return receiptErr;

    if (splitType === "PERCENTAGE") {
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

    if (loading) return;

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
        participants: selectedMemberIds,
        category,
        expenseDate,
        notes: notes.trim(),
        splitType,
      };

      if (splitType === "PERCENTAGE") {
        const pctPayload = {};
        selectedMemberIds.forEach((uid) => {
          pctPayload[String(uid)] = Number(percentages[uid] || 0);
        });
        payload.percentages = pctPayload;
      }

      const addRes = await api.post("/expenses/add", payload);

      const maybeExpenseId =
        addRes?.data?.expenseId ??
        addRes?.data?.id ??
        addRes?.data?.expense?.id ??
        null;

      if (receiptFile) {
        if (!maybeExpenseId) {
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
    <div className="add-expense-page">
      <div className="expense-shell">
        <div className="expense-card">
          <div className="expense-header">
            <div className="expense-badge">FairShare • New Expense</div>
            <h1>Add New Expense</h1>
            <p>
              Choose who participates and how the expense is split between selected
              members.
            </p>
          </div>

          <form onSubmit={handleAddExpense} className="expense-form">
            {error && <div className="error-message">⚠️ {error}</div>}

            <div className="form-section">
              <div className="form-section-title">💸 Expense Details</div>

              <div className="input-group">
                <label>Title</label>
                <input
                  placeholder="e.g. Pizza Night"
                  value={desc}
                  onChange={(e) => setDesc(e.target.value)}
                  required
                />
              </div>

              <div className="row">
                <div className="input-group">
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

                <div className="input-group">
                  <label>Date</label>
                  <input
                    type="date"
                    value={expenseDate}
                    onChange={(e) => setExpenseDate(e.target.value)}
                    required
                  />
                </div>
              </div>
            </div>

            <div className="form-section">
              <div className="form-section-title">👥 Group & Category</div>

              <div className="row">
                <div className="input-group">
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

                <div className="input-group">
                  <label>Category</label>
                  <select
                    className="custom-select"
                    value={category}
                    onChange={(e) => setCategory(e.target.value)}
                  >
                    {categories.map((cat) => (
                      <option key={cat} value={cat}>
                        {cat}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
            </div>

            <div className="form-section">
              <div className="form-section-title">⚖️ Split Settings</div>

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
                <small className="helper-text">
                  Equal split divides the amount evenly. Percentage split lets you define
                  custom shares totaling 100%.
                </small>
              </div>

              <div className="input-group">
                <label>Split Among (Select Members)</label>

                {members.length === 0 ? (
                  <div className="info-box">
                    <small>No members found for this group.</small>
                  </div>
                ) : (
                  <div className="member-list">
                    {members.map((m) => {
                      const mid = m.user.id;
                      const displayName =
                        m.user.name ||
                        m.user.username ||
                        m.user.email ||
                        `User ${mid}`;

                      const checked = selectedMemberIds.includes(mid);

                      return (
                        <div key={mid} className="member-card">
                          <label className="member-left">
                            <input
                              type="checkbox"
                              checked={checked}
                              onChange={() => toggleMember(mid)}
                            />
                            <span className="member-name">{displayName}</span>
                          </label>

                          {splitType === "PERCENTAGE" && checked && (
                            <div className="member-percent-box">
                              <input
                                type="number"
                                step="0.01"
                                min="0"
                                value={percentages[mid] ?? ""}
                                onChange={(e) => handlePercentageChange(mid, e.target.value)}
                                className="percentage-input"
                              />
                              <span className="member-chip">%</span>
                            </div>
                          )}
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>

              <div className="info-box split-preview">
                <small>
                  <b>{selectedMemberIds.length}</b> participant
                  {selectedMemberIds.length === 1 ? "" : "s"} selected
                  {splitType === "EQUAL" && selectedMemberIds.length > 0 && Number(amount || 0) > 0
                    ? ` • $${perHeadAmount.toFixed(2)} each`
                    : ""}
                  {splitType === "PERCENTAGE"
                    ? ` • Percentage total: ${percentageSum.toFixed(2)}%`
                    : ""}
                </small>
              </div>
            </div>

            <div className="form-section">
              <div className="form-section-title">🧾 Receipt & Notes</div>

              <div className="input-group">
                <label>Attach Receipt (Optional)</label>
                <div className="file-box">
                  <input
                    type="file"
                    accept="image/*,application/pdf"
                    onChange={(e) => setReceiptFile(e.target.files?.[0] || null)}
                  />
                  <small className="helper-text">
                    Upload an image or PDF. It will be linked after the expense is created.
                  </small>
                </div>

                {receiptFile && (
                  <div className="info-box">
                    <small>
                      Selected: <b>{receiptFile.name}</b> (
                      {Math.round(receiptFile.size / 1024)} KB)
                    </small>
                  </div>
                )}
              </div>

              <div className="input-group">
                <label>Notes (Optional)</label>
                <textarea
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  placeholder="Add more details about this expense..."
                />
              </div>
            </div>

            <div className="info-box">
              <small>
                FairShare will calculate and store splits based on your selected members
                and split type.
              </small>
            </div>

            <button
              type="submit"
              className="auth-button"
              disabled={loading || groups.length === 0}
            >
              {loading ? "Calculating Splits..." : "Add Expense"}
            </button>

            <button
              type="button"
              className="secondary-btn cancel-btn"
              onClick={() => navigate("/dashboard")}
            >
              Cancel
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}