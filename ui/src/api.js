/* Thin wrapper over the REST API: attaches Basic-auth from sessionStorage
   to every call so we never rely on the browser's native auth prompt. */

class ApiError extends Error {
  constructor(message, status, body) {
    super(message);
    this.status = status;
    this.body = body;
  }
}

export const Api = (() => {
  const KEY = "admissions.auth";
  const BASE = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");

  function creds() {
    const raw = sessionStorage.getItem(KEY);
    return raw ? JSON.parse(raw) : null;
  }
  function setCreds(username, password) {
    sessionStorage.setItem(KEY, JSON.stringify({ username, password }));
  }
  function clearCreds() {
    sessionStorage.removeItem(KEY);
  }
  function authHeader() {
    const c = creds();
    return c ? { Authorization: "Basic " + btoa(c.username + ":" + c.password) } : {};
  }

  async function request(path, opts = {}) {
    const headers = Object.assign({}, authHeader(), opts.headers || {});
    const isForm = typeof FormData !== "undefined" && opts.body instanceof FormData;
    if (opts.body && !isForm && !headers["Content-Type"]) {
      headers["Content-Type"] = "application/json";
    }
    const res = await fetch(BASE + path, Object.assign({}, opts, { headers }));

    let body = null;
    const text = await res.text();
    if (text) {
      try { body = JSON.parse(text); } catch { body = text; }
    }

    if (res.status === 401) {
      clearCreds();
      throw new ApiError("Your session ended. Sign in again.", 401, body);
    }
    if (!res.ok) {
      const message = (body && (body.detail || body.title)) || res.statusText || "Something went wrong.";
      throw new ApiError(message, res.status, body);
    }
    return body;
  }

  return {
    creds, setCreds, clearCreds,

    async signIn(username, password) {
      setCreds(username, password);
      try {
        const me = await request("/api/v1/me");
        return me;
      } catch (e) {
        clearCreds();
        throw e;
      }
    },

    me: () => request("/api/v1/me"),

    // public parent / guardian registration
    parentRegistrationRequestOtp: (data) => request("/api/v1/public/parent-registration/request-otp", { method: "POST", body: JSON.stringify(data) }),
    parentRegistrationComplete: (data) => request("/api/v1/public/parent-registration/complete", { method: "POST", body: JSON.stringify(data) }),

    // leads
    listLeads: () => request("/api/v1/leads"),
    createLead: (data) => request("/api/v1/leads", { method: "POST", body: JSON.stringify(data) }),
    assignLead: (id, userId) => request(`/api/v1/leads/${id}/assign`, { method: "POST", body: JSON.stringify({ userId }) }),
    followupLead: (id, dueAt, note) => request(`/api/v1/leads/${id}/followups`, { method: "POST", body: JSON.stringify({ dueAt, note }) }),
    loseLead: (id, reason) => request(`/api/v1/leads/${id}/lost`, { method: "POST", body: JSON.stringify(reason || "") }),
    convertLead: (id, academicYear) => request(`/api/v1/leads/${id}/convert?academicYear=${encodeURIComponent(academicYear)}`, { method: "POST" }),

    // applications
    listApplications: (stage) => request(`/api/v1/applications${stage ? "?stage=" + encodeURIComponent(stage) : ""}`),
    startApplication: (academicYear, classCode) => request("/api/v1/applications", { method: "POST", body: JSON.stringify({ academicYear, classCode }) }),
    getApplication: (id) => request(`/api/v1/applications/${id}`),
    saveApplication: (id, version, patch) => request(`/api/v1/applications/${id}`, {
      method: "PATCH",
      headers: version != null ? { "If-Match": String(version) } : {},
      body: JSON.stringify(patch),
    }),
    submitApplication: (id) => request(`/api/v1/applications/${id}/submit`, { method: "POST" }),
    availableTransitions: (id) => request(`/api/v1/applications/${id}/transitions/available`),
    transition: (id, toStage, reason, expectedVersion) => request(`/api/v1/applications/${id}/transitions`, {
      method: "POST",
      body: JSON.stringify({ toStage, reason, expectedVersion }),
    }),

    // documents
    listDocuments: (appId) => request(`/api/v1/applications/${appId}/documents`),
    uploadDocument: (appId, docType, file) => {
      const fd = new FormData();
      fd.append("docType", docType);
      fd.append("file", file);
      return request(`/api/v1/applications/${appId}/documents`, { method: "POST", body: fd });
    },
    verifyDocument: (docId, status, reason) => request(`/api/v1/documents/${docId}/verification`, {
      method: "PUT",
      body: JSON.stringify({ status, reason }),
    }),

    // scheduling
    listSlots: () => request("/api/v1/interaction-slots"),
    bookSlot: (slotId, applicationId) => request(`/api/v1/interaction-slots/${slotId}/bookings`, {
      method: "POST",
      body: JSON.stringify({ applicationId }),
    }),

    // assessment
    recordAssessment: (appId, kind, score, remarks) => request(`/api/v1/applications/${appId}/assessments`, {
      method: "POST",
      body: JSON.stringify({ kind, score, remarks }),
    }),

    // payments
    listInvoices: (appId) => request(`/api/v1/applications/${appId}/invoices`),
    createPayment: (invoiceId, method) => request(`/api/v1/invoices/${invoiceId}/payments`, {
      method: "POST",
      headers: { "Idempotency-Key": crypto.randomUUID() },
      body: JSON.stringify({ method }),
    }),
    devCapture: (paymentId) => request(`/api/v1/dev/payments/${paymentId}/capture`, { method: "POST" }),

    // messages
    listMessages: (appId) => request(`/api/v1/applications/${appId}/messages`),



    // class catalogue & seats
    createClass: (data) => request('/api/v1/classes', { method:'POST', body:JSON.stringify(data) }),
    updateClass: (id,data) => request(`/api/v1/classes/${id}`, { method:'PUT', body:JSON.stringify(data) }),
    deleteClass: (id) => request(`/api/v1/classes/${id}`, { method:'DELETE' }),

    // full school ERP
    erpDashboard: (academicYear) => request(`/api/v1/erp/dashboard?academicYear=${encodeURIComponent(academicYear)}`),
    erpStudents: (academicYear, classCode) => request(`/api/v1/erp/students?academicYear=${encodeURIComponent(academicYear)}${classCode ? `&classCode=${encodeURIComponent(classCode)}` : ""}`),
    erpCreateStudent: (data) => request('/api/v1/erp/students', { method:'POST', body:JSON.stringify(data) }),
    erpStudent: (id) => request(`/api/v1/erp/students/${id}`),
    erpUpdateStudent: (id, data) => request(`/api/v1/erp/students/${id}`, { method:'PUT', body:JSON.stringify(data) }),
    erpUpdateStudentStatus: (id, status) => request(`/api/v1/erp/students/${id}/status`, { method:'PATCH', body:JSON.stringify({status}) }),
    erpDeleteStudent: (id) => request(`/api/v1/erp/students/${id}`, { method:'DELETE' }),
    otpRequest: (studentId, channel) => request('/api/v1/otp/request', { method:'POST', body:JSON.stringify({studentId,channel}) }),
    otpVerify: (challengeId, otp) => request('/api/v1/otp/verify', { method:'POST', body:JSON.stringify({challengeId,otp}) }),
    otpStatus: (studentId) => request(`/api/v1/otp/status/${studentId}`),
    erpAttendance: (academicYear, classCode, date) => request(`/api/v1/erp/attendance?academicYear=${encodeURIComponent(academicYear)}${classCode ? `&classCode=${encodeURIComponent(classCode)}` : ""}${date ? `&date=${encodeURIComponent(date)}` : ""}`),
    erpSaveAttendance: (date, rows) => request('/api/v1/erp/attendance', { method:'POST', body:JSON.stringify({date, rows}) }),
    erpFees: (academicYear, classCode) => request(`/api/v1/erp/fees?academicYear=${encodeURIComponent(academicYear)}${classCode ? `&classCode=${encodeURIComponent(classCode)}` : ""}`),
    erpCreateFee: (data) => request('/api/v1/erp/fees', { method:'POST', body:JSON.stringify(data) }),
    erpUpdateFee: (id, data) => request(`/api/v1/erp/fees/${id}`, { method:'PUT', body:JSON.stringify(data) }),
    erpWaiveFee: (id) => request(`/api/v1/erp/fees/${id}/waive`, { method:'POST' }),
    erpDeleteFee: (id) => request(`/api/v1/erp/fees/${id}`, { method:'DELETE' }),
    erpRecordFeePayment: (id, amount, mode, reference) => request(`/api/v1/erp/fees/${id}/payment`, { method:'POST', body:JSON.stringify({amount,mode,reference}) }),
    erpTimetable: (academicYear, classCode) => request(`/api/v1/erp/timetable?academicYear=${encodeURIComponent(academicYear)}${classCode ? `&classCode=${encodeURIComponent(classCode)}` : ""}`),
    erpSaveTimetable: (academicYear, classCode, entries) => request('/api/v1/erp/timetable', { method:'POST', body:JSON.stringify({academicYear,classCode,entries}) }),
    erpTeachers: () => request('/api/v1/erp/teachers'),
    erpExams: (academicYear) => request(`/api/v1/erp/exams?academicYear=${encodeURIComponent(academicYear)}`),
    erpCreateExam: (data) => request('/api/v1/erp/exams', { method:'POST', body:JSON.stringify(data) }),
    erpUpdateExam: (id, data) => request(`/api/v1/erp/exams/${id}`, { method:'PUT', body:JSON.stringify(data) }),
    erpSaveExamSubjects: (examId, academicYear, classCode, subjects) => request(`/api/v1/erp/exams/${examId}/subjects`, { method:'POST', body:JSON.stringify({academicYear,classCode,subjects}) }),
    erpDeleteExamSubject: (subjectId) => request(`/api/v1/erp/exam-subjects/${subjectId}`, { method:'DELETE' }),
    erpMarks: (examId, academicYear, classCode) => request(`/api/v1/erp/marks?examId=${encodeURIComponent(examId)}&academicYear=${encodeURIComponent(academicYear)}&classCode=${encodeURIComponent(classCode)}`),
    erpSaveMarks: (rows) => request('/api/v1/erp/marks', { method:'POST', body:JSON.stringify({rows}) }),
    erpReportCards: (academicYear) => request(`/api/v1/erp/report-cards?academicYear=${encodeURIComponent(academicYear)}`),
    erpTransport: () => request('/api/v1/erp/transport'),
    erpCreateTransportRoute: (data) => request('/api/v1/erp/transport/routes', {method:'POST', body:JSON.stringify(data)}),
    erpUpdateTransportRoute: (id,data) => request(`/api/v1/erp/transport/routes/${id}`, {method:'PUT', body:JSON.stringify(data)}),
    erpDeleteTransportRoute: (id) => request(`/api/v1/erp/transport/routes/${id}`, {method:'DELETE'}),
    erpCreateTransportStop: (routeId,data) => request(`/api/v1/erp/transport/routes/${routeId}/stops`, {method:'POST', body:JSON.stringify(data)}),
    erpUpdateTransportStop: (routeId,stopId,data) => request(`/api/v1/erp/transport/routes/${routeId}/stops/${stopId}`, {method:'PUT', body:JSON.stringify(data)}),
    erpDeleteTransportStop: (routeId,stopId) => request(`/api/v1/erp/transport/routes/${routeId}/stops/${stopId}`, {method:'DELETE'}),
    erpAssignTransport: (data) => request('/api/v1/erp/transport/allocations', {method:'POST', body:JSON.stringify(data)}),
    erpUnassignTransport: (studentId) => request(`/api/v1/erp/transport/allocations/student/${studentId}`, {method:'DELETE'}),
    erpUsers: () => request('/api/v1/erp/users'),
    erpNotifications: () => request('/api/v1/erp/notifications'),
    erpCreateNotification: (data) => request('/api/v1/erp/notifications', {method:'POST', body:JSON.stringify(data)}),
    erpReadNotification: (id) => request(`/api/v1/erp/notifications/${id}/read`, {method:'POST'}),
    erpReadAllNotifications: () => request('/api/v1/erp/notifications/read-all', {method:'POST'}),
    erpCertificates: () => request('/api/v1/erp/certificates'),
    erpIssueCertificate: (data) => request('/api/v1/erp/certificates', {method:'POST', body:JSON.stringify(data)}),
    erpDownloadCsv: async (kind, academicYear) => {
      const res = await fetch(`${BASE}/api/v1/erp/export/${encodeURIComponent(kind)}?academicYear=${encodeURIComponent(academicYear)}`, { headers: authHeader() });
      if (!res.ok) throw new ApiError('Could not download report.', res.status, await res.text());
      const blob = await res.blob();
      const cd = res.headers.get('content-disposition') || '';
      const match = cd.match(/filename=\?"?([^";]+)\?"?/i);
      const filename = match ? match[1] : `${kind}-${academicYear}.csv`;
      const url = URL.createObjectURL(blob); const a=document.createElement('a'); a.href=url; a.download=filename; document.body.appendChild(a); a.click(); a.remove(); URL.revokeObjectURL(url);
    },

    // catalog & reports
    currentForm: () => request("/api/v1/forms/current"),
    listClasses: (academicYear) => request(`/api/v1/classes?academicYear=${encodeURIComponent(academicYear)}`),
    reportSeats: (academicYear) => request(`/api/v1/reports/seats?academicYear=${encodeURIComponent(academicYear)}`),
    reportSources: () => request("/api/v1/reports/sources"),
    reportFunnel: (academicYear, classCode) => request(`/api/v1/reports/funnel?academicYear=${encodeURIComponent(academicYear)}&classCode=${encodeURIComponent(classCode)}`),
    reportOverview: (academicYear) => request(`/api/v1/reports/overview?academicYear=${encodeURIComponent(academicYear)}`),
    reportStages: (academicYear) => request(`/api/v1/reports/stages?academicYear=${encodeURIComponent(academicYear)}`),
    reportClassDemand: (academicYear) => request(`/api/v1/reports/class-demand?academicYear=${encodeURIComponent(academicYear)}`),
    downloadReport: async (academicYear, report) => {
      const res = await fetch(`${BASE}/api/v1/reports/export?academicYear=${encodeURIComponent(academicYear)}&report=${encodeURIComponent(report)}`, {
        headers: authHeader(),
      });
      if (!res.ok) throw new ApiError("Could not download report.", res.status, await res.text());
      const blob = await res.blob();
      const disposition = res.headers.get("content-disposition") || "";
      const match = disposition.match(/filename=\?"?([^";]+)\?"?/i);
      const filename = match ? match[1] : `admissions-${report}-${academicYear}.csv`;
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url; a.download = filename; document.body.appendChild(a); a.click(); a.remove();
      URL.revokeObjectURL(url);
    },
  };
})();
