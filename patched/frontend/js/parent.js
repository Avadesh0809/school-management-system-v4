/* Parent self-service read-only views.
   All endpoints derive the parent identity from the JWT — no studentId is ever
   passed from the client by default. Parents NEVER see create/edit/delete UI. */
(function () {
  const fmtMoney = v => '₹' + (Number(v || 0)).toLocaleString();
  const badge = (txt, cls) => `<span class="badge ${cls}">${UI.escapeHtml(txt || '')}</span>`;
  const statusCls = s => s==='PRESENT'||s==='PAID' ? 'success'
    : s==='ABSENT'||s==='OVERDUE' ? 'danger'
    : s==='LATE'||s==='PENDING' ? 'warn' : 'info';

  function panel(title, body) {
    return `<div class="card" style="margin-bottom:16px;">
      <div class="card-header"><div class="card-title">${UI.escapeHtml(title)}</div></div>
      <div class="card-body">${body}</div>
    </div>`;
  }

  function table(headers, rows) {
    if (!rows || !rows.length) return '<div class="muted" style="padding:12px;">No records.</div>';
    return `<div style="overflow-x:auto;"><table class="data-table"><thead><tr>
      ${headers.map(h=>`<th>${UI.escapeHtml(h)}</th>`).join('')}
    </tr></thead><tbody>
      ${rows.map(r=>`<tr>${r.map(c=>`<td>${c==null?'':c}</td>`).join('')}</tr>`).join('')}
    </tbody></table></div>`;
  }

  async function children() {
    const res = await API.get('/parent/me/children');
    return res && (res.data || res) || [];
  }

  async function renderAttendance(root) {
    root.innerHTML = '<div class="spinner"></div>';
    try {
      const [kids, res] = await Promise.all([children(), API.get('/parent/me/attendance')]);
      const data = res.data || res || {};
      const summary = data.summary || [];
      const records = data.records || [];
      const kidName = id => (kids.find(k=>k.id===id) || {}).name || ('#'+id);

      const summaryHtml = panel('Attendance Summary',
        table(['Child','Attendance %'],
          summary.map(s => [UI.escapeHtml(kidName(s.studentId)), (s.percentage||0).toFixed(1)+'%'])
        )
      );
      const recHtml = panel('Recent Attendance',
        table(['Child','Date','Status','Remarks'],
          records.slice(0, 100).map(r => [
            UI.escapeHtml(kidName(r.studentId)),
            UI.escapeHtml(r.record?.date || ''),
            badge(r.record?.status, statusCls(r.record?.status)),
            UI.escapeHtml(r.record?.remarks || '')
          ])
        )
      );
      root.innerHTML = summaryHtml + recHtml;
    } catch (e) { root.innerHTML = `<div class="alert error">Failed to load: ${UI.escapeHtml(e.message)}</div>`; }
  }

  async function renderMarks(root) {
    root.innerHTML = '<div class="spinner"></div>';
    try {
      const [kids, res] = await Promise.all([children(), API.get('/parent/me/marks')]);
      const groups = res.data || res || [];
      const kidName = id => (kids.find(k=>k.id===id) || {}).name || ('#'+id);
      root.innerHTML = groups.map(g => panel(`Marks — ${kidName(g.studentId)}`,
        table(['Exam','Subject','Marks','Max','Grade'],
          (g.marks||[]).map(m => [
            UI.escapeHtml(m.examName||''),
            UI.escapeHtml(m.subjectName||''),
            m.marksObtained, m.maxMarks, UI.escapeHtml(m.grade||'')
          ])
        )
      )).join('') || '<div class="muted">No marks available.</div>';
    } catch (e) { root.innerHTML = `<div class="alert error">Failed to load: ${UI.escapeHtml(e.message)}</div>`; }
  }

  async function renderFees(root) {
    root.innerHTML = '<div class="spinner"></div>';
    try {
      const [kids, res] = await Promise.all([children(), API.get('/parent/me/fees')]);
      const groups = res.data || res || [];
      const kidName = id => (kids.find(k=>k.id===id) || {}).name || ('#'+id);
      root.innerHTML = groups.map(g => {
        const items = g.fees || [];
        const pending = items.filter(f=>f.status!=='PAID').reduce((a,f)=>a+(Number(f.amount||0)-Number(f.amountPaid||0)),0);
        return panel(`Fees — ${kidName(g.studentId)} (Outstanding: ${fmtMoney(pending)})`,
          table(['Term','Amount','Paid','Due','Status'],
            items.map(f => [
              UI.escapeHtml(f.term||''), fmtMoney(f.amount), fmtMoney(f.amountPaid),
              UI.escapeHtml(f.dueDate||''), badge(f.status, statusCls(f.status))
            ])
          )
        );
      }).join('') || '<div class="muted">No fee records.</div>';
    } catch (e) { root.innerHTML = `<div class="alert error">Failed to load: ${UI.escapeHtml(e.message)}</div>`; }
  }

  async function renderAssignments(root) {
    root.innerHTML = '<div class="spinner"></div>';
    try {
      const res = await API.get('/parent/me/assignments');
      const groups = res.data || res || [];
      root.innerHTML = groups.map(g => panel(
        `Assignments — ${UI.escapeHtml(g.studentName||'')} (${g.submittedCount||0}/${g.totalCount||0} submitted)`,
        table(['Title','Subject','Due Date'],
          (g.pending||[]).map(a => [
            UI.escapeHtml(a.title||''),
            UI.escapeHtml(a.subjectName||''),
            UI.escapeHtml(a.dueDate||'')
          ])
        )
      )).join('') || '<div class="muted">No assignments.</div>';
    } catch (e) { root.innerHTML = `<div class="alert error">Failed to load: ${UI.escapeHtml(e.message)}</div>`; }
  }

  async function renderExams(root) {
    root.innerHTML = '<div class="spinner"></div>';
    try {
      const res = await API.get('/parent/me/exams');
      const items = res.data || res || [];
      root.innerHTML = panel('Upcoming Exams',
        table(['Name','Academic Year','Start','End'],
          items.map(e => [
            UI.escapeHtml(e.name||''), UI.escapeHtml(e.academicYear||''),
            UI.escapeHtml(e.startDate||''), UI.escapeHtml(e.endDate||'')
          ])
        )
      );
    } catch (e) { root.innerHTML = `<div class="alert error">Failed to load: ${UI.escapeHtml(e.message)}</div>`; }
  }

  async function renderNotices(root) {
    root.innerHTML = '<div class="spinner"></div>';
    try {
      const res = await API.get('/parent/me/notices');
      const items = res.data || res || [];
      root.innerHTML = panel('Recent Notices',
        table(['Title','Published','Message'],
          items.slice(0,50).map(n => [
            UI.escapeHtml(n.title||''),
            UI.escapeHtml(n.publishedOn||''),
            UI.escapeHtml((n.message||'').slice(0,160))
          ])
        )
      );
    } catch (e) { root.innerHTML = `<div class="alert error">Failed to load: ${UI.escapeHtml(e.message)}</div>`; }
  }

  window.ParentView = {
    attendance: renderAttendance,
    marks: renderMarks,
    fees: renderFees,
    assignments: renderAssignments,
    exams: renderExams,
    notices: renderNotices,
    children: async function (root) {
      root.innerHTML = '<div class="spinner"></div>';
      try {
        const kids = await children();
        root.innerHTML = panel('My Children',
          table(['Name','Admission No.','Class','Section'],
            kids.map(k => [UI.escapeHtml(k.name||''), UI.escapeHtml(k.admissionNo||''),
              UI.escapeHtml(k.className||''), UI.escapeHtml(k.section||'')])
          )
        );
      } catch (e) { root.innerHTML = `<div class="alert error">Failed to load: ${UI.escapeHtml(e.message)}</div>`; }
    }
  };
})();
