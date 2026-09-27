<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { marked } from 'marked';
import DOMPurify from 'dompurify';
import { chatStream, getChatHistory, getChatSessions, deleteChatSession } from '../api';
import { store, isLoggedIn } from '../store';
import type { ChatMessage, ChatHistoryMsg, ChatMeta, ChatSessionSummary, ConfirmEvent } from '../types';

/* ---------------- 状态 ---------------- */
const sessionId = ref(localStorage.getItem('agent_session_id') || '');
const input = ref('');
const messages = ref<ChatMessage[]>([]);
const sending = ref(false);
const listRef = ref<HTMLElement | null>(null);

/** ReAct 多轮工具调用开关 */
const reactEnabled = ref(false);
/** 当前会话已确认执行的高风险操作凭据（Human-in-the-Loop，由服务端签发，仅原样回传） */
const confirmTokens = ref<string[]>([]);
/** 最近一次用户消息文本，用于确认后自动重发 */
const lastUserMessage = ref('');
/** 当前等待用户确认的 HITL 事件 */
const pendingConfirm = ref<ConfirmEvent | null>(null);
/** 用户确认后待重发的消息（等当前 chatStream 完全结束后再发，避免 sending 状态冲突） */
const pendingResendMessage = ref('');
/** 会话列表 */
const sessions = ref<ChatSessionSummary[]>([]);
/** 是否正在加载会话列表 */
const loadingSessions = ref(false);
/** 进行中 SSE 流的控制器（用于离开页面时主动断开） */
let streamAbort: AbortController | null = null;

/* ---------------- Markdown 渲染 ---------------- */
/**
 * AI 回复为 Markdown，经 marked 转 HTML 后 DOMPurify 净化（防注入）。
 * gfm:true   —— 支持任务列表、表格等 GFM 语法
 * breaks:true —— 单换行渲染为 <br>（AI 用 "- " 分点的行必须换行展示）
 * 解析失败回退为纯文本（转义 HTML 后原样展示，避免样式丢失）
 */
function renderMarkdown(text: string): string {
  if (!text) return '';
  try {
    const html = marked.parse(text, { async: false, gfm: true, breaks: true }) as string;
    return DOMPurify.sanitize(html);
  } catch {
    return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
  }
}

/* ---------------- 会话管理 ---------------- */
function newSession(): void {
  sessionId.value = crypto.randomUUID().replace(/-/g, '');
  localStorage.setItem('agent_session_id', sessionId.value);
  messages.value = [];
  confirmTokens.value = [];
  pendingConfirm.value = null;
  lastUserMessage.value = '';
  pendingResendMessage.value = '';
  ElMessage.success(`已创建新会话：${sessionId.value.slice(0, 8)}…`);
  void loadSessions();
}

function ensureSession(): void {
  if (!sessionId.value) {
    newSession();
  }
}

async function loadSessions(): Promise<void> {
  if (!isLoggedIn.value) return;
  loadingSessions.value = true;
  try {
    sessions.value = await getChatSessions(store.token);
  } catch (e) {
    console.warn('加载会话列表失败', e);
  } finally {
    loadingSessions.value = false;
  }
}

async function switchSession(targetSessionId: string): Promise<void> {
  if (targetSessionId === sessionId.value || sending.value) return;
  sessionId.value = targetSessionId;
  localStorage.setItem('agent_session_id', targetSessionId);
  messages.value = [];
  confirmTokens.value = [];
  pendingConfirm.value = null;
  lastUserMessage.value = '';
  pendingResendMessage.value = '';
  await loadHistory();
}

async function removeSession(targetSessionId: string): Promise<void> {
  try {
    await ElMessageBox.confirm('删除后不可恢复，是否继续？', '删除会话', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await deleteChatSession(store.token, targetSessionId);
    ElMessage.success('会话已删除');
    if (targetSessionId === sessionId.value) {
      newSession();
    } else {
      await loadSessions();
    }
  } catch (e) {
    ElMessage.error('删除失败');
  }
}

/* 监听 App.vue 顶部「新会话」按钮：纪元变化即重置（组件卸载期间的变化由挂载时的 epoch 对比补偿） */
const handledEpoch = ref(store.newSessionReq);
watch(
  () => store.newSessionReq,
  () => {
    if (store.newSessionReq === handledEpoch.value) return;
    handledEpoch.value = store.newSessionReq;
    newSession();
  },
);

/* ---------------- 对话（流式） ---------------- */
const quickCmds: string[] = [
  '帮我搜索3000块左右的手机',
  '查看我的购物车',
  '查看我的订单',
  '把 MacBook Air M3 加入购物车',
  '帮我下单 TestPhone，1 件',
  '查看我的默认地址',
];

/**
 * 中止进行中的 SSE 流。
 *
 * 离开聊天页（组件卸载）时必须调用：不主动断开的话 `getReader()` 会一直读到服务端
 * complete，期间组件已销毁、token 回调仍在往已卸载的响应式状态里写数据。
 * 同时清掉待重发消息，避免流被中止后又有一次「确认后重发」发向已卸载的页面。
 */
function abortStream(): void {
  streamAbort?.abort();
  streamAbort = null;
  pendingResendMessage.value = '';
}

/**
 * 发送一条用户消息并消费流式回复。
 *
 * @param text     消息内容，缺省取输入框
 * @param echoUser 是否在消息列表里补一条用户气泡并清空输入框。确认后自动重发传 false：
 *                 那条消息用户已经看到过，重复补一条会让同一句话在界面上出现两次，
 *                 而且此时用户可能正在输入别的内容，不该被清空。
 */
async function send(text?: string, echoUser = true): Promise<void> {
  const content = (text ?? input.value).trim();
  if (!content || sending.value) return;
  if (!isLoggedIn.value) {
    ElMessage.warning('请先登录');
    return;
  }
  ensureSession();

  if (echoUser) {
    input.value = '';
    messages.value.push({ id: crypto.randomUUID(), role: 'user', content });
  }
  lastUserMessage.value = content;

  // 立即创建空助手消息：meta 事件补全工具过程，token 事件逐块追加回复。
  // 必须用 reactive 包一层再逐块追加：直接改「push 进数组的那个原始对象」绕过了
  // 数组代理的 set 拦截，不会触发依赖更新。此时视图只在其它响应式状态变化时才刷新
  // （如 finally 里把 sending 置回 false），实测表现为「正在思考…」之后整段一次性出现，
  // SSE 流式的打字机效果完全失效。
  const assistantMsg = reactive<ChatMessage>({ id: crypto.randomUUID(), role: 'assistant', content: '' });
  messages.value.push(assistantMsg);
  sending.value = true;
  pendingConfirm.value = null;
  scrollToBottom();

  // 一次会话一个控制器：组件卸载时用它断开本条流
  const controller = new AbortController();
  streamAbort = controller;

  try {
    await chatStream(
      store.token,
      content,
      sessionId.value,
      {
        onMeta: (meta: ChatMeta) => {
          // 同步后端最新会话ID（后端可能因归属校验失败而重建会话）
          if (meta.sessionId) {
            sessionId.value = meta.sessionId;
            localStorage.setItem('agent_session_id', meta.sessionId);
          }
          assistantMsg.skillId = meta.skillId;
          assistantMsg.plan = meta.plan;
          assistantMsg.toolResults = meta.toolResults;
          scrollToBottom();
        },
        onConfirm: (confirm: ConfirmEvent) => {
          pendingConfirm.value = confirm;
          assistantMsg.content = confirm.confirmMessage || '检测到高风险操作，请确认是否继续？';
          scrollToBottom();
          showConfirmDialog(confirm);
        },
        onToken: (t: string) => {
          assistantMsg.content += t;
          scrollToBottom();
        },
        onDone: () => {
          /* 无需处理，finally 收尾 */
        },
      },
      {
        confirmTokens: confirmTokens.value,
        reactEnabled: reactEnabled.value,
        signal: controller.signal,
      },
    );
  } catch (e) {
    const err = e as Error;
    if (err.name === 'AbortError') {
      // 主动中止（离开页面）：不是故障，不写「连接中断」提示
      return;
    }
    assistantMsg.content = assistantMsg.content
      ? `${assistantMsg.content}\n\n> ⚠️ 连接中断：${err.message}`
      : `请求失败：${err.message}`;
  } finally {
    // 只有仍是自己那条流时才清理，避免覆盖后续请求的控制器
    if (streamAbort === controller) {
      streamAbort = null;
    }
    sending.value = false;
    scrollToBottom();
    // 刷新侧栏会话列表：会话摘要（最后一条消息预览、条数）只在本轮结束后才变化；
    // 且首轮请求里客户端自编的 sessionId 会被服务端重建（见 ConversationService.getOrCreateSession），
    // 不刷新的话当前会话既不会高亮、预览也停留在进页面时的旧值。
    void loadSessions();
    // 确认弹窗若在流结束前就被点击，重发请求会暂存在这里，等本轮收尾后再发
    const resend = pendingResendMessage.value;
    pendingResendMessage.value = '';
    if (resend) {
      // 重发不补用户气泡：这条消息已经展示过了（见 send 的 echoUser 参数）
      setTimeout(() => void send(resend, false), 0);
    }
  }
}

/**
 * Human-in-the-Loop 确认后重发上一条用户消息。
 *
 * 服务端发出 confirm 事件后立即 complete，因此弹窗往往在「当前流跑完之后」才被点击。
 * 旧实现只在 send() 的 finally 里消费待重发消息，点得稍晚（几秒）就再也不会重发 ——
 * 页面停在确认提示上、也没有任何后续请求。这里按当前是否还在发送分别处理，
 * 两种时序都能续跑。
 *
 * 重发一律传 echoUser=false：用户气泡只保留最初的那一条，否则同一句话会出现两次。
 *
 * @param message 需要重发的用户消息
 */
function resendAfterConfirm(message: string): void {
  if (!message) return;
  if (sending.value) {
    pendingResendMessage.value = message;
    return;
  }
  setTimeout(() => void send(message, false), 0);
}

/**
 * Human-in-the-Loop：弹出二次确认对话框。
 * 用户确认后，将服务端签发的确认凭据加入已确认列表并自动重发上一条用户消息。
 */
async function showConfirmDialog(confirm: ConfirmEvent): Promise<void> {
  const tokens = confirm.pendingSteps
    .map((s) => s.confirmToken)
    .filter((t): t is string => !!t);
  const stepDesc = confirm.pendingSteps.map((s) => `${s.description}（${s.toolName}）`).join('\n');
  try {
    await ElMessageBox.confirm(
      `${confirm.confirmMessage}\n\n涉及步骤：\n${stepDesc}`,
      '高风险操作确认',
      {
        confirmButtonText: '确认执行',
        cancelButtonText: '取消',
        type: 'warning',
        dangerouslyUseHTMLString: false,
      },
    );
    if (tokens.length === 0) {
      // 无凭据时重发只会再次被拦截，直接提示用户重新发起
      ElMessage.error('确认凭据缺失，请重新发起请求');
      pendingConfirm.value = null;
      return;
    }
    // 用户确认：记录服务端凭据，并重发这条消息（凭据随请求一起回传）
    confirmTokens.value = Array.from(new Set([...confirmTokens.value, ...tokens]));
    ElMessage.success('已确认，继续执行');
    resendAfterConfirm(lastUserMessage.value);
  } catch {
    // 用户取消：仅给出提示，不再重发
    ElMessage.info('已取消该操作');
    pendingConfirm.value = null;
  }
}

function scrollToBottom(): void {
  void nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight;
    }
  });
}

async function loadHistory(): Promise<void> {
  if (!sessionId.value || !isLoggedIn.value) return;
  try {
    const history: ChatHistoryMsg[] = await getChatHistory(store.token, sessionId.value);
    if (history.length === 0) return;
    messages.value = history.map((h) => ({
      id: crypto.randomUUID(),
      role: h.role,
      content: h.content,
    }));
    scrollToBottom();
  } catch (e) {
    // 静默失败：允许用户从空会话开始
    console.warn('加载历史会话失败', e);
  }
}

onMounted(() => {
  ensureSession();
  // 补偿：组件卸载期间顶部按钮被点击，挂载时对比纪元后统一重置
  if (store.newSessionReq !== handledEpoch.value) {
    handledEpoch.value = store.newSessionReq;
    newSession();
  } else {
    void loadHistory();
  }
  void loadSessions();
});

// 离开聊天页时断开进行中的 SSE 流（否则连接与 token 回调会一直活到本轮回复结束）
onUnmounted(abortStream);
</script>

<template>
  <div class="chat-page">
    <!-- 左侧会话列表 -->
    <aside class="chat-sidebar">
      <div class="sidebar-header">
        <span>历史会话</span>
        <el-button type="primary" size="small" :disabled="sending" @click="newSession()">
          新建会话
        </el-button>
      </div>
      <el-skeleton v-if="loadingSessions" :rows="4" animated />
      <div v-else class="session-list">
        <div
          v-for="s in sessions"
          :key="s.sessionId"
          class="session-item"
          :class="{ active: s.sessionId === sessionId }"
          @click="switchSession(s.sessionId)"
        >
          <div class="session-title">{{ s.lastMessagePreview || '无消息' }}</div>
          <div class="session-meta">{{ s.messageCount }} 条消息</div>
          <el-button
            class="session-delete"
            type="danger"
            link
            size="small"
            @click.stop="removeSession(s.sessionId)"
          >
            删除
          </el-button>
        </div>
        <el-empty v-if="sessions.length === 0" description="暂无历史会话" />
      </div>
    </aside>

    <!-- 消息区 -->
    <main ref="listRef" class="chat-body">
      <el-empty
        v-if="messages.length === 0"
        description="输入问题开始对话，例如：帮我搜索3000块左右的手机"
      />

      <div
        v-for="msg in messages"
        :key="msg.id"
        class="msg-row"
        :class="msg.role"
      >
        <div class="bubble">
          <!-- 用户消息：纯文本 -->
          <div v-if="msg.role === 'user'" class="bubble-text">{{ msg.content }}</div>

          <!-- 助手消息：Markdown 渲染（流式逐块刷新） -->
          <template v-else>
            <div v-if="msg.content" class="md-body" v-html="renderMarkdown(msg.content)" />
            <div v-else class="thinking">正在思考…</div>

            <!-- 工具调用过程卡片 -->
            <template v-if="msg.toolResults?.length">
              <el-divider content-position="left">工具调用（{{ msg.plan?.steps.length ?? 0 }} 步规划）</el-divider>
              <div class="plan-goal">
                目标：{{ msg.plan?.goal ?? '-' }} ｜ 技能：{{ msg.skillId }}
              </div>
              <el-collapse>
                <el-collapse-item
                  v-for="(tr, idx) in msg.toolResults"
                  :key="idx"
                  :title="`${tr.toolName} · ${tr.status}`"
                >
                  <div class="tool-block">
                    <div class="tool-field">
                      <span class="field-label">参数</span>
                      <pre>{{ tr.args }}</pre>
                    </div>
                    <div class="tool-field">
                      <span class="field-label">结果</span>
                      <pre>{{ tr.result }}</pre>
                    </div>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </template>
          </template>
        </div>
      </div>
    </main>

    <!-- 输入区 -->
    <footer class="chat-footer">
      <div class="quick-cmds">
        <el-tag
          v-for="cmd in quickCmds"
          :key="cmd"
          class="quick-tag"
          effect="plain"
          @click="send(cmd)"
        >
          {{ cmd }}
        </el-tag>
      </div>
      <div class="options-row">
        <el-switch
          v-model="reactEnabled"
          active-text="ReAct 多轮调用"
          inactive-text="单次执行"
          :disabled="sending"
        />
        <el-tag
          v-if="confirmTokens.length > 0"
          type="success"
          size="small"
          effect="plain"
        >
          已确认 {{ confirmTokens.length }} 个高风险操作
        </el-tag>
      </div>
      <div class="input-row">
        <el-input
          v-model="input"
          placeholder="说点什么，比如：把 MacBook Air M3 加入购物车"
          :disabled="!isLoggedIn || sending"
          @keyup.enter="send()"
        />
        <el-button type="primary" :loading="sending" :disabled="!isLoggedIn" @click="send()">
          {{ sending ? '回复生成中…' : '发送' }}
        </el-button>
      </div>
      <p class="hint">
        会话 ID：{{ sessionId.slice(0, 16) }}…（存于浏览器 localStorage，清空浏览器数据或刷新页面后可重开新会话）
      </p>
    </footer>
  </div>
</template>

<style scoped>
.chat-page {
  height: 100%;
  display: flex;
  flex-direction: row;
}

/* 左侧会话列表 */
.chat-sidebar {
  width: 260px;
  flex-shrink: 0;
  border-right: 1px solid #e4e7ed;
  background: #fff;
  display: flex;
  flex-direction: column;
}
.sidebar-header {
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e4e7ed;
  font-weight: 600;
}
.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.session-item {
  position: relative;
  padding: 10px 12px;
  margin-bottom: 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
  border: 1px solid transparent;
}
.session-item:hover {
  background: #f5f7fa;
}
.session-item.active {
  background: #ecf5ff;
  border-color: #b3d8ff;
}
.session-title {
  font-size: 14px;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  padding-right: 40px;
}
.session-meta {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.session-delete {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  opacity: 0;
  transition: opacity 0.2s;
}
.session-item:hover .session-delete {
  opacity: 1;
}

/* 消息区 */
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  background: #f5f7fa;
  min-width: 0;
}
.msg-row {
  display: flex;
}
.msg-row.user {
  justify-content: flex-end;
}
.msg-row.assistant {
  justify-content: flex-start;
}
.bubble {
  max-width: 76%;
  padding: 12px 16px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #e4e7ed;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}
.msg-row.user .bubble {
  background: #409eff;
  border-color: #409eff;
  color: #fff;
}
.bubble-text {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
}
.thinking {
  color: #909399;
  font-style: italic;
}

/* Markdown 渲染 */
.md-body {
  line-height: 1.75;
  word-break: break-word;
  font-size: 14px;
}
.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3),
.md-body :deep(h4) {
  margin: 14px 0 8px;
  font-weight: 600;
}
.md-body :deep(h1) {
  font-size: 18px;
}
.md-body :deep(h2) {
  font-size: 16px;
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 4px;
}
.md-body :deep(h3) {
  font-size: 15px;
}
.md-body :deep(p) {
  margin: 6px 0;
}
.md-body :deep(ul),
.md-body :deep(ol) {
  margin: 6px 0;
  padding-left: 22px;
}
.md-body :deep(li) {
  margin: 3px 0;
  list-style: disc;
}
.md-body :deep(ol > li) {
  list-style: decimal;
}
.md-body :deep(strong) {
  font-weight: 600;
}
.md-body :deep(code) {
  background: #f0f2f5;
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 12.5px;
  font-family: Consolas, 'Courier New', monospace;
}
.md-body :deep(pre) {
  background: #f5f7fa;
  padding: 10px 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 8px 0;
}
.md-body :deep(pre code) {
  background: none;
  padding: 0;
}
.md-body :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
  width: 100%;
  font-size: 13px;
}
.md-body :deep(th),
.md-body :deep(td) {
  border: 1px solid #dcdfe6;
  padding: 6px 10px;
  text-align: left;
}
.md-body :deep(th) {
  background: #f5f7fa;
  font-weight: 600;
}
.md-body :deep(blockquote) {
  margin: 8px 0;
  padding: 4px 12px;
  border-left: 3px solid #409eff;
  color: #606266;
  background: #f5f7fa;
  border-radius: 4px;
}
.md-body :deep(a) {
  color: #409eff;
  text-decoration: none;
}
.plan-goal {
  font-size: 13px;
  color: #909399;
  margin-bottom: 6px;
}
.tool-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.tool-field {
  font-size: 13px;
}
.field-label {
  color: #909399;
  margin-right: 6px;
  font-weight: 600;
}
.tool-field pre {
  margin: 4px 0 0;
  background: #f5f7fa;
  padding: 8px 10px;
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
  font-size: 12px;
  font-family: Consolas, 'Courier New', monospace;
}

/* 输入区 */
.chat-footer {
  padding: 12px 24px 16px;
  background: #fff;
  border-top: 1px solid #e4e7ed;
}
.quick-cmds {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}
.quick-tag {
  cursor: pointer;
  user-select: none;
}
.quick-tag:hover {
  color: #409eff;
  border-color: #409eff;
}
.options-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
  min-height: 28px;
}
.input-row {
  display: flex;
  gap: 10px;
}
.hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: #c0c4cc;
}
</style>
