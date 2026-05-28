const API_BASE = '/api/debate';

const { createApp, ref, nextTick, onMounted } = Vue;

createApp({
    setup() {
        const view = ref('arena');
        const debate = ref(null);
        const historyList = ref([]);
        const topicInput = ref('');
        const maxRounds = ref(5);
        const loading = ref(false);
        const chatArea = ref(null);

        // 流式状态
        const streaming = ref(false);
        const streamingRole = ref('');
        const streamingContent = ref('');
        const streamingRound = ref(0);

        // ===== API 封装 =====
        async function api(method, path, body) {
            const opts = { method, headers: { 'Content-Type': 'application/json' } };
            if (body) opts.body = JSON.stringify(body);
            const res = await fetch(API_BASE + path, opts);
            const json = await res.json();
            if (json.code !== 200) throw new Error(json.message || '请求失败');
            return json.data;
        }

        // ===== SSE 流式读取 =====
        async function streamApi(path, body) {
            const res = await fetch(API_BASE + path, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: body ? JSON.stringify(body) : null
            });

            if (!res.ok) {
                const json = await res.json().catch(() => null);
                throw new Error(json?.message || `HTTP ${res.status}`);
            }

            const reader = res.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';

            return {
                async *[Symbol.asyncIterator]() {
                    while (true) {
                        const { done, value } = await reader.read();
                        if (done) break;
                        buffer += decoder.decode(value, { stream: true });

                        let boundary;
                        while ((boundary = buffer.indexOf('\n\n')) !== -1) {
                            const rawEvent = buffer.substring(0, boundary);
                            buffer = buffer.substring(boundary + 2);

                            const event = parseSSE(rawEvent);
                            if (event) yield event;
                        }
                    }
                    // 处理残留
                    if (buffer.trim()) {
                        const event = parseSSE(buffer);
                        if (event) yield event;
                    }
                }
            };
        }

        function parseSSE(raw) {
            let eventType = 'message';
            let data = '';
            for (const line of raw.split('\n')) {
                if (line.startsWith('event:')) {
                    eventType = line.substring(6).trim();
                } else if (line.startsWith('data:')) {
                    data = line.substring(5).trim();
                }
            }
            if (!data) return null;
            return { event: eventType, data };
        }

        function scrollToBottom() {
            nextTick(() => {
                if (chatArea.value) chatArea.value.scrollTop = chatArea.value.scrollHeight;
            });
        }

        // ===== 核心操作（流式） =====
        async function createAndStart() {
            if (!topicInput.value.trim()) return;
            loading.value = true;
            try {
                // 创建辩论
                const created = await api('POST', '', {
                    topic: topicInput.value.trim(),
                    maxRounds: maxRounds.value
                });

                // 初始化辩论状态
                debate.value = await api('GET', `/${created.id}`, null);

                // 流式开始辩论
                streaming.value = true;
                streamingRole.value = 'PRO';
                streamingContent.value = '';
                streamingRound.value = 1;

                const stream = await streamApi(`/${created.id}/stream/start`);
                for await (const evt of stream) {
                    if (evt.event === 'token') {
                        const parsed = JSON.parse(evt.data);
                        if (parsed.role) streamingRole.value = parsed.role;
                        streamingContent.value += parsed.content;
                        scrollToBottom();
                    } else if (evt.event === 'done') {
                        debate.value = JSON.parse(evt.data);
                    } else if (evt.event === 'error') {
                        throw new Error(evt.data);
                    }
                }

                streaming.value = false;
                scrollToBottom();
            } catch (e) {
                streaming.value = false;
                alert('创建失败: ' + e.message);
            } finally {
                loading.value = false;
            }
        }

        async function advance() {
            if (!debate.value) return;
            loading.value = true;
            try {
                const currentRound = debate.value.currentRound;

                streaming.value = true;
                streamingRole.value = 'CON';
                streamingContent.value = '';
                streamingRound.value = currentRound;

                const stream = await streamApi(`/${debate.value.id}/stream/advance`);
                for await (const evt of stream) {
                    if (evt.event === 'speaker') {
                        // 切换发言者
                        const parsed = JSON.parse(evt.data);
                        streamingRole.value = parsed.role;
                        streamingContent.value = '';
                        streamingRound.value = parsed.round || (parsed.role === 'PRO' ? currentRound + 1 : currentRound);
                    } else if (evt.event === 'token') {
                        const parsed = JSON.parse(evt.data);
                        streamingContent.value += parsed.content;
                        scrollToBottom();
                    } else if (evt.event === 'done') {
                        debate.value = JSON.parse(evt.data);
                    } else if (evt.event === 'error') {
                        throw new Error(evt.data);
                    }
                }

                streaming.value = false;
                scrollToBottom();
            } catch (e) {
                streaming.value = false;
                alert('推进失败: ' + e.message);
            } finally {
                loading.value = false;
            }
        }

        async function terminate() {
            if (!debate.value) return;
            if (!confirm('确定要终止本场辩论吗？')) return;
            loading.value = true;
            try {
                debate.value = await api('POST', `/${debate.value.id}/terminate`, null);
            } catch (e) {
                alert('终止失败: ' + e.message);
            } finally {
                loading.value = false;
            }
        }

        async function judge() {
            if (!debate.value) return;
            loading.value = true;
            try {
                streaming.value = true;
                streamingRole.value = 'JUDGE';
                streamingContent.value = '';
                streamingRound.value = 0;

                const stream = await streamApi(`/${debate.value.id}/stream/judge`);
                for await (const evt of stream) {
                    if (evt.event === 'token') {
                        const parsed = JSON.parse(evt.data);
                        streamingContent.value += parsed.content;
                        scrollToBottom();
                    } else if (evt.event === 'done') {
                        debate.value = JSON.parse(evt.data);
                    } else if (evt.event === 'error') {
                        throw new Error(evt.data);
                    }
                }

                streaming.value = false;
                scrollToBottom();
            } catch (e) {
                streaming.value = false;
                alert('评审失败: ' + e.message);
            } finally {
                loading.value = false;
            }
        }

        function reset() {
            debate.value = null;
            topicInput.value = '';
            streaming.value = false;
            streamingContent.value = '';
        }

        async function loadHistory() {
            try {
                historyList.value = await api('GET', '', null);
            } catch (e) {
                console.error('加载历史失败', e);
            }
        }

        async function loadDebate(id) {
            loading.value = true;
            try {
                debate.value = await api('GET', `/${id}`, null);
                streaming.value = false;
                view.value = 'arena';
                scrollToBottom();
            } catch (e) {
                alert('加载失败: ' + e.message);
            } finally {
                loading.value = false;
            }
        }

        async function deleteDebate(id) {
            if (!confirm('确定要删除这场辩论吗？')) return;
            try {
                await api('DELETE', `/${id}`, null);
                historyList.value = historyList.value.filter(d => d.id !== id);
                if (debate.value && debate.value.id === id) {
                    debate.value = null;
                }
            } catch (e) {
                alert('删除失败: ' + e.message);
            }
        }

        // ===== 辅助函数 =====
        function agentIcon(role) {
            return { PRO: '⚖️', CON: '⚔️', JUDGE: '🏛️' }[role] || '💬';
        }

        function agentName(role) {
            return { PRO: '正方辩手', CON: '反方辩手', JUDGE: '裁判' }[role] || role;
        }

        function statusText(status) {
            return { PENDING: '待开始', RUNNING: '辩论中', FINISHED: '已结束', TERMINATED: '已终止' }[status] || status;
        }

        function flowClass(role) {
            if (!debate.value) return '';
            const d = debate.value;
            if (d.status === 'FINISHED' || d.status === 'TERMINATED') {
                return role === 'JUDGE' ? 'active-judge' : 'done';
            }
            if (d.currentSpeaker === role) {
                return { PRO: 'active-pro', CON: 'active-con', JUDGE: 'active-judge' }[role];
            }
            return '';
        }

        function renderMd(text) {
            if (!text) return '';
            return marked.parse(text);
        }

        function formatTime(timeStr) {
            if (!timeStr) return '';
            const d = new Date(timeStr);
            return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
        }

        return {
            view, debate, historyList, topicInput, maxRounds, loading, chatArea,
            streaming, streamingRole, streamingContent, streamingRound,
            createAndStart, advance, terminate, judge, reset,
            loadHistory, loadDebate, deleteDebate,
            agentIcon, agentName, statusText, flowClass, renderMd, formatTime
        };
    }
}).mount('#app');
