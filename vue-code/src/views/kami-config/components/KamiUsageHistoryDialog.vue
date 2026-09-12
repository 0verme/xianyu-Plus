<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { toast } from '@/utils/toast'
import {
  getKamiUsageHistoryDetail,
  queryKamiUsageHistory,
  type KamiUsageHistory,
  type KamiUsageHistoryPage
} from '@/api/kami-config'

interface Props {
  modelValue: boolean
  configId: number | null
  configName?: string
}

const props = defineProps<Props>()
const emit = defineEmits<{ (event: 'update:modelValue', value: boolean): void }>()

const loading = ref(false)
const detailLoading = ref(false)
const page = ref<KamiUsageHistoryPage>({ records: [], total: 0, pageNum: 1, pageSize: 20 })
const filters = ref({
  orderId: '',
  buyerKeyword: '',
  goodsId: '',
  deliveryStatus: '',
  startTime: '',
  endTime: ''
})
const detail = ref<KamiUsageHistory | null>(null)

const totalPages = computed(() => Math.max(1, Math.ceil(page.value.total / page.value.pageSize)))
const hasPrevious = computed(() => page.value.pageNum > 1)
const hasNext = computed(() => page.value.pageNum < totalPages.value)

const close = () => {
  emit('update:modelValue', false)
}

const loadPage = async (pageNum = 1) => {
  if (!props.configId) return
  loading.value = true
  try {
    const response = await queryKamiUsageHistory({
      kamiConfigId: props.configId,
      orderId: filters.value.orderId.trim() || undefined,
      buyerKeyword: filters.value.buyerKeyword.trim() || undefined,
      goodsId: filters.value.goodsId.trim() || undefined,
      deliveryStatus: filters.value.deliveryStatus || undefined,
      startTime: filters.value.startTime || undefined,
      endTime: filters.value.endTime || undefined,
      pageNum,
      pageSize: 20
    })
    if (response.code === 200 && response.data) {
      page.value = response.data
    } else {
      toast.error(response.msg || '加载使用历史失败')
    }
  } catch (error) {
    console.error('加载卡密使用历史失败', error)
  } finally {
    loading.value = false
  }
}

const resetAndLoad = () => {
  void loadPage(1)
}

const openDetail = async (record: KamiUsageHistory) => {
  detailLoading.value = true
  try {
    const response = await getKamiUsageHistoryDetail(record.id)
    if (response.code === 200 && response.data) {
      detail.value = response.data
    } else {
      toast.error(response.msg || '加载历史详情失败')
    }
  } catch (error) {
    console.error('加载卡密使用历史详情失败', error)
  } finally {
    detailLoading.value = false
  }
}

const closeDetail = () => {
  detail.value = null
}

const formatTime = (value?: string | null) => value ? value.replace('T', ' ') : '-'

watch(
  () => [props.modelValue, props.configId] as const,
  ([visible]) => {
    if (visible) {
      detail.value = null
      filters.value = {
        orderId: '',
        buyerKeyword: '',
        goodsId: '',
        deliveryStatus: '',
        startTime: '',
        endTime: ''
      }
      void loadPage(1)
    }
  }
)
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="modelValue" class="history-overlay" @click.self="close">
        <section class="history-dialog" aria-labelledby="kami-history-title">
          <header class="history-dialog__header">
            <div>
              <h2 id="kami-history-title">卡密使用历史</h2>
              <p>{{ configName || '当前卡券库' }} · 列表中的卡密已脱敏</p>
            </div>
            <button type="button" class="history-dialog__close" aria-label="关闭" @click="close">×</button>
          </header>

          <div class="history-dialog__filters">
            <input
              v-model="filters.orderId"
              class="native-input"
              placeholder="按订单号搜索"
              @keyup.enter="resetAndLoad"
            />
            <input
              v-model="filters.buyerKeyword"
              class="native-input"
              placeholder="按买家搜索"
              @keyup.enter="resetAndLoad"
            />
            <input
              v-model="filters.goodsId"
              class="native-input"
              placeholder="按商品ID搜索"
              @keyup.enter="resetAndLoad"
            />
            <select v-model="filters.deliveryStatus" class="native-input" aria-label="发货状态">
              <option value="">全部状态</option>
              <option value="DELIVERED">DELIVERED</option>
              <option value="RESERVED">RESERVED</option>
              <option value="REVIEW_REQUIRED">REVIEW_REQUIRED</option>
            </select>
            <label class="history-dialog__time-filter">
              <span>开始时间</span>
              <input v-model="filters.startTime" class="native-input" type="datetime-local" @keyup.enter="resetAndLoad" />
            </label>
            <label class="history-dialog__time-filter">
              <span>结束时间</span>
              <input v-model="filters.endTime" class="native-input" type="datetime-local" @keyup.enter="resetAndLoad" />
            </label>
            <button type="button" class="btn-default" @click="resetAndLoad">搜索</button>
          </div>

          <div class="history-dialog__body">
            <div v-if="loading" class="history-dialog__empty">加载中...</div>
            <div v-else-if="page.records.length === 0" class="history-dialog__empty">暂无使用历史</div>
            <div v-else class="history-table-wrap">
              <table class="history-table">
                <thead>
                  <tr>
                    <th>时间</th>
                    <th>订单号</th>
                    <th>买家</th>
                    <th>卡密（脱敏）</th>
                    <th>状态</th>
                    <th>详情</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="record in page.records" :key="record.id">
                    <td>{{ formatTime(record.deliveryTime) }}</td>
                    <td>{{ record.orderId }}</td>
                    <td>{{ record.buyerUserName || record.buyerUserId || '-' }}</td>
                    <td class="history-table__content">{{ record.kamiContent }}</td>
                    <td>{{ record.deliveryStatus }}</td>
                    <td>
                      <button
                        type="button"
                        class="history-table__detail"
                        :disabled="detailLoading"
                        @click="openDetail(record)"
                      >查看完整卡密</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <footer class="history-dialog__footer">
            <span>共 {{ page.total }} 条，第 {{ page.pageNum }} / {{ totalPages }} 页</span>
            <div>
              <button type="button" class="btn-default btn-sm" :disabled="!hasPrevious || loading" @click="loadPage(page.pageNum - 1)">上一页</button>
              <button type="button" class="btn-default btn-sm" :disabled="!hasNext || loading" @click="loadPage(page.pageNum + 1)">下一页</button>
            </div>
          </footer>

          <div v-if="detail" class="history-detail">
            <div class="history-detail__header">
              <strong>使用历史详情</strong>
              <button type="button" aria-label="关闭详情" @click="closeDetail">×</button>
            </div>
            <dl>
              <dt>卡密</dt>
              <dd class="history-detail__secret">{{ detail.kamiContent }}</dd>
              <dt>订单号</dt>
              <dd>{{ detail.orderId }}</dd>
              <dt>买家</dt>
              <dd>{{ detail.buyerUserName || detail.buyerUserId || '-' }}</dd>
              <dt>发货序号</dt>
              <dd>{{ detail.deliveryIndex }}</dd>
              <dt>发货时间</dt>
              <dd>{{ formatTime(detail.deliveryTime) }}</dd>
            </dl>
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.history-overlay {
  position: fixed;
  inset: 0;
  z-index: 2200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.28);
  backdrop-filter: blur(14px);
}

.history-dialog {
  position: relative;
  display: flex;
  flex-direction: column;
  width: min(1080px, 100%);
  max-height: min(760px, 92vh);
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 24px 72px rgba(15, 23, 42, 0.22);
}

.history-dialog__header,
.history-dialog__footer,
.history-detail__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.history-dialog__header {
  padding: 20px 24px 14px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
}

.history-dialog__header h2 {
  margin: 0;
  color: #111827;
  font-size: 20px;
}

.history-dialog__header p {
  margin: 5px 0 0;
  color: #64748b;
  font-size: 13px;
}

.history-dialog__close,
.history-detail__header button {
  border: 0;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font-size: 24px;
}

.history-dialog__filters {
  display: flex;
  gap: 8px;
  padding: 14px 24px;
}

.history-dialog__filters .native-input {
  min-width: 180px;
}

.history-dialog__time-filter {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #64748b;
  font-size: 12px;
}

.history-dialog__time-filter .native-input {
  min-width: 190px;
}

.history-dialog__body {
  min-height: 240px;
  overflow: auto;
  padding: 0 24px;
}

.history-dialog__empty {
  padding: 80px 0;
  color: #64748b;
  text-align: center;
}

.history-table {
  width: 100%;
  border-collapse: collapse;
  color: #334155;
  font-size: 13px;
}

.history-table th,
.history-table td {
  padding: 11px 10px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.07);
  text-align: left;
  white-space: nowrap;
}

.history-table th {
  position: sticky;
  top: 0;
  background: #f8fafc;
  color: #64748b;
  font-weight: 600;
}

.history-table__content {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.history-table__detail {
  border: 0;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
  white-space: nowrap;
}

.history-table__detail:disabled {
  cursor: wait;
  opacity: 0.55;
}

.history-dialog__footer {
  padding: 14px 24px 18px;
  color: #64748b;
  font-size: 13px;
}

.history-dialog__footer > div {
  display: flex;
  gap: 8px;
}

.history-detail {
  position: absolute;
  right: 24px;
  bottom: 66px;
  width: min(420px, calc(100% - 48px));
  padding: 16px;
  border: 1px solid #bfdbfe;
  border-radius: 14px;
  background: #eff6ff;
  box-shadow: 0 12px 30px rgba(30, 64, 175, 0.16);
}

.history-detail__header {
  color: #1e3a8a;
}

.history-detail dl {
  display: grid;
  grid-template-columns: 82px 1fr;
  gap: 8px 12px;
  margin: 14px 0 0;
  font-size: 13px;
}

.history-detail dt {
  color: #64748b;
}

.history-detail dd {
  margin: 0;
  color: #1e293b;
  overflow-wrap: anywhere;
}

.history-detail__secret {
  color: #0f172a;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-weight: 700;
  white-space: pre-wrap;
}

@media (max-width: 700px) {
  .history-overlay {
    align-items: flex-end;
    padding: 0;
  }

  .history-dialog {
    max-height: 92vh;
    border-radius: 20px 20px 0 0;
  }

  .history-dialog__header,
  .history-dialog__filters,
  .history-dialog__body,
  .history-dialog__footer {
    padding-left: 16px;
    padding-right: 16px;
  }

  .history-dialog__filters {
    flex-wrap: wrap;
  }

  .history-dialog__filters .native-input {
    flex: 1 1 140px;
    min-width: 0;
  }

  .history-table th,
  .history-table td {
    padding: 10px 8px;
  }
}
</style>
