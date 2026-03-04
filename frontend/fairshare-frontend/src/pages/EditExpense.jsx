import { useState, useEffect, useMemo } from "react";
import { useParams, useNavigate } from "react-router-dom";
import api from "../api/api";
import "./AddExpense.css";

export default function EditExpense() {
  const { id } = useParams();
  const navigate = useNavigate();

  // Existing fields
  const [amount, setAmount] = useState("");
  const [desc, setDesc] = useState("");
  const [category, setCategory] = useState("General");
  const [expenseDate, setExpenseDate] = useState("");
  const [notes, setNotes] = useState("");

  const [groupId, setGroupId] = useState("");

  // Sprint 2 fields
  const [splitType, setSplitType] = useState("EQUAL"); // EQUAL | PERCENTAGE
  const [members, setMembers] = useState([]);
  const [selectedMemberIds, setSelectedMemberIds] = useState([]);
  const [percentages, setPercentages] = useState({}); // { [userId]: number }

  // ✅ Sprint 3: Receipt upload/view
  const [receiptFile, setReceiptFile] = useState(null);
  const [hasReceipt, setHasReceipt] = useState(false);
  const [receiptPreviewUrl, setReceiptPreviewUrl] = useState(""); // optional preview (image/pdf)
  const [receiptLoading, setReceiptLoading] = useState(false);

  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

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
    return Object.values(percentages).reduce(
      (acc, v) => acc + (Number(v) || 0),
      0
    );
  }, [percentages]);

  // ✅ helper: validate receipt (optional)
  const validateReceipt = () => {
    if (!receiptFile) return "";
    const type = receiptFile.type || "";
    const isImage = type.startsWith("image/");
    const isPdf = type === "application/pdf";
    if (!isImage && !isPdf) return "Receipt must be an image or a PDF.";
    const maxBytes = 10 * 1024 * 1024; // 10MB
    if (receiptFile.size > maxBytes) return "Receipt file is too large (max 10MB).";
    return "";
  };

  useEffect(() => {
    const fetchExpenseData = async () => {
      try {
        // 1) Expense basic data
        const res = await api.get(`/expenses/${id}`);
        const exp = res.data;

        setAmount(exp.amount);
        setDesc(exp.description);
        setGroupId(exp.groupId);
        setCategory(exp.category || "General");
        setNotes(exp.notes || "");
        if (exp.expenseDate) setExpenseDate(exp.expenseDate);

        // ✅ Receipt presence check (works if backend includes receiptUrl/receiptPath/hasReceipt)
        // If your backend doesn't return these yet, it will just stay false.
        const possibleReceiptUrl =
          exp.receiptUrl || exp.receiptURL || exp.receiptPath || exp.receipt || "";
        if (possibleReceiptUrl) {
          setHasReceipt(true);
          setReceiptPreviewUrl(possibleReceiptUrl);
        } else if (typeof exp.hasReceipt === "boolean") {
          setHasReceipt(exp.hasReceipt);
        } else {
          setHasReceipt(false);
        }

        // 2) Load group members for checkbox list
        const memberRes = await api.get(`/groups/group-members/${exp.groupId}`);
        const memberList = memberRes.data || [];
        setMembers(memberList);

        // 3) Load split info for editing (participants + percentages + splitType)
        // If this endpoint doesn't exist yet, we'll fallback gracefully.
        let splitInfo = null;
        try {
          const splitRes = await api.get(`/expenses/${id}/splits`);
          splitInfo = splitRes.data;
        } catch (e) {
          splitInfo = null;
        }

        const allMemberIds = memberList.map((m) => m.user.id);

        if (splitInfo && splitInfo.participants && splitInfo.participants.length > 0) {
          setSelectedMemberIds(splitInfo.participants);

          const st = (splitInfo.splitType || "EQUAL").toUpperCase();
          setSplitType(st === "PERCENTAGE" ? "PERCENTAGE" : "EQUAL");

          if (st === "PERCENTAGE" && splitInfo.percentages) {
            const pctMap = {};
            Object.keys(splitInfo.percentages).forEach((k) => {
              pctMap[Number(k)] = splitInfo.percentages[k];
            });
            setPercentages(pctMap);
          } else {
            const evenPct = allMemberIds.length > 0 ? 100 / allMemberIds.length : 0;
            const pctMap = {};
            (splitInfo.participants || allMemberIds).forEach((uid) => {
              pctMap[uid] = Number(evenPct.toFixed(2));
            });
            setPercentages(pctMap);
          }
        } else {
          setSplitType("EQUAL");
          setSelectedMemberIds(allMemberIds);

          const evenPct = allMemberIds.length > 0 ? 100 / allMemberIds.length : 0;
          const pctMap = {};
          allMemberIds.forEach((uid) => (pctMap[uid] = Number(evenPct.toFixed(2))));
          setPercentages(pctMap);
        }

        setLoading(false);
      } catch (err) {
        console.error("Error loading expense:", err);
        setError("Failed to load expense details.");
        setLoading(false);
      }
    };

    fetchExpenseData();

    // cleanup preview blob url if we set it later
    return () => {
      if (receiptPreviewUrl && receiptPreviewUrl.startsWith("blob:")) {
        URL.revokeObjectURL(receiptPreviewUrl);
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const toggleMember = (memberId) => {
    setSelectedMemberIds((prev) => {
      const exists = prev.includes(memberId);
      const next = exists ? prev.filter((x) => x !== memberId) : [...prev, memberId];

      // Keep percentages in sync
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

  const handlePercentageChange = (uid, value) => {
    setPercentages((prev) => ({
      ...prev,
      [uid]: value === "" ? "" : Number(value),
    }));
  };

  const validate = () => {
    if (!desc || desc.trim().length === 0) return "Please enter a title.";
    if (!amount || Number(amount) <= 0) return "Amount must be greater than zero.";
    if (!expenseDate) return "Please select a date.";
    if (!selectedMemberIds || selectedMemberIds.length === 0)
      return "Please select at least one participant.";

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

  // ✅ Upload/replace receipt (optional separate action)
  const handleUploadReceipt = async () => {
    if (!receiptFile) {
      setError("Please choose a receipt file first.");
      return;
    }

    const receiptErr = validateReceipt();
    if (receiptErr) {
      setError(receiptErr);
      return;
    }

    try {
      setReceiptLoading(true);
      setError("");

      const formData = new FormData();
      formData.append("file", receiptFile);

      // ✅ backend endpoint you should have:
      // POST /api/expenses/{id}/receipt
      await api.post(`/expenses/${id}/receipt`, formData, {
        headers: { "Content-Type": "multipart/form-data" },
      });

      // show local preview immediately
      const localUrl = URL.createObjectURL(receiptFile);
      setReceiptPreviewUrl(localUrl);
      setHasReceipt(true);
      setReceiptFile(null);
    } catch (err) {
      console.error(err);
      setError(
        err?.response?.data?.error ||
          err?.response?.data ||
          "Receipt upload failed. Please check backend endpoint."
      );
    } finally {
      setReceiptLoading(false);
    }
  };

  const handleUpdate = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError("");

    const validationError = validate();
    if (validationError) {
      setError(validationError);
      setSubmitting(false);
      return;
    }

    try {
      const payload = {
        amount: parseFloat(amount),
        description: desc.trim(),
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

      await api.put(`/expenses/edit/${id}`, payload);

      // ✅ optional: if user selected a receipt file, upload it after saving changes
      if (receiptFile) {
        const formData = new FormData();
        formData.append("file", receiptFile);
        await api.post(`/expenses/${id}/receipt`, formData, {
          headers: { "Content-Type": "multipart/form-data" },
        });

        const localUrl = URL.createObjectURL(receiptFile);
        setReceiptPreviewUrl(localUrl);
        setHasReceipt(true);
        setReceiptFile(null);
      }

      navigate(-1);
    } catch (err) {
      console.error(err);
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
      <div
        className="auth-card animate-fade-in"
        style={{ maxWidth: "650px", margin: "2rem auto" }}
      >
        <div className="auth-header">
          <h1>Edit Expense</h1>
          <p>Update the transaction details, participants, and split type.</p>
        </div>

        <form onSubmit={handleUpdate} className="auth-form">
          {error && <div className="error-message">⚠️ {error}</div>}

          {/* Title */}
          <div className="input-group">
            <label>Title</label>
            <input
              type="text"
              placeholder="e.g., Dinner at Mario's"
              value={desc}
              onChange={(e) => setDesc(e.target.value)}
              required
            />
          </div>

          <div className="row" style={{ display: "flex", gap: "15px" }}>
            {/* Amount */}
            <div className="input-group" style={{ flex: 1 }}>
              <label>Amount ($)</label>
              <input
                type="number"
                step="0.01"
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
            {/* Category */}
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

            {/* Split Type */}
            <div className="input-group" style={{ flex: 1 }}>
              <label>Split Type</label>
              <select
                className="custom-select"
                value={splitType}
                onChange={(e) => setSplitType(e.target.value)}
              >
                <option value="EQUAL">Equal Split</option>
                <option value="PERCENTAGE">Percentage Split</option>
              </select>
            </div>
          </div>

          {/* Participants + Percentages */}
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
                    m.user.name || m.user.username || m.user.email || `User ${mid}`;
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

            <div
              className="info-box"
              style={{
                background: "#f8fafc",
                padding: "10px",
                borderRadius: "8px",
                marginTop: "10px",
              }}
            >
              <small>
                Splitting between <b>{selectedMemberIds.length}</b> selected member(s).
              </small>
            </div>
          </div>

          {/* Notes */}
          <div className="input-group">
            <label>Notes (Optional)</label>
            <textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Add more details..."
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

          {/* ✅ Receipt Upload Section */}
          <div className="input-group">
            <label>Receipt (Image/PDF)</label>

            {hasReceipt ? (
              <div className="info-box" style={{ marginBottom: "10px" }}>
                <small>
                  Receipt attached ✅{" "}
                  {receiptPreviewUrl ? (
                    <>
                      {" "}
                      •{" "}
                      <a href={receiptPreviewUrl} target="_blank" rel="noreferrer">
                        View
                      </a>
                    </>
                  ) : null}
                </small>
              </div>
            ) : (
              <div className="info-box" style={{ marginBottom: "10px" }}>
                <small>No receipt attached yet.</small>
              </div>
            )}

            <input
              type="file"
              accept="image/*,application/pdf"
              onChange={(e) => setReceiptFile(e.target.files?.[0] || null)}
            />

            {receiptFile && (
              <div className="info-box" style={{ marginTop: "10px" }}>
                <small>
                  Selected: <b>{receiptFile.name}</b> ({Math.round(receiptFile.size / 1024)} KB)
                </small>
              </div>
            )}

            {/* Optional explicit upload button (works even without saving other fields) */}
            <button
              type="button"
              className="secondary-btn"
              style={{
                width: "100%",
                marginTop: "10px",
                background: "white",
                color: "#0f172a",
                border: "1px solid #e2e8f0",
              }}
              disabled={!receiptFile || receiptLoading}
              onClick={handleUploadReceipt}
            >
              {receiptLoading ? "Uploading Receipt..." : "Upload / Replace Receipt"}
            </button>

            <small style={{ color: "#64748b", display: "block", marginTop: "8px" }}>
              This uses: <b>POST /api/expenses/{id}/receipt</b> with multipart field <b>file</b>.
            </small>
          </div>

          <button type="submit" className="auth-button" disabled={submitting}>
            {submitting ? "Updating..." : "Save Changes"}
          </button>

          <button
            type="button"
            className="delete-btn-outline"
            style={{
              width: "100%",
              marginTop: "10px",
              background: "#fee2e2",
              color: "#dc2626",
              border: "none",
              padding: "0.75rem",
              borderRadius: "8px",
              fontWeight: "bold",
              cursor: "pointer",
            }}
            onClick={handleDelete}
          >
            Delete Expense
          </button>

          <button
            type="button"
            className="secondary-btn"
            style={{
              width: "100%",
              marginTop: "10px",
              background: "white",
              color: "#64748b",
              border: "1px solid #cbd5e1",
            }}
            onClick={() => navigate(-1)}
          >
            Cancel
          </button>
        </form>
      </div>
    </div>
  );
}