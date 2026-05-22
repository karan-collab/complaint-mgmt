/**
 * Domain layer for the SocietyCare UI.
 *
 * This module used to be a localStorage-backed mock; it is now a thin adapter
 * over the Spring Boot REST API (scripts/api.js).
 *
 * Views call the same surface as before:
 *
 *   CATEGORIES, DISPLAY_STATUSES, normalizeFlat, getDisplayStatus
 *
 * plus async helpers that return promises and resolve to domain objects shaped
 * exactly like the old in-memory complaints (so view code keeps working with
 * minimal changes):
 *
 *   {
 *     id, flat, category, description,
 *     status,                // "Active" | "Complete"
 *     worker: { name, profession, phone, id } | null,
 *     createdAt, assignedAt, completedAt
 *   }
 */
(function () {
  const api = window.CM.api;
  const session = window.CM.session;

  const CATEGORIES = ['Plumber', 'Carpenter', 'Electrician', 'Painting', 'Others'];
  const DISPLAY_STATUSES = ['Assignment Pending', 'Pending Work', 'Completed'];
  const DEMO_FLAT = 'A-101';

  // ------------------------------------------------------------------ utils

  function normalizeFlat(flat) {
    return String(flat || '').trim().toUpperCase();
  }

  function categoryIdToLabel(id) {
    if (!id) return 'Others';
    const map = {
      PLUMBER: 'Plumber',
      CARPENTER: 'Carpenter',
      ELECTRICIAN: 'Electrician',
      PAINTING: 'Painting',
      OTHERS: 'Others',
    };
    return map[String(id).toUpperCase()] || String(id);
  }

  function categoryLabelToId(label) {
    if (!label) return null;
    const map = {
      Plumber: 'PLUMBER',
      Carpenter: 'CARPENTER',
      Electrician: 'ELECTRICIAN',
      Painting: 'PAINTING',
      Others: 'OTHERS',
    };
    return map[String(label).trim()] || String(label).toUpperCase();
  }

  function mapComplaint(dto) {
    if (!dto) return null;
    const categoryLabel = categoryIdToLabel(dto.category);
    const backendStatus = dto.status && dto.status.name;
    const isComplete = backendStatus === 'Complete';
    const worker = dto.professional
      ? {
          id: dto.professional.id,
          name: dto.professional.name,
          phone: dto.professional.phone,
          profession: categoryIdToLabel(dto.professional.category),
        }
      : null;
    return {
      id: dto.id,
      flat: dto.flatNo,
      residentName: dto.residentName || null,
      category: categoryLabel,
      description: dto.description,
      status: isComplete ? 'Complete' : 'Active',
      worker,
      createdAt: dto.createdAt,
      assignedAt: dto.assignedAt,
      completedAt: dto.completedAt,
    };
  }

  function getDisplayStatus(c) {
    if (!c) return 'Assignment Pending';
    if (c.status === 'Complete') return 'Completed';
    return c.worker ? 'Pending Work' : 'Assignment Pending';
  }

  // ------------------------------------------------------------- auth/me

  function loginResident({ flatNo, password }) {
    return api.post('/auth/resident/login', {
      flatNo: normalizeFlat(flatNo),
      password: String(password || ''),
    });
  }

  function loginAdmin({ username, password }) {
    return api.post('/auth/admin/login', {
      username: String(username || '').trim(),
      password: String(password || ''),
    });
  }

  function fetchMe() {
    return api.get('/auth/me');
  }

  function changeMyPassword({ currentPassword, newPassword }) {
    return api.post('/auth/me/password', {
      currentPassword: String(currentPassword || ''),
      newPassword: String(newPassword || ''),
    });
  }

  // -------------------------------------------------------- complaints

  async function getComplaints(flat) {
    const list = await api.get(
      flat ? `/complaints?flat=${encodeURIComponent(normalizeFlat(flat))}` : '/complaints'
    );
    return (list || []).map(mapComplaint);
  }

  async function getAllComplaints() {
    const list = await api.get('/complaints');
    return (list || []).map(mapComplaint);
  }

  async function getComplaintById(id) {
    const dto = await api.get(`/complaints/${encodeURIComponent(id)}`);
    return mapComplaint(dto);
  }

  async function addComplaint({ category, description }) {
    const dto = await api.post('/complaints', {
      category: categoryLabelToId(category),
      description: String(description || '').trim(),
    });
    return mapComplaint(dto);
  }

  async function assignWorker(id, { professionalId }) {
    const dto = await api.post(`/complaints/${encodeURIComponent(id)}/assign`, {
      professionalId,
    });
    return mapComplaint(dto);
  }

  async function markComplete(id) {
    const dto = await api.post(`/complaints/${encodeURIComponent(id)}/complete`);
    return mapComplaint(dto);
  }

  // ------------------------------------------------------- professionals

  async function listProfessionals(category) {
    const id = category ? categoryLabelToId(category) : null;
    const path = id ? `/professionals?category=${encodeURIComponent(id)}` : '/professionals';
    const list = await api.get(path);
    return (list || []).map((p) => ({
      id: p.id,
      name: p.name,
      phone: p.phone,
      category: categoryIdToLabel(p.category),
    }));
  }

  async function createProfessional({ name, phone, category }) {
    const dto = await api.post('/professionals', {
      name: String(name || '').trim(),
      phone: String(phone || '').trim(),
      category: categoryLabelToId(category),
    });
    return {
      id: dto.id,
      name: dto.name,
      phone: dto.phone,
      category: categoryIdToLabel(dto.category),
    };
  }

  async function deleteProfessional(id) {
    await api.delete(`/professionals/${encodeURIComponent(id)}`);
  }

  // ------------------------------------------------------- admin residents

  async function listResidents() {
    const list = await api.get('/admin/residents');
    return (list || []).map((r) => ({
      id: r.id,
      flat: r.flatNo,
      name: r.name,
      phone: r.phone || null,
    }));
  }

  async function createResident({ name, flatNo, phone, password }) {
    const dto = await api.post('/admin/residents', {
      name: String(name || '').trim(),
      flatNo: normalizeFlat(flatNo),
      phone: phone ? String(phone).trim() : null,
      password: String(password || ''),
    });
    return {
      id: dto.id,
      flat: dto.flatNo,
      name: dto.name,
      phone: dto.phone || null,
    };
  }

  async function resetResidentPassword(id, { newPassword }) {
    await api.post(`/admin/residents/${encodeURIComponent(id)}/password`, {
      newPassword: String(newPassword || ''),
    });
  }

  // ----------------------------------------------- legacy compatibility

  // The old storage.js owned `setSession`, `setAdminSession`, `clearSession`
  // and `getSession`. They're now provided by window.CM.session, but we
  // re-export them here so view code that still imports them from storage
  // keeps working during the transition.
  function getSession() {
    return session ? session.get() : null;
  }
  function setSession() {
    throw new Error('storage.setSession is no longer used \u2014 call api.loginResident()');
  }
  function setAdminSession() {
    throw new Error('storage.setAdminSession is no longer used \u2014 call api.loginAdmin()');
  }
  function clearSession() {
    if (session) session.clear();
  }

  window.CM = window.CM || {};
  window.CM.storage = {
    // constants
    CATEGORIES,
    DISPLAY_STATUSES,
    DEMO_FLAT_HINT: DEMO_FLAT,

    // utils
    normalizeFlat,
    getDisplayStatus,
    categoryIdToLabel,
    categoryLabelToId,

    // session passthroughs (legacy)
    getSession,
    setSession,
    setAdminSession,
    clearSession,

    // auth
    loginResident,
    loginAdmin,
    fetchMe,
    changeMyPassword,

    // complaints
    getComplaints,
    getAllComplaints,
    getComplaintById,
    addComplaint,
    assignWorker,
    markComplete,

    // professionals
    listProfessionals,
    createProfessional,
    deleteProfessional,

    // residents (admin)
    listResidents,
    createResident,
    resetResidentPassword,
  };
})();
