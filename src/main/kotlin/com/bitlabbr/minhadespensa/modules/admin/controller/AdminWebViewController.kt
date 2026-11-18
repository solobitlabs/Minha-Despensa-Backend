/*
 *   Copyright (c) 2026 Willian Santos
 *
 *   This work is licensed under the Creative Commons
 *   Attribution-NonCommercial 4.0 International License (CC BY-NC 4.0).
 *
 *   You are free to:
 *     - Share  — copy and redistribute the material in any medium or format
 *     - Adapt  — remix, transform, and build upon the material
 *
 *   Under the following terms:
 *     - Attribution    — You must give appropriate credit, provide a link to
 *                        the license, and indicate if changes were made.
 *     - NonCommercial  — You may not use the material for commercial purposes.
 *
 *   Owner rights:
 *     - Willian Santos retains all commercial rights.
 *    - The copyright holder may use, sell, sublicense, or relicense this
 *       work under different terms at any time.
 *
 *   Full license: https://creativecommons.org/licenses/by-nc/4.0/legalcode
 */

package com.bitlabbr.minhadespensa.modules.admin.controller

import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class AdminWebViewController {

    @GetMapping("/admin", "/admin/", produces = [MediaType.TEXT_HTML_VALUE])
    fun renderAdminConsole(): ResponseEntity<String> {
        val csp = "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline'; " +
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: blob:; " +
            "connect-src 'self'; " +
            "frame-ancestors 'none';"

        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .cacheControl(CacheControl.noStore())
            .header("Content-Security-Policy", csp)
            .header("X-Frame-Options", "DENY")
            .header("X-Content-Type-Options", "nosniff")
            .header(HttpHeaders.PRAGMA, "no-cache")
            .body(ADMIN_SPA_HTML)
    }

    companion object {
        private val ADMIN_SPA_HTML = """
<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Minha Despensa — Console Administrativo</title>
  <style>
    :root {
      --bg: #0b1020;
      --surface: rgba(19, 27, 48, 0.88);
      --surface-2: rgba(27, 38, 66, 0.92);
      --border: rgba(148, 163, 184, 0.18);
      --text: #e2e8f0;
      --muted: #94a3b8;
      --primary: #38bdf8;
      --primary-hover: #0ea5e9;
      --success: #22c55e;
      --warning: #f59e0b;
      --danger: #ef4444;
      --radius: 12px;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      background: radial-gradient(circle at top right, #172554, var(--bg) 55%);
      color: var(--text);
      min-height: 100vh;
    }
    .hidden { display: none !important; }
    .container { max-width: 1280px; margin: 0 auto; padding: 24px 18px; }
    .card {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: var(--radius);
      padding: 20px;
      backdrop-filter: blur(12px);
    }
    .login-wrap {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 20px;
    }
    .login-card { width: 100%; max-width: 400px; }
    h1, h2, h3 { margin: 0 0 12px; font-weight: 700; }
    .muted { color: var(--muted); font-size: 13px; }
    label { display: block; font-size: 13px; color: var(--muted); margin: 12px 0 6px; }
    input, select {
      width: 100%;
      padding: 10px 12px;
      border-radius: 8px;
      border: 1px solid var(--border);
      background: var(--surface-2);
      color: var(--text);
      font-size: 14px;
    }
    button {
      border: none;
      border-radius: 8px;
      padding: 9px 14px;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      color: #0b1020;
      background: var(--primary);
      transition: background .15s;
    }
    button:hover { background: var(--primary-hover); }
    button.secondary { background: var(--surface-2); color: var(--text); border: 1px solid var(--border); }
    button.danger { background: var(--danger); color: #fff; }
    button.warning { background: var(--warning); color: #0b1020; }
    button.success { background: var(--success); color: #0b1020; }
    .topbar {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      margin-bottom: 18px;
    }
    .kpis {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
      gap: 12px;
      margin-bottom: 18px;
    }
    .kpi-value { font-size: 26px; font-weight: 800; margin-top: 6px; color: var(--primary); }
    .tabs { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 16px; }
    .tab-btn { background: var(--surface-2); color: var(--muted); border: 1px solid var(--border); }
    .tab-btn.active { background: var(--primary); color: #0b1020; border-color: var(--primary); }
    .toolbar { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 14px; align-items: center; }
    .toolbar input, .toolbar select { width: auto; min-width: 180px; flex: 1; }
    .table-wrap { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; font-size: 13px; }
    th, td { text-align: left; padding: 10px 8px; border-bottom: 1px solid var(--border); vertical-align: middle; }
    th { color: var(--muted); font-weight: 600; font-size: 12px; text-transform: uppercase; }
    .badge {
      display: inline-block;
      padding: 3px 8px;
      border-radius: 999px;
      font-size: 11px;
      font-weight: 700;
      background: rgba(56, 189, 248, 0.16);
      color: var(--primary);
    }
    .badge.ok { background: rgba(34, 197, 94, 0.18); color: #4ade80; }
    .badge.warn { background: rgba(245, 158, 11, 0.18); color: #fbbf24; }
    .badge.err { background: rgba(239, 68, 68, 0.18); color: #f87171; }
    .actions { display: flex; gap: 6px; flex-wrap: wrap; }
    .thumb { width: 38px; height: 38px; object-fit: cover; border-radius: 6px; border: 1px solid var(--border); background: #0f172a; }
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(2, 6, 23, 0.78);
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 16px;
      z-index: 50;
    }
    .modal { width: 100%; max-width: 540px; max-height: 92vh; overflow-y: auto; }
    .alert { padding: 10px 12px; border-radius: 8px; margin-bottom: 12px; font-size: 13px; }
    .alert.err { background: rgba(239, 68, 68, 0.18); color: #fca5a5; border: 1px solid rgba(239, 68, 68, 0.4); }
    .alert.ok { background: rgba(34, 197, 94, 0.18); color: #86efac; border: 1px solid rgba(34, 197, 94, 0.4); }
  </style>
</head>
<body>
  <div id="loginView" class="login-wrap">
    <div class="card login-card">
      <h2>🔐 Admin Console</h2>
      <p class="muted">Minha Despensa — Acesso restrito (SYSTEM_ADMIN)</p>
      <div id="loginError" class="alert err hidden"></div>
      <form id="loginForm">
        <label for="adminUser">Usuário</label>
        <input id="adminUser" type="text" autocomplete="username" required />
        <label for="adminPass">Senha</label>
        <input id="adminPass" type="password" autocomplete="current-password" required />
        <div style="margin-top: 16px;">
          <button type="submit" style="width: 100%;">Entrar com Segurança</button>
        </div>
      </form>
    </div>
  </div>

  <div id="appView" class="container hidden">
    <div class="topbar">
      <div>
        <h1>🛡️ Minha Despensa — Admin Console</h1>
        <span class="muted">Sanitização de Catálogo Canônico, Mídia, Usuários & Auditoria</span>
      </div>
      <div class="actions">
        <button id="refreshBtn" class="secondary">↻ Atualizar</button>
        <button id="logoutBtn" class="danger">Sair</button>
      </div>
    </div>

    <div id="globalNotice" class="alert hidden"></div>

    <div class="kpis">
      <div class="card"><div class="muted">Produtos Canônicos</div><div id="kpiProducts" class="kpi-value">-</div></div>
      <div class="card"><div class="muted">Sanitizados (Verificados)</div><div id="kpiVerified" class="kpi-value">-</div></div>
      <div class="card"><div class="muted">Com Foto Canônica</div><div id="kpiPhotos" class="kpi-value">-</div></div>
      <div class="card"><div class="muted">Usuários</div><div id="kpiUsers" class="kpi-value">-</div></div>
      <div class="card"><div class="muted">Bloqueados</div><div id="kpiBlocked" class="kpi-value">-</div></div>
      <div class="card"><div class="muted">Casas</div><div id="kpiHouses" class="kpi-value">-</div></div>
    </div>

    <div class="tabs">
      <button class="tab-btn active" data-tab="products">📦 Catálogo & Sanitização</button>
      <button class="tab-btn" data-tab="users">👥 Usuários & Sessões</button>
      <button class="tab-btn" data-tab="audit">📜 Auditoria</button>
    </div>

    <div id="tab-products" class="card">
      <div class="toolbar">
        <input id="prodQuery" type="text" placeholder="Buscar por nome, marca ou EAN..." />
        <select id="prodSource">
          <option value="">Todas as fontes</option>
          <option value="ADMIN_CURATED">ADMIN_CURATED</option>
          <option value="OPEN_FOOD_FACTS">OPEN_FOOD_FACTS</option>
          <option value="OPEN_BEAUTY_FACTS">OPEN_BEAUTY_FACTS</option>
          <option value="OPEN_PRODUCTS_FACTS">OPEN_PRODUCTS_FACTS</option>
          <option value="SEFAZ_INVOICE">SEFAZ_INVOICE</option>
          <option value="USER_SYNC">USER_SYNC</option>
        </select>
        <select id="prodVerified">
          <option value="">Todos os status</option>
          <option value="false">Pendentes de Sanitização</option>
          <option value="true">Verificados</option>
        </select>
        <button id="searchProdBtn">Filtrar</button>
        <button id="newProdBtn" class="success">+ Novo Produto</button>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Foto</th>
              <th>EAN</th>
              <th>Nome / Marca</th>
              <th>Unidade</th>
              <th>Fonte</th>
              <th>Status</th>
              <th>Ações</th>
            </tr>
          </thead>
          <tbody id="prodTableBody"></tbody>
        </table>
      </div>
    </div>

    <div id="tab-users" class="card hidden">
      <div class="toolbar">
        <input id="userQuery" type="text" placeholder="Buscar usuário por nome, e-mail ou ID..." />
        <select id="userBlocked">
          <option value="">Todos</option>
          <option value="false">Ativos</option>
          <option value="true">Bloqueados</option>
        </select>
        <button id="searchUserBtn">Filtrar</button>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Nome</th>
              <th>E-mail</th>
              <th>Casas</th>
              <th>Status</th>
              <th>Ações</th>
            </tr>
          </thead>
          <tbody id="userTableBody"></tbody>
        </table>
      </div>
    </div>

    <div id="tab-audit" class="card hidden">
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Data</th>
              <th>Admin</th>
              <th>Ação</th>
              <th>Alvo</th>
              <th>Detalhes</th>
              <th>IP</th>
            </tr>
          </thead>
          <tbody id="auditTableBody"></tbody>
        </table>
      </div>
    </div>
  </div>

  <div id="productModal" class="modal-backdrop hidden">
    <div class="card modal">
      <h3 id="modalTitle">Editar Produto Canônico</h3>
      <form id="productForm">
        <label for="mEan">Código EAN</label>
        <input id="mEan" type="text" required />
        <label for="mName">Nome Canônico</label>
        <input id="mName" type="text" required />
        <label for="mBrand">Marca</label>
        <input id="mBrand" type="text" />
        <label for="mCategory">Categoria</label>
        <input id="mCategory" type="text" value="Outros" />
        <div style="display:flex; gap:10px;">
          <div style="flex:1;">
            <label for="mUnit">Unidade</label>
            <select id="mUnit">
              <option value="UNIT">UNIT</option>
              <option value="KILOGRAM">KILOGRAM</option>
              <option value="GRAM">GRAM</option>
              <option value="LITER">LITER</option>
              <option value="MILLILITER">MILLILITER</option>
              <option value="PACKAGE">PACKAGE</option>
            </select>
          </div>
          <div style="flex:1;">
            <label for="mWeight">Peso/Qtd</label>
            <input id="mWeight" type="number" step="any" min="0.001" value="1" required />
          </div>
        </div>
        <label style="display:flex; align-items:center; gap:8px; margin-top:12px;">
          <input id="mVerified" type="checkbox" style="width:auto;" checked />
          Marcar como sanitizado/verificado (ADMIN_CURATED)
        </label>
        <div id="mediaSection" style="margin-top:14px; padding-top:12px; border-top:1px solid var(--border);">
          <label for="mPhotoFile">Foto Canônica (WebP / JPEG / PNG até 500KB)</label>
          <input id="mPhotoFile" type="file" accept="image/webp,image/jpeg,image/png" />
          <div class="actions" style="margin-top:8px;">
            <button type="button" id="uploadPhotoBtn" class="secondary">Enviar Foto</button>
            <button type="button" id="removePhotoBtn" class="danger">Remover Foto</button>
          </div>
        </div>
        <div class="actions" style="margin-top:18px; justify-content:flex-end;">
          <button type="button" id="closeModalBtn" class="secondary">Cancelar</button>
          <button type="submit">Salvar</button>
        </div>
      </form>
    </div>
  </div>

  <script>
    const TOKEN_KEY = "despensa_admin_jwt";
    let isCreating = false;

    function getToken() { return sessionStorage.getItem(TOKEN_KEY); }
    function setToken(t) { sessionStorage.setItem(TOKEN_KEY, t); }
    function clearToken() { sessionStorage.removeItem(TOKEN_KEY); }

    function showNotice(msg, isError) {
      const el = document.getElementById("globalNotice");
      el.textContent = msg;
      el.className = "alert " + (isError ? "err" : "ok");
      el.classList.remove("hidden");
      setTimeout(() => el.classList.add("hidden"), 5000);
    }

    async function apiFetch(url, options = {}) {
      const headers = Object.assign({}, options.headers || {});
      const token = getToken();
      if (token) headers["Authorization"] = "Bearer " + token;
      const res = await fetch(url, Object.assign({}, options, { headers }));
      if (res.status === 401) {
        clearToken();
        showLogin();
        throw new Error("Sessão expirada.");
      }
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.message || ("Erro HTTP " + res.status));
      return data;
    }

    function showLogin() {
      document.getElementById("loginView").classList.remove("hidden");
      document.getElementById("appView").classList.add("hidden");
    }

    function showApp() {
      document.getElementById("loginView").classList.add("hidden");
      document.getElementById("appView").classList.remove("hidden");
      loadAll();
    }

    document.getElementById("loginForm").addEventListener("submit", async (e) => {
      e.preventDefault();
      const errEl = document.getElementById("loginError");
      errEl.classList.add("hidden");
      try {
        const res = await fetch("/api/v1/admin/auth/login", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            username: document.getElementById("adminUser").value,
            password: document.getElementById("adminPass").value
          })
        });
        const payload = await res.json();
        if (!res.ok) throw new Error(payload.message || "Falha na autenticação.");
        setToken(payload.data.accessToken);
        showApp();
      } catch (err) {
        errEl.textContent = err.message;
        errEl.classList.remove("hidden");
      }
    });

    document.getElementById("logoutBtn").addEventListener("click", () => { clearToken(); showLogin(); });
    document.getElementById("refreshBtn").addEventListener("click", loadAll);

    document.querySelectorAll(".tab-btn").forEach(btn => {
      btn.addEventListener("click", () => {
        document.querySelectorAll(".tab-btn").forEach(b => b.classList.remove("active"));
        btn.classList.add("active");
        const target = btn.getAttribute("data-tab");
        ["products", "users", "audit"].forEach(t => {
          document.getElementById("tab-" + t).classList.toggle("hidden", t !== target);
        });
      });
    });

    async function loadDashboard() {
      const res = await apiFetch("/api/v1/admin/dashboard");
      const d = res.data;
      document.getElementById("kpiProducts").textContent = d.totalGlobalProducts;
      document.getElementById("kpiVerified").textContent = d.verifiedGlobalProducts;
      document.getElementById("kpiPhotos").textContent = d.productsWithPhoto;
      document.getElementById("kpiUsers").textContent = d.totalUsers;
      document.getElementById("kpiBlocked").textContent = d.blockedUsers;
      document.getElementById("kpiHouses").textContent = d.totalHouseholds;
    }

    async function loadProducts() {
      const q = encodeURIComponent(document.getElementById("prodQuery").value.trim());
      const src = encodeURIComponent(document.getElementById("prodSource").value);
      const ver = document.getElementById("prodVerified").value;
      const res = await apiFetch("/api/v1/admin/products?query=" + q + "&source=" + src + (ver ? "&verified=" + ver : "") + "&size=50");
      const tbody = document.getElementById("prodTableBody");
      tbody.replaceChildren();

      for (const p of res.data.items) {
        const tr = document.createElement("tr");

        const tdImg = document.createElement("td");
        if (p.imageHash) {
          const img = document.createElement("img");
          img.className = "thumb";
          loadAuthorizedThumb(p.ean, img);
          tdImg.appendChild(img);
        } else {
          const span = document.createElement("span");
          span.className = "muted";
          span.textContent = "—";
          tdImg.appendChild(span);
        }

        const tdEan = document.createElement("td");
        tdEan.textContent = p.ean;

        const tdName = document.createElement("td");
        const strong = document.createElement("strong");
        strong.textContent = p.name;
        const br = document.createElement("br");
        const brandSpan = document.createElement("span");
        brandSpan.className = "muted";
        brandSpan.textContent = (p.brand || "Sem marca") + " • " + p.category;
        tdName.append(strong, br, brandSpan);

        const tdUnit = document.createElement("td");
        tdUnit.textContent = p.netWeight + " " + p.measureUnit;

        const tdSrc = document.createElement("td");
        const bSrc = document.createElement("span");
        bSrc.className = "badge";
        bSrc.textContent = p.source;
        tdSrc.appendChild(bSrc);

        const tdStatus = document.createElement("td");
        const bVer = document.createElement("span");
        bVer.className = "badge " + (p.isVerified ? "ok" : "warn");
        bVer.textContent = p.isVerified ? "Verificado" : "Pendente";
        tdStatus.appendChild(bVer);

        const tdAct = document.createElement("td");
        tdAct.className = "actions";
        const editBtn = document.createElement("button");
        editBtn.className = "secondary";
        editBtn.textContent = "Sanitizar";
        editBtn.onclick = () => openEditModal(p);

        const enrichBtn = document.createElement("button");
        enrichBtn.className = "secondary";
        enrichBtn.textContent = "OpenFacts";
        enrichBtn.onclick = () => enrichProduct(p.ean);

        const delBtn = document.createElement("button");
        delBtn.className = "danger";
        delBtn.textContent = "Excluir";
        delBtn.onclick = () => deleteProduct(p.ean, p.name);

        tdAct.append(editBtn, enrichBtn, delBtn);
        tr.append(tdImg, tdEan, tdName, tdUnit, tdSrc, tdStatus, tdAct);
        tbody.appendChild(tr);
      }
    }

    async function loadAuthorizedThumb(ean, imgEl) {
      try {
        const res = await fetch("/api/v1/catalog/global/" + encodeURIComponent(ean) + "/media", {
          headers: { "Authorization": "Bearer " + getToken() }
        });
        if (res.ok) {
          const blob = await res.blob();
          imgEl.src = URL.createObjectURL(blob);
        }
      } catch (_) {}
    }

    async function loadUsers() {
      const q = encodeURIComponent(document.getElementById("userQuery").value.trim());
      const blk = document.getElementById("userBlocked").value;
      const res = await apiFetch("/api/v1/admin/users?query=" + q + (blk ? "&blocked=" + blk : "") + "&size=50");
      const tbody = document.getElementById("userTableBody");
      tbody.replaceChildren();

      for (const u of res.data.items) {
        const tr = document.createElement("tr");
        const tdName = document.createElement("td"); tdName.textContent = u.name;
        const tdEmail = document.createElement("td"); tdEmail.textContent = u.email;
        const tdHouses = document.createElement("td"); tdHouses.textContent = u.householdCount;

        const tdStatus = document.createElement("td");
        const b = document.createElement("span");
        b.className = "badge " + (u.isBlocked ? "err" : "ok");
        b.textContent = u.isBlocked ? "Bloqueado" : "Ativo";
        tdStatus.appendChild(b);

        const tdAct = document.createElement("td");
        tdAct.className = "actions";
        const blkBtn = document.createElement("button");
        blkBtn.className = u.isBlocked ? "success" : "warning";
        blkBtn.textContent = u.isBlocked ? "Desbloquear" : "Bloquear";
        blkBtn.onclick = () => toggleBlockUser(u);

        const revBtn = document.createElement("button");
        revBtn.className = "secondary";
        revBtn.textContent = "Revogar Sessões";
        revBtn.onclick = () => revokeSessions(u);

        tdAct.append(blkBtn, revBtn);
        tr.append(tdName, tdEmail, tdHouses, tdStatus, tdAct);
        tbody.appendChild(tr);
      }
    }

    async function loadAudit() {
      const res = await apiFetch("/api/v1/admin/audit-logs?limit=50");
      const tbody = document.getElementById("auditTableBody");
      tbody.replaceChildren();
      for (const l of res.data) {
        const tr = document.createElement("tr");
        const tdDate = document.createElement("td"); tdDate.textContent = new Date(l.createdAt).toLocaleString("pt-BR");
        const tdAdm = document.createElement("td"); tdAdm.textContent = l.adminUsername;
        const tdAct = document.createElement("td"); tdAct.textContent = l.action;
        const tdTgt = document.createElement("td"); tdTgt.textContent = l.targetType + ":" + l.targetId;
        const tdDet = document.createElement("td"); tdDet.textContent = l.details || "-";
        const tdIp = document.createElement("td"); tdIp.textContent = l.clientIp || "-";
        tr.append(tdDate, tdAdm, tdAct, tdTgt, tdDet, tdIp);
        tbody.appendChild(tr);
      }
    }

    function openEditModal(p) {
      isCreating = false;
      document.getElementById("modalTitle").textContent = "Sanitizar Produto Canônico";
      const eanInput = document.getElementById("mEan");
      eanInput.value = p.ean;
      eanInput.disabled = true;
      document.getElementById("mName").value = p.name;
      document.getElementById("mBrand").value = p.brand || "";
      document.getElementById("mCategory").value = p.category || "Outros";
      document.getElementById("mUnit").value = p.measureUnit;
      document.getElementById("mWeight").value = p.netWeight;
      document.getElementById("mVerified").checked = true;
      document.getElementById("mediaSection").classList.remove("hidden");
      document.getElementById("productModal").classList.remove("hidden");
    }

    document.getElementById("newProdBtn").addEventListener("click", () => {
      isCreating = true;
      document.getElementById("modalTitle").textContent = "Novo Produto Canônico";
      const eanInput = document.getElementById("mEan");
      eanInput.value = "";
      eanInput.disabled = false;
      document.getElementById("mName").value = "";
      document.getElementById("mBrand").value = "";
      document.getElementById("mCategory").value = "Outros";
      document.getElementById("mUnit").value = "UNIT";
      document.getElementById("mWeight").value = "1";
      document.getElementById("mVerified").checked = true;
      document.getElementById("mediaSection").classList.add("hidden");
      document.getElementById("productModal").classList.remove("hidden");
    });

    document.getElementById("closeModalBtn").addEventListener("click", () => {
      document.getElementById("productModal").classList.add("hidden");
    });

    document.getElementById("productForm").addEventListener("submit", async (e) => {
      e.preventDefault();
      const ean = document.getElementById("mEan").value.trim();
      const payload = {
        ean,
        name: document.getElementById("mName").value.trim(),
        brand: document.getElementById("mBrand").value.trim() || null,
        category: document.getElementById("mCategory").value.trim() || "Outros",
        measureUnit: document.getElementById("mUnit").value,
        netWeight: parseFloat(document.getElementById("mWeight").value),
        isVerified: document.getElementById("mVerified").checked
      };
      try {
        if (isCreating) {
          await apiFetch("/api/v1/admin/products", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
          });
          showNotice("Produto criado com sucesso.", false);
        } else {
          await apiFetch("/api/v1/admin/products/" + encodeURIComponent(ean), {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
          });
          showNotice("Produto sanitizado com sucesso.", false);
        }
        document.getElementById("productModal").classList.add("hidden");
        loadAll();
      } catch (err) {
        showNotice(err.message, true);
      }
    });

    document.getElementById("uploadPhotoBtn").addEventListener("click", async () => {
      const ean = document.getElementById("mEan").value.trim();
      const fileInput = document.getElementById("mPhotoFile");
      if (!fileInput.files.length) return showNotice("Selecione um arquivo de imagem.", true);
      const fd = new FormData();
      fd.append("file", fileInput.files[0]);
      try {
        await apiFetch("/api/v1/admin/products/" + encodeURIComponent(ean) + "/media", { method: "PUT", body: fd });
        showNotice("Foto canônica atualizada.", false);
        loadAll();
      } catch (err) { showNotice(err.message, true); }
    });

    document.getElementById("removePhotoBtn").addEventListener("click", async () => {
      const ean = document.getElementById("mEan").value.trim();
      try {
        await apiFetch("/api/v1/admin/products/" + encodeURIComponent(ean) + "/media", { method: "DELETE" });
        showNotice("Foto canônica removida.", false);
        loadAll();
      } catch (err) { showNotice(err.message, true); }
    });

    async function enrichProduct(ean) {
      try {
        await apiFetch("/api/v1/admin/products/" + encodeURIComponent(ean) + "/enrich", { method: "POST" });
        showNotice("Produto atualizado via Open Facts.", false);
        loadAll();
      } catch (err) { showNotice(err.message, true); }
    }

    async function deleteProduct(ean, name) {
      if (!confirm("Remover '" + name + "' (" + ean + ") do catálogo canônico?")) return;
      try {
        await apiFetch("/api/v1/admin/products/" + encodeURIComponent(ean), { method: "DELETE" });
        showNotice("Produto removido.", false);
        loadAll();
      } catch (err) { showNotice(err.message, true); }
    }

    async function toggleBlockUser(u) {
      const block = !u.isBlocked;
      const reason = block ? prompt("Motivo do bloqueio (opcional):", "Violação dos termos") : null;
      if (block && reason === null) return;
      try {
        await apiFetch("/api/v1/admin/users/" + encodeURIComponent(u.id) + "/block", {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ blocked: block, reason })
        });
        showNotice(block ? "Usuário bloqueado." : "Usuário desbloqueado.", false);
        loadAll();
      } catch (err) { showNotice(err.message, true); }
    }

    async function revokeSessions(u) {
      if (!confirm("Revogar todas as sessões ativas de " + u.email + "?")) return;
      try {
        await apiFetch("/api/v1/admin/users/" + encodeURIComponent(u.id) + "/revoke-sessions", { method: "POST" });
        showNotice("Sessões revogadas.", false);
        loadAudit();
      } catch (err) { showNotice(err.message, true); }
    }

    document.getElementById("searchProdBtn").addEventListener("click", loadProducts);
    document.getElementById("searchUserBtn").addEventListener("click", loadUsers);

    async function loadAll() {
      try {
        await Promise.all([loadDashboard(), loadProducts(), loadUsers(), loadAudit()]);
      } catch (err) { showNotice(err.message, true); }
    }

    if (getToken()) showApp(); else showLogin();
  </script>
</body>
</html>
        """.trimIndent()
    }
}
