const state = {
  index: [],
  catalogVersion: null,
  current: null,
  saved: null,
  sourcePath: "",
  folder: "",
  catalogVisible: true,
  featured: false,
  dirty: false,
  filter: "all",
  deckQuery: "",
  itemQuery: "",
  issues: [],
  validationTimer: null,
  toastTimer: null,
  aiStatus: null,
  aiBusy: new Set(),
  aiProposal: null,
  batchBusy: false,
};

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const clone = (value) => JSON.parse(JSON.stringify(value));
const todayISO = () => new Date().toISOString().replace(/\.\d{3}Z$/, "Z");
const dateOnly = (value) => (typeof value === "string" ? value.slice(0, 10) : "");
const draftKey = () => `piyokey-deck-draft:${state.sourcePath || state.current?.deck_id || "new"}`;

function blankDeck() {
  const now = todayISO();
  return {
    deck_id: "new_deck",
    version: 1,
    name: "새 한국어 덱",
    author: { id: "official_hanco", nickname: "ピヨキー 公式" },
    official: true,
    type: "word",
    level: 1,
    tags: ["公式"],
    created_at: now,
    updated_at: now,
    items: [{ id: "i_001", ko: "한글", reading_ja: "ハングル", meaning_ja: "韓国語", audio: null }],
  };
}

function copyDeckId(deckId) {
  return `${String(deckId || "new_deck").slice(0, 59)}_copy`;
}

function normalizeImportedDeck(value) {
  const fallback = blankDeck();
  const deck = { ...fallback, ...clone(value) };
  deck.author = { ...fallback.author, ...(value.author && typeof value.author === "object" ? value.author : {}) };
  deck.tags = Array.isArray(value.tags) ? value.tags : [];
  deck.items = Array.isArray(value.items)
    ? value.items.map((item, index) => ({
        id: `i_${String(index + 1).padStart(3, "0")}`,
        ko: "",
        reading_ja: "",
        meaning_ja: "",
        audio: null,
        ...(item && typeof item === "object" ? item : {}),
      }))
    : [];
  return deck;
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: options.body ? { "Content-Type": "application/json" } : {},
    ...options,
  });
  let payload;
  try { payload = await response.json(); } catch { payload = { message: "서버 응답을 읽지 못했습니다." }; }
  if (!response.ok) {
    const error = new Error(payload.message || "요청에 실패했습니다.");
    error.payload = payload;
    throw error;
  }
  return payload;
}

async function loadIndex(selectPath = null) {
  const payload = await api("/api/state");
  state.index = payload.decks;
  state.catalogVersion = payload.catalog_version;
  $("#catalogVersion").textContent = `v${payload.catalog_version ?? "—"}`;
  $("#catalogGeneratedAt").textContent = payload.generated_at ? `갱신 ${formatDate(payload.generated_at)}` : "갱신 시각 없음";
  renderDeckList();
  if (selectPath) {
    const summary = state.index.find((entry) => entry.path === selectPath);
    if (summary) await selectDeck(summary);
  }
}

async function loadAIStatus() {
  try {
    state.aiStatus = await api("/api/ai/status");
    const chip = $("#aiStatusButton");
    chip.className = `ai-status-chip${state.aiStatus.configured && state.aiStatus.audio_available ? " is-ready" : state.aiStatus.configured ? "" : " is-error"}`;
    $("b", chip).textContent = state.aiStatus.configured ? "AI 준비됨" : "AI 연결 필요";
    $("#setupTextStatus").textContent = state.aiStatus.configured ? "연결됨" : "API 키 필요";
    $("#setupAudioStatus").textContent = state.aiStatus.audio_available ? "gTTS 설치됨" : "설치 필요";
    $("#setupModelStatus").textContent = state.aiStatus.model || "—";
    $("#setupAudioEngineStatus").textContent = state.aiStatus.audio_engine || "—";
  } catch {
    state.aiStatus = { configured: false, audio_available: false, model: "—", audio_engine: "—" };
    $("#aiStatusButton").className = "ai-status-chip is-error";
    $("#aiStatusButton b").textContent = "AI 상태 오류";
  }
}

function formatDate(value) {
  const date = new Date(value);
  if (Number.isNaN(date.valueOf())) return value;
  return new Intl.DateTimeFormat("ko-KR", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" }).format(date);
}

function renderDeckList() {
  const query = state.deckQuery.trim().toLocaleLowerCase();
  const filtered = state.index.filter((deck) => {
    if (state.filter === "catalog" && !deck.catalog_visible) return false;
    if (state.filter === "game" && deck.catalog_visible) return false;
    const haystack = [deck.name, deck.deck_id, ...(deck.tags || [])].join(" ").toLocaleLowerCase();
    return !query || haystack.includes(query);
  });
  $("#deckCount").textContent = filtered.length;
  const list = $("#deckList");
  if (!filtered.length) {
    list.innerHTML = `<div class="deck-list-empty">조건에 맞는 덱이 없습니다.<br />검색어를 바꾸거나 새 덱을 만들어 보세요.</div>`;
    return;
  }
  const groups = new Map();
  filtered.forEach((deck) => {
    const group = deck.catalog_visible ? "발견 카탈로그" : folderLabel(deck.folder);
    if (!groups.has(group)) groups.set(group, []);
    groups.get(group).push(deck);
  });
  list.innerHTML = "";
  groups.forEach((decks, group) => {
    const label = document.createElement("div");
    label.className = "deck-list-group";
    label.textContent = group;
    list.append(label);
    decks.forEach((deck) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = `deck-list-item${deck.path === state.sourcePath ? " is-active" : ""}`;
      button.innerHTML = `
        <span class="deck-item-top"><strong>${escapeHTML(deck.name)}</strong><span class="deck-version">v${deck.version}</span></span>
        <span class="deck-item-meta"><span class="deck-item-tags">${escapeHTML((deck.tags || []).slice(0, 2).join(" · "))}</span><span>${deck.item_count}개</span></span>`;
      button.addEventListener("click", () => selectDeck(deck));
      list.append(button);
    });
  });
}

function folderLabel(folder) {
  return ({ "": "일반 덱", flow: "흐름 전용", acid_rain: "산성비 전용", choseong: "초성 전용", word_match: "단어 맞추기 전용", dictation: "받아쓰기 전용" })[folder || ""] || folder;
}

async function selectDeck(summary) {
  if (!canLeaveCurrent()) return;
  try {
    const payload = await api(`/api/deck?path=${encodeURIComponent(summary.path)}`);
    state.current = payload.deck;
    state.saved = clone(payload.deck);
    state.sourcePath = summary.path;
    state.folder = summary.folder || "";
    state.catalogVisible = summary.catalog_visible;
    state.featured = summary.featured;
    state.dirty = false;
    $("#bumpVersionInput").checked = true;
    restoreDraftIfAvailable();
    showEditor();
    renderAll();
  } catch (error) { showToast(error.message, true); }
}

function canLeaveCurrent() {
  if (!state.dirty) return true;
  return window.confirm("저장하지 않은 변경이 있습니다. 이 화면을 나갈까요?");
}

function createNewDeck(source = null) {
  if (!canLeaveCurrent()) return;
  state.current = source ? clone(source) : blankDeck();
  if (source) {
    state.current.deck_id = copyDeckId(source.deck_id);
    state.current.version = 1;
    state.current.created_at = todayISO();
    state.current.updated_at = state.current.created_at;
  }
  state.saved = null;
  state.sourcePath = "";
  state.folder = "";
  state.catalogVisible = true;
  state.featured = false;
  state.dirty = true;
  $("#bumpVersionInput").checked = false;
  showEditor();
  renderAll();
  $('[data-deck-field="name"]').select();
}

function showEditor() {
  $("#emptyState").hidden = true;
  $("#editor").hidden = false;
}

function renderAll() {
  renderMetadata();
  renderItems();
  validateAndRender();
  renderSaveState();
  renderDeckList();
}

function renderMetadata() {
  const deck = state.current;
  $$('[data-deck-field]').forEach((input) => {
    const field = input.dataset.deckField;
    input.value = deck[field] ?? "";
  });
  $$('[data-author-field]').forEach((input) => { input.value = deck.author?.[input.dataset.authorField] ?? ""; });
  $('[data-deck-field="deck_id"]').disabled = Boolean(state.sourcePath);
  $("#tagsInput").value = (deck.tags || []).join(", ");
  $("#folderSelect").value = state.folder;
  $("#createdAtInput").value = dateOnly(deck.created_at);
  $("#officialInput").checked = Boolean(deck.official);
  $("#catalogVisibleInput").checked = state.catalogVisible;
  $("#featuredInput").checked = state.featured;
  $("#featuredInput").disabled = !state.catalogVisible;
  $("#folderSelect").disabled = state.catalogVisible;
  $("#deckFolderLabel").textContent = state.catalogVisible ? "발견 카탈로그" : folderLabel(state.folder);
  $("#deckIdLabel").textContent = deck.deck_id || "deck_id";
  $("#deckTitle").textContent = deck.name || "이름 없는 덱";
  $("#deckSubtitle").textContent = state.sourcePath ? `${deck.items.length}개 항목 · 마지막 수정 ${formatDate(deck.updated_at)}` : "새 덱의 기본 정보와 학습 텍스트를 채워 주세요.";
  renderTags();
  renderPreview();
}

function renderTags() {
  $("#tagPreview").innerHTML = (state.current.tags || []).map((tag) => `<span>${escapeHTML(tag)}</span>`).join("");
}

function renderPreview() {
  const deck = state.current;
  $("#previewInitial").textContent = [...(deck.name || "ㅎ")][0] || "ㅎ";
  $("#previewLevel").textContent = `LEVEL ${deck.level || 1}`;
  $("#previewName").textContent = deck.name || "덱 이름";
  $("#previewAuthor").textContent = deck.author?.nickname || "작성자";
  $("#previewTags").innerHTML = (deck.tags || []).slice(0, 3).map((tag) => `<span>${escapeHTML(tag)}</span>`).join("");
  $("#previewItemCount").textContent = `${deck.items?.length || 0}개 항목`;
  const samples = (deck.items || []).filter((item) => item.ko || item.meaning_ja).slice(0, 3);
  $("#samplePreview").innerHTML = samples.length
    ? samples.map((item) => `<div class="sample-row"><strong>${escapeHTML(item.ko || "—")}</strong><span>${escapeHTML(item.meaning_ja || "—")}</span></div>`).join("")
    : `<div class="sample-empty">미리볼 학습 항목이 없습니다.</div>`;
}

function renderItems() {
  const query = state.itemQuery.trim().toLocaleLowerCase();
  const rows = $("#itemRows");
  rows.innerHTML = "";
  state.current.items.forEach((item, index) => {
    const haystack = [item.ko, item.reading_ja, item.meaning_ja, item.audio].join(" ").toLocaleLowerCase();
    if (query && !haystack.includes(query)) return;
    const row = document.createElement("tr");
    row.dataset.index = index;
    const aiLoading = state.aiBusy.has(item.id);
    if (aiLoading) row.classList.add("is-ai-loading");
    const audioValue = item.audio ?? "";
    row.innerHTML = `
      <td class="number-cell">${String(index + 1).padStart(2, "0")}</td>
      <td><div class="ko-input-wrap"><input class="table-input" data-item-field="ko" value="${escapeAttribute(item.ko)}" maxlength="30" aria-label="${index + 1}번 한국어" /><span class="character-count${[...item.ko].length > 10 ? " is-over" : ""}">${[...item.ko].length}/10</span></div></td>
      <td><input class="table-input" data-item-field="reading_ja" value="${escapeAttribute(item.reading_ja)}" maxlength="300" aria-label="${index + 1}번 가타카나 읽기" /></td>
      <td><input class="table-input" data-item-field="meaning_ja" value="${escapeAttribute(item.meaning_ja)}" maxlength="500" aria-label="${index + 1}번 일본어 뜻" /></td>
      <td><div class="audio-cell"><input class="table-input" data-item-field="audio" value="${escapeAttribute(audioValue)}" placeholder="null · 기기 TTS" aria-label="${index + 1}번 오디오 경로" /><button class="null-audio-button" data-row-action="null-audio" title="기기 TTS 사용" type="button">TTS</button></div></td>
      <td class="actions-cell"><button class="ai-row-button" data-row-action="ai" title="AI로 읽기·뜻·오디오 생성" aria-label="${index + 1}번 항목 AI 채우기" type="button" ${!item.ko || aiLoading ? "disabled" : ""}>${aiLoading ? "…" : "✦ AI"}</button><button class="row-action" data-row-action="duplicate" title="행 복제" aria-label="${index + 1}번 행 복제" type="button">⧉</button><button class="row-action delete" data-row-action="delete" title="행 삭제" aria-label="${index + 1}번 행 삭제" type="button">×</button></td>`;
    rows.append(row);
  });
  $("#itemCount").textContent = state.itemQuery ? `${rows.children.length}/${state.current.items.length}` : state.current.items.length;
  markInvalidRows();
}

function openAISetup() {
  $("#aiSetupDialog").showModal();
}

function audioMediaURL(relativePath) {
  if (!relativePath) return "";
  return `/media/${String(relativePath).split("/").map(encodeURIComponent).join("/")}`;
}

async function runAIForItem(index) {
  const item = state.current.items[index];
  if (!item?.ko?.trim()) { showToast("먼저 한국어를 입력해 주세요.", true); return; }
  if (!state.aiStatus?.configured) { openAISetup(); return; }
  const itemId = item.id;
  state.aiBusy.add(itemId);
  renderItems();
  try {
    const result = await api("/api/ai/enrich", {
      method: "POST",
      body: JSON.stringify({
        ko: item.ko,
        deck_name: state.current.name,
        deck_type: state.current.type,
        generate_audio: true,
      }),
    });
    if (!state.current.items.some((candidate) => candidate.id === itemId)) return;
    state.aiProposal = { itemId, korean: item.ko, ...result };
    $("#aiPreviewKorean").textContent = item.ko;
    $("#aiReadingResult").value = result.reading_ja;
    $("#aiMeaningResult").value = result.meaning_ja;
    $("#aiAudioResult").value = result.audio || "생성하지 못함";
    $("#applyReadingInput").checked = true;
    $("#applyMeaningInput").checked = true;
    $("#applyAudioInput").checked = Boolean(result.audio);
    $("#applyAudioInput").disabled = !result.audio;
    const meta = [
      `<span>${escapeHTML(result.model)}</span>`,
      `<span>${escapeHTML(result.audio_engine || "gTTS MP3")}</span>`,
      ...(result.warnings || []).map((warning) => `<span class="warning">${escapeHTML(warning)}</span>`),
    ];
    $("#aiResultMeta").innerHTML = meta.join("");
    const audio = $("#aiAudioPreview");
    if (result.audio) {
      audio.src = audioMediaURL(result.audio);
      audio.hidden = false;
    } else {
      audio.removeAttribute("src");
      audio.hidden = true;
    }
    $("#aiPreviewDialog").showModal();
  } catch (error) {
    if (/API 키/.test(error.message)) openAISetup();
    showToast(error.message, true);
  } finally {
    state.aiBusy.delete(itemId);
    renderItems();
  }
}

function applyAIProposal() {
  const proposal = state.aiProposal;
  if (!proposal) return;
  const item = state.current.items.find((candidate) => candidate.id === proposal.itemId);
  if (!item) { $("#aiPreviewDialog").close(); return; }
  if ($("#applyReadingInput").checked) item.reading_ja = $("#aiReadingResult").value.trim();
  if ($("#applyMeaningInput").checked) item.meaning_ja = $("#aiMeaningResult").value.trim();
  if ($("#applyAudioInput").checked && proposal.audio) item.audio = proposal.audio;
  $("#aiPreviewDialog").close();
  state.aiProposal = null;
  markDirty();
  renderItems();
  validateAndRender();
  showToast("AI 생성 결과를 항목에 적용했습니다.");
}

async function fillEmptyWithAI() {
  if (state.batchBusy) return;
  const allCandidates = state.current.items.filter((item) => item.ko?.trim() && (!item.reading_ja?.trim() || !item.meaning_ja?.trim() || !item.audio));
  if (!allCandidates.length) { showToast("AI로 채울 빈 항목이 없습니다."); return; }
  const candidates = allCandidates.slice(0, 25);
  const needsText = candidates.some((item) => !item.reading_ja?.trim() || !item.meaning_ja?.trim());
  if (needsText && !state.aiStatus?.configured) { openAISetup(); return; }
  const suffix = allCandidates.length > 25 ? " 이번에는 앞의 25개만 처리합니다." : "";
  if (!window.confirm(`${candidates.length}개 항목의 빈 읽기·뜻·오디오를 생성할까요?${suffix}`)) return;

  state.batchBusy = true;
  const button = $("#aiFillEmptyButton");
  button.disabled = true;
  button.classList.add("is-loading");
  let completed = 0;
  const failures = [];
  for (const item of candidates) {
    button.textContent = `✦ AI ${completed + 1}/${candidates.length}`;
    state.aiBusy.add(item.id);
    renderItems();
    try {
      const missingText = !item.reading_ja?.trim() || !item.meaning_ja?.trim();
      const result = missingText
        ? await api("/api/ai/enrich", {
            method: "POST",
            body: JSON.stringify({
              ko: item.ko,
              deck_name: state.current.name,
              deck_type: state.current.type,
              generate_audio: !item.audio,
            }),
          })
        : await api("/api/audio/generate", { method: "POST", body: JSON.stringify({ ko: item.ko }) });
      if (!item.reading_ja?.trim() && result.reading_ja) item.reading_ja = result.reading_ja;
      if (!item.meaning_ja?.trim() && result.meaning_ja) item.meaning_ja = result.meaning_ja;
      if (!item.audio && result.audio) item.audio = result.audio;
      completed += 1;
    } catch (error) {
      failures.push(`${item.ko}: ${error.message}`);
    } finally {
      state.aiBusy.delete(item.id);
    }
  }
  state.batchBusy = false;
  button.disabled = false;
  button.classList.remove("is-loading");
  button.textContent = "✦ 빈칸 AI 채우기";
  if (completed) markDirty();
  renderItems();
  validateAndRender();
  showToast(failures.length ? `${completed}개 완료, ${failures.length}개 실패` : `${completed}개 항목을 채웠습니다.`, failures.length > 0);
}

function nextItemId() {
  const used = new Set(state.current.items.map((item) => item.id));
  let number = state.current.items.length + 1;
  while (used.has(`i_${String(number).padStart(3, "0")}`)) number += 1;
  return `i_${String(number).padStart(3, "0")}`;
}

function addItem(afterIndex = null, source = null) {
  const item = source ? { ...clone(source), id: nextItemId() } : { id: nextItemId(), ko: "", reading_ja: "", meaning_ja: "", audio: null };
  if (afterIndex === null) state.current.items.push(item);
  else state.current.items.splice(afterIndex + 1, 0, item);
  markDirty();
  renderItems();
  validateAndRender();
  const row = $(`#itemRows tr[data-index="${afterIndex === null ? state.current.items.length - 1 : afterIndex + 1}"] input[data-item-field="ko"]`);
  row?.focus();
}

function validateClient(deck) {
  const issues = [];
  const add = (severity, code, path, message) => issues.push({ severity, code, path, message });
  const idPattern = /^[a-z0-9][a-z0-9_-]{2,63}$/;
  if (!idPattern.test(deck.deck_id || "")) add("error", "identifier", "deck_id", "덱 ID 형식을 확인해 주세요.");
  if (!String(deck.name || "").trim()) add("error", "required", "name", "덱 이름을 입력해 주세요.");
  if (!Number.isInteger(deck.version) || deck.version < 1) add("error", "range", "version", "버전은 1 이상이어야 합니다.");
  if (!Array.isArray(deck.tags) || deck.tags.length < 1 || deck.tags.length > 8) add("error", "tag_count", "tags", "태그는 1~8개여야 합니다.");
  if (new Set(deck.tags || []).size !== (deck.tags || []).length) add("error", "duplicate", "tags", "중복 태그가 있습니다.");
  if (!deck.author || !idPattern.test(deck.author.id || "")) add("error", "identifier", "author.id", "작성자 ID 형식을 확인해 주세요.");
  if (!String(deck.author?.nickname || "").trim()) add("error", "required", "author.nickname", "작성자 표시 이름이 필요합니다.");
  if (!Array.isArray(deck.items) || deck.items.length < 1) add("error", "item_count", "items", "학습 항목이 하나 이상 필요합니다.");
  const ids = new Set();
  const targets = new Map();
  (deck.items || []).forEach((item, index) => {
    const path = `items[${index}]`;
    if (!idPattern.test(item.id || "")) add("error", "identifier", `${path}.id`, "항목 ID 형식을 확인해 주세요.");
    else if (ids.has(item.id)) add("error", "duplicate", `${path}.id`, "중복된 항목 ID입니다.");
    ids.add(item.id);
    if (!String(item.ko || "").trim()) add("error", "required", `${path}.ko`, "한국어를 입력해 주세요.");
    else if ([...item.ko].length > 10) add("error", "max_target_length", `${path}.ko`, "공백 포함 10자 이하여야 합니다.");
    if (!String(item.reading_ja || "").trim()) add("error", "required", `${path}.reading_ja`, "가타카나 읽기를 입력해 주세요.");
    if (!String(item.meaning_ja || "").trim()) add("error", "required", `${path}.meaning_ja`, "일본어 뜻을 입력해 주세요.");
    if (item.audio === null) add("warning", "missing_audio", `${path}.audio`, "오프라인 음원이 없어 기기 TTS로 대체됩니다.");
    if (item.ko) targets.set(item.ko, (targets.get(item.ko) || 0) + 1);
  });
  targets.forEach((count, target) => { if (count > 1) add("warning", "duplicate_target", "items", `‘${target}’ 항목이 ${count}번 중복됩니다.`); });
  return issues;
}

function validateAndRender() {
  if (!state.current) return;
  state.issues = validateClient(state.current);
  renderValidation();
  renderPreview();
  clearTimeout(state.validationTimer);
  state.validationTimer = setTimeout(async () => {
    try {
      const payload = await api("/api/validate", { method: "POST", body: JSON.stringify({ deck: state.current }) });
      state.issues = payload.issues;
      renderValidation();
    } catch { /* client validation remains visible */ }
  }, 320);
}

function renderValidation() {
  const errors = state.issues.filter((entry) => entry.severity === "error");
  const warnings = state.issues.filter((entry) => entry.severity === "warning");
  const score = $("#validationScore");
  score.className = `validation-score${errors.length ? " has-error" : warnings.length ? " has-warning" : ""}`;
  score.textContent = errors.length ? errors.length : warnings.length ? warnings.length : "OK";
  const profile = $("#profileStatus");
  profile.className = `health-badge${errors.length ? " has-error" : ""}`;
  profile.textContent = errors.length ? `오류 ${errors.length}` : warnings.length ? `경고 ${warnings.length}` : "검사 통과";
  $("#saveButton").disabled = errors.length > 0;
  const list = $("#validationList");
  const visible = [...errors, ...warnings].slice(0, 18);
  list.innerHTML = visible.length
    ? visible.map((entry) => `<div class="validation-item ${entry.severity}"><code>${escapeHTML(entry.path)}</code><p>${escapeHTML(entry.message)}</p></div>`).join("")
    : `<div class="validation-empty">스키마와 한글 조합 규칙을 모두 통과했습니다.<br />저장할 준비가 됐어요.</div>`;
  if (state.issues.length > visible.length) list.insertAdjacentHTML("beforeend", `<div class="validation-item warning"><p>그 외 ${state.issues.length - visible.length}개 항목이 있습니다.</p></div>`);
  markInvalidRows();
}

function markInvalidRows() {
  $$("#itemRows tr").forEach((row) => {
    const index = Number(row.dataset.index);
    const rowIssues = state.issues.filter((entry) => entry.severity === "error" && entry.path.startsWith(`items[${index}]`));
    row.classList.toggle("has-error", rowIssues.length > 0);
    $$('[data-item-field]', row).forEach((input) => {
      input.classList.toggle("is-invalid", rowIssues.some((entry) => entry.path.endsWith(`.${input.dataset.itemField}`)));
    });
  });
}

function markDirty() {
  state.dirty = true;
  renderSaveState();
  persistDraft();
}

function renderSaveState(kind = null, text = null) {
  const view = $("#saveState");
  view.className = `save-state${kind === "error" ? " is-error" : state.dirty ? " is-dirty" : ""}`;
  view.lastChild.textContent = text || (state.dirty ? "저장 전 변경 있음" : "모든 변경 저장됨");
}

function persistDraft() {
  if (!state.current) return;
  try {
    localStorage.setItem(draftKey(), JSON.stringify({ deck: state.current, folder: state.folder, catalogVisible: state.catalogVisible, featured: state.featured, savedAt: Date.now() }));
  } catch { /* storage is best effort */ }
}

function restoreDraftIfAvailable() {
  $("#draftBanner").hidden = true;
  try {
    const raw = localStorage.getItem(draftKey());
    if (!raw) return;
    const draft = JSON.parse(raw);
    if (!draft.deck || JSON.stringify(draft.deck) === JSON.stringify(state.saved)) return;
    state.current = draft.deck;
    state.folder = draft.folder ?? state.folder;
    state.catalogVisible = draft.catalogVisible ?? state.catalogVisible;
    state.featured = draft.featured ?? state.featured;
    state.dirty = true;
    $("#draftBanner").hidden = false;
  } catch { /* ignore invalid draft */ }
}

function clearDraft() {
  try { localStorage.removeItem(draftKey()); } catch { /* ignore */ }
  $("#draftBanner").hidden = true;
}

async function saveDeck() {
  validateAndRender();
  const errors = state.issues.filter((entry) => entry.severity === "error");
  if (errors.length) { showToast("오류를 먼저 고쳐 주세요.", true); return; }
  const sourceSummary = state.index.find((entry) => entry.path === state.sourcePath);
  if (sourceSummary?.catalog_visible && !state.catalogVisible && !window.confirm("이 덱을 발견 카탈로그에서 제외할까요? 덱 파일은 그대로 남습니다.")) return;
  const button = $("#saveButton");
  button.disabled = true;
  button.textContent = "저장 중…";
  renderSaveState(null, "파일과 카탈로그 저장 중");
  try {
    const payload = await api("/api/save", {
      method: "POST",
      body: JSON.stringify({
        deck: state.current,
        source_path: state.sourcePath,
        folder: state.folder,
        catalog_visible: state.catalogVisible,
        featured: state.featured,
        bump_version: $("#bumpVersionInput").checked,
      }),
    });
    clearDraft();
    state.current = payload.deck;
    state.saved = clone(payload.deck);
    state.sourcePath = payload.path;
    state.dirty = false;
    $("#bumpVersionInput").checked = true;
    await loadIndex();
    renderAll();
    showToast(payload.message);
  } catch (error) {
    if (error.payload?.issues) { state.issues = error.payload.issues; renderValidation(); }
    renderSaveState("error", "저장하지 못함");
    showToast(error.message, true);
  } finally {
    button.textContent = "저장";
    button.disabled = state.issues.some((entry) => entry.severity === "error");
  }
}

function exportDeck() {
  const blob = new Blob([`${JSON.stringify(state.current, null, 2)}\n`], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${state.current.deck_id}_v${state.current.version}.json`;
  link.click();
  URL.revokeObjectURL(url);
}

async function importDeck(file) {
  try {
    const deck = JSON.parse(await file.text());
    if (!deck || typeof deck !== "object" || Array.isArray(deck)) throw new Error("덱 JSON 객체가 아닙니다.");
    createNewDeck();
    state.current = normalizeImportedDeck(deck);
    state.current.deck_id = state.index.some((entry) => entry.deck_id === deck.deck_id) ? copyDeckId(deck.deck_id) : deck.deck_id;
    state.current.version = 1;
    state.current.created_at ||= todayISO();
    state.current.updated_at = todayISO();
    renderAll();
    showToast("JSON을 새 덱으로 불러왔습니다.");
  } catch (error) { showToast(`JSON을 불러오지 못했습니다: ${error.message}`, true); }
}

function applyPaste() {
  const text = $("#pasteTextarea").value.trim();
  if (!text) { showToast("붙여넣을 표가 비어 있습니다.", true); return; }
  let lines = text.split(/\r?\n/).filter((line) => line.trim()).map((line) => line.split("\t"));
  if (lines[0] && /한국어|korean|ko/i.test(lines[0][0])) lines = lines.slice(1);
  const valid = lines.filter((columns) => columns.length >= 3);
  if (!valid.length) { showToast("탭으로 구분된 열 3개 이상이 필요합니다.", true); return; }
  const pasted = valid.map((columns) => ({
    id: "",
    ko: (columns[0] || "").trim(),
    reading_ja: (columns[1] || "").trim(),
    meaning_ja: (columns[2] || "").trim(),
    audio: (columns[3] || "").trim() || null,
  }));
  if ($('input[name="pasteMode"]:checked').value === "replace") state.current.items = [];
  pasted.forEach((item) => { item.id = nextItemId(); state.current.items.push(item); });
  state.itemQuery = "";
  $("#itemSearch").value = "";
  $("#pasteDialog").close();
  $("#pasteTextarea").value = "";
  markDirty();
  renderItems();
  validateAndRender();
  showToast(`${pasted.length}개 항목을 적용했습니다.`);
}

function escapeHTML(value) {
  return String(value ?? "").replace(/[&<>"]/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[character]);
}

function escapeAttribute(value) { return escapeHTML(value).replace(/'/g, "&#39;"); }

function showToast(message, isError = false) {
  const toast = $("#toast");
  toast.textContent = message;
  toast.className = `toast is-visible${isError ? " is-error" : ""}`;
  clearTimeout(state.toastTimer);
  state.toastTimer = setTimeout(() => { toast.className = "toast"; }, 2800);
}

function bindEvents() {
  $("#newDeckButton").addEventListener("click", () => createNewDeck());
  $('[data-action="new"]').addEventListener("click", () => createNewDeck());
  $("#cloneButton").addEventListener("click", () => createNewDeck(state.current));
  $("#saveButton").addEventListener("click", saveDeck);
  $("#exportButton").addEventListener("click", exportDeck);
  $("#importButton").addEventListener("click", () => $("#fileInput").click());
  $("#fileInput").addEventListener("change", (event) => { if (event.target.files[0]) importDeck(event.target.files[0]); event.target.value = ""; });
  $("#deckSearch").addEventListener("input", (event) => { state.deckQuery = event.target.value; renderDeckList(); });
  $("#itemSearch").addEventListener("input", (event) => { state.itemQuery = event.target.value; renderItems(); });
  $$(".filter-tab").forEach((button) => button.addEventListener("click", () => {
    $$(".filter-tab").forEach((candidate) => candidate.classList.remove("is-active"));
    button.classList.add("is-active");
    state.filter = button.dataset.filter;
    renderDeckList();
  }));
  $$('[data-deck-field]').forEach((input) => input.addEventListener("input", () => {
    const field = input.dataset.deckField;
    state.current[field] = ["version", "level"].includes(field) ? Number(input.value) : input.value;
    markDirty(); renderMetadata(); validateAndRender();
  }));
  $$('[data-author-field]').forEach((input) => input.addEventListener("input", () => {
    state.current.author[input.dataset.authorField] = input.value;
    markDirty(); renderMetadata(); validateAndRender();
  }));
  $("#tagsInput").addEventListener("input", (event) => {
    state.current.tags = event.target.value.split(",").map((tag) => tag.trim()).filter(Boolean);
    markDirty(); renderTags(); renderPreview(); validateAndRender();
  });
  $("#createdAtInput").addEventListener("input", (event) => {
    if (event.target.value) state.current.created_at = `${event.target.value}T00:00:00Z`;
    markDirty(); validateAndRender();
  });
  $("#officialInput").addEventListener("change", (event) => { state.current.official = event.target.checked; markDirty(); validateAndRender(); });
  $("#catalogVisibleInput").addEventListener("change", (event) => {
    state.catalogVisible = event.target.checked;
    if (state.catalogVisible) state.folder = "";
    if (!state.catalogVisible) state.featured = false;
    markDirty(); renderMetadata(); validateAndRender();
  });
  $("#featuredInput").addEventListener("change", (event) => { state.featured = event.target.checked; markDirty(); });
  $("#folderSelect").addEventListener("change", (event) => { state.folder = event.target.value; markDirty(); renderMetadata(); });
  $("#addItemButton").addEventListener("click", () => addItem());
  $("#addItemBottomButton").addEventListener("click", () => addItem());
  $("#pasteButton").addEventListener("click", () => $("#pasteDialog").showModal());
  $("#applyPasteButton").addEventListener("click", applyPaste);
  $("#aiStatusButton").addEventListener("click", openAISetup);
  $("#aiFillEmptyButton").addEventListener("click", fillEmptyWithAI);
  $("#applyAIButton").addEventListener("click", applyAIProposal);
  $("#itemRows").addEventListener("input", (event) => {
    const input = event.target.closest("[data-item-field]");
    if (!input) return;
    const row = input.closest("tr");
    const item = state.current.items[Number(row.dataset.index)];
    item[input.dataset.itemField] = input.dataset.itemField === "audio" ? (input.value.trim() || null) : input.value;
    if (input.dataset.itemField === "ko") {
      const count = $(".character-count", row);
      count.textContent = `${[...input.value].length}/10`;
      count.classList.toggle("is-over", [...input.value].length > 10);
    }
    markDirty(); validateAndRender();
  });
  $("#itemRows").addEventListener("click", (event) => {
    const button = event.target.closest("[data-row-action]");
    if (!button) return;
    const index = Number(button.closest("tr").dataset.index);
    const action = button.dataset.rowAction;
    if (action === "delete") {
      state.current.items.splice(index, 1);
      markDirty(); renderItems(); validateAndRender();
    } else if (action === "duplicate") addItem(index, state.current.items[index]);
    else if (action === "ai") runAIForItem(index);
    else if (action === "null-audio") {
      state.current.items[index].audio = null;
      markDirty(); renderItems(); validateAndRender();
    }
  });
  $("#discardDraftButton").addEventListener("click", () => {
    clearDraft();
    if (state.saved) { state.current = clone(state.saved); state.dirty = false; renderAll(); }
  });
  window.addEventListener("beforeunload", (event) => { if (state.dirty) { event.preventDefault(); event.returnValue = ""; } });
  document.addEventListener("keydown", (event) => {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "s") { event.preventDefault(); if (state.current) saveDeck(); }
  });
}

async function init() {
  bindEvents();
  try {
    await Promise.all([loadIndex(), loadAIStatus()]);
    const first = state.index.find((deck) => deck.catalog_visible) || state.index[0];
    if (first) await selectDeck(first);
  } catch (error) {
    $("#emptyState h2").textContent = "카탈로그를 열지 못했습니다";
    $("#emptyState > p:not(.section-kicker)").textContent = error.message;
    showToast(error.message, true);
  }
}

init();
